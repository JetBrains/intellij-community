// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("RAW_RUN_BLOCKING")

package com.intellij.gradle.testFramework.fixtures.impl

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.gradle.testFramework.projectInfo.GradleProjectInfo
import com.intellij.gradle.testFramework.projectInfo.initProject
import com.intellij.gradle.testFramework.util.refreshAndAwait
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.eel.fs.EelFileUtils
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.utils.vfs.refreshAndGetVirtualDirectory
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.throwIfNotEmpty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.StandardCopyOption
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * The files of an immutable Gradle project.
 *
 * The fixture root holds the working tree, which is the opened project, and a baseline directory.
 * The baseline is the output of [GradleProjectInfo.initProject]. No project opens it.
 * [restore] copies each difference from the baseline back to the working tree.
 *
 * [setUp] compares the baseline with a new output of [GradleProjectInfo.initProject] in a temporary directory.
 * When they differ, or the project directory is missing, it creates the working tree again and runs the Gradle sync.
 *
 * @param relativePath the path of the fixture root, relative to the `FileTestFixture` directory.
 * @param excludedFilePatterns the glob or regexp patterns of the working tree entries that [restore] ignores.
 * @see java.nio.file.FileSystem.getPathMatcher
 */
internal class GradleImmutableProjectFiles(
  private val relativePath: String,
  private val projectInfo: GradleProjectInfo,
  excludedFilePatterns: List<String>,
) {

  private val excludedFilePatterns: List<PathMatcher> = excludedFilePatterns.map(FileSystems.getDefault()::getPathMatcher)

  private val errors = CopyOnWriteArrayList<Throwable>()
  private var isInitialized: Boolean = false

  private var rootDisposable: Disposable? = null
  private lateinit var fixtureRoot: VirtualFile
  private lateinit var fixtureProjectRoot: VirtualFile

  val root: VirtualFile get() = fixtureRoot
  val projectRoot: VirtualFile get() = fixtureProjectRoot

  private val rootPath: Path get() = fixtureRoot.toNioPath()
  private val projectRootPath: Path get() = rootPath.resolve(projectInfo.projectRelativePath)
  private val baselinePath: Path get() = rootPath.resolve(BASELINE_NAME)
  private val stateFile: Path get() = rootPath.resolve(STATE_FILE_NAME)

  fun setUp() {
    checkModuleDirectoryNames(projectInfo)

    val rootDisposable = Disposer.newDisposable("GradleImmutableProjectFiles($relativePath)")
    this.rootDisposable = rootDisposable
    fixtureRoot = createFixtureRoot(relativePath, rootDisposable)

    val oldState = readFixtureState(stateFile)
    runBlocking(Dispatchers.Default) {
      val newBaselinePath = Files.createTempDirectory("gradle-immutable-project-baseline")
      try {
        projectInfo.initProject(newBaselinePath)

        if (areCachesValid(oldState, newBaselinePath)) {
          fixtureProjectRoot = findProjectRoot()
          isInitialized = true
          restore()
        }
        else {
          createWorkingTree(newBaselinePath)
        }
      }
      finally {
        EelFileUtils.deleteRecursively(newBaselinePath)
      }
    }
  }

  fun tearDown() {
    runAll(
      { if (isInitialized) restore() },
      { throwIfNotEmpty(errors) },
      { rootDisposable?.let { Disposer.dispose(it) } },
      { rootDisposable = null },
    )
  }

  private fun areCachesValid(state: State, newBaselinePath: Path): Boolean {
    return state.isInitialized == true &&
           state.errors.isNullOrEmpty() &&
           baselinePath.isDirectory() &&
           projectRootPath.isDirectory() &&
           // The wrapper properties file starts with a timestamp.
           areTreesEqual(baselinePath, newBaselinePath) { it.endsWith(WRAPPER_PROPERTIES_PATH) }
  }

  /**
   * Replaces the baseline with a copy of [newBaselinePath], copies it to the working tree, and runs the Gradle sync.
   */
  private suspend fun createWorkingTree(newBaselinePath: Path) {
    dumpFixtureState()
    for (path in rootPath.listDirectoryEntries()) {
      if (path != stateFile) {
        EelFileUtils.deleteRecursively(path)
      }
    }
    NioFiles.copyRecursively(newBaselinePath, baselinePath)
    NioFiles.copyRecursively(baselinePath, rootPath)
    root.refreshAndAwait()

    fixtureProjectRoot = findProjectRoot()
    createGradleProjectCaches(projectRoot)

    isInitialized = true
    dumpFixtureState()
  }

  private fun findProjectRoot(): VirtualFile {
    return projectRootPath.refreshAndGetVirtualDirectory()
  }

  /**
   * Makes the working tree equal to the baseline again.
   * A failure adds an error, so the next [setUp] creates the working tree again.
   */
  fun restore() {
    try {
      runWriteActionAndWait {
        FileDocumentManager.getInstance().saveAllDocuments()
        val differences = findDifferences(baselinePath, rootPath, ::isExcluded)
        if (differences.isNotEmpty()) {
          applyBaseline(differences)
          refreshWorkingTree(differences)
        }
      }
    }
    catch (exception: Throwable) {
      rethrowControlFlowException(exception)
      addIllegalOperationError("Cannot restore the fixture files: $exception")
      throw exception
    }
  }

  /**
   * Changes the working tree on disk only. The VFS learns about the changes from [refreshWorkingTree].
   *
   * Deletes before it creates. A name that differs only in case is thus free on a file system that ignores the case.
   */
  private fun applyBaseline(differences: List<FileTreeDifference>) {
    val extraEntries = ArrayList<Path>()
    val missingDirectories = ArrayList<Path>()
    val files = ArrayList<Path>()
    for (difference in differences) {
      when (difference) {
        is FileTreeDifference.ExtraEntry -> extraEntries.add(difference.relativePath)
        is FileTreeDifference.MissingDirectory -> missingDirectories.add(difference.relativePath)
        is FileTreeDifference.MissingFile, is FileTreeDifference.ChangedFile -> files.add(difference.relativePath)
      }
    }
    for (relativePath in extraEntries) {
      EelFileUtils.deleteRecursively(rootPath.resolve(relativePath))
    }
    for (relativePath in missingDirectories) {
      Files.createDirectories(rootPath.resolve(relativePath))
    }
    for (relativePath in files) {
      Files.copy(baselinePath.resolve(relativePath), rootPath.resolve(relativePath), StandardCopyOption.REPLACE_EXISTING)
    }
  }

  /**
   * Refreshes the entries that [applyBaseline] changed. An excluded entry keeps its VFS state.
   *
   * The refresh of an entry does not compare its name. Only the scan of its directory finds a name that differs only in case.
   */
  @RequiresWriteLock
  private fun refreshWorkingTree(differences: List<FileTreeDifference>) {
    val paths = differences.mapTo(LinkedHashSet()) { rootPath.resolve(it.relativePath) }
    val directories = paths.mapTo(LinkedHashSet()) { it.parent }
    VfsUtil.markDirtyAndRefresh(false, false, false, *directories.toTypedArray())
    VfsUtil.markDirtyAndRefresh(false, true, false, *paths.toTypedArray())
  }

  private fun isExcluded(relativePath: Path): Boolean {
    if (relativePath.nameCount == 1 && relativePath.name in RESERVED_NAMES) {
      return true
    }
    val path = rootPath.resolve(relativePath)
    return excludedFilePatterns.any { it.matches(path) }
  }

  /**
   * Adds an error, which closes the project after the test. The next [setUp] then creates the working tree again.
   */
  fun addIllegalOperationError(message: String) {
    errors.add(Exception(message))
    dumpFixtureState()
  }

  fun hasErrors(): Boolean {
    return errors.isNotEmpty()
  }

  private fun dumpFixtureState() {
    val errors = errors.map { it.message ?: it.toString() }
    writeFixtureState(stateFile, State(isInitialized, errors))
  }

  companion object {

    private const val BASELINE_NAME = "_baseline"
    private const val WRAPPER_PROPERTIES_PATH = "gradle/wrapper/gradle-wrapper.properties"

    private val RESERVED_NAMES = setOf(BASELINE_NAME, STATE_FILE_NAME)

    private fun checkModuleDirectoryNames(projectInfo: GradleProjectInfo) {
      for (moduleInfo in projectInfo.modules) {
        val modulePath = Path.of(moduleInfo.relativePath).normalize()
        check(modulePath.getName(0).name !in RESERVED_NAMES) {
          "The module directory '${moduleInfo.relativePath}' has a name that the fixture reserves: $RESERVED_NAMES"
        }
      }
      for (compositeInfo in projectInfo.composites) {
        checkModuleDirectoryNames(compositeInfo)
      }
    }
  }
}

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.externalSystem.autoimport.changes.vfs.VirtualFileChangesListener
import com.intellij.openapi.externalSystem.autoimport.changes.vfs.VirtualFileChangesListener.Companion.installBulkVirtualFileListener
import com.intellij.openapi.externalSystem.util.runReadAction
import com.intellij.openapi.externalSystem.util.runWriteActionAndGet
import com.intellij.openapi.externalSystem.util.runWriteActionAndWait
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.*
import com.intellij.openapi.vfs.*
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.utils.editor.reloadFromDisk
import com.intellij.testFramework.utils.vfs.*
import com.intellij.util.throwIfNotEmpty
import com.intellij.util.xmlb.XmlSerializer
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.gradle.testFramework.configuration.TestFilesConfigurationImpl
import org.jetbrains.plugins.gradle.testFramework.fixtures.FileTestFixture
import org.jetbrains.plugins.gradle.testFramework.util.onFailureCatching
import org.jetbrains.plugins.gradle.testFramework.util.refreshAndWait
import org.jetbrains.plugins.gradle.testFramework.util.withSuppressedErrors
import java.nio.file.Path
import java.util.*

internal class FileTestFixtureImpl(
  private val relativePath: String,
  private val configure: FileTestFixture.Builder.() -> Unit
) : FileTestFixture {

  private var isInitialized: Boolean = false
  private var isSuppressedErrors: Boolean = false
  private lateinit var errors: MutableList<Throwable>
  private lateinit var snapshots: MutableMap<Path, Optional<String>>
  private lateinit var excludedFiles: Set<Path>

  private lateinit var testRootDisposable: Disposable
  private lateinit var fixtureRoot: VirtualFile
  private lateinit var fixtureStateFile: VirtualFile

  override val root: VirtualFile get() = fixtureRoot

  override fun setUp() {
    isInitialized = false
    isSuppressedErrors = false
    errors = ArrayList()
    snapshots = HashMap()

    testRootDisposable = Disposer.newDisposable()

    fixtureRoot = createFixtureRoot(relativePath)
    fixtureStateFile = createFixtureStateFile()

    val oldState = readFixtureState()

    installFixtureFilesWatcher()

    val configuration = createFixtureConfiguration()

    excludedFiles = configuration.excludedFiles
      .map { root.toNioPath().getResolvedPath(it) }
      .toSet()

    withSuppressedErrors {
      repairFixtureCaches(oldState)
    }
    dumpFixtureState()

    withSuppressedErrors {
      runBlocking {
        configureFixtureCaches(configuration)
      }
    }

    isInitialized = true
    dumpFixtureState()
  }

  override fun tearDown() {
    runAll(
      { rollbackAll() },
      { throwIfNotEmpty(getErrors()) },
      { Disposer.dispose(testRootDisposable) }
    )
  }

  private fun createFixtureRoot(relativePath: String): VirtualFile {
    val systemPath = Path.of(PathManager.getSystemPath())
    val systemDirectory = systemPath.findOrCreateDirectory().refreshAndGetVirtualDirectory()
    val fixtureRoot = "FileTestFixture/$relativePath"
    VfsRootAccess.allowRootAccess(testRootDisposable, systemDirectory.path + "/$fixtureRoot")
    return runWriteActionAndGet {
      systemDirectory.findOrCreateDirectory(fixtureRoot)
    }
  }

  private fun createFixtureStateFile(): VirtualFile {
    return runWriteActionAndGet {
      root.findOrCreateFile("_FileTestFixture.xml")
    }
  }

  private fun repairFixtureCaches(state: State) {
    val isInitialized = state.isInitialized ?: false
    val isSuppressedErrors = state.isSuppressedErrors ?: false
    val errors = state.errors ?: emptyList()
    val snapshots = state.snapshots ?: emptyMap()
    if (!isInitialized || isSuppressedErrors || errors.isNotEmpty()) {
      invalidateFixtureCaches()
    }
    else {
      for ((path, text) in snapshots) {
        revertFile(path.toNioPath(), text)
      }
    }
  }

  private fun invalidateFixtureCaches() {
    runWriteActionAndWait {
      root.deleteChildrenRecursively { it != fixtureStateFile }
    }
  }

  private fun dumpFixtureState() {
    val errors = errors.map { it.message ?: it.toString() }
    val snapshots = snapshots.entries.associate { (k, v) -> k.toCanonicalPath() to v.orElse(null) }
    writeFixtureState(State(isInitialized, isSuppressedErrors, errors, snapshots))
  }

  private fun readFixtureState(): State {
    return runCatching {
      runReadAction {
        val element = JDOMUtil.load(fixtureStateFile.toNioPath())
        XmlSerializer.deserialize(element, State::class.java)
      }
    }.getOrElse { State() }
  }

  private fun writeFixtureState(state: State) {
    runWriteActionAndWait {
      val element = XmlSerializer.serialize(state)
      JDOMUtil.write(element, fixtureStateFile.toNioPath())
    }
  }

  private fun createFixtureConfiguration(): Configuration {
    return Configuration().apply(configure)
  }

  private suspend fun configureFixtureCaches(configuration: Configuration) {
    runCatching {
      if (!configuration.areContentsEqual(root)) {
        invalidateFixtureCaches()
        configuration.createFiles(root)
      }
      root.refreshAndWait()
    }
      .onFailureCatching { invalidateFixtureCaches() }
      .getOrThrow()
  }

  override fun isModified(): Boolean {
    return snapshots.isNotEmpty()
  }

  override fun hasErrors(): Boolean {
    return getErrors().isNotEmpty()
  }

  private fun getErrors(): List<Throwable> {
    runBlocking { root.refreshAndWait() }
    return errors
  }

  override fun snapshot(relativePath: String) {
    snapshot(root.toNioPath().getResolvedPath(relativePath))
  }

  private fun snapshot(path: Path) {
    if (path in snapshots) return
    val text = getTextContent(path)
    snapshots[path] = Optional.ofNullable(text)
    dumpFixtureState()
  }

  override fun rollbackAll() {
    for (path in snapshots.keys.toSet()) {
      rollback(path)
    }
  }

  override fun rollback(relativePath: String) {
    rollback(root.toNioPath().getResolvedPath(relativePath))
  }

  private fun rollback(path: Path) {
    val text = requireNotNull(snapshots[path]) { "Cannot find snapshot for $path" }
    revertFile(path, text.orElse(null))
    snapshots.remove(path)
    dumpFixtureState()
  }

  private fun revertFile(path: Path, text: String?) {
    runWriteActionAndWait {
      if (text != null) {
        path.findOrCreateFile()
        val file = path.refreshAndGetVirtualFile()
        file.findDocument()?.reloadFromDisk()
        file.writeText(text)
      }
      else {
        val file = path.refreshAndFindVirtualFile()
        file?.deleteRecursively()
      }
    }
  }

  private fun getTextContent(path: Path): String? {
    val file = path.refreshAndFindVirtualFile() ?: return null
    return file.readText()
  }

  override fun suppressErrors(isSuppressedErrors: Boolean) {
    this.isSuppressedErrors = isSuppressedErrors
    dumpFixtureState()
  }

  override fun addIllegalOperationError(message: String) {
    if (!isSuppressedErrors) {
      errors.add(Exception(message))
      dumpFixtureState()
    }
  }

  private fun installFixtureFilesWatcher() {
    val listener = object : VirtualFileChangesListener {
      override fun isProcessRecursively(): Boolean = true

      override fun isRelevant(file: VirtualFile, event: VFileEvent): Boolean {
        return !file.isDirectory &&
               file != fixtureStateFile &&
               VfsUtil.isAncestor(root, file, false) &&
               run {
                 val path = file.toNioPath() // file can have no nio.Path, check root is its ancestor firstly

                 path !in snapshots &&
                 path !in excludedFiles &&
                 excludedFiles.all { !FileUtil.isAncestor(it, path, false) }
               }
      }

      override fun updateFile(file: VirtualFile, event: VFileEvent) {
        addIllegalOperationError("Unexpected project modification $event")
      }
    }
    installBulkVirtualFileListener(listener, testRootDisposable)
  }

  private class Configuration
    : TestFilesConfigurationImpl(),
      FileTestFixture.Builder {

    val excludedFiles = HashSet<String>()

    override fun excludeFiles(vararg relativePath: String) {
      excludedFiles.addAll(relativePath)
    }
  }

  private data class State(
    var isInitialized: Boolean? = null,
    var isSuppressedErrors: Boolean? = null,
    var errors: List<String>? = null,
    var snapshots: Map<String, String?>? = null
  )
}
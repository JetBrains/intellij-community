// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.OnlyIndexableFilesAreLoadedIntoVfsOnDirectoryCreationTest.Companion.collectFilesLoadedIntoVfsBeforeListenersRuns
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity
import com.intellij.platform.workspace.jps.entities.InheritedSdkDependency
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleSourceDependency
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.testFramework.TestObservation
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.testFramework.useProjectAsync
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds

@RegistryKey("vfs.refresh.iterate.included.files.under.exclude", "true")
@TestApplication
class VfsRefreshLoadingFilesToVfsTest {
  @JvmField
  @RegisterExtension
  val rootDir: TempDirectoryExtension = TempDirectoryExtension()

  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `new content dir under excluded parent is loaded into vfs after refresh`(): Unit = runBlocking {
    stageNestedExcludedLayout()

    withOpenedProject { project, rootVirtualFile ->
      val outerRoot = rootDir.rootPath.resolve("outer")
      val excludedRoot = outerRoot.resolve("build")
      val generatedRoot = excludedRoot.resolve("generated")

      addModuleWithContentRootUnderExplicitExclude(project, outerRoot, excludedRoot, generatedRoot)

      generatedRoot.createDirectories()
      generateFiles(generatedRoot, packagePrefix = "com/example")

      delay(1.seconds)
      val filesLoadedIntoVfs = collectFilesLoadedIntoVfsBeforeListenersRuns(rootVirtualFile, disposable)
      assertSubtreeLoadedIntoVfs(filesLoadedIntoVfs, rootVirtualFile, relativeRoot = "outer/build/generated", packagePrefix = "com/example")
    }
  }

  @Test
  fun `new content dir under excluded parent is loaded into vfs after refresh when appear simultaneously`(): Unit = runBlocking {
    stageNestedExcludedLayout()

    withOpenedProject { project, rootVirtualFile ->
      val root = rootDir.rootPath
      val excludedDir = rootDir.rootPath.resolve("other/build")
      val generatedRoot = excludedDir.resolve("inner/generated")

      addModuleWithContentRootUnderExcludedPattern(project, root, excludedDir.name, generatedRoot)

      generatedRoot.createDirectories()
      generateFiles(generatedRoot, packagePrefix = "com/example")

      delay(1.seconds)

      val vfuManager = project.workspaceModel.getVirtualFileUrlManager()
      val excludedDirVfu = vfuManager.findByUrl(excludedDir.toIdeUrl())
      assertThat(excludedDirVfu).isNull()

      val filesLoadedIntoVfs = collectFilesLoadedIntoVfsBeforeListenersRuns(rootVirtualFile, disposable)
      assertSubtreeLoadedIntoVfs(filesLoadedIntoVfs,
                                 rootVirtualFile,
                                 relativeRoot = generatedRoot.relativeTo(root).pathString,
                                 packagePrefix = "com/example")
    }
  }

  @Test
  fun `new dir under content root is loaded into vfs after refresh`(): Unit = runBlocking {
    stageFlatContentLayout()

    withOpenedProject { project, rootVirtualFile ->
      addModuleWithContentRoot(project, rootDir.rootPath.resolve("single"))

      val newPackageRoot = rootDir.rootPath.resolve("single/newpkg")
      newPackageRoot.createDirectories()
      generateFiles(newPackageRoot, packagePrefix = "")

      delay(1.seconds)
      val filesLoadedIntoVfs = collectFilesLoadedIntoVfsBeforeListenersRuns(rootVirtualFile, disposable)
      assertSubtreeLoadedIntoVfs(filesLoadedIntoVfs, rootVirtualFile, relativeRoot = "single/newpkg", packagePrefix = "")
    }
  }

  private suspend fun withOpenedProject(action: suspend (Project, VirtualFile) -> Unit) {
    rootDir.newDirectoryPath(".idea") // forces project.basePath to match project root which may affect how VFS files are refreshed
    val options = OpenProjectTask { createModule = false }
    ProjectUtil.openOrImportAsync(rootDir.rootPath, options)!!.useProjectAsync { project ->
      TestObservation.awaitConfiguration(project)
      val rootVirtualFile = findVirtualFile(rootDir.rootPath)
      rootVirtualFile.children // load all children to trigger full sync later
      action(project, rootVirtualFile)
    }
  }

  private fun findVirtualFile(path: Path): VirtualFile {
    return checkNotNull(VfsTestUtil.findFileByCaseSensitivePath(path.pathString)) {
      "VirtualFile not found for path: $path"
    }
  }

  private fun assertSubtreeLoadedIntoVfs(
    filesLoadedIntoVfs: List<VirtualFile>,
    rootVirtualFile: VirtualFile,
    relativeRoot: String,
    packagePrefix: String,
  ) {
    val projectRoot = rootVirtualFile.toNioPath()
    val filesInVfs = filesLoadedIntoVfs.associateBy { file ->
      projectRoot.relativize(file.toNioPath()).invariantSeparatorsPathString
    }
    val subtree = expectedSubtree(relativeRoot, packagePrefix)

    assertThat(filesInVfs.keys)
      .describedAs("Expected `$relativeRoot` subtree to be loaded into VFS after refresh")
      .containsAll(subtree.paths)

    assertThat(subtree.directories).allSatisfy { directoryPath ->
      val directory = filesInVfs.getValue(directoryPath) as NewVirtualFile
      assertThat(directory.allChildrenLoaded())
        .describedAs("Expected VFS children to be cached for `$directoryPath`")
        .isTrue()
    }
  }

  private fun expectedSubtree(relativeRoot: String, packagePrefix: String): ExpectedSubtree {
    val directories = linkedSetOf(relativeRoot)
    val paths = linkedSetOf(relativeRoot)
    var packageRoot = relativeRoot
    if (packagePrefix.isNotEmpty()) {
      for (segment in packagePrefix.split('/')) {
        packageRoot = "$packageRoot/$segment"
        directories.add(packageRoot)
        paths.add(packageRoot)
      }
    }

    repeat(SUBDIR_COUNT) { pkg ->
      val packageDir = "$packageRoot/pkg$pkg"
      directories.add(packageDir)
      paths.add(packageDir)
      repeat(FILES_PER_SUBDIR) { idx ->
        paths.add("$packageDir/Generated${pkg}_$idx.java")
      }
    }
    return ExpectedSubtree(directories, paths)
  }

  private fun generateFiles(root: Path, packagePrefix: String) {
    val pathPrefix = if (packagePrefix.isEmpty()) "" else "$packagePrefix/"
    val dotPrefix = if (packagePrefix.isEmpty()) "" else "${packagePrefix.replace('/', '.')}."
    repeat(SUBDIR_COUNT) { pkg ->
      val packageDir = root.resolve("${pathPrefix}pkg$pkg")
      packageDir.createDirectories()
      repeat(FILES_PER_SUBDIR) { idx ->
        packageDir.resolve("Generated${pkg}_$idx.java").writeText(
          "package ${dotPrefix}pkg$pkg; public final class Generated${pkg}_$idx {}\n"
        )
      }
    }
  }

  private suspend fun addModuleWithContentRoot(project: Project, contentRoot: Path) {
    val urlManager = project.workspaceModel.getVirtualFileUrlManager()
    project.workspaceModel.update("add single content root") { storage ->
      storage.addEntity(ModuleEntity("single", moduleDependencies(), NonPersistentEntitySource) {
        contentRoots = listOf(ContentRootEntity(contentRoot.toVirtualFileUrl(urlManager), emptyList(), NonPersistentEntitySource))
      })
    }
  }

  private suspend fun addModuleWithContentRootUnderExplicitExclude(
    project: Project,
    contentRoot: Path,
    excludedRoot: Path,
    nestedContentRoot: Path,
  ) {
    val urlManager = project.workspaceModel.getVirtualFileUrlManager()
    project.workspaceModel.update("add content root under excluded dir") { storage ->
      storage.addEntity(ModuleEntity("module", moduleDependencies(), NonPersistentEntitySource) {
        contentRoots = listOf(
          ContentRootEntity(contentRoot.toVirtualFileUrl(urlManager), emptyList(), NonPersistentEntitySource) {
            excludedUrls = listOf(ExcludeUrlEntity(excludedRoot.toVirtualFileUrl(urlManager), NonPersistentEntitySource))
          },
          ContentRootEntity(nestedContentRoot.toVirtualFileUrl(urlManager), emptyList(), NonPersistentEntitySource),
        )
      })
    }
  }

  private suspend fun addModuleWithContentRootUnderExcludedPattern(
    project: Project,
    contentRoot: Path,
    excludedPattern: String,
    nestedContentRoot: Path,
  ) {
    val urlManager = project.workspaceModel.getVirtualFileUrlManager()
    project.workspaceModel.update("add content root under excluded pattern") { storage ->
      storage.addEntity(ModuleEntity("module", moduleDependencies(), NonPersistentEntitySource) {
        contentRoots = listOf(
          ContentRootEntity(contentRoot.toVirtualFileUrl(urlManager), listOf(excludedPattern), NonPersistentEntitySource),
          ContentRootEntity(nestedContentRoot.toVirtualFileUrl(urlManager), emptyList(), NonPersistentEntitySource),
        )
      })
    }
  }

  private fun stageNestedExcludedLayout() {
    val buildDir = rootDir.rootPath.resolve("outer/build")
    buildDir.createDirectories()
    buildDir.resolve("placeholder.txt").writeText("seed\n")
  }

  private fun stageFlatContentLayout() {
    val contentDir = rootDir.rootPath.resolve("single")
    contentDir.createDirectories()
    contentDir.resolve("placeholder.txt").writeText("seed\n")
  }

  private data class ExpectedSubtree(
    val directories: Set<String>,
    val paths: Set<String>,
  )

  companion object {
    private const val SUBDIR_COUNT = 10
    private const val FILES_PER_SUBDIR = 20
  }
}

private fun moduleDependencies() = listOf(InheritedSdkDependency, ModuleSourceDependency)

private fun Path.toIdeUrl(): String = VfsUtilCore.pathToUrl(pathString)

private fun Path.toVirtualFileUrl(urlManager: VirtualFileUrlManager) = urlManager.getOrCreateFromUrl(toIdeUrl())

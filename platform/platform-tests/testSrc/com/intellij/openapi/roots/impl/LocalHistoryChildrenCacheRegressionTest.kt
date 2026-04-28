// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.OnlyIndexableFilesAreLoadedIntoVfsOnDirectoryCreationTest.Companion.collectFilesLoadedIntoVfsBeforeListenersRuns
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.testFramework.TestObservation
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.testFramework.useProjectAsync
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.pathString
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds

@TestApplication
class LocalHistoryChildrenCacheRegressionTest {
  @JvmField
  @RegisterExtension
  val rootDir: TempDirectoryExtension = TempDirectoryExtension()

  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `new content dir under excluded parent is loaded into vfs after refresh`(): Unit = runBlocking {
    stageNestedExcludedLayout()

    withOpenedProject { _, rootVirtualFile ->
      val generatedRoot = rootDir.rootPath.resolve("outer/build/generated")
      generatedRoot.createDirectories()
      generateFiles(generatedRoot, packagePrefix = "com/example")

      delay(1.seconds)
      val filesLoadedIntoVfs = collectFilesLoadedIntoVfsBeforeListenersRuns(rootVirtualFile, disposable)
      assertSubtreeLoadedIntoVfs(filesLoadedIntoVfs, rootVirtualFile, relativeRoot = "outer/build/generated", packagePrefix = "com/example")
    }
  }

  @Test
  fun `new dir under content root is loaded into vfs after refresh`(): Unit = runBlocking {
    stageFlatContentLayout()

    withOpenedProject { _, rootVirtualFile ->
      val newPackageRoot = rootDir.rootPath.resolve("single/newpkg")
      newPackageRoot.createDirectories()
      generateFiles(newPackageRoot, packagePrefix = "")

      delay(1.seconds)
      val filesLoadedIntoVfs = collectFilesLoadedIntoVfsBeforeListenersRuns(rootVirtualFile, disposable)
      assertSubtreeLoadedIntoVfs(filesLoadedIntoVfs, rootVirtualFile, relativeRoot = "single/newpkg", packagePrefix = "")
    }
  }

  private suspend fun withOpenedProject(action: suspend (Project, VirtualFile) -> Unit) {
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

  private fun stageNestedExcludedLayout() {
    rootDir.newDirectoryPath(".idea")
    rootDir.rootPath.resolve(".idea/modules.xml").writeText(NESTED_MODULES_XML)
    rootDir.rootPath.resolve("outer.iml").writeText(OUTER_IML)
    rootDir.rootPath.resolve("inner.iml").writeText(INNER_IML)

    val buildDir = rootDir.rootPath.resolve("outer/build")
    buildDir.createDirectories()
    buildDir.resolve("placeholder.txt").writeText("seed\n")
  }

  private fun stageFlatContentLayout() {
    rootDir.newDirectoryPath(".idea")
    rootDir.rootPath.resolve(".idea/modules.xml").writeText(FLAT_MODULES_XML)
    rootDir.rootPath.resolve("single.iml").writeText(SINGLE_IML)

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

    private val NESTED_MODULES_XML = $$"""
      <?xml version="1.0" encoding="UTF-8"?>
      <project version="4">
        <component name="ProjectModuleManager">
          <modules>
            <module fileurl="file://$PROJECT_DIR$/outer.iml" filepath="$PROJECT_DIR$/outer.iml" />
            <module fileurl="file://$PROJECT_DIR$/inner.iml" filepath="$PROJECT_DIR$/inner.iml" />
          </modules>
        </component>
      </project>
    """.trimIndent()

    private val FLAT_MODULES_XML = $$"""
      <?xml version="1.0" encoding="UTF-8"?>
      <project version="4">
        <component name="ProjectModuleManager">
          <modules>
            <module fileurl="file://$PROJECT_DIR$/single.iml" filepath="$PROJECT_DIR$/single.iml" />
          </modules>
        </component>
      </project>
    """.trimIndent()

    private val OUTER_IML = $$"""
      <?xml version="1.0" encoding="UTF-8"?>
      <module type="JAVA_MODULE" version="4">
        <component name="NewModuleRootManager" inherit-compiler-output="true">
          <exclude-output />
          <content url="file://$MODULE_DIR$/outer">
            <excludeFolder url="file://$MODULE_DIR$/outer/build" />
          </content>
          <orderEntry type="inheritedJdk" />
          <orderEntry type="sourceFolder" forTests="false" />
        </component>
      </module>
    """.trimIndent()

    private val INNER_IML = $$"""
      <?xml version="1.0" encoding="UTF-8"?>
      <module type="JAVA_MODULE" version="4">
        <component name="NewModuleRootManager" inherit-compiler-output="true">
          <exclude-output />
          <content url="file://$MODULE_DIR$/outer/build/generated">
            <sourceFolder url="file://$MODULE_DIR$/outer/build/generated" isTestSource="false" />
          </content>
          <orderEntry type="inheritedJdk" />
          <orderEntry type="sourceFolder" forTests="false" />
        </component>
      </module>
    """.trimIndent()

    private val SINGLE_IML = $$"""
      <?xml version="1.0" encoding="UTF-8"?>
      <module type="JAVA_MODULE" version="4">
        <component name="NewModuleRootManager" inherit-compiler-output="true">
          <exclude-output />
          <content url="file://$MODULE_DIR$/single">
            <sourceFolder url="file://$MODULE_DIR$/single" isTestSource="false" />
          </content>
          <orderEntry type="inheritedJdk" />
          <orderEntry type="sourceFolder" forTests="false" />
        </component>
      </module>
    """.trimIndent()
  }
}

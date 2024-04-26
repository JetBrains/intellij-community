// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing


import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.OrderEntryUtil
import com.intellij.openapi.roots.impl.indexing.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.assertions.Assertions.assertThat
import org.junit.Test

@RunsInEdt
class IndexableFilesIndexBasicOriginsTest : IndexableFilesIndexOriginsTestBase() {
  @Test
  fun `origins of files from a module content root`() {
    lateinit var contentFile: FileSpec
    lateinit var contentRoot: ModuleRootSpec
    lateinit var sourceFile: FileSpec
    lateinit var sourceRoot: ModuleRootSpec
    lateinit var testFile: FileSpec
    lateinit var testRoot: ModuleRootSpec
    lateinit var resourceFile: FileSpec
    lateinit var resourceRoot: ModuleRootSpec
    lateinit var testResourceFile: FileSpec
    lateinit var testResourceRoot: ModuleRootSpec
    val module = projectModelRule.createJavaModule("moduleName") {
      contentRoot = content("contentRoot") {
        contentFile = file("ContentFile.java", "class ContentFile {}")
        sourceRoot = source("sources") {
          sourceFile = file("SourceFile.java", "class SourceFile {}")
        }
        resourceRoot = resourceRoot("resources") {
          resourceFile = file("resource.txt", "no data")
        }
        testRoot = testSourceRoot("tests") {
          testFile = file("Test.java", "class Test {}")
        }
        testResourceRoot = testResourceRoot("testResources") {
          testResourceFile = file("testResource.txt", "no data")
        }
      }
    }
    assertEveryFileOrigin(createModuleContentOrigin(contentRoot, module), contentFile, contentRoot)
    assertOrigin(createModuleContentOrigin(sourceRoot, module), sourceFile)
    assertOrigin(createModuleContentOrigin(testRoot, module), testFile)
    assertOrigin(createModuleContentOrigin(testResourceRoot, module), testResourceFile)
    assertOrigin(createModuleContentOrigin(resourceRoot, module), resourceFile)
  }

  @Test
  fun `origins of files from a Library`() {
    val libraryRoot = tempDirectory.newVirtualDirectory("library")
    lateinit var classFile: FileSpec
    lateinit var classesDir: DirectorySpec
    lateinit var excludedClassesDir: DirectorySpec
    lateinit var excludedClassFile: FileSpec

    lateinit var sourceFile: FileSpec
    lateinit var sourcesDir: DirectorySpec
    lateinit var excludedSourcesDir: DirectorySpec
    lateinit var excludedSourceFile: FileSpec

    buildDirectoryContent(libraryRoot) {
      dir("library") {
        classesDir = dir("classes") {
          excludedClassesDir = dir("excluded") {
            excludedClassFile = file("ExcludedClassFile.java", "class ExcludedClassFile {}")
          }
          classFile = file("ClassFile.java", "class ClassFile {}")
        }
        sourcesDir = dir("sources") {
          excludedSourcesDir = dir("excluded") {
            excludedSourceFile = file("ExcludedSourceFile.java", "class ExcludedSourceFile {}")
          }
          sourceFile = file("SourceFile.java", "class SourceFile {}")
        }
      }
    }
    val library = projectModelRule.addProjectLevelLibrary("libraryName") { model ->
      model.addRoot(classesDir.file, OrderRootType.CLASSES)
      model.addRoot(sourcesDir.file, OrderRootType.SOURCES)
      model.addExcludedRoot(excludedClassesDir.file.url)
      model.addExcludedRoot(excludedSourcesDir.file.url)
    }
    assertNoOrigin(classFile, classesDir, sourceFile, sourcesDir,
                   excludedClassesDir, excludedClassFile, excludedSourcesDir, excludedSourceFile)
    val module = projectModelRule.createModule()
    runWriteAction { OrderEntryUtil.addLibraryToRoots(module, library, DependencyScope.RUNTIME, false) }
    val libraryOrigin = createLibraryOrigin(library)
    assertEveryFileOrigin(libraryOrigin, classesDir, classFile, sourcesDir, sourceFile)
    assertNoOrigin(excludedClassesDir, excludedClassFile, excludedSourcesDir, excludedSourceFile)
    assertOrigins(listOf(libraryOrigin), listOf(classFile, classesDir, sourceFile, sourcesDir))
  }

  @Test
  fun `origins of files from an SDK`() {
    val sdkRoot = tempDirectory.newVirtualDirectory("sdkRoot")
    lateinit var classFile: FileSpec
    lateinit var classesDir: DirectorySpec

    lateinit var sourceFile: FileSpec
    lateinit var sourcesDir: DirectorySpec

    buildDirectoryContent(sdkRoot) {
      dir("sdk") {
        classesDir = dir("classes") {
          classFile = file("ClassFile.java", "class ClassFile {}")
        }
        sourcesDir = dir("sources") {
          sourceFile = file("SourceFile.java", "class SourceFile {}")
        }
      }
    }

    val sdk = projectModelRule.addSdk("sdk") { sdkModificator ->
      sdkModificator.addRoot(classesDir.file, OrderRootType.CLASSES)
      sdkModificator.addRoot(sourcesDir.file, OrderRootType.SOURCES)
    }

    assertNoOrigin(classesDir, classFile, sourcesDir, sourceFile)

    val module = projectModelRule.createModule()
    ModuleRootModificationUtil.setModuleSdk(module, sdk)

    val sdkOrigin = createSdkOrigin(sdk)
    assertEveryFileOrigin(sdkOrigin, classesDir, classFile, sourcesDir, sourceFile)
    assertOrigins(listOf(sdkOrigin), listOf(classFile, classesDir, sourcesDir, sourceFile))
  }

  @Test
  fun `origins of files from IndexableSetContributor`() {
    val projectFilesRoot = tempDirectory.newVirtualDirectory("projectFilesRoot")
    lateinit var additionalProjectRoots: DirectorySpec
    lateinit var additionalProjectRootJava: FileSpec
    buildDirectoryContent(projectFilesRoot) {
      additionalProjectRoots = dir("additionalRoots") {
        additionalProjectRootJava = file("AdditionalRoot.java", "class AdditionalRoot {}")
      }
    }

    lateinit var additionalRoots: DirectorySpec
    lateinit var additionalRootJava: FileSpec

    val appFilesRoot = tempDirectory.newVirtualDirectory("appFilesRoot")
    buildDirectoryContent(appFilesRoot) {
      additionalRoots = dir("additionalRoots") {
        additionalRootJava = file("AdditionalRoot.java", "class AdditionalRoot {}")
      }
    }
    val additionalProjectRootsFile = additionalProjectRoots.file  // load VFS synchronously outside read action
    val additionalRootsFile = additionalRoots.file                // load VFS synchronously outside read action
    val contributor = object : IndexableSetContributor() {
      override fun getAdditionalProjectRootsToIndex(project: Project): Set<VirtualFile> =
        setOf(additionalProjectRootsFile)

      override fun getAdditionalRootsToIndex(): Set<VirtualFile> =
        setOf(additionalRootsFile)
    }
    maskIndexableSetContributors(contributor)

    assertEveryFileOrigin(createIndexableSetOrigin(contributor, null), additionalRoots, additionalRootJava)
    assertEveryFileOrigin(createIndexableSetOrigin(contributor, project), additionalProjectRoots, additionalProjectRootJava)
  }

  @Test
  fun `origins of files from AdditionalLibraryRootsProvider`() {
    val libraryRoot = tempDirectory.newVirtualDirectory("libraryRoot")

    lateinit var sourceFile: FileSpec
    lateinit var sourcesDir: DirectorySpec
    lateinit var sourcesExcludedDir: DirectorySpec
    lateinit var sourceFileExcludedByCondition: FileSpec

    lateinit var binaryFile: FileSpec
    lateinit var binariesDir: DirectorySpec
    lateinit var binariesExcludedDir: DirectorySpec

    buildDirectoryContent(libraryRoot) {
      sourcesDir = dir("sources") {
        sourceFile = file("SourceClass.java", "class SourceClass {}")
        sourceFileExcludedByCondition = file("SourceFileExcludedByCondition.java", "class SourceFileExcludedByCondition {}")
        // Must not be indexed because it is listed in excluded roots of SyntheticLibrary.
        sourcesExcludedDir = dir("excluded") {
          file("SourceExcluded.java", "class SourceExcluded {}")
        }
      }
      binariesDir = dir("binaries") {
        binaryFile = file("BinaryClass.java", "class BinaryClass {}")
        // Must not be indexed because it is listed in excluded roots of SyntheticLibrary.
        binariesExcludedDir = dir("excluded") {
          file("BinaryExcluded.java", "class BinaryExcluded {}")
        }
      }
    }

    lateinit var moduleExcludedSourcesDir: DirectorySpec
    lateinit var reIncludedSource: FileSpec

    lateinit var moduleExcludedBinariesDir: DirectorySpec
    lateinit var reIncludedBinary: FileSpec

    projectModelRule.createJavaModule("moduleName") {
      content("contentRoot") {
        // Roots provided by AdditionalLibraryRootsProvider are considered source roots beneath excluded roots.
        // So these files must be indexed despite being excluded by the module's exclude root.
        moduleExcludedSourcesDir = excluded("excludedSources") {
          reIncludedSource = file("ExcludedSourcesClass.java", "class ExcludedSourcesClass {}")
        }
        moduleExcludedBinariesDir = excluded("excludedBinaries") {
          reIncludedBinary = file("ExcludedBinariesClass.java", "class ExcludedBinariesClass {}")
        }
      }
    }
    val excludedFile = sourceFileExcludedByCondition.file              // load VFS synchronously outside read action
    val syntheticLibrary = createAndSetAdditionalLibraryRootProviderWithSingleLibrary(
      listOf(sourcesDir.file, moduleExcludedSourcesDir.file),
      listOf(binariesDir.file, moduleExcludedBinariesDir.file),
      setOf(sourcesExcludedDir.file, binariesExcludedDir.file)
    ) { file -> file == excludedFile }

    val libraryOrigin = createSyntheticLibraryOrigin(syntheticLibrary)
    assertEveryFileOrigin(libraryOrigin, sourceFile, sourcesDir, binariesDir, binaryFile,
                          reIncludedSource, moduleExcludedSourcesDir, reIncludedBinary, moduleExcludedBinariesDir)
    assertNoOrigin(sourceFileExcludedByCondition, sourcesExcludedDir, binariesExcludedDir)
    assertOrigins(
      listOf(libraryOrigin),
      listOf(
        sourceFile, sourcesDir, binariesDir, binaryFile, sourceFileExcludedByCondition, sourcesExcludedDir, binariesExcludedDir,
        reIncludedSource, moduleExcludedSourcesDir, reIncludedBinary, moduleExcludedBinariesDir
      ))
  }

  @Test
  fun `origins of files from unloaded modules`() {
    lateinit var contentFileToUnload: FileSpec
    lateinit var contentRootToUnload: ModuleRootSpec
    lateinit var contentFileToRetain: FileSpec
    lateinit var contentRootToRetain: ModuleRootSpec
    val moduleToUnload = projectModelRule.createJavaModule("moduleToUnload") {
      contentRootToUnload = content("contentRoot") {
        contentFileToUnload = file("ContentFileToUnload.java", "class ContentFileToUnload {}")
      }
    }
    val moduleToRetain = projectModelRule.createJavaModule("moduleToRetail") {
      contentRootToRetain = content("contentRoot") {
        contentFileToRetain = file("contentFileToRetain.java", "class contentFileToRetain {}")
      }
    }
    val originToRetain = createModuleContentOrigin(contentRootToRetain, moduleToRetain)
    val originToUnload = createModuleContentOrigin(contentRootToUnload, moduleToUnload)
    assertOrigins(listOf(originToRetain, originToUnload), listOf(contentFileToUnload, contentFileToRetain))

    runWithModalProgressBlocking(project, "") {
      ModuleManager.getInstance(project).setUnloadedModules(listOf("moduleToUnload"))
    }

    assertOrigin(originToRetain, contentFileToRetain)
    assertNoOrigin(contentFileToUnload, contentRootToUnload)

    runWithModalProgressBlocking(project, "") {
      ModuleManager.getInstance(project).setUnloadedModules(emptyList())
    }

    val newModuleToUnload = projectModelRule.moduleManager.findModuleByName("moduleToUnload")
    assertThat(newModuleToUnload).isNotNull
    assertOrigins(listOf(originToRetain, createModuleContentOrigin(contentRootToUnload, newModuleToUnload!!)),
                  listOf(contentFileToUnload, contentFileToRetain))
  }
}
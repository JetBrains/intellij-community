// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.indexing.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.RunsInEdt
import org.junit.Test

@RunsInEdt
class IndexableFilesIndexOriginsExcludedDirectoryTest : IndexableFilesIndexOriginsTestBase() {

  @Test
  fun `excluded files`() {
    lateinit var excludedJava: FileSpec
    lateinit var excludedDirectory: DirectorySpec

    projectModelRule.createJavaModule("moduleName") {
      content("contentRoot") {
        excludedDirectory = excluded("excluded") {
          excludedJava = file("ExcludedFile.java", "class ExcludedFile {}")
        }
      }
    }
    assertNoOrigin(excludedJava, excludedDirectory)
  }

  @Test
  fun `source root beneath excluded directory must be indexed`() {
    lateinit var sourceFile: FileSpec
    lateinit var excludedDir: DirectorySpec
    lateinit var sourceRoot: DirectorySpec
    val module = projectModelRule.createJavaModule("moduleName") {
      content("contentRoot") {
        excludedDir = excluded("excluded") {
          sourceRoot = source("sources") {
            sourceFile = file("A.java", "class A {}")
          }
        }
      }
    }
    assertNoOrigin(excludedDir)
    assertOrigin(createModuleContentOrigin(sourceRoot, module), sourceFile)
  }

  @Test
  fun `files of a Library beneath module excluded directory`() {
    lateinit var libraryRoot: DirectorySpec
    lateinit var libraryClass: FileSpec
    lateinit var excludedDir: DirectorySpec

    val module = projectModelRule.createJavaModule("moduleName") {
      content("contentRoot") {
        excludedDir = excluded("excluded") {
          libraryRoot = dir("library") {
            libraryClass = file("LibraryClass.java", "class LibraryClass {}")
          }
        }
      }
    }
    assertNoOrigin(libraryClass, excludedDir)

    val library = projectModelRule.addModuleLevelLibrary(module, "libraryName") { model ->
      model.addRoot(libraryRoot.file, OrderRootType.CLASSES)
    }
    assertOrigin(createLibraryOrigin(library), libraryClass)
    assertNoOrigin(excludedDir)
  }

  @Test
  fun `files of an SDK beneath module excluded directory`() {
    lateinit var sdkRoot: DirectorySpec
    lateinit var sdkClass: FileSpec

    val module = projectModelRule.createJavaModule("moduleName") {
      content("contentRoot") {
        excluded("excluded") {
          sdkRoot = dir("sdk") {
            sdkClass = file("SdkClass.java", "class SdkClass {}")
          }
        }
      }
    }

    assertNoOrigin(sdkRoot, sdkClass)

    val sdk = projectModelRule.addSdk("sdkName") { sdkModificator ->
      sdkModificator.addRoot(sdkRoot.file, OrderRootType.CLASSES)
    }
    assertNoOrigin(sdkRoot, sdkClass)

    ModuleRootModificationUtil.setModuleSdk(module, sdk)
    val origin = createSdkOrigin(sdk)
    assertEveryFileOrigin(origin, sdkClass, sdkRoot)
  }

  @Test
  fun `files of AdditionalLibraryRootsProvider beneath module excluded directory`() {
    lateinit var targetSource: FileSpec
    lateinit var targetSources: DirectorySpec
    lateinit var excluded: DirectorySpec
    lateinit var targetBinary: FileSpec
    lateinit var targetBinaries: DirectorySpec

    projectModelRule.createJavaModule("moduleName") {
      content("contentRoot") {
        excluded = excluded("excluded") {
          targetSources = moduleDir("sources") {
            targetSource = file("TargetSource.java", "class TargetSource {}")
          }
          targetBinaries = moduleDir("binaries") {
            targetBinary = file("TargetBinary.java", "class TargetBinary {}")
          }
        }
      }
    }
    val syntheticLibrary = createAndSetAdditionalLibraryRootProviderWithSingleLibrary(listOf(targetSources.file),
                                                                                      listOf(targetBinaries.file))
    assertOrigins(listOf(createSyntheticLibraryOrigin(syntheticLibrary)), listOf(targetBinary, targetSource, targetBinaries, targetSources))
    assertNoOrigin(excluded)
  }

  @Test
  fun `files of IndexableSetContributor beneath module excluded directory`() {
    lateinit var additionalRoots: DirectorySpec
    lateinit var additionalProjectRoots: DirectorySpec

    lateinit var projectFile: FileSpec
    lateinit var appFile: FileSpec
    projectModelRule.createJavaModule("moduleName") {
      // Must not be indexed despite being provided by IndexableSetContributor.
      content("contentRoot") {
        excluded("excluded") {
          additionalProjectRoots = dir("additionalProjectRoots") {
            projectFile = file("ExcludedFile.java", "class ExcludedFile {}")
          }
          additionalRoots = dir("additionalRoots") {
            appFile = file("ExcludedFile.java", "class ExcludedFile {}")
          }
        }
      }
    }
    assertNoOrigin(additionalRoots, additionalProjectRoots, projectFile, appFile)

    val additionalProjectRootsFile = additionalProjectRoots.file  // load VFS synchronously outside read action
    val additionalRootsFile = additionalRoots.file                // load VFS synchronously outside read action
    val contributor = object : IndexableSetContributor() {
      override fun getAdditionalProjectRootsToIndex(project: Project): Set<VirtualFile> =
        setOf(additionalProjectRootsFile)

      override fun getAdditionalRootsToIndex(): Set<VirtualFile> =
        setOf(additionalRootsFile)
    }
    maskIndexableSetContributors(contributor)
    assertNoOrigin(additionalRoots, additionalProjectRoots, projectFile, appFile)
  }

  @Test
  fun `nested content root's excluded file should be indexed after nested content root removal`() {
    val parentContentRootFileName = "ParentContentRootFile.txt"
    val nestedContentRootFileName = "NestedContentRootFile.txt"
    val excludedFileName = "ExcludedFile.txt"
    lateinit var parentContentRootFile: FileSpec
    lateinit var parentContentRoot: ModuleRootSpec
    lateinit var nestedContentRoot: ModuleRootSpec
    lateinit var nestedContentRootFile: FileSpec
    lateinit var excludedDir: DirectorySpec
    val module = projectModelRule.createJavaModule("moduleName") {
      parentContentRoot = content("parentContentRoot") {
        parentContentRootFile = file(parentContentRootFileName, "class ParentContentRootFile {}")
        nestedContentRoot = content("nestedContentRoot") {
          nestedContentRootFile = file(nestedContentRootFileName, "class NestedContentRootFile {}")
          excludedDir = dir("excluded") {}
        }
      }
    }


    val parentContentOrigin = createModuleContentOrigin(parentContentRoot, module)
    assertOrigin(parentContentOrigin, parentContentRootFile)
    val nestedContentOrigin = createModuleContentOrigin(nestedContentRoot, module)
    assertEveryFileOrigin(nestedContentOrigin, nestedContentRootFile, excludedDir)

    ModuleRootModificationUtil.updateExcludedFolders(module, nestedContentRoot.file, emptyList(), listOf(excludedDir.file.url))
    val excludedFile = FileSpec(excludedDir.specPath / excludedFileName, "class ExcludedFile {}".toByteArray())
    excludedDir.addChild(excludedFileName, excludedFile)
    excludedFile.generate(excludedDir.file, excludedFileName)

    assertOrigin(parentContentOrigin, parentContentRootFile)
    assertOrigin(nestedContentOrigin, nestedContentRootFile)
    assertNoOrigin(excludedDir, excludedFile)

    PsiTestUtil.removeContentEntry(module, nestedContentRoot.file)

    assertEveryFileOrigin(parentContentOrigin, parentContentRootFile, nestedContentRootFile, excludedDir, excludedFile)
  }
}
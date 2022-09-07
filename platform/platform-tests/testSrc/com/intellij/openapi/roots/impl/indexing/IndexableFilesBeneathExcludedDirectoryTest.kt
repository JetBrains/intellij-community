// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl.indexing

import com.intellij.find.ngrams.TrigramIndex
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.SyntheticLibrary

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.cache.impl.id.IdIndex
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.UsefulTestCase.assertContainsElements
import com.intellij.testFramework.UsefulTestCase.assertTrue
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.ID
import com.intellij.util.indexing.IndexableSetContributor
import org.junit.Test

@RunsInEdt
class IndexableFilesBeneathExcludedDirectoryTest : IndexableFilesBaseTest() {

  @Test
  fun `excluded files must not be indexed`() {
    lateinit var excludedJava: FileSpec

    projectModelRule.createJavaModule("moduleName") {
      content("contentRoot") {
        excluded("excluded") {
          excludedJava = file("ExcludedFile.java", "class ExcludedFile {}")
        }
      }
    }
    assertIndexableFiles()
    assertHasNoIndexes(excludedJava.file)
  }

  @Test
  fun `source root beneath excluded directory must be indexed`() {
    lateinit var aJava: FileSpec
    projectModelRule.createJavaModule("moduleName") {
      content("contentRoot") {
        excluded("excluded") {
          source("sources") {
            aJava = file("A.java", "class A {}")
          }
        }
      }
    }
    assertIndexableFiles(aJava.file)
  }

  @Test
  fun `files of a Library residing beneath module excluded directory must be indexed`() {
    lateinit var libraryRoot: DirectorySpec
    lateinit var libraryClass: FileSpec

    val module = projectModelRule.createJavaModule("moduleName") {
      content("contentRoot") {
        excluded("excluded") {
          // Must be indexed despite being excluded by module.
          libraryRoot = dir("library") {
            libraryClass = file("LibraryClass.java", "class LibraryClass {}")
          }
        }
      }
    }
    projectModelRule.addModuleLevelLibrary(module, "libraryName") { model ->
      model.addRoot(libraryRoot.file, OrderRootType.CLASSES)
    }
    assertIndexableFiles(libraryClass.file)
  }

  @Test
  fun `files of an SDK residing beneath module excluded directory must be indexed`() {
    lateinit var sdkRoot: DirectorySpec
    lateinit var sdkClass: FileSpec

    val module = projectModelRule.createJavaModule("moduleName") {
      content("contentRoot") {
        excluded("excluded") {
          // Must be indexed despite being excluded by module.
          sdkRoot = dir("sdk") {
            sdkClass = file("SdkClass.java", "class SdkClass {}")
          }
        }
      }
    }

    val sdk = projectModelRule.addSdk("sdkName") { sdkModificator ->
      sdkModificator.addRoot(sdkRoot.file, OrderRootType.CLASSES)
    }
    ModuleRootModificationUtil.setModuleSdk(module, sdk)

    assertIndexableFiles(sdkClass.file)
  }

  // Roots provided by AdditionalLibraryRootsProvider are considered library source roots,
  // and they must be indexed even if they reside beneath excluded directories.
  @Test
  fun `files of AdditionalLibraryRootsProvider residing beneath module excluded directory must be indexed`() {
    lateinit var targetSource: FileSpec
    lateinit var targetSources: DirectorySpec

    lateinit var targetBinary: FileSpec
    lateinit var targetBinaries: DirectorySpec

    projectModelRule.createJavaModule("moduleName") {
      content("contentRoot") {
        excluded("excluded") {
          targetSources = moduleDir("sources") {
            targetSource = file("TargetSource.java", "class TargetSource {}")
          }
          targetBinaries = moduleDir("binaries") {
            targetBinary = file("TargetBinary.java", "class TargetBinary {}")
          }
        }
      }
    }

    val additionalLibraryRootsProvider = object : AdditionalLibraryRootsProvider() {
      override fun getAdditionalProjectLibraries(project: Project) = listOf(
        SyntheticLibrary.newImmutableLibrary("test",
                                             listOf(targetSources.file),
                                             listOf(targetBinaries.file),
                                             emptySet(),
                                             null
        )
      )
    }

    maskAdditionalLibraryRootsProviders(additionalLibraryRootsProvider)
    assertIndexableFiles(targetSource.file, targetBinary.file)
  }

  @Test
  fun `files of IndexableSetContributor residing beneath module excluded directory must not be indexed`() {
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

    val contributor = object : IndexableSetContributor() {
      override fun getAdditionalProjectRootsToIndex(project: Project): Set<VirtualFile> =
        setOf(additionalProjectRoots.file)

      override fun getAdditionalRootsToIndex(): Set<VirtualFile> =
        setOf(additionalRoots.file)
    }
    maskIndexableSetContributors(contributor)
    assertIndexableFiles(projectFile.file, appFile.file)
  }

  @Test
  fun `nested content root's excluded file should be indexed after nested content root removal`() {
    // no java plugin here, so we use plain text files
    val parentContentRootFileName = "ParentContentRootFile.txt"
    val nestedContentRootFileName = "NestedContentRootFile.txt"
    val excludedFileName = "ExcludedFile.txt"
    lateinit var parentContentRootFile: FileSpec
    lateinit var nestedContentRoot: ModuleRootSpec
    lateinit var nestedContentRootFile: FileSpec
    lateinit var excludedDir: DirectorySpec
    val module = projectModelRule.createJavaModule("moduleName") {
      content("parentContentRoot") {
        parentContentRootFile = file(parentContentRootFileName, "class ParentContentRootFile {}")
        nestedContentRoot = content("nestedContentRoot") {
          nestedContentRootFile = file(nestedContentRootFileName, "class NestedContentRootFile {}")
          excludedDir = dir("excluded") {}
        }
      }
    }
    ModuleRootModificationUtil.updateExcludedFolders(module, nestedContentRoot.file, emptyList(), listOf(excludedDir.file.url))
    val excludedFile = FileSpec(excludedDir.specPath / excludedFileName, "class ExcludedFile {}".toByteArray())
    excludedDir.addChild(excludedFileName, excludedFile)
    excludedFile.generate(excludedDir.file, excludedFileName)

    val fileBasedIndex = FileBasedIndex.getInstance()
    assertIndexableFiles(parentContentRootFile.file, nestedContentRootFile.file)
    // Currently order of checks masks the fact,
    // that called before calls to content-dependent indices on files in question,
    // content-independent indices won't return those files
    fileBasedIndex.assertHasDataInIndex(nestedContentRootFile.file, IdIndex.NAME, TrigramIndex.INDEX_ID)
    fileBasedIndex.assertHasDataInIndex(parentContentRootFile.file, IdIndex.NAME, TrigramIndex.INDEX_ID)
    fileBasedIndex.assertNoDataInIndex(excludedFile.file, IdIndex.NAME, TrigramIndex.INDEX_ID)
    assertContainsElements(FilenameIndex.getAllFilesByExt(project, "txt", GlobalSearchScope.projectScope(project)),
                           parentContentRootFile.file, nestedContentRootFile.file)
    assertContainsElements(FileTypeIndex.getFiles(PlainTextFileType.INSTANCE, GlobalSearchScope.projectScope(project)),
                           parentContentRootFile.file, nestedContentRootFile.file)

    PsiTestUtil.removeContentEntry(module, nestedContentRoot.file)
    assertIndexableFiles(parentContentRootFile.file, nestedContentRootFile.file, excludedFile.file)
    fileBasedIndex.assertHasDataInIndex(nestedContentRootFile.file, IdIndex.NAME, TrigramIndex.INDEX_ID)
    fileBasedIndex.assertHasDataInIndex(parentContentRootFile.file, IdIndex.NAME, TrigramIndex.INDEX_ID)
    fileBasedIndex.assertHasDataInIndex(excludedFile.file, IdIndex.NAME, TrigramIndex.INDEX_ID)

    assertContainsElements(FilenameIndex.getAllFilesByExt(project, "txt", GlobalSearchScope.projectScope(project)),
                           parentContentRootFile.file, nestedContentRootFile.file, excludedFile.file)
    assertContainsElements(FileTypeIndex.getFiles(PlainTextFileType.INSTANCE, GlobalSearchScope.projectScope(project)),
                           parentContentRootFile.file, nestedContentRootFile.file, excludedFile.file)
  }

  private fun FileBasedIndex.assertHasDataInIndex(file: VirtualFile, vararg indexIds: ID<*, *>) {
    for (indexId in indexIds) {
      val values = getFileData(indexId, file, project).values
      assertTrue("No data is found in $indexId for ${file.name}", !values.isEmpty())
    }
  }

  private fun FileBasedIndex.assertNoDataInIndex(file: VirtualFile, vararg indexIds: ID<*, *>) {
    for (indexId in indexIds) {
      val values = getFileData(indexId, file, project).values
      assertTrue("Some data found in " + indexId + " for " + file.name, values.isEmpty())
    }
  }
}
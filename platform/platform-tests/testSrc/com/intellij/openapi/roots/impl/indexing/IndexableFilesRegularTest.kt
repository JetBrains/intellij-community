// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl.indexing

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.cache.CacheManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.UsageSearchContext
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.indexing.IndexableSetContributor
import org.junit.Test
import kotlin.test.assertEquals

@RunsInEdt
class IndexableFilesRegularTest : IndexableFilesBaseTest() {
  @Test
  fun `indexing files of a module content root`() {
    lateinit var contentFile: FileSpec
    lateinit var sourceFile: FileSpec
    lateinit var testFile: FileSpec
    lateinit var resourceFile: FileSpec
    lateinit var testResourceFile: FileSpec
    projectModelRule.createJavaModule("moduleName") {
      content("contentRoot") {
        contentFile = file("ContentFile.java", "class ContentFile {}")
        source("sources") {
          sourceFile = file("SourceFile.java", "class SourceFile {}")
        }
        resourceRoot("resources") {
          resourceFile = file("resource.txt", "no data")
        }
        testSourceRoot("tests") {
          testFile = file("Test.java", "class Test {}")
        }
        testResourceRoot("testResources") {
          testResourceFile = file("testResource.txt", "no data")
        }
      }
    }
    assertIndexableFiles(contentFile.file, sourceFile.file, testFile.file, resourceFile.file, testResourceFile.file)
  }

  @Test
  fun `indexing files of a Library`() {
    val libraryRoot = tempDirectory.newVirtualDirectory("library")
    lateinit var classFile: FileSpec
    lateinit var classesDir: DirectorySpec
    lateinit var excludedClassesDir: DirectorySpec

    lateinit var sourceFile: FileSpec
    lateinit var sourcesDir: DirectorySpec
    lateinit var excludedSourcesDir: DirectorySpec

    buildDirectoryContent(libraryRoot) {
      dir("library") {
        classesDir = dir("classes") {
          excludedClassesDir = dir("excluded") {
            file("ExcludedClassFile.java", "class ExcludedClassFile {}")
          }
          classFile = file("ClassFile.java", "class ClassFile {}")
        }
        sourcesDir = dir("sources") {
          excludedSourcesDir = dir("excluded") {
            file("ExcludedSourceFile.java", "class ExcludedSourceFile {}")
          }
          sourceFile = file("SourceFile.java", "class SourceFile {}")
        }
      }
    }
    val module = projectModelRule.createModule()
    projectModelRule.addModuleLevelLibrary(module, "libraryName") { model ->
      model.addRoot(classesDir.file, OrderRootType.CLASSES)
      model.addRoot(sourcesDir.file, OrderRootType.SOURCES)
      model.addExcludedRoot(excludedClassesDir.file.url)
      model.addExcludedRoot(excludedSourcesDir.file.url)
    }
    assertIndexableFiles(classFile.file, sourceFile.file)
  }

  @Test
  fun `indexing files of an SDK`() {
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

    val sdk = projectModelRule.addSdk(projectModelRule.createSdk("sdk")) { sdkModificator ->
      sdkModificator.addRoot(classesDir.file, OrderRootType.CLASSES)
      sdkModificator.addRoot(sourcesDir.file, OrderRootType.SOURCES)
    }

    val module = projectModelRule.createModule()
    ModuleRootModificationUtil.setModuleSdk(module, sdk)
    assertIndexableFiles(classFile.file, sourceFile.file)
  }

  @Test
  fun `indexing files provided by IndexableSetContributor`() {
    val moduleRoot = tempDirectory.newVirtualDirectory("moduleRoot")
    lateinit var additionalProjectRoots: DirectorySpec
    lateinit var additionalProjectRootJava: FileSpec

    projectModelRule.createJavaModule("moduleName") {
      dir("additionalProjectRoot") {
        additionalProjectRoots = dir("additionalProjectRoots") {
          additionalProjectRootJava = file("AdditionalProjectRoot.java", "class AdditionalProjectRoot {}")
        }
      }
    }

    lateinit var additionalRoots: DirectorySpec
    lateinit var additionalRootJava: FileSpec
    buildDirectoryContent(moduleRoot) {
      additionalRoots = dir("additionalRoots") {
        additionalRootJava = file("AdditionalRoot.java", "class AdditionalRoot {}")
      }
    }
    val contributor = object : IndexableSetContributor() {
      override fun getAdditionalProjectRootsToIndex(project: Project): Set<VirtualFile> =
        setOf(additionalProjectRoots.file)

      override fun getAdditionalRootsToIndex(): Set<VirtualFile> =
        setOf(additionalRoots.file)
    }
    maskIndexableSetContributors(contributor)
    assertIndexableFiles(additionalProjectRootJava.file, additionalRootJava.file)
    assertIdIndexContainsWord(additionalRootJava.file, "AdditionalRoot")
    assertIdIndexContainsWord(additionalProjectRootJava.file, "AdditionalProjectRoot")

    // make some change
    runWriteAction {
      VfsUtil.saveText(additionalRootJava.file, "class Foo {}")
      VfsUtil.saveText(additionalProjectRootJava.file, "class Foo {}")
    }

    assertIdIndexContainsWord(additionalRootJava.file, "Foo")
    assertIdIndexContainsWord(additionalProjectRootJava.file, "Foo")
  }

  @Test
  fun `indexing files provided by AdditionalLibraryRootsProvider`() {
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
    val additionalLibraryRootsProvider = object : AdditionalLibraryRootsProvider() {
      override fun getAdditionalProjectLibraries(project: Project) = listOf(
        SyntheticLibrary.newImmutableLibrary(
          listOf(sourcesDir.file, moduleExcludedSourcesDir.file),
          listOf(binariesDir.file, moduleExcludedBinariesDir.file),
          setOf(sourcesExcludedDir.file, binariesExcludedDir.file)
        ) { file -> file == sourceFileExcludedByCondition.file }
      )
    }
    maskAdditionalLibraryRootsProviders(additionalLibraryRootsProvider)
    assertIndexableFiles(sourceFile.file, binaryFile.file, reIncludedSource.file, reIncludedBinary.file)
  }

  private fun assertIdIndexContainsWord(file: VirtualFile, word: String) {
    val fileScope = GlobalSearchScope.fileScope(project, file)
    val cacheManager = CacheManager.getInstance(project)
    val filesFromIndex = cacheManager.getVirtualFilesWithWord(word,
                                                              UsageSearchContext.ANY,
                                                              fileScope,
                                                              true)

    val fileFromIndex = UsefulTestCase.assertOneElement(filesFromIndex)
    assertEquals(file, fileFromIndex)
  }
}
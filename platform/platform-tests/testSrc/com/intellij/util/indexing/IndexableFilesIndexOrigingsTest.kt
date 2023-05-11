// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing


import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.roots.impl.OrderEntryUtil
import com.intellij.openapi.roots.impl.indexing.*
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.*
import com.intellij.testFramework.UsefulTestCase.assertSize
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.indexing.roots.IndexableEntityProviderMethods
import com.intellij.util.indexing.roots.LibraryIndexableFilesIteratorImpl
import com.intellij.util.indexing.roots.SdkIndexableFilesIteratorImpl
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

@RunsInEdt
class IndexableFilesIndexOriginsTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val edtRule = EdtRule()

  @Rule
  @JvmField
  val projectModelRule = ProjectModelRule()

  @Rule
  @JvmField
  val disposableRule = DisposableRule()

  @Rule
  @JvmField
  val tempDirectory = TempDirectory()

  val project: Project get() = projectModelRule.project

  @Before
  fun setUp() {
    runWriteAction {
      (IndexableSetContributor.EP_NAME.point as ExtensionPointImpl<*>).unregisterExtensions({ _, _ -> false }, false)
      (AdditionalLibraryRootsProvider.EP_NAME.point as ExtensionPointImpl<*>).unregisterExtensions({ _, _ -> false }, false)
    }
  }

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
    assertOrigin(contentFile, createModuleContentOrigin(contentRoot, module))
    assertOrigin(sourceFile, createModuleContentOrigin(sourceRoot, module))
    assertOrigin(testFile, createModuleContentOrigin(testRoot, module))
    assertOrigin(testResourceFile, createModuleContentOrigin(testResourceRoot, module))
    assertOrigin(resourceFile, createModuleContentOrigin(resourceRoot, module))
    assertOrigin(contentRoot, createModuleContentOrigin(contentRoot, module))
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
    assertOrigin(classesDir, libraryOrigin)
    assertOrigin(classFile, libraryOrigin)
    assertOrigin(sourcesDir, libraryOrigin)
    assertOrigin(sourceFile, libraryOrigin)
    assertNoOrigin(excludedClassesDir, excludedClassFile, excludedSourcesDir, excludedSourceFile)
    assertOrigins(listOf(classFile, classesDir, sourceFile, sourcesDir), listOf(libraryOrigin))
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

    val sdkOrigin = SdkIndexableFilesIteratorImpl.createIterator(sdk).origin
    assertOrigin(classesDir, sdkOrigin)
    assertOrigin(classFile, sdkOrigin)
    assertOrigin(sourcesDir, sdkOrigin)
    assertOrigin(sourceFile, sdkOrigin)
    assertOrigins(listOf(classFile, classesDir, sourcesDir, sourceFile), listOf(sdkOrigin))
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
    runWriteAction {
      ExtensionTestUtil.maskExtensions(IndexableSetContributor.EP_NAME, listOf(contributor), disposableRule.disposable)
      fireRootsChanged()
    }
    val appOrigin = createIndexableSetOrigin(contributor, null)
    assertOrigin(additionalRoots, appOrigin)
    assertOrigin(additionalRootJava, appOrigin)
    val projectOrigin = createIndexableSetOrigin(contributor, project)
    assertOrigin(additionalProjectRoots, projectOrigin)
    assertOrigin(additionalProjectRootJava, projectOrigin)
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
    val sourcesDirFile = sourcesDir.file                               // load VFS synchronously outside read action
    val moduleExcludedSourcesDirFile = moduleExcludedSourcesDir.file   // load VFS synchronously outside read action
    val binariesDirFile = binariesDir.file                             // load VFS synchronously outside read action
    val moduleExcludedBinariesDirFile = moduleExcludedBinariesDir.file // load VFS synchronously outside read action
    val sourcesExcludedDirFile = sourcesExcludedDir.file               // load VFS synchronously outside read action
    val binariesExcludedDirFile = binariesExcludedDir.file             // load VFS synchronously outside read action
    val syntheticLibrary = SyntheticLibrary.newImmutableLibrary(
      listOf(sourcesDirFile, moduleExcludedSourcesDirFile),
      listOf(binariesDirFile, moduleExcludedBinariesDirFile),
      setOf(sourcesExcludedDirFile, binariesExcludedDirFile)
    ) { file -> file == excludedFile }
    val additionalLibraryRootsProvider = object : AdditionalLibraryRootsProvider() {
      override fun getAdditionalProjectLibraries(project: Project): List<SyntheticLibrary> = listOf(syntheticLibrary)
    }
    runWriteAction {
      ExtensionTestUtil.maskExtensions(AdditionalLibraryRootsProvider.EP_NAME, listOf(additionalLibraryRootsProvider),
                                       disposableRule.disposable)
      fireRootsChanged()
    }

    val libraryOrigin = createSyntheticLibraryOrigin(syntheticLibrary)
    assertOrigin(sourceFile, libraryOrigin)
    assertOrigin(sourcesDir, libraryOrigin)
    assertOrigin(binariesDir, libraryOrigin)
    assertOrigin(binaryFile, libraryOrigin)
    assertNoOrigin(sourceFileExcludedByCondition, sourcesExcludedDir, binariesExcludedDir)
    assertOrigin(reIncludedSource, libraryOrigin)
    assertOrigin(moduleExcludedSourcesDir, libraryOrigin)
    assertOrigin(reIncludedBinary, libraryOrigin)
    assertOrigin(moduleExcludedBinariesDir, libraryOrigin)
    assertOrigins(
      listOf(
        sourceFile, sourcesDir, binariesDir, binaryFile, sourceFileExcludedByCondition, sourcesExcludedDir, binariesExcludedDir,
        reIncludedSource, moduleExcludedSourcesDir, reIncludedBinary, moduleExcludedBinariesDir
      ),
      listOf(libraryOrigin))
  }

  private fun createModuleContentOrigin(fileSpec: ContentSpec, module: com.intellij.openapi.module.Module): IndexableSetOrigin =
    IndexableEntityProviderMethods.createIterators(module, listOf(fileSpec.file)).first().origin

  private fun createLibraryOrigin(library: Library): IndexableSetOrigin =
    LibraryIndexableFilesIteratorImpl.createIteratorList(library).also { assertSize(1, it) }.first().origin

  private fun createIndexableSetOrigin(contributor: IndexableSetContributor,
                                       project: Project?): IndexableSetOrigin =
    IndexableEntityProviderMethods.createForIndexableSetContributor(contributor,
                                                                    project != null,
                                                                    project?.let { contributor.getAdditionalProjectRootsToIndex(project) }
                                                                    ?: contributor.additionalRootsToIndex).also {
      assertSize(1, it)
    }.first().origin

  private fun createSyntheticLibraryOrigin(syntheticLibrary: SyntheticLibrary): IndexableSetOrigin =
    IndexableEntityProviderMethods.createForSyntheticLibrary(syntheticLibrary, syntheticLibrary.allRoots).also {
      assertSize(1, it)
    }.first().origin

  private fun assertOrigin(fileSpec: ContentSpec, origin: IndexableSetOrigin) {
    val origins = IndexableFilesIndex.getInstance(project).getOrigins(Collections.singleton(fileSpec.file))
    assertEquals(1, origins.size, "Wrong number of origins: $origins")
    assertEquals(origin, origins.first())
  }

  private fun assertOrigins(fileSpecs: Collection<ContentSpec>, expectedOrigins: Collection<IndexableSetOrigin>) {
    val actualOrigins = IndexableFilesIndex.getInstance(project).getOrigins(fileSpecs.map { spec -> spec.file })
    assertEquals(expectedOrigins, actualOrigins)
  }

  private fun assertNoOrigin(vararg fileSpecs: ContentSpec) {
    for (spec in fileSpecs) {
      val origins = IndexableFilesIndex.getInstance(project).getOrigins(Collections.singleton(spec.file))
      assert(origins.isEmpty()) { "Found unexpected origins $origins for ${spec.file}" }
    }
  }

  private fun fireRootsChanged() {
    ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(EmptyRunnable.getInstance(), RootsChangeRescanningInfo.TOTAL_RESCAN)
  }

  private val ContentSpec.file: VirtualFile get() = resolveVirtualFile()
}
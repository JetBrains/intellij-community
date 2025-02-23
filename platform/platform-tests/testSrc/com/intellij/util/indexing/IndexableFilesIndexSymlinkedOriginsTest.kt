// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.indexing.*
import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.asSafely
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileInternalInfo
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * For now [[IndexableFilesIndex.getOrigins]] doesn't provide for a file [[com.intellij.util.indexing.roots.kind.IndexableSetOrigin]]
 * of file(s) that target it via symlinks. There is no reason to support it for now, and at least [[WorkspaceFileIndex]] and other APIs based on it
 * do the same way. In case such reasons appear, behaviour and this test should be updated.
 *
 * @see IndexableFilesSymlinksTest
 */
class IndexableFilesIndexSymlinkedOriginsTest : IndexableFilesIndexOriginsTestBase() {

  @Before
  fun applicabilityCheck() {
    IoTestUtil.assumeSymLinkCreationIsSupported()
  }

  @Test
  fun `symlink from content root to excluded target`() {
    lateinit var targetFile: ContentSpec
    lateinit var targetDir: ContentSpec
    lateinit var targetUnderDir: ContentSpec
    lateinit var contentRoot: ContentSpec

    lateinit var symlink: ContentSpec
    lateinit var symlinkDir: ContentSpec
    val module = projectModelRule.createJavaModule("moduleName") {
      contentRoot = content("contentRoot") {
        excluded("excluded") {
          targetFile = file("Target.java", "class Target {}")
          targetDir = dir("targetDir") {
            targetUnderDir = file("TargetUnderDir.java", "class TargetUnderDir {}")
          }
        }
        symlink = symlink("symlink.java", targetFile)
        symlinkDir = symlink("symlinkDir", targetDir)
      }
    }
    val contentOrigin = createModuleContentOrigin(contentRoot, module)
    assertEveryFileOrigin(contentOrigin, symlink, symlinkDir)
    assertOrigin(contentOrigin, symlinkDir, "TargetUnderDir.java")
    val fileInfo = WorkspaceFileIndex.getInstance(project).asSafely<WorkspaceFileIndexEx>()?.getFileInfo(targetUnderDir.file, true, true,
                                                                                                         true, true, true)
    assertNotNull(fileInfo)
    assertEquals(WorkspaceFileInternalInfo.NonWorkspace.EXCLUDED, fileInfo)
    assertNoOrigin(targetFile, targetDir, targetUnderDir)
  }


  @Test
  fun `symlinks in a Library`() {
    lateinit var classesSymlink: SymlinkSpec
    lateinit var sourcesSymlink: SymlinkSpec
    lateinit var classFile: FileSpec
    lateinit var sourceFile: FileSpec

    lateinit var classesDir: DirectorySpec
    lateinit var sourcesDir: DirectorySpec
    buildDirectoryContent(tempDirectory.newVirtualDirectory("library")) {
      dir("target") {
        classesDir = dir("classes") {
          classFile = file("ClassFile.java", "class ClassFile {}")
        }
        sourcesDir = dir("sources") {
          sourceFile = file("SourceFile.java", "class SourceFile {}")
        }
      }
      classesSymlink = symlink("classes", classesDir)
      sourcesSymlink = symlink("sources", sourcesDir)
    }

    val module = projectModelRule.createModule()
    val library = projectModelRule.addModuleLevelLibrary(module, "libraryName") { model ->
      model.addRoot(classesSymlink.file, OrderRootType.CLASSES)
      model.addRoot(sourcesSymlink.file, OrderRootType.SOURCES)
    }
    val libraryOrigin = createLibraryOrigin(library)
    assertOrigin(libraryOrigin, classesSymlink)
    assertOrigin(libraryOrigin, sourcesSymlink)
    assertNoOrigin(classFile, sourceFile, classesDir, sourcesDir)
    assertOrigin(libraryOrigin, classesSymlink, "ClassFile.java")
    assertOrigin(libraryOrigin, sourcesSymlink, "SourceFile.java")
  }

  /**
   * Indicator of the following bug: IDEA-189247
   * When a directory is soft-linked into a project, its excluded subdirectories are still indexed.
   */
  @Test
  fun `excluded directories referenced via symlink`() {
    lateinit var sourcesDir: DirectorySpec
    lateinit var workspaceSymlink: SymlinkSpec
    lateinit var contentRoot: DirectorySpec
    val module = projectModelRule.createJavaModule("moduleName") {
      contentRoot = content("contentRoot") {
        val workspace = moduleDir("workspace") {
          sourcesDir = source("sources") {
            file("SourceFile.java", "class SourceFile {}")
          }
          excluded("excluded") {
            file("ExcludedFile.java", "class ExcludedFile {}")
          }
        }
        workspaceSymlink = symlink("workspace-link", workspace)
      }
    }
    val sourceOrigin = createModuleContentOrigin(sourcesDir, module)
    assertOrigin(sourceOrigin, sourcesDir)
    val contentOrigin = createModuleContentOrigin(contentRoot, module)
    assertOrigin(contentOrigin, workspaceSymlink)
    assertOrigin(contentOrigin, workspaceSymlink, "sources")
    assertOrigin(contentOrigin, workspaceSymlink, "sources/SourceFile.java")
    assertOrigin(contentOrigin, workspaceSymlink, "excluded")
    assertOrigin(contentOrigin, workspaceSymlink, "excluded/ExcludedFile.java")
  }

  @Test
  fun `symlink which is a root of a Library with excluded target is`() {
    lateinit var classesSymlink: SymlinkSpec
    lateinit var sourcesSymlink: SymlinkSpec

    lateinit var classesDir: DirectorySpec
    lateinit var sourcesDir: DirectorySpec

    projectModelRule.createJavaModule("moduleName") {
      content("contentRoot") {
        excluded("excluded") {
          moduleDir("target") {
            classesDir = dir("classes") {
              file("ClassFile.java", "class ClassFile {}")
            }
            sourcesDir = dir("sources") {
              file("SourceFile.java", "class SourceFile {}")
            }
          }
        }
      }
    }

    buildDirectoryContent(tempDirectory.newVirtualDirectory("library")) {
      classesSymlink = symlink("classes", classesDir)
      sourcesSymlink = symlink("sources", sourcesDir)
    }

    val module = projectModelRule.createModule()
    val library = projectModelRule.addModuleLevelLibrary(module, "libraryName") { model ->
      model.addRoot(classesSymlink.file, OrderRootType.CLASSES)
      model.addRoot(sourcesSymlink.file, OrderRootType.SOURCES)
    }

    val libraryOrigin = createLibraryOrigin(library)
    assertOrigin(libraryOrigin, classesSymlink)
    assertOrigin(libraryOrigin, classesSymlink, "ClassFile.java")
    assertOrigin(libraryOrigin, sourcesSymlink)
    assertOrigin(libraryOrigin, sourcesSymlink, "SourceFile.java")
  }

  @Test
  fun `symlinks in an SDK must be indexed`() {
    lateinit var classesSymlink: SymlinkSpec
    lateinit var sourcesSymlink: SymlinkSpec

    buildDirectoryContent(tempDirectory.newVirtualDirectory("sdk")) {
      lateinit var classesDir: DirectorySpec
      lateinit var sourcesDir: DirectorySpec
      dir("target") {
        classesDir = dir("classes") {
          file("ClassFile.java", "class ClassFile {}")
        }
        sourcesDir = dir("sources") {
          file("SourceFile.java", "class SourceFile {}")
        }
      }
      classesSymlink = symlink("classes", classesDir)
      sourcesSymlink = symlink("sources", sourcesDir)
    }

    val sdk = projectModelRule.addSdk("sdk") { sdkModificator ->
      sdkModificator.addRoot(classesSymlink.file, OrderRootType.CLASSES)
      sdkModificator.addRoot(sourcesSymlink.file, OrderRootType.SOURCES)
    }

    val module = projectModelRule.createModule()
    ModuleRootModificationUtil.setModuleSdk(module, sdk)

    val sdkOrigin = createSdkOrigin(sdk)
    assertOrigin(sdkOrigin, classesSymlink)
    assertOrigin(sdkOrigin, classesSymlink, "ClassFile.java")
    assertOrigin(sdkOrigin, sourcesSymlink)
    assertOrigin(sdkOrigin, sourcesSymlink, "SourceFile.java")
  }

  @Test
  fun `symlink which is root of an SDK and points to excluded target`() {
    lateinit var classesSymlink: SymlinkSpec
    lateinit var sourcesSymlink: SymlinkSpec

    lateinit var classesDir: DirectorySpec
    lateinit var sourcesDir: DirectorySpec

    projectModelRule.createJavaModule("moduleName") {
      content("contentRoot") {
        excluded("excluded") {
          moduleDir("target") {
            classesDir = dir("classes") {
              file("ClassFile.java", "class ClassFile {}")
            }
            sourcesDir = dir("sources") {
              file("SourceFile.java", "class SourceFile {}")
            }
          }
        }
      }
    }

    buildDirectoryContent(tempDirectory.newVirtualDirectory("sdk")) {
      classesSymlink = symlink("classes", classesDir)
      sourcesSymlink = symlink("sources", sourcesDir)
    }

    val sdk = projectModelRule.addSdk("sdk") { sdkModificator ->
      sdkModificator.addRoot(classesSymlink.file, OrderRootType.CLASSES)
      sdkModificator.addRoot(sourcesSymlink.file, OrderRootType.SOURCES)
    }

    val module = projectModelRule.createModule()
    ModuleRootModificationUtil.setModuleSdk(module, sdk)

    val origin = createSdkOrigin(sdk)
    assertOrigin(origin, classesSymlink)
    assertOrigin(createSdkOrigin(sdk), classesSymlink, "ClassFile.java")
    assertOrigin(createSdkOrigin(sdk), sourcesSymlink)
    assertOrigin(createSdkOrigin(sdk), sourcesSymlink, "SourceFile.java")
  }

  @Test
  fun `symlink provided by IndexableSetContributor`() {
    val additionalRoot = tempDirectory.newVirtualDirectory("additionalRoot")

    lateinit var symlinkToTarget: SymlinkSpec
    buildDirectoryContent(additionalRoot) {
      val targetDir = dir("target") {
        file("TargetFile.java", "class TargetFile {}")
      }
      symlinkToTarget = symlink("symlinkDir", targetDir)
    }
    val symlinkToTargetFile = symlinkToTarget.file // load VFS synchronously outside read action
    val contributor = object : IndexableSetContributor() {
      override fun getAdditionalRootsToIndex(): Set<VirtualFile> =
        setOf(symlinkToTargetFile)
    }
    maskIndexableSetContributors(contributor)
    val origin = createIndexableSetOrigin(contributor, null)
    assertOrigin(origin, symlinkToTarget)
    assertOrigin(origin, symlinkToTarget, "TargetFile.java")
  }

  @Test
  fun `symlink which is a root provided by IndexableSetContributor and points to excluded target`() {
    lateinit var targetDir: DirectorySpec

    val contributorRoot = tempDirectory.newVirtualDirectory("contributorRoot")

    projectModelRule.createJavaModule("moduleName") {
      content("contentRoot") {
        excluded("excluded") {
          targetDir = moduleDir("target") {
            file("TargetFile.java", "class TargetFile {}")
          }
        }
      }
    }

    lateinit var symlinkToTarget: SymlinkSpec
    buildDirectoryContent(contributorRoot) {
      symlinkToTarget = symlink("symlinkDir", targetDir)
    }
    val symlinkToTargetFile = symlinkToTarget.file // load VFS synchronously outside read action
    val contributor = object : IndexableSetContributor() {
      override fun getAdditionalRootsToIndex(): Set<VirtualFile> =
        setOf(symlinkToTargetFile)
    }
    maskIndexableSetContributors(contributor)
    val indexableSetOrigin = createIndexableSetOrigin(contributor, null)
    assertOrigin(indexableSetOrigin, symlinkToTarget)
    assertOrigin(indexableSetOrigin, symlinkToTarget, "TargetFile.java")
  }

  @Test
  fun `symlink provided by AdditionalLibraryRootsProvider`() {
    lateinit var sourcesDirSymlink: SymlinkSpec
    lateinit var binariesDirSymlink: SymlinkSpec

    buildDirectoryContent(tempDirectory.newVirtualDirectory("libraryRoot")) {
      lateinit var targetSources: DirectorySpec
      lateinit var targetBinaries: DirectorySpec
      dir("target") {
        targetSources = dir("targetSources") {
          file("SourceFile.java", "class SourceFile {}")
        }
        targetBinaries = dir("targetBinaries") {
          file("BinaryFile.java", "class BinaryFile {}")
        }
      }
      dir("root") {
        sourcesDirSymlink = symlink("sources", targetSources)
        binariesDirSymlink = symlink("binaries", targetBinaries)
      }
    }

    val syntheticLibrary = createAndSetAdditionalLibraryRootProviderWithSingleLibrary(listOf(sourcesDirSymlink.file),
                                                                                      listOf(binariesDirSymlink.file))
    val syntheticLibraryOrigin = createSyntheticLibraryOrigin(syntheticLibrary)
    assertOrigin(syntheticLibraryOrigin, sourcesDirSymlink)
    assertOrigin(syntheticLibraryOrigin, sourcesDirSymlink, "SourceFile.java")
    assertOrigin(syntheticLibraryOrigin, binariesDirSymlink)
    assertOrigin(syntheticLibraryOrigin, binariesDirSymlink, "BinaryFile.java")
  }

  @Test
  fun `symlink which is a root provided by AdditionalLibraryRootsProvider and which points to excluded target`() {
    lateinit var targetSources: DirectorySpec
    lateinit var targetBinaries: DirectorySpec

    projectModelRule.createJavaModule("moduleName") {
      content("contentRoot") {
        excluded("excluded") {
          targetSources = moduleDir("sources") {
            file("TargetSource.java", "class TargetSource {}")
          }
          targetBinaries = moduleDir("binaries") {
            file("TargetBinary.java", "class TargetBinary {}")
          }
        }
      }
    }

    lateinit var sourcesSymlink: SymlinkSpec
    lateinit var binariesSymlink: SymlinkSpec
    buildDirectoryContent(tempDirectory.newVirtualDirectory("library")) {
      sourcesSymlink = symlink("sources", targetSources)
      binariesSymlink = symlink("binaries", targetBinaries)
    }

    val syntheticLibrary = createAndSetAdditionalLibraryRootProviderWithSingleLibrary(listOf(sourcesSymlink.file),
                                                                                      listOf(binariesSymlink.file))
    val syntheticLibraryOrigin = createSyntheticLibraryOrigin(syntheticLibrary)
    assertOrigin(syntheticLibraryOrigin, sourcesSymlink)
    assertOrigin(syntheticLibraryOrigin, sourcesSymlink, "TargetSource.java")
    assertOrigin(syntheticLibraryOrigin, binariesSymlink)
    assertOrigin(syntheticLibraryOrigin, binariesSymlink, "TargetBinary.java")
  }


  @Test
  fun `all of directory symlinks from AdditionalLibraryRootsProvider having the same target directory`() {
    lateinit var symlinkDirOne: ContentSpec
    lateinit var symlinkDirTwo: ContentSpec
    buildDirectoryContent(tempDirectory.newVirtualDirectory("libraryRoot")) {
      val targetDir = dir("target") {
        file("TargetFile.java", "class TargetFile {}")
      }
      symlinkDirOne = symlink("symlinkDirOne", targetDir)
      symlinkDirTwo = symlink("symlinkDirTwo", targetDir)
    }

    val syntheticLibrary = createAndSetAdditionalLibraryRootProviderWithSingleLibrary(listOf(symlinkDirOne.file),
                                                                                      listOf(symlinkDirTwo.file))
    val syntheticLibraryOrigin = createSyntheticLibraryOrigin(syntheticLibrary)
    assertOrigin(syntheticLibraryOrigin, symlinkDirOne)
    assertOrigin(syntheticLibraryOrigin, symlinkDirOne, "TargetFile.java")
    assertOrigin(syntheticLibraryOrigin, symlinkDirTwo)
    assertOrigin(syntheticLibraryOrigin, symlinkDirTwo, "TargetFile.java")
  }

  @Test
  fun `all of symlinks from AdditionalLibraryRootsProvider having the same target file`() {
    lateinit var targetFile: FileSpec
    lateinit var sourceDir: DirectorySpec

    lateinit var symlinkOne: SymlinkSpec
    lateinit var symlinkTwo: SymlinkSpec
    buildDirectoryContent(tempDirectory.newVirtualDirectory("libraryRoot")) {
      dir("target") {
        targetFile = file("TargetFile.java", "class TargetFile {}")
      }
      sourceDir = dir("source") {
        symlinkOne = symlink("symlinkOne", targetFile)
        symlinkTwo = symlink("symlinkTwo", targetFile)
      }
    }

    val syntheticLibrary = createAndSetAdditionalLibraryRootProviderWithSingleLibrary(listOf(sourceDir.file))
    val syntheticLibraryOrigin = createSyntheticLibraryOrigin(syntheticLibrary)
    assertEveryFileOrigin(syntheticLibraryOrigin, symlinkOne, symlinkTwo)
  }
}
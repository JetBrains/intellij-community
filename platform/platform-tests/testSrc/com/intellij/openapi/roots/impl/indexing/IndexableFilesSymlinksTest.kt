// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl.indexing

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.*
import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.indexing.IndexableSetContributor
import org.junit.Before
import org.junit.Test

@RunsInEdt
class IndexableFilesSymlinksTest : IndexableFilesBaseTest() {
  @Before
  fun applicabilityCheck() {
    IoTestUtil.assumeSymLinkCreationIsSupported()
  }

  // Symlinks in ProjectFileIndex are treated as if they were copy of the target's content,
  // so exclusion of the target is not taken into account.
  @Test
  fun `symlink from content root to excluded target must be indexed`() {
    lateinit var targetFile: ContentSpec
    lateinit var targetDir: ContentSpec

    lateinit var symlink: ContentSpec
    lateinit var symlinkDir: ContentSpec
    projectModelRule.createJavaModule("moduleName") {
      content("contentRoot") {
        excluded("excluded") {
          targetFile = file("Target.java", "class Target {}")
          targetDir = dir("targetDir") {
            file("TargetUnderDir.java", "class TargetUnderDir {}")
          }
        }
        symlink = symlink("symlink.java", targetFile)
        symlinkDir = symlink("symlinkDir", targetDir)
      }
    }
    assertIndexableFiles(symlink.file, symlinkDir.file, symlinkDir.file / "TargetUnderDir.java")
  }

  @Test
  fun `symlinks in a Library must be indexed`() {
    lateinit var classesSymlink: SymlinkSpec
    lateinit var sourcesSymlink: SymlinkSpec

    buildDirectoryContent(tempDirectory.newVirtualDirectory("library")) {
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

    val module = projectModelRule.createModule()
    projectModelRule.addModuleLevelLibrary(module, "libraryName") { model ->
      model.addRoot(classesSymlink.file, OrderRootType.CLASSES)
      model.addRoot(sourcesSymlink.file, OrderRootType.SOURCES)
    }
    assertIndexableFiles(
      classesSymlink.file,
      classesSymlink.file / "ClassFile.java",
      sourcesSymlink.file,
      sourcesSymlink.file / "SourceFile.java"
    )
  }

  /**
   * Indicator of the following bug: IDEA-189247
   * When a directory is soft-linked into a project, its excluded subdirectories are still indexed.
   */
  @Test
  fun `index excluded directories if they are referenced via symlink`() {
    lateinit var sourcesDir: DirectorySpec
    lateinit var workspaceSymlink: SymlinkSpec
    projectModelRule.createJavaModule("moduleName") {
      content("contentRoot") {
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
    assertIndexableFiles(
      sourcesDir.file / "SourceFile.java",
      workspaceSymlink.file,
      workspaceSymlink.file / "sources" / "SourceFile.java",
      workspaceSymlink.file / "excluded" / "ExcludedFile.java",
    )
  }

  @Test
  fun `symlink which is a root of a Library must be indexed even if its target is excluded`() {
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
    projectModelRule.addModuleLevelLibrary(module, "libraryName") { model ->
      model.addRoot(classesSymlink.file, OrderRootType.CLASSES)
      model.addRoot(sourcesSymlink.file, OrderRootType.SOURCES)
    }
    assertIndexableFiles(
      classesSymlink.file,
      classesSymlink.file / "ClassFile.java",
      sourcesSymlink.file,
      sourcesSymlink.file / "SourceFile.java"
    )
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
    assertIndexableFiles(
      classesSymlink.file,
      classesSymlink.file / "ClassFile.java",
      sourcesSymlink.file,
      sourcesSymlink.file / "SourceFile.java"
    )
  }

  @Test
  fun `symlink which is root of an SDK and points to excluded target must be indexed`() {
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
    assertIndexableFiles(
      classesSymlink.file,
      classesSymlink.file / "ClassFile.java",
      sourcesSymlink.file,
      sourcesSymlink.file / "SourceFile.java"
    )
  }

  @Test
  fun `symlink provided by IndexableSetContributor must be indexed`() {
    val additionalRoot = tempDirectory.newVirtualDirectory("additionalRoot")

    lateinit var symlinkToTarget: SymlinkSpec
    buildDirectoryContent(additionalRoot) {
      val targetDir = dir("target") {
        file("TargetFile.java", "class TargetFile {}")
      }
      symlinkToTarget = symlink("symlinkDir", targetDir)
    }
    val contributor = object : IndexableSetContributor() {
      override fun getAdditionalRootsToIndex(): Set<VirtualFile> =
        setOf(symlinkToTarget.file)
    }
    maskIndexableSetContributors(contributor)
    assertIndexableFiles(symlinkToTarget.file, symlinkToTarget.file / "TargetFile.java")
  }

  @Test
  fun `symlink which is a root provided by IndexableSetContributor and points to excluded target must be indexed`() {
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
    val contributor = object : IndexableSetContributor() {
      override fun getAdditionalRootsToIndex(): Set<VirtualFile> =
        setOf(symlinkToTarget.file)
    }
    maskIndexableSetContributors(contributor)
    assertIndexableFiles(symlinkToTarget.file, symlinkToTarget.file / "TargetFile.java")
  }

  @Test
  fun `symlink provided by AdditionalLibraryRootsProvider must be indexed`() {
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

    val additionalLibraryRootsProvider = object : AdditionalLibraryRootsProvider() {
      override fun getAdditionalProjectLibraries(project: Project) = listOf(
        SyntheticLibrary.newImmutableLibrary(
          "test",
          listOf(sourcesDirSymlink.file),
          listOf(binariesDirSymlink.file),
          emptySet(),
          null
        )
      )
    }

    maskAdditionalLibraryRootsProviders(additionalLibraryRootsProvider)
    assertIndexableFiles(
      sourcesDirSymlink.file,
      sourcesDirSymlink.file / "SourceFile.java",
      binariesDirSymlink.file,
      binariesDirSymlink.file / "BinaryFile.java"
    )
  }

  @Test
  fun `symlink which is a root provided by AdditionalLibraryRootsProvider and which points to excluded target must be indexed`() {
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

    val additionalLibraryRootsProvider = object : AdditionalLibraryRootsProvider() {
      override fun getAdditionalProjectLibraries(project: Project) = listOf(
        SyntheticLibrary.newImmutableLibrary(
          "test",
          listOf(sourcesSymlink.file),
          listOf(binariesSymlink.file),
          emptySet(),
          null
        )
      )
    }

    maskAdditionalLibraryRootsProviders(additionalLibraryRootsProvider)
    assertIndexableFiles(
      sourcesSymlink.file,
      sourcesSymlink.file / "TargetSource.java",
      binariesSymlink.file,
      binariesSymlink.file / "TargetBinary.java"
    )
  }

  @Test
  fun `all of directory symlinks from AdditionalLibraryRootsProvider having the same target directory are indexed`() {
    lateinit var symlinkDirOne: ContentSpec
    lateinit var symlinkDirTwo: ContentSpec
    buildDirectoryContent(tempDirectory.newVirtualDirectory("libraryRoot")) {
      val targetDir = dir("target") {
        file("TargetFile.java", "class TargetFile {}")
      }
      symlinkDirOne = symlink("symlinkDirOne", targetDir)
      symlinkDirTwo = symlink("symlinkDirTwo", targetDir)
    }

    val additionalLibraryRootsProvider = object : AdditionalLibraryRootsProvider() {
      override fun getAdditionalProjectLibraries(project: Project) = listOf(
        SyntheticLibrary.newImmutableLibrary(
          "test",
          listOf(symlinkDirOne.file),
          listOf(symlinkDirTwo.file),
          emptySet(),
          null
        )
      )
    }
    maskAdditionalLibraryRootsProviders(additionalLibraryRootsProvider)
    assertIndexableFiles(
      symlinkDirOne.file,
      symlinkDirOne.file / "TargetFile.java",
      symlinkDirTwo.file,
      symlinkDirTwo.file / "TargetFile.java"
    )
  }

  @Test
  fun `all of symlinks from AdditionalLibraryRootsProvider having the same target file are indexed`() {
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

    val additionalLibraryRootsProvider = object : AdditionalLibraryRootsProvider() {
      override fun getAdditionalProjectLibraries(project: Project) = listOf(
        SyntheticLibrary.newImmutableLibrary(listOf(sourceDir.file))
      )
    }

    maskAdditionalLibraryRootsProviders(additionalLibraryRootsProvider)
    assertIndexableFiles(symlinkOne.file, symlinkTwo.file)
  }
}
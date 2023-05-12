// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootModel
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy
import com.intellij.openapi.roots.impl.indexing.DirectorySpec
import com.intellij.openapi.roots.impl.indexing.FileSpec
import com.intellij.openapi.roots.impl.indexing.buildDirectoryContent
import com.intellij.openapi.roots.impl.indexing.createJavaModule
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.util.Function
import org.junit.Test

class IndexableFilesIndexExcludedOriginsTest : IndexableFilesIndexOriginsTestBase() {
  @Test
  fun `no origins for files under directory excluded by DirectoryIndexExcludePolicy`() {
    lateinit var excludedDir: DirectorySpec
    lateinit var excludedFile: FileSpec
    projectModelRule.createJavaModule("moduleName") {
      content("contentRoot") {
        excludedDir = dir("excludedByPolicy") {
          excludedFile = file("ExcludedFile.java", "class ExcludedFile {}")
        }
      }
    }
    val excludedDirFile = excludedDir.file  // load VFS synchronously outside read action
    val directoryIndexExcludePolicy = object : DirectoryIndexExcludePolicy {
      override fun getExcludeUrlsForProject(): Array<String> =
        arrayOf(excludedDirFile.url)
    }
    maskDirectoryIndexExcludePolicy(directoryIndexExcludePolicy)
    assertNoOrigin(excludedDir, excludedFile)
  }

  @Test
  fun `no origins for files under module directory excluded by DirectoryIndexExcludePolicy`() {
    lateinit var excludedDir: DirectorySpec
    lateinit var excludedFile: FileSpec
    projectModelRule.createJavaModule("moduleName") {
      content("contentRoot") {
        excludedDir = dir("excludedByPolicy") {
          excludedFile = file("ExcludedFile.java", "class ExcludedFile {}")
        }
      }
    }
    val excludedDirFile = excludedDir.file  // load VFS synchronously outside read action
    val directoryIndexExcludePolicy = object : DirectoryIndexExcludePolicy {
      override fun getExcludeRootsForModule(rootModel: ModuleRootModel) =
        arrayOf(VirtualFilePointerManager.getInstance().create(excludedDirFile, disposableRule.disposable, null))
    }
    maskDirectoryIndexExcludePolicy(directoryIndexExcludePolicy)
    assertNoOrigin(excludedDir, excludedFile)
  }

  @Test
  fun `files of SDK excluded by DirectoryIndexExcludePolicy must not be indexed`() {
    val sdkRoot = tempDirectory.newVirtualDirectory("sdkRoot")

    lateinit var classesDir: DirectorySpec
    lateinit var sourcesDir: DirectorySpec
    lateinit var excludedClassFile: FileSpec
    lateinit var excludedSourceFile: FileSpec

    lateinit var excludedClassesDir: DirectorySpec
    lateinit var excludedSourcesDir: DirectorySpec

    buildDirectoryContent(sdkRoot) {
      dir("sdk") {
        classesDir = dir("classes") {
          excludedClassesDir = dir("excluded") {
            excludedClassFile = file("ExcludedClassFile.java", "class ExcludedClassFile {}")
          }
        }
        sourcesDir = dir("sources") {
          excludedSourcesDir = dir("excluded") {
            excludedSourceFile = file("ExcludedSourceFile.java", "class ExcludedSourceFile {}")
          }
        }
      }
    }

    val classesDirFile = classesDir.file  // load VFS synchronously outside read action
    val sourcesDirFile = sourcesDir.file  // load VFS synchronously outside read action
    val sdk = projectModelRule.addSdk("sdkName") { sdkModificator ->
      sdkModificator.addRoot(classesDirFile, OrderRootType.CLASSES)
      sdkModificator.addRoot(sourcesDirFile, OrderRootType.SOURCES)
    }
    val module = projectModelRule.createModule()
    ModuleRootModificationUtil.setModuleSdk(module, sdk)
    val sdkOrigin = createSdkOrigin(sdk)
    assertEveryFileOrigin(sdkOrigin, excludedClassesDir, excludedSourcesDir, excludedSourceFile, excludedClassFile)

    val excludedClassesDirFile = excludedClassesDir.file  // load VFS synchronously outside read action
    val excludedSourcesDirFile = excludedSourcesDir.file  // load VFS synchronously outside read action
    val policy = object : DirectoryIndexExcludePolicy {
      override fun getExcludeSdkRootsStrategy() = Function<Sdk, List<VirtualFile>> { sdkExclude ->
        if (sdkExclude == sdk) listOf(excludedClassesDirFile, excludedSourcesDirFile) else emptyList()
      }
    }
    maskDirectoryIndexExcludePolicy(policy)
    assertNoOrigin(excludedClassesDir, excludedClassFile, excludedSourcesDir, excludedSourceFile)
  }

  private fun maskDirectoryIndexExcludePolicy(vararg directoryIndexExcludePolicy: DirectoryIndexExcludePolicy) {
    runWriteAction {
      (DirectoryIndexExcludePolicy.EP_NAME.getPoint(project) as ExtensionPointImpl<DirectoryIndexExcludePolicy>).maskAll(
        directoryIndexExcludePolicy.toList(), disposableRule.disposable, true)
      fireRootsChanged()
    }
  }
}
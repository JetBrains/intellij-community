// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl.indexing

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootModel
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.Function
import org.junit.Test

@RunsInEdt
class IndexableFilesExcludedWithDirectoryIndexExcludePolicyTest : IndexableFilesBaseTest() {
  @Test
  fun `file residing under directory excluded by DirectoryIndexExcludePolicy must not be indexed`() {
    lateinit var excludedDir: DirectorySpec
    projectModelRule.createJavaModule("moduleName") {
      content("contentRoot") {
        excludedDir = dir("excludedByPolicy") {
          file("ExcludedFile.java", "class ExcludedFile {}")
        }
      }
    }
    val directoryIndexExcludePolicy = object : DirectoryIndexExcludePolicy {
      override fun getExcludeUrlsForProject(): Array<String> =
        arrayOf(excludedDir.file.url)
    }
    maskDirectoryIndexExcludePolicy(directoryIndexExcludePolicy)
    assertIndexableFiles()
  }

  @Test
  fun `file residing under module directory excluded by DirectoryIndexExcludePolicy must not be indexed`() {
    lateinit var excludedDir: DirectorySpec
    projectModelRule.createJavaModule("moduleName") {
      content("contentRoot") {
        excludedDir = dir("excludedByPolicy") {
          file("ExcludedFile.java", "class ExcludedFile {}")
        }
      }
    }
    val directoryIndexExcludePolicy = object : DirectoryIndexExcludePolicy {
      override fun getExcludeRootsForModule(rootModel: ModuleRootModel) =
        arrayOf(VirtualFilePointerManager.getInstance().create(excludedDir.file, disposableRule.disposable, null))
    }
    maskDirectoryIndexExcludePolicy(directoryIndexExcludePolicy)
    assertIndexableFiles()
  }

  @Test
  fun `files of SDK excluded by DirectoryIndexExcludePolicy must not be indexed`() {
    val sdkRoot = tempDirectory.newVirtualDirectory("sdkRoot")

    lateinit var classesDir: DirectorySpec
    lateinit var sourcesDir: DirectorySpec

    lateinit var excludedClassesDir: DirectorySpec
    lateinit var excludedSourcesDir: DirectorySpec

    buildDirectoryContent(sdkRoot) {
      dir("sdk") {
        classesDir = dir("classes") {
          excludedClassesDir = dir("excluded") {
            file("ExcludedClassFile.java", "class ExcludedClassFile {}")
          }
        }
        sourcesDir = dir("sources") {
          excludedSourcesDir = dir("excluded") {
            file("ExcludedSourceFile.java", "class ExcludedSourceFile {}")
          }
        }
      }
    }

    val sdk = projectModelRule.addSdk("sdkName") { sdkModificator ->
      sdkModificator.addRoot(classesDir.file, OrderRootType.CLASSES)
      sdkModificator.addRoot(sourcesDir.file, OrderRootType.SOURCES)
    }

    val directoryIndexExcludePolicy = object : DirectoryIndexExcludePolicy {
      override fun getExcludeSdkRootsStrategy() = Function<Sdk, List<VirtualFile>> { sdkExclude ->
        check(sdkExclude == sdk)
        listOf(excludedClassesDir.file, excludedSourcesDir.file)
      }
    }

    maskDirectoryIndexExcludePolicy(directoryIndexExcludePolicy)
    val module = projectModelRule.createModule()
    ModuleRootModificationUtil.setModuleSdk(module, sdk)
    assertIndexableFiles()
  }

  private fun maskDirectoryIndexExcludePolicy(vararg directoryIndexExcludePolicy: DirectoryIndexExcludePolicy) {
    runWriteAction {
      (DirectoryIndexExcludePolicy.EP_NAME.getPoint(project) as ExtensionPointImpl<DirectoryIndexExcludePolicy>).maskAll(
        directoryIndexExcludePolicy.toList(), disposableRule.disposable, true)
      fireRootsChanged()
    }
  }

}
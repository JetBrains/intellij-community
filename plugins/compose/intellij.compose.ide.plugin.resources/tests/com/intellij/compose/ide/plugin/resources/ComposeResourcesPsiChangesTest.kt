// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.intellij.compose.ide.plugin.resources

import com.intellij.compose.ide.plugin.resources.psi.ComposeResourcesPsiChangesListener
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.file.impl.FileManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test


@TestRoot("../../../community/plugins/compose/intellij.compose.ide.plugin.resources/testData")
@TestMetadata("")
class ComposeResourcesPsiChangesTest : ComposeResourcesTestCase() {

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test adding new resources inside composeResources inner directories`() = doTest { files, fileManager, values ->
    val composeResourcesDrawableDir = files.find { it.name.endsWith("compose-multiplatform.xml") }!!.parent
    val dir = runReadAction { fileManager.findDirectory(composeResourcesDrawableDir)!! }
    runWriteAction { composeResourcesDrawableDir.createChildData(dir, "test1.png") }

    assertEquals(1, values.size)
    assertEquals("commonMain", values.first().sourceSetName)

    runWriteAction {
      composeResourcesDrawableDir.createChildData(dir, "test2.png")
      composeResourcesDrawableDir.createChildData(dir, "test3.png")
      composeResourcesDrawableDir.createChildData(dir, "test4.png")
      composeResourcesDrawableDir.createChildData(dir, "test5.png")
      composeResourcesDrawableDir.createChildData(dir, "test6.png")
      composeResourcesDrawableDir.createChildData(dir, "test7.png")
      composeResourcesDrawableDir.createChildData(dir, "test8.png")
      composeResourcesDrawableDir.createChildData(dir, "test9.png")
      composeResourcesDrawableDir.createChildData(dir, "test10.png")
    }

    assertEquals(10, values.size)
  }

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeResources")
  fun `test moving resource files from outside composeResources inner directories`() = doTest { files, fileManager, values ->
    val composeResourcesDrawableDir = files.find { it.name.endsWith("compose-multiplatform.xml") }!!.parent
    val composeResourcesDir = composeResourcesDrawableDir.parent
    val root = runReadAction { fileManager.findDirectory(composeResourcesDir.parent)!! }
    val file = runWriteAction { root.virtualFile.createChildData(root, "root.png") }
    assertEquals(0, values.size) // outside composeResources dirs, no change

    runWriteAction { file.move(file, composeResourcesDrawableDir) }
    assertEquals(1, values.size)

    runWriteAction { file.move(file, composeResourcesDir.parent) }
    assertEquals(1, values.size) // outside composeResources dirs, no change
  }

  private fun doTest(body: suspend TestScope.(List<VirtualFile>, FileManager, List<ComposeResourcesDir>) -> Unit) = runTest {
    val files = importProjectFromTestData()
    val psiManager = PsiManagerEx.getInstanceEx(project)
    val fileManager = psiManager.fileManager

    psiManager.addPsiTreeChangeListener(ComposeResourcesPsiChangesListener(project), getTestRootDisposable())

    val values: MutableList<ComposeResourcesDir> = mutableListOf()
    val service = project.service<ComposeResourcesGenerationService>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      service.composeResourcesPsiChanges.toList(values)
    }

    assertEquals(0, values.size)
    body(files, fileManager, values)
  }
}
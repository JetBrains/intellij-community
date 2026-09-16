// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.intellij.compose.ide.plugin.resources

import com.intellij.compose.ide.plugin.resources.psi.ComposeResourcesPsiChangesListener
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.file.impl.FileManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


@ComposeResourcesCommonMainOnly
class ComposeResourcesPsiChangesTest : ComposeResourcesCodeInsightTestCase() {

  override val additionalSyntaxAndPatterns: Array<String>
    get() = arrayOf("glob:**/composeApp/src/commonMain/root.png", "glob:**/composeApp/src/commonMain/test.png")

  @Test
  fun `test adding new resources inside composeResources inner directories`() = doTest { fileManager, values ->
    val composeResourcesDrawableDir = getCommonComposeResourcesDrawableDir()
    val dir = readActionBlocking { fileManager.findDirectory(composeResourcesDrawableDir)!! }
    snapshotProjectFile("${getCommonComposeResourcesDrawableDirPath()}/test1.png")
    edtWriteAction { composeResourcesDrawableDir.createChildData(dir, "test1.png") }

    assertEquals(1, values.size)
    // TODO: Not ideal assert
    assertEquals(COMMON_MAIN, values.first().accessorsQualifier)

    for (index in 2..10) {
      snapshotProjectFile("${getCommonComposeResourcesDrawableDirPath()}/test$index.png")
    }
    edtWriteAction {
      for (index in 2..10) {
        composeResourcesDrawableDir.createChildData(dir, "test$index.png")
      }
    }

    assertEquals(10, values.size)
  }

  private data class MovingContext(
    val root: PsiDirectory,
    val composeResourcesDir: VirtualFile,
    val composeResourcesDrawableDir: VirtualFile,
  )

  @Test
  fun `test moving resource files from outside composeResources inner directories`() = doMovingTest { movingContext, values ->
    snapshotProjectFile("${getCommonSourceSetDirPath()}/root.png")
    val file = edtWriteAction { movingContext.root.virtualFile.createChildData(movingContext.root, "root.png") }
    assertEquals(0, values.size) // outside composeResources dirs, no change

    snapshotProjectFile("${getCommonComposeResourcesDrawableDirPath()}/root.png")
    edtWriteAction { file.move(file, movingContext.composeResourcesDrawableDir) }
    assertEquals(1, values.size)

    edtWriteAction { file.move(file, movingContext.composeResourcesDir.parent) }
    assertEquals(2, values.size)
  }

  @Test
  fun `test moving resource files from composeResources to outer directory`() = doMovingTest { movingContext, values ->
    snapshotProjectFile("${getCommonComposeResourcesDrawableDirPath()}/test.png")
    val file = edtWriteAction {
      movingContext.composeResourcesDrawableDir.createChildData(movingContext.composeResourcesDrawableDir, "test.png")
    }
    assertEquals(0, values.size)

    snapshotProjectFile("${getCommonSourceSetDirPath()}/test.png")
    edtWriteAction { file.move(file, movingContext.root.virtualFile) }
    assertEquals(1, values.size)

    edtWriteAction { file.move(file, movingContext.composeResourcesDrawableDir) }
    assertEquals(2, values.size)
  }

  private fun doMovingTest(body: suspend TestScope.(MovingContext, List<ComposeResourcesData>) -> Unit) =
    doTest { fileManager, values ->
      val composeResourcesDrawableDir = getCommonComposeResourcesDrawableDir()
      val composeResourcesDir = composeResourcesDrawableDir.parent
      val root = readActionBlocking { fileManager.findDirectory(composeResourcesDir.parent)!! }

      val context = MovingContext(root, composeResourcesDir, composeResourcesDrawableDir)
      body(context, values)
    }

  private fun doTest(body: suspend TestScope.(FileManager, List<ComposeResourcesData>) -> Unit) = testComposeResourcesProject {
    runTest {
      val psiManager = PsiManagerEx.getInstanceEx(project)
      val fileManager = psiManager.fileManager

      psiManager.addPsiTreeChangeListener(ComposeResourcesPsiChangesListener(project), codeInsightFixture.testRootDisposable)

      val values: MutableList<ComposeResourcesData> = mutableListOf()
      val service = project.service<ComposeResourcesGenerationService>()
      backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        service.composeResourcesPsiChanges.toList(values)
      }
      testScheduler.runCurrent()
      // The Gradle fixture reuses the project, and the service flow keeps the last event in its replay cache.
      values.clear()

      assertEquals(0, values.size)
      body(fileManager, values)
    }
  }

  private fun getCommonComposeResourcesDrawableDir(): VirtualFile =
    canonicalProjectFile("composeApp/src/$COMMON_MAIN/composeResources/drawable/compose-multiplatform.xml").parent

  private fun getCommonComposeResourcesDrawableDirPath(): String =
    "composeApp/src/$COMMON_MAIN/composeResources/drawable"

  private fun getCommonSourceSetDirPath(): String =
    "composeApp/src/$COMMON_MAIN"
}
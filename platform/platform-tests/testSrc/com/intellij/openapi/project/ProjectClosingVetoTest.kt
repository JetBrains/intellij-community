// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentSynchronizationVetoer
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.io.findOrCreateFile
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.application
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import kotlin.test.assertTrue

@TestApplication
internal class ProjectClosingVetoTest {
  private val projectDir by tempPathFixture()

  @Test
  fun testDocumentSaveVetoDoesntBlockProjectClosing() {
    val project = ProjectManagerEx.getInstanceEx().openProject(projectDir, OpenProjectTask.build())
    assertNotNull(project, "Couldn't create test project")

    try {
      val testFileName = "fileWithVetoedSaving.txt"

      FileDocumentSynchronizationVetoer.EP_NAME.point.registerExtension(object : FileDocumentSynchronizationVetoer() {
        override fun maySaveDocument(document: Document, isSaveExplicit: Boolean): Boolean {
          return FileDocumentManager.getInstance().getFile(document)?.name != testFileName
        }
      }, project)

      val filePath = projectDir.findOrCreateFile(testFileName)
      val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(filePath)
      assertNotNull(file, "Couldn't create test file")

      val document = application.runReadAction(Computable { FileDocumentManager.getInstance().getDocument(file) })
      assertNotNull(document, "Couldn't get document for test file")

      val projectClosed = invokeAndWaitIfNeeded {
        WriteCommandAction.runWriteCommandAction(project) {
          document.insertString(0, "foo")
        }

        ProjectManagerEx.getInstanceEx().closeAndDispose(project)
      }
      assertTrue(projectClosed, "Project wasn't closed")
    }
    finally {
      PlatformTestUtil.forceCloseProjectWithoutSaving(project)
    }
  }
}

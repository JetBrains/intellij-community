// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase.resources.full

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.findOrCreateFile
import com.intellij.openapi.vfs.writeText
import com.intellij.psi.PsiManager
import com.intellij.testFramework.junit5.resources.FullApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@Service(Service.Level.PROJECT)
private class MyService(val project: Project)

/**
 * Test that gets all resources in a fully automated manner, thanks to [FullApplication]
 */
@FullApplication
class JUnit5Automatic {

  @Test
    /**
     * [tempDir] is a JUnit5 temporary directory here
     */
  fun funProjectModuleEditor(module: Module, project: Project, @TempDir tempDir: Path): Unit = runBlocking {
    assertEquals(module.project, project.service<MyService>().project)
    writeAction {
      val file = LocalFileSystem.getInstance().findFileByNioFile(tempDir)!!
        .findOrCreateFile("file.txt")
        .apply { writeText("hello") }
      FileEditorManager.getInstance(project).openFile(file).first()
      Assertions.assertNotNull(PsiManager.getInstance(project).findFile(file)?.fileDocument)
    }
  }

  @Test
  fun funModule(module: Module) {
    assertFalse(module.isDisposed)
  }
}
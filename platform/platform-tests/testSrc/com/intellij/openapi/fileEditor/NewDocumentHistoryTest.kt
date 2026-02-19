// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.codeInsight.navigation.activateFileWithPsiElement
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.executeSomeCoroutineTasksAndDispatchAllInvocationEvents
import org.junit.Assert

class NewDocumentHistoryTest : HeavyFileEditorManagerTestCase() {
  override fun tearDown() {
    try {
      IdeDocumentHistory.getInstance(project).clearHistory()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun testBackNavigationBetweenEditors() {
    FileEditorProvider.EP_FILE_EDITOR_PROVIDER.point.registerExtension(FileEditorManagerTest.MyFileEditorProvider(),
                                                                       myFixture.testRootDisposable)
    val file = getFile("/src/1.txt")!!
    val manager = FileEditorManagerEx.getInstanceEx(project)
    val editors = manager.openFile(file = file, window = null, options = FileEditorOpenOptions(requestFocus = true)).allEditors
    assertThat(editors).hasSize(2)
    assertThat(manager.getSelectedEditor(file)?.name).isEqualTo("Text")

    manager.setSelectedEditor(file, "mock")

    executeSomeCoroutineTasksAndDispatchAllInvocationEvents(project)
    assertThat(manager.getSelectedEditor(file)!!.name).isEqualTo(FileEditorManagerTest.MyFileEditorProvider.DEFAULT_FILE_EDITOR_NAME)
    manager.closeAllFiles()
    IdeDocumentHistory.getInstance(project).back()
    assertThat(manager.getSelectedEditor(file)?.name).isEqualTo(FileEditorManagerTest.MyFileEditorProvider.DEFAULT_FILE_EDITOR_NAME)
  }

  fun testSelectFileOnNavigation() {
    val file1 = getFile("/src/1.txt")
    val manager = FileEditorManagerEx.getInstanceEx(project)
    manager.openFile(file1!!, true)
    val file2 = getFile("/src/2.txt")
    manager.openFile(file2!!, true)
    activateFileWithPsiElement(PsiManager.getInstance(project).findFile(file1)!!)
    val files = manager.selectedFiles
    assertEquals(1, files.size)
    assertEquals("1.txt", files[0].name)
  }

  fun testMergingCommands() {
    val file1 = getFile("/src/1.txt")
    val file2 = getFile("/src/2.txt")
    val file3 = getFile("/src/3.txt")
    val manager = FileEditorManagerEx.getInstanceEx(project)
    manager.openFile(file1!!, true)
    manager.openFile(file2!!, true)
    val group = Any()
    CommandProcessor.getInstance().executeCommand(project, EmptyRunnable.INSTANCE, null, group)
    CommandProcessor.getInstance().executeCommand(project, {
      manager.openFile(file = file3!!, focusEditor = true)
    }, null, group)
    IdeDocumentHistory.getInstance(project).back()
    val selectedFiles = manager.selectedFiles
    Assert.assertArrayEquals(arrayOf<VirtualFile?>(file2), selectedFiles)
  }
}
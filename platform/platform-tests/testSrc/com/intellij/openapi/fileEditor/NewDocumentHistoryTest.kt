// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx.Companion.getInstanceEx
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.junit.Assert

class NewDocumentHistoryTest : HeavyFileEditorManagerTestCase() {
  private var history: IdeDocumentHistoryImpl? = null

  public override fun setUp() {
    super.setUp()
    history = IdeDocumentHistoryImpl(project)
  }

  override fun tearDown() {
    try {
      Disposer.dispose(history!!)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      history = null
      super.tearDown()
    }
  }

  fun testBackNavigationBetweenEditors() {
    FileEditorProvider.EP_FILE_EDITOR_PROVIDER.point.registerExtension(FileEditorManagerTest.MyFileEditorProvider(),
                                                                       myFixture.testRootDisposable)
    val file = getFile("/src/1.txt")
    assertNotNull(file)
    val manager = getInstanceEx(project)
    val editors = manager.openFile(file!!, true)
    assertEquals(2, editors.size)
    assertEquals("Text", manager.getSelectedEditor(file)!!.name)
    manager.setSelectedEditor(file, "mock")
    assertEquals(FileEditorManagerTest.MyFileEditorProvider.DEFAULT_FILE_EDITOR_NAME, manager.getSelectedEditor(file)!!.name)
    manager.closeAllFiles()
    history!!.back()
    assertEquals(FileEditorManagerTest.MyFileEditorProvider.DEFAULT_FILE_EDITOR_NAME, manager.getSelectedEditor(file)!!.name)
  }

  fun testSelectFileOnNavigation() {
    val file1 = getFile("/src/1.txt")
    val manager = getInstanceEx(project)
    manager.openFile(file1!!, true)
    val file2 = getFile("/src/2.txt")
    manager.openFile(file2!!, true)
    NavigationUtil.activateFileWithPsiElement(PsiManager.getInstance(project).findFile(file1)!!)
    val files = manager.selectedFiles
    assertEquals(1, files.size)
    assertEquals("1.txt", files[0].name)
  }

  fun testMergingCommands() {
    val file1 = getFile("/src/1.txt")
    val file2 = getFile("/src/2.txt")
    val file3 = getFile("/src/3.txt")
    val manager = getInstanceEx(project)
    manager.openFile(file1!!, true)
    manager.openFile(file2!!, true)
    val group = Any()
    CommandProcessor.getInstance().executeCommand(project, {}, null, group)
    CommandProcessor.getInstance().executeCommand(project, {
      manager.openFile(file = file3!!, focusEditor = true)
    }, null, group)
    history!!.back()
    val selectedFiles = manager.selectedFiles
    Assert.assertArrayEquals(arrayOf<VirtualFile?>(file2), selectedFiles)
  }
}
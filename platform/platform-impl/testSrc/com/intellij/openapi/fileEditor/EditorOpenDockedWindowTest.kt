// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.impl.createEditorDockContainer
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.ui.docking.DockManager
import com.intellij.ui.docking.impl.DockManagerImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JLabel

@TestApplication
class EditorOpenDockedWindowTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  private val projectFixture = projectFixture(
    openProjectTask = OpenProjectTask {
      beforeInitTasks += { it.putUserData(FileEditorManagerKeys.ALLOW_IN_LIGHT_PROJECT, true) }
    },
    openAfterCreation = true,
  )
  private val managerFixture = projectFixture.fileEditorManagerFixture()

  @Test
  fun `future reuses a background composite`(): Unit = checkReuse(suspending = false, selectAsCurrent = false)

  @Test
  fun `future selects a reused composite`(): Unit = checkReuse(suspending = false, selectAsCurrent = true)

  @Test
  fun `suspending open reuses a background composite`(): Unit = checkReuse(suspending = true, selectAsCurrent = false)

  @Test
  fun `suspending open selects a reused composite`(): Unit = checkReuse(suspending = true, selectAsCurrent = true)

  @Test
  fun `a new window takes the north panel from the editors that open after it`(): Unit = timeoutRunBlocking {
    assertThat(northPanelOfNewWindow(showNorthPanel = null)).isEqualTo("true")
  }

  @Test
  fun `a new window has no north panel when its editor disables the panel`(): Unit = timeoutRunBlocking {
    assertThat(northPanelOfNewWindow(showNorthPanel = false)).isEqualTo("false")
  }

  /** Opens a file in a new window and does not block for its editors. Returns the `withNorthPanel` state of the window. */
  private suspend fun northPanelOfNewWindow(showNorthPanel: Boolean?): String {
    val file = LightVirtualFile("window.txt", "text")
    FileEditorProviderBean.EP_NAME.point.registerExtension(FileEditorProviderBean(NorthPanelProvider(file, showNorthPanel)), disposable)
    val manager = managerFixture.get()
    val request = FileEditorOpenRequest.defaults().withOpenMode(FileEditorOpenMode.NEW_WINDOW)
    val composite = manager.openFile(file, request)
    assertThat(composite.allEditors).hasSize(1)
    // the window decides in an event that is in the queue of the EDT before this one
    return withContext(Dispatchers.EDT) {
      try {
        val windows = (DockManager.getInstance(projectFixture.get()) as DockManagerImpl).state.getChildren("window")
        windows.single().getAttributeValue("withNorthPanel")
      }
      finally {
        manager.closeFile(file, moveFocus = false, closeAllCopies = true)
      }
    }
  }

  // HIDE_DEFAULT_EDITOR keeps the text editor out of the composite
  private class NorthPanelProvider(private val file: VirtualFile, private val showNorthPanel: Boolean?) : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean = file === this.file

    override fun acceptRequiresReadAction(): Boolean = false

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
      return NorthPanelEditor(file).also { it.putUserData(FileEditorManagerKeys.SHOW_NORTH_PANEL, showNorthPanel) }
    }

    override fun getEditorTypeId(): String = "north-panel-test"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
  }

  private class NorthPanelEditor(private val file: VirtualFile) : UserDataHolderBase(), FileEditor {
    private val component = JLabel(file.name)

    override fun getComponent(): JComponent = component

    override fun getPreferredFocusedComponent(): JComponent = component

    override fun getName(): String = file.name

    override fun getFile(): VirtualFile = file

    override fun setState(state: FileEditorState) {
    }

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = true

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {
    }

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
    }

    override fun dispose() {
    }
  }

  private fun checkReuse(suspending: Boolean, selectAsCurrent: Boolean): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val manager = managerFixture.get()
      val container = createEditorDockContainer(manager, false, this@timeoutRunBlocking.childScope("test dock"), false)
      val dockDisposable = Disposer.newDisposable(disposable)
      Disposer.register(dockDisposable, container)
      DockManager.getInstance(projectFixture.get()).register(container, dockDisposable)
      val window = container.splitters.currentWindow!!
      try {
        val file = LightVirtualFile("first.txt", "first")
        val otherFile = LightVirtualFile("second.txt", "second")
        val original = manager.requestOpenFileInWindow(file, window, FileEditorOpenRequest.defaults()).await()
        manager.requestOpenFileInWindow(otherFile, window, FileEditorOpenRequest.defaults()).await()
        val request = FileEditorOpenRequest.defaults().withOpenMode(FileEditorOpenMode.NEW_WINDOW)
          .withReuseOpen(true).withPin(true).withSelectAsCurrent(selectAsCurrent)
        val result = if (suspending) manager.openFile(file, request) else manager.requestOpenFile(file, request).await()
        assertThat(result).isSameAs(original)
        assertThat(window.getComposite(file)!!.isPinned).isTrue()
        assertThat(window.selectedFile).isEqualTo(if (selectAsCurrent) file else otherFile)
        assertThat(container.splitters.splitCount).isEqualTo(1)
      }
      finally {
        window.fileList.toList().forEach { window.closeFile(it) }
        Disposer.dispose(dockDisposable)
      }
    }
  }
}

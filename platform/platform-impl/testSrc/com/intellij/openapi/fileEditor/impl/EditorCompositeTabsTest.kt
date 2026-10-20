// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorProviderBean
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JLabel

/**
 * The tabs of an [EditorComposite] must stay in sync with its editors when an editor is removed or added after the tabs are created.
 */
@TestApplication
internal class EditorCompositeTabsTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  private val projectFixture = projectFixture(
    openProjectTask = OpenProjectTask {
      beforeInitTasks += { it.putUserData(FileEditorManagerKeys.ALLOW_IN_LIGHT_PROJECT, true) }
    },
    openAfterCreation = true,
  )
  private val managerFixture = projectFixture.fileEditorManagerFixture()

  private val file = LightVirtualFile("composite.txt")

  // close the file before the test disposable unregisters its providers
  @AfterEach
  fun closeFiles(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    managerFixture.get().closeAllFiles()
  }

  @Test
  fun `removing the first editor closes its own tab`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val composite = openComposite("a", "b", "c")
    composite.setSelectedEditor("c")

    composite.removeEditor("a")

    assertThat(composite.allEditors.map { it.name }).containsExactly("b", "c")
    assertThat(composite.tabTitles()).containsExactly("b", "c")
    assertThat(composite.selectedEditor?.name).isEqualTo("c")
  }

  @Test
  fun `a tab selected after an editor is removed selects its own editor`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val composite = openComposite("a", "b", "c")
    composite.removeEditor("a")

    composite.setSelectedEditor("c")

    assertThat(composite.selectedEditor?.name).isEqualTo("c")
  }

  @Test
  fun `a tab selected after an editor is added selects its own editor`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val composite = openComposite("a", "b")
    val provider = TestProvider("c")
    composite.addEditor(editor = provider.createEditor(projectFixture.get(), file), provider = provider)

    composite.setSelectedEditor("c")

    assertThat(composite.tabTitles()).containsExactly("a", "b", "c")
    assertThat(composite.selectedEditor?.name).isEqualTo("c")
  }

  private suspend fun openComposite(vararg names: String): EditorComposite {
    for (name in names) {
      FileEditorProviderBean.EP_NAME.point.registerExtension(FileEditorProviderBean(TestProvider(name)), disposable)
    }
    val manager = managerFixture.get()
    manager.openFile(file, false)
    val composite = requireNotNull(manager.getComposite(file))
    composite.waitForAvailable()
    assertThat(composite.tabTitles()).containsExactly(*names)
    return composite
  }

  private fun EditorComposite.tabTitles(): List<String> {
    return requireNotNull(UIUtil.findComponentOfType(component, JBTabsImpl::class.java)).tabs.map { it.text }
  }

  // HIDE_DEFAULT_EDITOR keeps the text editor out of the composite
  private inner class TestProvider(private val name: String) : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean = file === this@EditorCompositeTabsTest.file

    override fun acceptRequiresReadAction(): Boolean = false

    override fun createEditor(project: Project, file: VirtualFile): FileEditor = TestEditor(file, name)

    override fun getEditorTypeId(): String = name

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
  }

  private class TestEditor(private val file: VirtualFile, private val name: String) : UserDataHolderBase(), FileEditor {
    private val component = JLabel(name)

    override fun getComponent(): JComponent = component

    override fun getPreferredFocusedComponent(): JComponent = component

    override fun getName(): String = name

    override fun getFile(): VirtualFile = file

    override fun getState(level: FileEditorStateLevel): FileEditorState = FileEditorState.INSTANCE

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
}

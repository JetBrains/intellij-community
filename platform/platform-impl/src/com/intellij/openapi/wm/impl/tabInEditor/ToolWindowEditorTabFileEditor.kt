// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.initOnShow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.beans.PropertyChangeListener
import javax.swing.JComponent

internal class ToolWindowEditorTabFileEditor(
  private val project: Project,
  private val file: ToolWindowEditorTabFile,
) : UserDataHolderBase(), FileEditor {
  private val tabManager: ToolWindowEditorTabManager
    get() = ToolWindowEditorTabManager.getInstance(project)

  /**
   * Returns the runtime session associated with [file], or `null` while no content is attached.
   *
   * A session is available immediately when the file is created for content moved from a tool window
   * to the editor. For a persisted editor tab, the file may initially exist without a session; the
   * session is created later, when the editor is shown for the first time (see [rootComponent]).
   */
  private val session: ToolWindowEditorTabSession?
    get() = tabManager.getSession(file)

  /**
   * The component of this editor. The editor composite takes it only once, so it does not change.
   *
   * It gets the session component at once if the content is attached (the content was moved from a tool window),
   * or when it is shown for the first time. So a restored tab that the user does not open creates no content.
   */
  private val rootComponent: Wrapper by lazy(LazyThreadSafetyMode.NONE) {
    val sessionComponent = session?.component
    val wrapper = Wrapper(sessionComponent)
    // Paint the editor background until the content is shown, so the tab does not blink when it opens.
    wrapper.isOpaque = true
    wrapper.background = JBColor.lazy { EditorColorsManager.getInstance().globalScheme.defaultBackground }
    if (sessionComponent == null) {
      restoreJob = wrapper.initOnShow("ToolWindowEditorTabFileEditor content restore") {
        // The restore can touch the platform model, so it needs the write-intent lock.
        withContext(Dispatchers.EDT) {
          initContent(wrapper)
        }
      }
    }
    wrapper
  }

  private var restoreJob: Job? = null

  override fun getComponent(): JComponent = rootComponent

  override fun getPreferredFocusedComponent(): JComponent = session?.preferredFocusedComponent ?: rootComponent

  /**
   * Puts the session component into [wrapper], and restores the content first if it is not restored yet.
   */
  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  private fun initContent(wrapper: Wrapper) {
    val session = tabManager.getOrRestoreSession(file)
    if (session == null) {
      // The persisted content cannot be restored, so remove the corresponding editor tab.
      FileEditorManager.getInstance(project).closeFile(file)
      return
    }

    wrapper.setContent(session.component)
    // The wrapper holds the focus that was requested before the content was restored.
    if (wrapper.isFocusOwner) {
      IdeFocusManager.getInstance(project).requestFocus(session.preferredFocusedComponent, true)
    }
  }

  override fun getName(): @NlsSafe String = tabManager.getTabTitle(file)

  /**
   * Returns the persistent state of the tool window content represented by this editor.
   *
   * A state can be produced only when the editor has an attached [ToolWindowEditorTabSession],
   * the underlying [ToolWindowEditorTabFile] is persistent, and the corresponding
   * [ToolWindowEditorTabPersistenceProvider] can serialize the attached content.
   * While the content is not restored yet, the persisted state that [setState] received is returned,
   * so the tab is restorable after the next save too.
   *
   * Returns [FileEditorState.INSTANCE] when there is no state to persist.
   */
  override fun getState(level: FileEditorStateLevel): FileEditorState {
    val content = session?.content
                  ?: return tabManager.getPendingState(file) ?: FileEditorState.INSTANCE
    if (file.persistentPath == null) return FileEditorState.INSTANCE

    val provider =
      ToolWindowEditorTabPersistenceProviderUtil.getProvider(file.toolWindowId)
      ?: return FileEditorState.INSTANCE

    if (!provider.canSerialize(content)) return FileEditorState.INSTANCE

    return ToolWindowEditorTabState(provider.serialize(content))
  }

  /**
   * Keeps the persisted [state] to restore the tool window content represented by this editor.
   *
   * During editor restoration, a [ToolWindowEditorTabFile] may already exist without an attached
   * [ToolWindowEditorTabSession]. In that case, the content is deserialized from the [ToolWindowEditorTabState]
   * when the editor is shown for the first time, or earlier when an operation needs the content
   * (see [ToolWindowEditorTabManager.getOrRestoreSession]).
   *
   * If a session is already attached, the state is ignored. If the persisted content cannot be
   * restored, the editor tab is closed and the file is removed from further restoration.
   */
  override fun setState(state: FileEditorState) {
    val tabState = state as? ToolWindowEditorTabState ?: return
    tabManager.addPendingState(file, tabState)
  }

  override fun isModified(): Boolean = false

  override fun isValid(): Boolean = file.isValid

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

  override fun getFile() = file

  override fun dispose() {
    restoreJob?.cancel()
    tabManager.closeEditorTabFile(file, releaseContent = true)
    FileEditorManager.getInstance(project).closeFile(this.file)
  }
}

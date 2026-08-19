// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.getPreferredFocusedComponent
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.content.Content
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val LOG = logger<ToolWindowEditorTabManager>()

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class ToolWindowEditorTabManager(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) {
  /**
   * Sessions of tool window editor tabs currently attached to this project, keyed by their virtual files.
   *
   * A [ToolWindowEditorTabFile] may exist without a session, for example while a persistent tab is being restored.
   * A session is added once the corresponding tool window content is attached and removed when the tab is closed
   * or invalidated.
   */
  private val sessionByFile = ConcurrentHashMap<ToolWindowEditorTabFile, ToolWindowEditorTabSession>()

  /**
   * Returns the state of [file] while its tool window content is attached, or `null` if it is not.
   */
  fun getSession(file: ToolWindowEditorTabFile): ToolWindowEditorTabSession? = sessionByFile[file]

  /**
   * Returns the current presentation of [file], or `null` if the file has no attached content yet.
   */
  fun getTabPresentation(file: ToolWindowEditorTabFile): ToolWindowEditorTabPresentation? = sessionByFile[file]?.presentation

  /**
   * Returns the title of [file].
   *
   * If a session presentation is available, its title is returned. Otherwise, a persistent tab
   * falls back to the last known title stored in its [PersistentToolWindowEditorTabPath]. This allows
   * restored tabs to display their title before their presentation is loaded.
   *
   * Returns an empty string if no title is available.
   */
  fun getTabTitle(file: ToolWindowEditorTabFile): @NlsContexts.TabTitle String {
    getTabPresentation(file)?.let { return it.title }

    return file.presentableName
  }

  /**
   * Creates an editor-tab file for [content] when the content is moved from a tool window to the editor.
   *
   * If the corresponding tool window provides a [ToolWindowEditorTabPersistenceProvider] and [content]
   * can be serialized, a persistent file is created and registered using a newly generated
   * [PersistentToolWindowEditorTabPath]. Otherwise, a transient file is created.
   *
   * The content is attached to the resulting file before it is returned.
   *
   * @param toolWindowId the ID of the tool window that owns [content]
   * @param content the tool window content being moved to the editor
   * @return the file representing [content] in the editor
   */
  @RequiresEdt
  internal fun createEditorTabFileForContent(
    toolWindowId: String,
    content: Content,
  ): ToolWindowEditorTabFile {
    val provider = ToolWindowEditorTabPersistenceProviderUtil.getProvider(toolWindowId)

    val persistentFile = if (provider?.canSerialize(content) == true) {
      val path = PersistentToolWindowEditorTabPath(
        projectLocationHash = project.locationHash,
        toolWindowId = toolWindowId,
        persistenceId = UUID.randomUUID().toString(),
      )

      ToolWindowEditorTabFileRegistry.getInstance().getOrCreatePersistentFile(path)
    }
    else {
      null
    }

    val file = persistentFile ?: ToolWindowEditorTabFile(
      toolWindowId = toolWindowId,
      persistentPath = null,
    )

    attachContentToFile(file, content)
    return file
  }

  /**
   * Restores and attaches the tool window content represented by [file] from the persisted [state].
   *
   * The content is deserialized by the [ToolWindowEditorTabPersistenceProvider] registered for the
   * file's tool window. If deserialization succeeds, the restored content is attached to [file],
   * creating its runtime [ToolWindowEditorTabSession].
   *
   * Returns `false` if no persistence provider is registered for the tool window, the provider cannot
   * deserialize the persisted content state, or no session can be attached to the restored content.
   *
   * If content is created but restoration fails, the created content is released before returning `false`.
   *
   * @param file the editor-tab file whose content should be restored
   * @param state the persisted editor-tab state containing the serialized tool window content
   * @return `true` if the content was successfully restored and attached; `false` otherwise
   */
  @RequiresEdt
  internal fun restoreEditorTabFileContent(
    file: ToolWindowEditorTabFile,
    state: ToolWindowEditorTabState,
  ): Boolean {
    val provider = ToolWindowEditorTabPersistenceProviderUtil.getProvider(file.toolWindowId) ?: return false

    val content = provider.deserialize(project, state.contentState) ?: return false
    if (attachContentToFile(file, content)) {
      return true
    }

    content.release()
    return false
  }

  /**
   * Attaches [content] to [file] by creating a [ToolWindowEditorTabSession].
   *
   * If [file] already has an associated session, this method does nothing.
   *
   * A session is also not created when the tool window has no registered [ToolWindowEditorTabSupport].
   *
   * The newly created session is registered in [sessionByFile].
   */
  @RequiresEdt
  private fun attachContentToFile(
    file: ToolWindowEditorTabFile,
    content: Content,
  ): Boolean {
    val support = ToolWindowEditorTabSupportUtil.getSupport(file.toolWindowId)
    if (support == null) {
      LOG.error("No ToolWindowEditorTabSupport found for tool window '${file.toolWindowId}'")
      return false
    }

    val presentationFlow = support.getTabPresentationFlow(project, content)
    return attachContentToFile(file, content, presentationFlow)
  }

  /**
   * Attaches [content] to [file] with its presentation driven by [presentationFlow].
   */
  @RequiresEdt
  private fun attachContentToFile(
    file: ToolWindowEditorTabFile,
    content: Content,
    presentationFlow: Flow<ToolWindowEditorTabPresentation>,
  ): Boolean {
    if (sessionByFile.containsKey(file)) return false

    val component = content.component

    val session = ToolWindowEditorTabSession(
      project = project,
      file = file,
      content = content,
      component = component,
      preferredFocusedComponent =
        content.preferredFocusableComponent
        ?: component.getPreferredFocusedComponent()
        ?: component,
      presentationFlow = presentationFlow,
      coroutineScope = coroutineScope.childScope("ToolWindowEditorTabSession[${file.toolWindowId}]"),
    )

    sessionByFile[file] = session

    return true
  }

  /**
   * Creates a transient tab file with [content] attached and its presentation driven by [presentationFlow] instead of by
   * a [ToolWindowEditorTabSupport] registered on the extension point.
   *
   * The tests need both halves of that: a presentation they can drive themselves, and a tab that has a session even
   * while its tool window has no support registered.
   */
  @TestOnly
  @RequiresEdt
  internal fun createTransientEditorTabFileForTest(
    toolWindowId: String,
    content: Content,
    presentationFlow: Flow<ToolWindowEditorTabPresentation>,
  ): ToolWindowEditorTabFile {
    val file = ToolWindowEditorTabFile(
      toolWindowId = toolWindowId,
      persistentPath = null,
    )

    attachContentToFile(file, content, presentationFlow)
    return file
  }

  /**
   * Closes the editor-tab session.
   *
   * @param releaseContent whether the session's content should also be released;
   * false when ownership of the content is transferred elsewhere
   */
  @RequiresEdt
  internal fun closeEditorTabFile(file: ToolWindowEditorTabFile, releaseContent: Boolean) {
    if (file.getUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN) == true) {
      return
    }

    ToolWindowEditorTabFileRegistry.getInstance().removeFile(file)
    sessionByFile.remove(file)?.close(releaseContent)

    // remove file from recent files
    // TODO: fix: After restoring the file to the tool window, recent files does not update immediately
    EditorHistoryManager.getInstance(project).removeFile(file)

    file.invalidate()
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ToolWindowEditorTabManager = project.service()
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentMap
import org.jdom.Element
import org.jetbrains.annotations.NonNls

/**
 * `Heavy` entries should be disposed with [.destroy] to prevent leak of VirtualFilePointer
 */
internal class HistoryEntry(
  @JvmField val filePointer: VirtualFilePointer,
  /**
   * can be null when read from XML
   */
  @JvmField var selectedProvider: FileEditorProvider?,
  @JvmField var isPreview: Boolean,
  val isPersisted: Boolean,
  // ordered
  private var providerToState: Map<FileEditorProvider, FileEditorState>,
  private val disposable: Disposable?,
) {
  val providers: List<FileEditorProvider>
    get() = java.util.List.copyOf(providerToState.keys)

  companion object {
    const val TAG: @NonNls String = "entry"
    const val FILE_ATTRIBUTE: String = "file"
    const val FILE_ID_ATTRIBUTE: String = "file-id"
    const val MANAGING_FS_ATTRIBUTE: String = "managing-fs"
    const val PROTOCOL_ATTRIBUTE: String = "protocol"

    fun createHeavy(project: Project, e: Element): HistoryEntry? {
      val fileEditorProviderManager = FileEditorProviderManager.getInstance()
      val entryData = parseEntry(project = project, element = e, fileEditorProviderManager = fileEditorProviderManager)

      val disposable = Disposer.newDisposable()
      val pointer = try {
        VirtualFilePointerManager.getInstance().create(entryData.url, disposable, null)
      }
      catch (e: Throwable) {
        thisLogger().error(e)
        return null
      }

      val stateMap = LinkedHashMap<FileEditorProvider, FileEditorState>()
      for (state in entryData.providerStates) {
        stateMap.put(state.first, state.second)
      }
      return HistoryEntry(
        filePointer = pointer,
        selectedProvider = entryData.selectedProvider,
        isPreview = entryData.preview,
        disposable = disposable,
        isPersisted = true,
        providerToState = stateMap,
      )
    }
  }

  val file: VirtualFile?
    get() = filePointer.file

  fun getState(provider: FileEditorProvider): FileEditorState? = providerToState.get(provider)

  fun putState(provider: FileEditorProvider, state: FileEditorState) {
    providerToState = providerToState.toPersistentMap().put(provider, state)
  }

  fun destroy() {
    disposable?.let { Disposer.dispose(it) }
  }

  /**
   * @return element that was added to the `element`.
   * Returned element has tag [.TAG]. Never null.
   */
  fun writeExternal(project: Project): Element {
    val element = Element(TAG)
    element.setAttribute(FILE_ATTRIBUTE, filePointer.url)

    for ((provider, value) in providerToState) {
      val providerElement = Element(PROVIDER_ELEMENT)
      if (provider == selectedProvider) {
        providerElement.setAttribute(SELECTED_ATTRIBUTE_VALUE, true.toString())
      }
      providerElement.setAttribute(EDITOR_TYPE_ID_ATTRIBUTE, provider.editorTypeId)

      val stateElement = Element(STATE_ELEMENT)
      provider.writeState(value, project, stateElement)
      if (!stateElement.isEmpty) {
        providerElement.addContent(stateElement)
      }

      element.addContent(providerElement)
    }

    if (isPreview) {
      element.setAttribute(PREVIEW_ATTRIBUTE, true.toString())
    }

    return element
  }

  fun onProviderRemoval(provider: FileEditorProvider) {
    if (selectedProvider === provider) {
      selectedProvider = null
    }
    providerToState = providerToState.toPersistentMap().remove(provider)
  }
}

internal const val PROVIDER_ELEMENT: @NonNls String = "provider"
internal const val EDITOR_TYPE_ID_ATTRIBUTE: @NonNls String = "editor-type-id"
internal const val SELECTED_ATTRIBUTE_VALUE: @NonNls String = "selected"
internal const val STATE_ELEMENT: @NonNls String = "state"
internal const val PREVIEW_ATTRIBUTE: @NonNls String = "preview"

private val EMPTY_ELEMENT = Element("state")

private fun parseEntry(
  project: Project,
  element: Element,
  fileEditorProviderManager: FileEditorProviderManager,
): EntryData {
  if (element.name != HistoryEntry.TAG) {
    throw IllegalArgumentException("unexpected tag: $element")
  }

  val url = element.getAttributeValue(HistoryEntry.FILE_ATTRIBUTE)
  var providerStates = persistentListOf<Pair<FileEditorProvider, FileEditorState>>()
  var selectedProvider: FileEditorProvider? = null

  val file = VirtualFileManager.getInstance().findFileByUrl(url)
  for (providerElement in element.getChildren(PROVIDER_ELEMENT)) {
    val typeId = providerElement.getAttributeValue(EDITOR_TYPE_ID_ATTRIBUTE)
    val provider = fileEditorProviderManager.getProvider(typeId) ?: continue
    if (providerElement.getAttributeValue(SELECTED_ATTRIBUTE_VALUE).toBoolean()) {
      selectedProvider = provider
    }

    if (file != null) {
      val stateElement = providerElement.getChild(STATE_ELEMENT)
      val state = provider.readState(stateElement ?: EMPTY_ELEMENT, project, file)
      providerStates = providerStates.add(provider to state)
    }
  }

  return EntryData(
    url = url,
    providerStates = providerStates,
    selectedProvider = selectedProvider,
    preview = element.getAttributeBooleanValue(PREVIEW_ATTRIBUTE),
  )
}

internal class EntryData(
  @JvmField val url: String,
  @JvmField val providerStates: List<Pair<FileEditorProvider, FileEditorState>>,
  @JvmField val selectedProvider: FileEditorProvider?,
  @JvmField val preview: Boolean,
)
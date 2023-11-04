// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.InvalidDataException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.impl.LightFilePointer
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.platform.ide.ideFingerprint
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import org.jdom.Element
import org.jetbrains.annotations.NonNls

/**
 * `Heavy` entries should be disposed with [.destroy] to prevent leak of VirtualFilePointer
 */
internal class HistoryEntry private constructor(@JvmField val filePointer: VirtualFilePointer,
                                                /**
                                                 * can be null when read from XML
                                                 */
                                                @JvmField var selectedProvider: FileEditorProvider?,
                                                @JvmField var isPreview: Boolean,
                                                @JvmField val ideFingerprint: Long?,
                                                private val disposable: Disposable?) {
  // ordered
  private var providerToState = persistentMapOf<FileEditorProvider, FileEditorState>()

  val providers: List<FileEditorProvider>
    get() = providerToState.keys.toPersistentList()

  companion object {
    const val TAG: @NonNls String = "entry"
    const val FILE_ATTR: String = "file"

    fun createLight(file: VirtualFile,
                    providers: List<FileEditorProvider?>,
                    states: List<FileEditorState?>,
                    selectedProvider: FileEditorProvider,
                    preview: Boolean): HistoryEntry {
      val pointer: VirtualFilePointer = LightFilePointer(file)
      val entry = HistoryEntry(filePointer = pointer,
                               selectedProvider = selectedProvider,
                               isPreview = preview,
                               ideFingerprint = null,
                               disposable = null)
      for (i in providers.indices) {
        entry.putState(providers.get(i) ?: continue, states.get(i) ?: continue)
      }
      return entry
    }

    fun createLight(project: Project,
                    element: Element,
                    fileEditorProviderManager: FileEditorProviderManager): Pair<HistoryEntry, VirtualFile?> {
      val (entryData, file) = parseEntry(project = project, element = element, fileEditorProviderManager = fileEditorProviderManager)
      val pointer = LightFilePointer(entryData.url)
      val entry = HistoryEntry(filePointer = pointer,
                               selectedProvider = entryData.selectedProvider,
                               isPreview = entryData.preview,
                               ideFingerprint = entryData.ideFingerprint,
                               disposable = null)
      for (state in entryData.providerStates) {
        entry.putState(state.first, state.second)
      }
      return entry to file
    }

    fun createHeavy(project: Project,
                    file: VirtualFile,
                    providers: List<FileEditorProvider?>,
                    states: List<FileEditorState?>,
                    selectedProvider: FileEditorProvider,
                    preview: Boolean): HistoryEntry {
      if (project.isDisposed) {
        return createLight(file = file, providers = providers, states = states, selectedProvider = selectedProvider, preview = preview)
      }

      val disposable = Disposer.newDisposable()
      val pointer = VirtualFilePointerManager.getInstance().create(file, disposable, null)

      val entry = HistoryEntry(filePointer = pointer,
                               selectedProvider = selectedProvider,
                               isPreview = preview,
                               ideFingerprint = null,
                               disposable = disposable)
      for (i in providers.indices) {
        entry.putState(providers.get(i) ?: continue, states.get(i) ?: continue)
      }
      return entry
    }

    @Throws(InvalidDataException::class)
    fun createHeavy(project: Project, e: Element): HistoryEntry {
      if (project.isDisposed) {
        return createLight(project, e, FileEditorProviderManager.getInstance()).first
      }

      val (entryData, _) = parseEntry(project = project, element = e, fileEditorProviderManager = FileEditorProviderManager.getInstance())

      val disposable = Disposer.newDisposable()
      val pointer = VirtualFilePointerManager.getInstance().create(entryData.url, disposable, null)
      val entry = HistoryEntry(filePointer = pointer,
                               selectedProvider = entryData.selectedProvider,
                               isPreview = entryData.preview,
                               ideFingerprint = entryData.ideFingerprint,
                               disposable = disposable)
      for (state in entryData.providerStates) {
        entry.putState(state.first, state.second)
      }
      return entry
    }
  }

  val file: VirtualFile?
    get() = filePointer.file

  fun getState(provider: FileEditorProvider): FileEditorState? = providerToState.get(provider)

  fun putState(provider: FileEditorProvider, state: FileEditorState) {
    providerToState = providerToState.put(provider, state)
  }

  fun destroy() {
    if (disposable != null) Disposer.dispose(disposable)
  }

  /**
   * @return element that was added to the `element`.
   * Returned element has tag [.TAG]. Never null.
   */
  fun writeExternal(element: Element, project: Project): Element {
    val e = Element(TAG)
    element.addContent(e)
    e.setAttribute(FILE_ATTR, filePointer.url)
    e.setAttribute("ideFingerprint", ideFingerprint().toString())

    for ((provider, value) in providerToState) {
      val providerElement = Element(PROVIDER_ELEMENT)
      if (provider == selectedProvider) {
        providerElement.setAttribute(SELECTED_ATTR_VALUE, true.toString())
      }
      providerElement.setAttribute(EDITOR_TYPE_ID_ATTR, provider.editorTypeId)

      val stateElement = Element(STATE_ELEMENT)
      provider.writeState(value, project, stateElement)
      if (!stateElement.isEmpty) {
        providerElement.addContent(stateElement)
      }

      e.addContent(providerElement)
    }

    if (isPreview) {
      e.setAttribute(PREVIEW_ATTR, true.toString())
    }

    return e
  }

  fun onProviderRemoval(provider: FileEditorProvider) {
    if (selectedProvider === provider) {
      selectedProvider = null
    }
    providerToState = providerToState.remove(provider)
  }
}

private const val PROVIDER_ELEMENT: @NonNls String = "provider"
private const val EDITOR_TYPE_ID_ATTR: @NonNls String = "editor-type-id"
private const val SELECTED_ATTR_VALUE: @NonNls String = "selected"
private const val STATE_ELEMENT: @NonNls String = "state"
private const val PREVIEW_ATTR: @NonNls String = "preview"

private val EMPTY_ELEMENT = Element("state")

private fun parseEntry(project: Project,
                       element: Element,
                       fileEditorProviderManager: FileEditorProviderManager): Pair<EntryData, VirtualFile?> {
  if (element.name != HistoryEntry.TAG) {
    throw IllegalArgumentException("unexpected tag: $element")
  }

  val url = element.getAttributeValue(HistoryEntry.FILE_ATTR)
  var providerStates = persistentListOf<Pair<FileEditorProvider, FileEditorState>>()
  var selectedProvider: FileEditorProvider? = null

  val file = VirtualFileManager.getInstance().findFileByUrl(url)
  for (providerElement in element.getChildren(PROVIDER_ELEMENT)) {
    val typeId = providerElement.getAttributeValue(EDITOR_TYPE_ID_ATTR)
    val provider = fileEditorProviderManager.getProvider(typeId) ?: continue
    if (providerElement.getAttributeValue(SELECTED_ATTR_VALUE).toBoolean()) {
      selectedProvider = provider
    }

    if (file != null) {
      val stateElement = providerElement.getChild(STATE_ELEMENT)
      val state = provider.readState(stateElement ?: EMPTY_ELEMENT, project, file)
      providerStates = providerStates.add(provider to state)
    }
  }

  val preview = element.getAttributeValue(PREVIEW_ATTR) != null
  return EntryData(
    url = url,
    providerStates = providerStates,
    selectedProvider = selectedProvider,
    ideFingerprint = try {
      element.getAttributeValue("ideFingerprint")?.toLong()
    }
    catch (ignored: NumberFormatException) {
      null
    },
    preview = preview,
  ) to file
}

internal class EntryData(
  @JvmField val url: String,
  @JvmField val providerStates: List<Pair<FileEditorProvider, FileEditorState>>,
  @JvmField val selectedProvider: FileEditorProvider?,
  @JvmField val ideFingerprint: Long?,
  @JvmField val preview: Boolean,
)
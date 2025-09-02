// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.fileEditor

import com.intellij.ide.util.treeView.findCachedImageIcon
import com.intellij.openapi.fileEditor.impl.*
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.FileIdAdapter
import com.intellij.platform.ide.IdeFingerprint
import com.intellij.util.xmlb.jdomToJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jdom.CDATA
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls

private const val PINNED: @NonNls String = "pinned"
private const val CURRENT_IN_TAB = "current-in-tab"

@Internal
class FileEntry(
  @JvmField val tab: FileEntryTab,
  @JvmField val pinned: Boolean,
  @JvmField val currentInTab: Boolean,
  @JvmField val url: String,
  @JvmField val id: Int?,
  @JvmField val managingFsCreationTimestamp: Long?,
  @JvmField val protocol: String?,
  @JvmField val selectedProvider: String?,
  val ideFingerprint: IdeFingerprint?,
  @JvmField val isPreview: Boolean,
  @JvmField val providers: Map<String, Element?>,
)

private const val TAB = "tab"

private val json = Json { ignoreUnknownKeys = true }

@Internal
@Serializable
class FileEntryTab(
  @NlsSafe @JvmField val tabTitle: String? = null,
  @NlsSafe @JvmField val foregroundColor: Int? = null,
  @NlsSafe @JvmField val textAttributes: JsonObject? = null,
  @NlsSafe @JvmField val icon: ByteArray? = null,
)

internal fun parseFileEntry(fileElement: Element, storedIdeFingerprint: IdeFingerprint): FileEntry {
  val historyElement = fileElement.getChild(HistoryEntry.TAG)

  var selectedProvider: String? = null
  // ordered
  val providerListElement = historyElement.getChildren(PROVIDER_ELEMENT)
  val providers = LinkedHashMap<String, Element?>(providerListElement.size)
  for (providerElement in providerListElement) {
    val typeId = providerElement.getAttributeValue(EDITOR_TYPE_ID_ATTRIBUTE)
    providers.put(typeId, providerElement.getChild(STATE_ELEMENT))
    if (providerElement.getAttributeValue(SELECTED_ATTRIBUTE_VALUE).toBoolean()) {
      selectedProvider = typeId
    }
  }

  return FileEntry(
    tab = json.decodeFromString<FileEntryTab>(fileElement.getChildText(TAB) ?: "{}"),
    pinned = fileElement.getAttributeBooleanValue(PINNED),
    currentInTab = fileElement.getAttributeBooleanValue(CURRENT_IN_TAB),
    isPreview = historyElement.getAttributeBooleanValue(PREVIEW_ATTRIBUTE),
    url = historyElement.getAttributeValue(HistoryEntry.FILE_ATTRIBUTE),
    id = historyElement.getAttributeValue(HistoryEntry.FILE_ID_ATTRIBUTE)?.toIntOrNull(),
    selectedProvider = selectedProvider,
    providers = providers,
    ideFingerprint = storedIdeFingerprint,
    managingFsCreationTimestamp = historyElement.getAttributeValue(HistoryEntry.MANAGING_FS_ATTRIBUTE)?.toLongOrNull(),
    protocol = historyElement.getAttributeValue(HistoryEntry.PROTOCOL_ATTRIBUTE)
  )
}

internal fun writeWindow(result: Element, window: EditorWindow, delayedStates: Map<EditorComposite, FileEntry>) {
  val selectedComposite = window.selectedComposite
  val serializer = FileEntryTab.serializer()
  for (tab in window.tabbedPane.tabs.tabs) {
    val composite = tab.composite

    if (!FileIdAdapter.getInstance().shouldSaveEditorState(composite.file)) {
      continue
    }

    val fileElement = Element("file")

    val delayedState = delayedStates.get(composite)
    if (delayedState == null) {
      fileElement.addContent(composite.writeCurrentStateAsHistoryEntry(window.manager.project))
    }
    else {
      fileElement.addContent(composite.writeDelayedStateAsHistoryEntry(delayedState))
    }

    if (composite.isPinned) {
      fileElement.setAttribute(PINNED, "true")
    }
    if (composite === selectedComposite) {
      fileElement.setAttribute(CURRENT_IN_TAB, "true")
    }

    fileElement.addContent(Element(TAB).addContent(CDATA(json.encodeToString(serializer, FileEntryTab(
      tabTitle = tab.text,
      foregroundColor = tab.defaultForeground?.rgb,
      textAttributes = tab.editorAttributes?.let { editorAttributes ->
        jdomToJson(Element("a").also { editorAttributes.writeExternal(it) })
      },
      icon = tab.icon?.let { findCachedImageIcon(it) }?.encodeToByteArray(),
    )))))

    result.addContent(fileElement)
  }
}
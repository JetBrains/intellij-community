// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.fileEditor

import com.intellij.ide.util.treeView.findCachedImageIcon
import com.intellij.openapi.fileEditor.impl.*
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.ide.IdeFingerprint
import org.jdom.Element
import org.jetbrains.annotations.NonNls
import java.util.*

private const val PINNED: @NonNls String = "pinned"
private const val CURRENT_IN_TAB = "current-in-tab"

internal class FileEntry(
  @NlsSafe @JvmField val tabTitle: String?,
  @JvmField val tabIcon: ByteArray?,
  @JvmField val pinned: Boolean,
  @JvmField val currentInTab: Boolean,
  @JvmField val url: String,
  @JvmField val selectedProvider: String?,
  val ideFingerprint: IdeFingerprint?,
  @JvmField val isPreview: Boolean,
  @JvmField val providers: Map<String, Element?>,
)

private const val TAB_TITLE = "tabTitle"
private const val TAB_ICON = "tabIcon"

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
    tabTitle = fileElement.getAttributeValue(TAB_TITLE),
    tabIcon = fileElement.getAttributeValue(TAB_ICON)?.let { Base64.getDecoder().decode(it) },
    pinned = fileElement.getAttributeBooleanValue(PINNED),
    currentInTab = fileElement.getAttributeBooleanValue(CURRENT_IN_TAB),
    isPreview = historyElement.getAttributeBooleanValue(PREVIEW_ATTRIBUTE),
    url = historyElement.getAttributeValue(HistoryEntry.FILE_ATTRIBUTE),
    selectedProvider = selectedProvider,
    providers = providers,
    ideFingerprint = storedIdeFingerprint,
  )
}

internal fun writeWindow(result: Element, window: EditorWindow, delayedStates: Map<EditorComposite, FileEntry>) {
  val selectedComposite = window.selectedComposite
  for (tab in window.tabbedPane.tabs.tabs) {
    val composite = tab.composite
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

    fileElement.setAttribute(TAB_TITLE, tab.text)

    tab.icon?.let { findCachedImageIcon(it) }?.let {
      fileElement.setAttribute(TAB_ICON, Base64.getEncoder().withoutPadding().encodeToString(it.encodeToByteArray()))
    }
    result.addContent(fileElement)
  }
}
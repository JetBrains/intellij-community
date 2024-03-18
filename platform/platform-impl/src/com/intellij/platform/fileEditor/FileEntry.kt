// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.fileEditor

import com.intellij.openapi.fileEditor.impl.*
import com.intellij.platform.ide.IdeFingerprint
import kotlinx.collections.immutable.persistentMapOf
import org.jdom.Element
import org.jetbrains.annotations.NonNls

private const val PINNED: @NonNls String = "pinned"
private const val CURRENT_IN_TAB = "current-in-tab"

internal class FileEntry(
  @JvmField val pinned: Boolean,
  @JvmField val currentInTab: Boolean,
  @JvmField val url: String,
  @JvmField val selectedProvider: String?,
  val ideFingerprint: IdeFingerprint?,
  @JvmField val isPreview: Boolean,
  @JvmField val providers: Map<String, Element?>,
)

internal fun parseFileEntry(fileElement: Element, storedIdeFingerprint: IdeFingerprint): FileEntry {
  val historyElement = fileElement.getChild(HistoryEntry.TAG)

  var selectedProvider: String? = null
  // ordered
  var providers = persistentMapOf<String, Element?>()
  for (providerElement in historyElement.getChildren(PROVIDER_ELEMENT)) {
    val typeId = providerElement.getAttributeValue(EDITOR_TYPE_ID_ATTRIBUTE)
    providers = providers.put(typeId, providerElement.getChild(STATE_ELEMENT))
    if (providerElement.getAttributeValue(SELECTED_ATTRIBUTE_VALUE).toBoolean()) {
      selectedProvider = typeId
    }
  }

  return FileEntry(
    pinned = fileElement.getAttributeBooleanValue(PINNED),
    currentInTab = fileElement.getAttributeValue(CURRENT_IN_TAB, "true").toBoolean(),
    isPreview = historyElement.getAttributeValue(PREVIEW_ATTRIBUTE) != null,
    url = historyElement.getAttributeValue(HistoryEntry.FILE_ATTRIBUTE),
    selectedProvider = selectedProvider,
    providers = providers,
    ideFingerprint = storedIdeFingerprint,
  )
}

internal fun writeWindow(result: Element, window: EditorWindow) {
  for (composite in window.getComposites()) {
    val fileElement = Element("file")
    fileElement.addContent(composite.writeCurrentStateAsHistoryEntry(project = window.manager.project))
    if (window.isFilePinned(composite.file)) {
      fileElement.setAttribute(PINNED, "true")
    }
    if (composite != window.selectedComposite) {
      fileElement.setAttribute(CURRENT_IN_TAB, "false")
    }
    result.addContent(fileElement)
  }
}
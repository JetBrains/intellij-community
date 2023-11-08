// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.fileEditor

import com.intellij.openapi.fileEditor.impl.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.platform.ide.ideFingerprint
import kotlinx.collections.immutable.persistentListOf
import org.jdom.Element
import org.jetbrains.annotations.NonNls

private const val PINNED: @NonNls String = "pinned"
private const val IDE_FINGERPRINT: @NonNls String = "ideFingerprint"
private const val CURRENT_IN_TAB = "current-in-tab"

internal class FileEntry(
  @JvmField val pinned: Boolean,
  @JvmField val currentInTab: Boolean,
  @JvmField val url: String,
  @JvmField val selectedProvider: String?,
  @JvmField val ideFingerprint: Long?,
  @JvmField val isPreview: Boolean,
  @JvmField val providers: List<Pair<String, Element>>,
)

internal fun parseFileEntry(fileElement: Element): FileEntry {
  val historyElement = fileElement.getChild(HistoryEntry.TAG)

  var selectedProvider: String? = null
  var providers = persistentListOf<Pair<String, Element>>()
  for (providerElement in historyElement.getChildren(PROVIDER_ELEMENT)) {
    val typeId = providerElement.getAttributeValue(EDITOR_TYPE_ID_ATTR)
    providers = providers.add(typeId to providerElement.getChild(STATE_ELEMENT))
    if (providerElement.getAttributeValue(SELECTED_ATTR_VALUE).toBoolean()) {
      selectedProvider = typeId
    }
  }

  return FileEntry(
    pinned = fileElement.getAttributeBooleanValue(PINNED),
    currentInTab = fileElement.getAttributeValue(CURRENT_IN_TAB, "true").toBoolean(),
    isPreview = historyElement.getAttributeValue(PREVIEW_ATTR) != null,
    url = historyElement.getAttributeValue(HistoryEntry.FILE_ATTR),
    selectedProvider = selectedProvider,
    providers = providers,
    ideFingerprint = StringUtilRt.parseLong(fileElement.getAttributeValue(IDE_FINGERPRINT), 0),
  )
}

internal fun writeComposite(composite: EditorComposite, pinned: Boolean, selectedEditor: EditorComposite?, project: Project): Element {
  val fileElement = Element("file")
  composite.currentStateAsHistoryEntry().writeExternal(fileElement, project)
  if (pinned) {
    fileElement.setAttribute(PINNED, "true")
  }
  fileElement.setAttribute(IDE_FINGERPRINT, ideFingerprint().toString())
  if (composite != selectedEditor) {
    fileElement.setAttribute(CURRENT_IN_TAB, "false")
  }
  return fileElement
}

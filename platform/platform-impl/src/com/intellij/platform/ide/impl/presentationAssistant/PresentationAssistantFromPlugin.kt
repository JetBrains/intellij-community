// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.presentationAssistant

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.xmlb.XmlSerializerUtil

class PresentationAssistantStateOld {
  var showActionDescriptions = true
  var fontSize = 24
  var hideDelay = 4 * 1000
  var mainKeymap = getDefaultMainKeymap()
  var alternativeKeymap = getDefaultAlternativeKeymap()
  var horizontalAlignment = PopupHorizontalAlignment.CENTER
  var verticalAlignment = PopupVerticalAlignment.BOTTOM
  var margin = 5
}

enum class PopupHorizontalAlignment(val displayName: String, val id: Int) { LEFT("Left", 0), CENTER("Center", 1), RIGHT("Right", 2) }
enum class PopupVerticalAlignment(val displayName: String, val id: Int) { TOP("Top", 0), BOTTOM("Bottom", 2) }

@State(name = "PresentationAssistant", storages = [Storage("presentation-assistant.xml")])
class PresentationAssistantOld : PersistentStateComponent<PresentationAssistantStateOld> {
  val configuration = PresentationAssistantStateOld()
  override fun getState() = configuration
  override fun loadState(p: PresentationAssistantStateOld) {
    XmlSerializerUtil.copyBean(p, configuration)
  }

  companion object {
    val INSTANCE get() = service<PresentationAssistantOld>()
  }

}

class KeymapDescription(var name: String = "", var displayText: String = "") {
  fun getKeymap() = KeymapManager.getInstance().getKeymap(name)

  override fun equals(other: Any?): Boolean {
    return other is KeymapDescription && other.name == name && other.displayText == displayText
  }

  override fun hashCode(): Int {
    return name.hashCode() + 31 * displayText.hashCode()
  }
}

fun getCurrentOSKind() = when {
  SystemInfo.isMac -> KeymapKind.MAC
  else -> KeymapKind.WIN
}

fun getDefaultMainKeymap() = KeymapDescription(getCurrentOSKind().defaultLabel, "")
fun getDefaultAlternativeKeymap() =
  getCurrentOSKind().getAlternativeKind().let { KeymapDescription(it.defaultLabel, "for ${it.displayName}") }
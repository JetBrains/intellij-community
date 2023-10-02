// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/**
 * @author nik
 */
package com.intellij.platform.ide.impl.presentationAssistant

import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.PlatformUtils

enum class KeymapKind(val displayName: String, val defaultKeymapName: String) {
  WIN("Win/Linux", "\$default"),
  MAC("Mac", "Mac OS X 10.5+");

  fun getAlternativeKind() = when (this) {
    WIN -> MAC
    MAC -> WIN
  }
}

fun getCurrentOSKind() = when {
  SystemInfo.isMac -> KeymapKind.MAC
  else -> KeymapKind.WIN
}

class KeymapDescription(var name: String = "", var displayText: String = "") {
  fun getKind() = if (name.contains("Mac OS")) KeymapKind.MAC else KeymapKind.WIN
  fun getKeymap() = KeymapManager.getInstance().getKeymap(name)

  override fun equals(other: Any?): Boolean {
    return other is KeymapDescription && other.name == name && other.displayText == displayText
  }

  override fun hashCode(): Int {
    return name.hashCode() + 31 * displayText.hashCode()
  }
}

fun getDefaultMainKeymap() = KeymapDescription(getCurrentOSKind().defaultKeymapName, "")
fun getDefaultAlternativeKeymap() =
  getCurrentOSKind().getAlternativeKind().let { KeymapDescription(it.defaultKeymapName, "for ${it.displayName}") }

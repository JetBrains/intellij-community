// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.presentationAssistant

import com.intellij.ide.IdeBundle
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.annotations.Nls

internal class KeymapKind(val value: String, @Nls val displayName: String, @Nls val defaultLabel: String) {
  val isMac: Boolean = getContainsMacOS(value)

  fun getAlternativeKind() = if (getContainsMacOS(value)) WIN else MAC

  companion object {
    val MAC: KeymapKind = KeymapKind(KeymapManager.MAC_OS_X_10_5_PLUS_KEYMAP,
                              IdeBundle.message("presentation.assistant.configurable.keymap.mac"),
                              IdeBundle.message("presentation.assistant.configurable.keymap.mac"))

    val WIN: KeymapKind = KeymapKind(KeymapManager.DEFAULT_IDEA_KEYMAP,
                              IdeBundle.message("presentation.assistant.configurable.keymap.win"),
                              IdeBundle.message("presentation.assistant.configurable.keymap.win.label"))

    fun from(@NlsSafe value: String): KeymapKind = when (value) {
      KeymapManager.MAC_OS_X_10_5_PLUS_KEYMAP -> MAC
      KeymapManager.DEFAULT_IDEA_KEYMAP -> WIN
      else -> KeymapManagerEx.getInstanceEx().getKeymap(value)?.let {
        KeymapKind(value = value,
                   displayName = it.presentableName,
                   defaultLabel = if (getContainsMacOS(it.name)) IdeBundle.message("presentation.assistant.configurable.keymap.mac")
                   else it.presentableName)
      } ?: KeymapKind(value, value, value)
    }

    fun defaultForOS() = when {
      SystemInfo.isMac -> MAC
      else -> WIN
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as KeymapKind

    return value == other.value
  }

  override fun hashCode(): Int = value.hashCode()

  override fun toString(): String {
    return "KeymapKind(value='$value', displayName='$displayName', defaultLabel='$defaultLabel', isMac=$isMac)"
  }
}

private fun getContainsMacOS(s: String): Boolean = s.contains("macOS") || s.contains("Mac OS") || s.contains("OSX")

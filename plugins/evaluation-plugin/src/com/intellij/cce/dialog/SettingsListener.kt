package com.intellij.cce.dialog

import com.intellij.cce.core.Language
import java.util.*

interface SettingsListener : EventListener {
  fun languageChanged(language: Language) {}
}
package com.intellij.settingsSync.core.migration

import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

class ConfigInfo(val id: @NonNls String, val configClass: Class<*>, val description: @Nls String) 
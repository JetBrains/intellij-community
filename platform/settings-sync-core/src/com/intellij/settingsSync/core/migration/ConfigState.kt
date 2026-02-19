package com.intellij.settingsSync.core.migration

import com.intellij.ide.IdeBundle
import com.intellij.settingsSync.core.SettingsSyncBundle

/**
 * The synchronization state of the given group of settings.
 */
class ConfigState {
  var info: ConfigInfo? = null
  var type: Type? = null

  enum class Type {
    Enable {
      override fun getText(): String {
        return IdeBundle.message("plugins.configurable.enable")
      }
    },
    Disable {
      override fun getText(): String {
        return IdeBundle.message("plugins.configurable.disable")
      }
    },
    DisableLocally {
      override fun getText(): String {
        return SettingsSyncBundle.message("settings.group.sync.state.disable.locally")
      }
    };

    abstract fun getText(): String
  }
}
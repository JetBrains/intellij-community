// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.mac.foundation.Foundation
import com.intellij.ui.mac.foundation.ID
import com.intellij.ui.mac.foundation.NSWorkspace
import java.io.File
import java.util.function.Function

private const val ICON = "Contents/Resources/custom.icns"

/**
 * @author Alexander Lobas
 */
class MacCustomAppIcon {
  companion object {
    fun available(): Boolean {
      return SystemInfo.isMac && !PluginManagerCore.isRunningFromSources() &&
             File(PathManager.getHomePath(), ICON).exists()
    }

    fun isCustom(): Boolean {
      val pool = Foundation.NSAutoreleasePool()
      try {
        val appPath = PathManager.getHomePath()
        val workspace = NSWorkspace.getInstance()
        val description = Foundation.invoke(Foundation.invoke(workspace, "iconForFile:", appPath), "description")
        return Foundation.toStringViaUTF8(description)?.contains("ISCustomIcon") == true
      }
      finally {
        pool.drain()
      }
    }

    fun setCustom(value: Boolean) {
      val pool = Foundation.NSAutoreleasePool()
      try {

        val appPath = PathManager.getHomePath()
        var image = ID.NIL

        if (value) {
          image = Foundation.invoke(Foundation.invoke("NSImage", "alloc"), "initWithContentsOfFile:", "$appPath/$ICON")
        }

        val workspace = NSWorkspace.getInstance()
        val result = Foundation.invoke(workspace, "setIcon:forFile:options:", image, appPath, 2).booleanValue()

        if (result) {
          if (PluginManagerConfigurable.showRestartDialog(IdeBundle.message("dialog.title.restart.required"), Function {
              IdeBundle.message("dialog.message.must.be.restarted.for.changes.to.take.effect",
                                ApplicationNamesInfo.getInstance().fullProductName)
            }) == Messages.YES) {
            ApplicationManagerEx.getApplicationEx().restart(true)
          }
        }
        else {
          Messages.showErrorDialog(IdeBundle.message("checkbox.ide.mac.app.icon"),
                                   IdeBundle.message("ide.mac.app.icon.error.message", if (value) 0 else 1))
        }
      }
      finally {
        pool.drain()
      }
    }
  }
}
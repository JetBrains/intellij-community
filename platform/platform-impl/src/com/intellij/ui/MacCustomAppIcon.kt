// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.mac.foundation.Foundation
import com.intellij.ui.mac.foundation.ID
import com.intellij.ui.mac.foundation.NSWorkspace
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.function.Function

private const val CUSTOM_ICON = "ide.mac.custom.app.icon"
private const val CUSTOM_ICON_ENABLED_BY_DEFAULT = true

/**
 * @author Alexander Lobas
 */
class MacCustomAppIcon {
  companion object {
    fun available(): Boolean {
      if (!SystemInfo.isMac || PluginManagerCore.isRunningFromSources()) {
        return false
      }
      val appPath = getApplicationPath()
      return appPath != null && File(getImagePath(appPath)).exists()
    }

    fun isCustom(): Boolean {
      val pool = Foundation.NSAutoreleasePool()
      try {
        val appPath = getApplicationPath()
        if (appPath == null) {
          return false
        }
        val workspace = NSWorkspace.getInstance()
        val image = Foundation.invoke(workspace, "iconForFile:", Foundation.nsString(appPath))
        val description = Foundation.invoke(image, "description")
        return Foundation.toStringViaUTF8(description)?.contains("ISCustomIcon") == true
      }
      finally {
        pool.drain()
      }
    }

    fun setCustom(value: Boolean, showDialog: Boolean) {
      val pool = Foundation.NSAutoreleasePool()
      try {

        val appPath = getApplicationPath()
        if (appPath == null) {
          return
        }
        var image = ID.NIL

        if (value) {
          val name = Foundation.nsString(getImagePath(appPath))
          image = Foundation.invoke(Foundation.invoke("NSImage", "alloc"), "initWithContentsOfFile:", name)
        }

        val workspace = NSWorkspace.getInstance()
        val result = Foundation.invoke(workspace, "setIcon:forFile:options:", image, Foundation.nsString(appPath), 2).booleanValue()

        if (result) {
          PropertiesComponent.getInstance().setValue(CUSTOM_ICON, value, CUSTOM_ICON_ENABLED_BY_DEFAULT)
          val process = Runtime.getRuntime().exec("killall Finder && killall Dock")

          if (showDialog && PluginManagerConfigurable.showRestartDialog(IdeBundle.message("dialog.title.restart.required"), Function {
              IdeBundle.message("dialog.message.must.be.restarted.for.changes.to.take.effect",
                                ApplicationNamesInfo.getInstance().fullProductName)
            }) == Messages.YES) {
            process.waitFor(20, TimeUnit.SECONDS)
            ApplicationManagerEx.getApplicationEx().restart(true)
          }
        }
        else if (showDialog) {
          Messages.showErrorDialog(IdeBundle.message("checkbox.ide.mac.app.icon"),
                                   IdeBundle.message("ide.mac.app.icon.error.message", if (value) 0 else 1))
        }
        else {
          Logger.getInstance(MacCustomAppIcon::class.java).error(IdeBundle.message("ide.mac.app.icon.error.message", if (value) 0 else 1))
        }
      }
      finally {
        pool.drain()
      }
    }

    private fun getApplicationPath(): String? {
      val appPath = PathManager.getHomePath()
      val index = appPath.lastIndexOf(".app")
      if (index > 0) {
        return appPath.substring(0, index + 4)
      }
      return null
    }

    private fun getImagePath(appPath: String): String {
      val customIcon = "custom.icns"
      return "$appPath/Contents/Resources/$customIcon"
    }
  }
}

@ApiStatus.Internal
class MacCustomAppIconStartupService : AppLifecycleListener {
  override fun appStarted() {
    if (MacCustomAppIcon.available() && PropertiesComponent.getInstance().getBoolean(CUSTOM_ICON, CUSTOM_ICON_ENABLED_BY_DEFAULT) && !MacCustomAppIcon.isCustom()) {
      MacCustomAppIcon.setCustom(true, false)
    }
  }
}
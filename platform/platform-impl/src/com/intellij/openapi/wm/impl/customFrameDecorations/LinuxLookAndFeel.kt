// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.IconManager
import com.intellij.ui.JBColor
import com.intellij.util.IconUtil
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import javax.swing.Icon
import kotlin.concurrent.thread


class LinuxLookAndFeel {
  companion object {
    val linuxIconPath = "/usr/share/icons"
    val linuxHomePath = System.getenv("HOME")
    fun getLinuxIcon(name: WindowToolbarIcons): Icon? {
      val iconName: String
      var iconPath: String? = null
      if (SystemInfo.isGNOME) {
        iconName = when (name) {
          WindowToolbarIcons.CLOSE -> "window-close-symbolic.svg"
          WindowToolbarIcons.MAXIMIZE -> "window-maximize-symbolic.svg"
          WindowToolbarIcons.RESTORE -> "window-restore-symbolic.svg"
          WindowToolbarIcons.MINIMIZE -> "window-minimize-symbolic.svg"
        }
        iconPath = findIconAbsolutePath(iconName)
      }
      else if (SystemInfo.isKDE) {
        iconName = when (name) {
          WindowToolbarIcons.CLOSE -> "close-normal.svg"
          WindowToolbarIcons.MAXIMIZE -> "maximize-normal.svg"
          WindowToolbarIcons.RESTORE -> "maximized-normal.svg"
          WindowToolbarIcons.MINIMIZE -> "minimize-normal.svg"
        }
        iconPath = "$linuxHomePath/.config/gtk-3.0/assets/$iconName"
      }


      if (iconPath.isNullOrEmpty()) return null
      var icon = IconManager.getInstance().getIcon("file:$iconPath",
                                                   AllIcons::class.java.classLoader)
      icon = IconUtil.colorizeReplace(icon, JBColor(0x6c7080, 0xcfd1d8))
      return icon
    }

    fun findIconAbsolutePath(iconName: String, useOtherTheme: String? = null, recursiveDepth: Int = 0): String? {
      val themeName = useOtherTheme ?: getCurrentIconTheme() ?: return null

      val themePath = "$linuxIconPath/$themeName"

      // Prefer first find in 16x16 subfolder
      var iconPath = execute("find $themePath/16x16 -type f -name $iconName")
      if (isValidIconPath(iconPath))
        return iconPath

      // If no icons found, found anywhere
      iconPath = execute("find $themePath -type f -name $iconName")
      if (isValidIconPath(iconPath))
        return iconPath


      if (recursiveDepth > 10) return null

      // If no icon found, then find icons in theme inheritance
      for (inheritIconTheme in getInheritedIconThemes(themeName)) {
        iconPath = findIconAbsolutePath(iconName, inheritIconTheme, recursiveDepth + 1)
        if (isValidIconPath(iconPath))
          return iconPath
      }

      return null
    }

    private fun isValidIconPath(iconPath: String?): Boolean {
      return !iconPath.isNullOrEmpty() && iconPath.startsWith(linuxIconPath)
    }

    fun getInheritedIconThemes(themeName: String): List<String> {
      try {
        val themePath = "$linuxIconPath/$themeName"
        val themeConfigFile = File("$themePath/index.theme")
        val inheritanceString = "Inherits="
        if (themeConfigFile.exists()) {
          val lines = themeConfigFile.readLines()
          for (line in lines) {
            if (line.startsWith(inheritanceString)) {
              return line.substringAfter(inheritanceString).split(",").map { it.trim() }
            }
          }
        }
        return listOf()
      }
      catch (exception: Exception) {
        return listOf()
      }
    }

    fun getCurrentIconTheme(): String? {
      return getDconfEntry("/org/gnome/desktop/interface/icon-theme")?.drop(1)?.dropLast(1) // Remove double quotes
    }

    fun getHeaderLayout(): List<String> {
      try {
        if (SystemInfo.isGNOME) {
          // Next line returns something like appmenu:minimize,maximize,close
          var elementsString = getDconfEntry("/org/gnome/desktop/wm/preferences/button-layout")
          elementsString = elementsString?.drop(1)?.dropLast(1) // Remove double quotes
          return elementsString?.split(":", ",") ?: emptyList()
        }
        else if (SystemInfo.isKDE) {
          val gtk3ConfigFile = File("$linuxHomePath/.config/gtk-3.0/settings.ini")
          val paramName = "gtk-decoration-layout="
          if (gtk3ConfigFile.exists()) {
            val lines = gtk3ConfigFile.readLines()
            for (line in lines) {
              if (line.startsWith(paramName)) {
                // Next line returns something like icon:minimize,maximize,close
                return line.substringAfter(paramName).split(":", ",").map { it.trim() }
              }
            }
          }
        }
      }
      catch (exception: Exception) {
        exception.printStackTrace()
      }
      return emptyList()
    }

    private fun getDconfEntry(key: String): String? {
      return execute("dconf read $key")
    }

    private fun execute(command: String): String? {
      try {
        val processBuilder = ProcessBuilder(command.split(" "))
        processBuilder.redirectErrorStream(true)

        val process = processBuilder.start()
        val reader = BufferedReader(InputStreamReader(process.inputStream))

        val line: String? = reader.readLine()
        process.waitFor()

        return line
      }
      catch (exception: Exception) {
        exception.printStackTrace()
      }
      return null
    }
  }
}

enum class WindowToolbarIcons {
  MAXIMIZE, MINIMIZE, RESTORE, CLOSE
}
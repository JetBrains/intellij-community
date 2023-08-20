// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations

import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.concurrent.thread


class LinuxLookAndFeel {
  companion object {
    fun findIconAbsolutePath(iconName: String): String? {
      val iconTheme = getIconTheme() ?: return null
      val command = "find /usr/share/icons/$iconTheme -type f -name $iconName"
      return execute(command)
    }

    fun getIconTheme(): String? {
      return getDconfEntry("/org/gnome/desktop/interface/icon-theme")?.drop(1)?.dropLast(1)
    }

    fun getHeaderLayout(): List<String> {
      // Next line returns something like appmenu:minimize,maximize,close
      var elementsString = getDconfEntry("/org/gnome/desktop/wm/preferences/button-layout")
      elementsString = elementsString?.drop(1)?.dropLast(1)
      val elements = elementsString?.split(":", ",")
      return elements ?: emptyList()
    }

    private fun getDconfEntry(key: String): String? {
      return execute("dconf read $key")
    }

    private fun execute(command: String): String? {
      val processBuilder = ProcessBuilder(command.split(" "))
      processBuilder.redirectErrorStream(true)

      val process = processBuilder.start()
      val reader = BufferedReader(InputStreamReader(process.inputStream))

      val line: String? = reader.readLine()
      process.waitFor()

      return line
    }

    private fun listenIconThemeChanges() {
      thread(start = true) {
        val processBuilder = ProcessBuilder(listOf("dbus-monitor", "member='Notify'"))
        processBuilder.redirectErrorStream(true)

        val process = processBuilder.start()
        val reader = BufferedReader(InputStreamReader(process.inputStream))


        Runtime.getRuntime().addShutdownHook(Thread {
          reader.close()
          process.destroy()
        })

        var line: String

        while (true) {
          line = reader.readLine()
          if (line.contains("/org/gnome/desktop/interface/icon-theme")) {
            println("Theme changed!!!")
            subscribers.forEach {it()}
          }
        }
      }
    }

    private val subscribers = mutableListOf<() -> Unit>()
    fun onIconThemeChanges(callback: () -> Unit) {
      subscribers.add(callback)
    }

    init {
      //listenIconThemeChanges()
    }
  }
}
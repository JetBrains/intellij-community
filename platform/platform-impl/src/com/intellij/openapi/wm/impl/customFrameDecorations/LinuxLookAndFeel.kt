// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations

import java.io.BufferedReader
import java.io.InputStreamReader

class LinuxLookAndFeel {
  companion object {
    fun getIconTheme(): String? {
      return getDconfEntry("/org/gnome/desktop/interface/icon-theme")
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

      val line: String? = reader.readLine() // Leer la primera línea
      val exitCode = process.waitFor()

      return line
    }
  }
}
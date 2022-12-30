package org.jetbrains.completion.full.line

import com.intellij.openapi.application.PluginPathManager
import java.io.BufferedReader
import java.io.FileReader

object FilesTest {
  const val FORMAT_BEFORE_FOLDER = "before-formatting"
  const val FORMAT_AFTER_FOLDER = "after-formatting"

  fun readFile(filename: String, lang: String): String {
    return StringBuilder().apply {
      BufferedReader(FileReader(
        PluginPathManager.getPluginHome("full-line").resolve("$lang/testResources/$filename").path
      ))
        .lineSequence()
        .forEach { append(it).append('\n') }
    }.toString()
  }
}

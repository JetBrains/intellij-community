package org.jetbrains.completion.full.line

import com.intellij.openapi.application.PluginPathManager

object FilesTest {
  const val FORMAT_BEFORE_FOLDER = "before-formatting"
  const val FORMAT_AFTER_FOLDER = "after-formatting"

  fun readFile(filename: String, lang: String): String {
    return PluginPathManager.getPluginHome("full-line").resolve("$lang/testResources/$filename").readText()
  }
}

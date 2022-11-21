package org.jetbrains.completion.full.line

import com.intellij.openapi.application.PluginPathManager
import java.nio.file.Paths

object FilesTest {
  const val FORMAT_BEFORE_FOLDER = "before-formatting"
  const val FORMAT_AFTER_FOLDER = "after-formatting"

  fun readFile(filename: String, lang: String): String {
    var testFile = PluginPathManager.getPluginHome("full-line").resolve("$lang/testResources/$filename")
    if (!testFile.exists()) {
      testFile = Paths.get(testFile.path.replace("community", "")).toRealPath().toFile()
    }
    return testFile.readText()
  }
}

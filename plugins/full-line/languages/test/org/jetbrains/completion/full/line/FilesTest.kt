package org.jetbrains.completion.full.line

import java.io.BufferedReader
import java.io.FileReader

object FilesTest {
  const val FORMAT_BEFORE_FOLDER = "before-formatting"
  const val FORMAT_AFTER_FOLDER = "after-formatting"

  fun fullPath(filename: String): String {
    return FilesTest::class.java.classLoader.getResource(filename)
      .let {
        assert(it != null) {
          "Missing file $filename. " +
          "\n\tRun `format.sh` from testResources folder to generate missing formatted files"
        }
        it!!.path
      }
  }

  fun readFile(filename: String): String {
    return StringBuilder().apply {
      BufferedReader(FileReader(fullPath(filename)))
        .lineSequence()
        .forEach { append(it).append('\n') }
    }.toString()
  }
}

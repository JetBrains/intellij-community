// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.builtInHelp.search

import org.apache.commons.compress.utils.IOUtils
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.store.FSDirectory
import org.jetbrains.annotations.NotNull
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.jetbrains.annotations.NonNls


abstract class HelpIndexProcessor {

  companion object {
    val resources = arrayOf("_0.cfe", "_0.cfs", "_0.si", "segments_1")
    @NonNls
    val STORAGE_PREFIX = "/search/"
    @NonNls
    val EMPTY_RESULT = "[]"
  }

  val analyzer: StandardAnalyzer = StandardAnalyzer()

  fun deployIndex(): FSDirectory? {
    val indexDir: Path? = Files.createTempDirectory("search-index")
    var result: FSDirectory? = null
    if (indexDir != null) {
      for (resourceName in resources) {
        val input = HelpSearch::class.java.getResourceAsStream(
          STORAGE_PREFIX + resourceName)
        val fos: FileOutputStream = FileOutputStream(Paths.get(indexDir.toAbsolutePath().toString(), resourceName).toFile())
        IOUtils.copy(input, fos)
        fos.flush()
        fos.close()
        input.close()
      }
      result = FSDirectory.open(indexDir)
    }
    return result
  }

  fun releaseIndex(indexDirectory: FSDirectory) {
    val path = indexDirectory.directory
    for (f in path.toFile().listFiles()) f.delete()
    Files.delete(path)
  }

  @NotNull
  abstract fun process(query: String, maxResults: Int): String
}
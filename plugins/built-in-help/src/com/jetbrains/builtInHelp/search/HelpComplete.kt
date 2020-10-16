// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.builtInHelp.search

import com.google.gson.Gson
import com.intellij.openapi.diagnostic.Logger
import org.apache.commons.compress.utils.IOUtils
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.search.spell.LuceneDictionary
import org.apache.lucene.search.suggest.analyzing.BlendedInfixSuggester
import org.apache.lucene.store.FSDirectory
import org.jetbrains.annotations.NotNull
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.jetbrains.annotations.NonNls


class HelpComplete {

  companion object {
    val resources = arrayOf("_0.cfe", "_0.cfs", "_0.si", "segments_1")
    @NonNls
    val PREFIX = "/search/"
    @NonNls
    val NOT_FOUND = "[]"

    private val analyzer: StandardAnalyzer = StandardAnalyzer()

    @NotNull
    fun complete(query: String, maxHits: Int): String {

      val indexDir: Path? = Files.createTempDirectory("search-index")
      var indexDirectory: FSDirectory? = null
      var reader: DirectoryReader? = null
      var suggester: BlendedInfixSuggester? = null
      if (indexDir != null)
        try {
          for (resourceName in resources) {
            val input = HelpSearch::class.java.getResourceAsStream(
              PREFIX + resourceName)
            val fos: FileOutputStream = FileOutputStream(Paths.get(indexDir.toAbsolutePath().toString(), resourceName).toFile())
            IOUtils.copy(input, fos)
            fos.flush()
            fos.close()
            input.close()
          }
          indexDirectory = FSDirectory.open(indexDir)
          reader = DirectoryReader.open(indexDirectory)

          suggester = BlendedInfixSuggester(indexDirectory, analyzer)

          suggester.build(LuceneDictionary(reader, "contents"))

          val completionResults = suggester.lookup(query, maxHits, false, true)

          return Gson().toJson(completionResults)
        }
        catch (e: Exception) {
          Logger.getInstance(HelpComplete::class.java).error("Error searching help for $query", e)
        }
        finally {
          suggester?.close()
          indexDirectory?.close()
          reader?.close()
          for (f in indexDir.toFile().listFiles()) f.delete()
          Files.delete(indexDir)
        }
      return NOT_FOUND
    }
  }
}
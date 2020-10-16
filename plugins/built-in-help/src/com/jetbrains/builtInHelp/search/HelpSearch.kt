// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.builtInHelp.search

import com.google.gson.Gson
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.Logger
import org.apache.commons.compress.utils.IOUtils
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.Query
import org.apache.lucene.search.TopScoreDocCollector
import org.apache.lucene.search.highlight.Highlighter
import org.apache.lucene.search.highlight.QueryScorer
import org.apache.lucene.search.highlight.Scorer
import org.apache.lucene.store.FSDirectory
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.NonNls
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


class HelpSearch {

  companion object {
    val resources = arrayOf("_0.cfe", "_0.cfs", "_0.si", "segments_1")
    @NonNls
    val PREFIX = "/search/"
    val NOT_FOUND = "[]"

    private val analyzer: StandardAnalyzer = StandardAnalyzer()

    @NotNull
    fun search(query: String, maxHits: Int): String {

      val indexDir: Path? = Files.createTempDirectory("search-index")
      var indexDirectory: FSDirectory? = null
      var reader: DirectoryReader? = null
      if (indexDir != null)
        try {

          for (resourceName in resources) {
            val input = HelpSearch::class.java.getResourceAsStream(
              PREFIX + resourceName)
            val fos = FileOutputStream(Paths.get(indexDir.toAbsolutePath().toString(), resourceName).toFile())
            IOUtils.copy(input, fos)
            fos.flush()
            fos.close()
            input.close()
          }

          indexDirectory = FSDirectory.open(indexDir)
          reader = DirectoryReader.open(indexDirectory)
          ApplicationInfo.getInstance()

          val searcher = IndexSearcher(reader)
          val collector: TopScoreDocCollector = TopScoreDocCollector.create(maxHits)

          val q: Query = QueryParser("contents", analyzer).parse(query)
          searcher.search(q, collector)
          val hits = collector.topDocs().scoreDocs


          val scorer: Scorer = QueryScorer(q)
          val highlighter = Highlighter(scorer)

          val results = ArrayList<HelpSearchResult>()
          for (i in hits.indices) {
            val doc = searcher.doc(hits[i].doc)
            results.add(
              HelpSearchResult(i, doc.get("filename"), highlighter.getBestFragment(
                analyzer, "contents", doc.get("contents")) + "...",
                               doc.get("title"), listOf("webhelp")))
          }

          val searchResults = HelpSearchResults(results)
          return if (searchResults.results.isEmpty()) NOT_FOUND else Gson().toJson(searchResults)
        }
        catch (e: Exception) {
          Logger.getInstance(HelpSearch::class.java).error("Error searching help for $query", e)
        }
        finally {
          indexDirectory?.close()
          reader?.close()
          val tempFiles = indexDir.toFile().listFiles()
          tempFiles?.forEach { it.delete() }
          Files.delete(indexDir)
        }
      return NOT_FOUND
    }
  }
}
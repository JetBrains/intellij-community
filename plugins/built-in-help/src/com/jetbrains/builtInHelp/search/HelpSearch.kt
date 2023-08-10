// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.builtInHelp.search

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.ResourceUtil
import org.apache.commons.compress.utils.IOUtils
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.queryparser.simple.SimpleQueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopScoreDocCollector
import org.apache.lucene.search.highlight.Highlighter
import org.apache.lucene.search.highlight.QueryScorer
import org.apache.lucene.search.highlight.Scorer
import org.apache.lucene.store.FSDirectory
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.NotNull
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*


class HelpSearch {

  companion object {
    private val resources = arrayOf("_0.cfe", "_0.cfs", "_0.si", "segments_1")

    @NonNls
    private const val NOT_FOUND = "[]"

    private val analyzer: StandardAnalyzer = StandardAnalyzer()

    @NotNull
    fun search(query: String?, maxHits: Int): String {

      if (query != null) {
        val indexDir: Path? = Files.createTempDirectory("search-index")
        var indexDirectory: FSDirectory? = null
        var reader: DirectoryReader? = null
        if (indexDir != null)
          try {

            for (resourceName in resources) {
              val input = ResourceUtil.getResourceAsStream(HelpSearch::class.java.classLoader,
                                                           "search",
                                                           resourceName)
              val fos =
                FileOutputStream(Paths.get(indexDir.toAbsolutePath().toString(), resourceName).toFile())
              IOUtils.copy(input, fos)
              fos.flush()
              fos.close()
              input?.close()
            }

            indexDirectory = FSDirectory.open(indexDir)
            reader = DirectoryReader.open(indexDirectory)

            val searcher = IndexSearcher(reader)
            val collector: TopScoreDocCollector = TopScoreDocCollector.create(maxHits, maxHits)
            val q = SimpleQueryParser(analyzer, mapOf(Pair("contents", 1.0F), Pair("title", 1.5F))).parse(query)
            searcher.search(q, collector)
            val hits = collector.topDocs().scoreDocs

            val scorer: Scorer = QueryScorer(q)
            val highlighter = Highlighter(scorer)

            val results = ArrayList<HelpSearchResult>()
            for (i in hits.indices) {
              val doc = searcher.doc(hits[i].doc)
              val contentValue = buildString {
                append(highlighter.getBestFragment(
                  analyzer, "contents", doc.get("contents")
                ))
                append("...")
              }
              results.add(
                HelpSearchResult(
                  doc.get("filename"),
                  doc.get("title"),
                  "",
                  doc.get("title"),
                  i.toString(),
                  HelpSearchResult.SnippetResult(
                    HelpSearchResult.SnippetResult.Content(
                      contentValue, "full"
                    )
                  ),
                  HelpSearchResult.HighlightedResult(
                    HelpSearchResult.HelpSearchResultDetails(doc.get("filename")),
                    HelpSearchResult.HelpSearchResultDetails(doc.get("title")),
                    HelpSearchResult.HelpSearchResultDetails(contentValue),
                    HelpSearchResult.HelpSearchResultDetails(doc.get("title")),
                    HelpSearchResult.HelpSearchResultDetails(doc.get("title"))
                  )
                )
              )
            }
            val searchResults = HelpSearchResults(results)
            if (searchResults.hits.isNotEmpty()) return jacksonObjectMapper().writeValueAsString(searchResults)
          }
          catch (e: Throwable) {
            Logger.getInstance(HelpSearch::class.java).info("Error searching help for $query", e)
          }
          finally {
            indexDirectory?.close()
            reader?.close()
            val tempFiles = indexDir.toFile().listFiles()
            tempFiles?.forEach { it.delete() }
            Files.delete(indexDir)
          }
      }
      return NOT_FOUND
    }
  }
}

data class HelpSearchResult(
  val url: String,
  val pageTitle: String,
  val breadcrumbs: String,
  val mainTitle: String,
  val objectID: String,
  val _snippetResult: SnippetResult,
  val _highlightResult: HighlightedResult
) {
  data class SnippetResult(val content: Content) {
    data class Content(
      val value: String,
      val matchLevel: String
    )

  }

  data class HelpSearchResultDetails(
    val value: String,
    val matchLevel: String = "full",
    val fullyHighlighted: Boolean = true,
    val matchedWords: List<String> = Collections.emptyList()
  )

  data class HighlightedResult(
    val url: HelpSearchResultDetails,
    val pageTitle: HelpSearchResultDetails,
    val metaDescription: HelpSearchResultDetails,
    val mainTitle: HelpSearchResultDetails,
    val headings: HelpSearchResultDetails
  )
}

data class HelpSearchResults(var hits: List<HelpSearchResult>)
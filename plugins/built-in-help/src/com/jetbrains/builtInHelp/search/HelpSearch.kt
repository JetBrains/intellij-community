// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.builtInHelp.search

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.ResourceUtil
import com.intellij.util.io.safeOutputStream
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.queryparser.simple.SimpleQueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopScoreDocCollectorManager
import org.apache.lucene.search.highlight.Highlighter
import org.apache.lucene.search.highlight.QueryScorer
import org.apache.lucene.search.highlight.Scorer
import org.apache.lucene.store.NIOFSDirectory
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.NotNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

class HelpSearch {

  companion object {

    @NonNls
    private const val NOT_FOUND = "[]"
    private val analyzer: StandardAnalyzer = StandardAnalyzer()

    @NotNull
    fun search(query: String?, maxHits: Int): String {

      if (query != null) {
        val indexDir: Path? = Files.createTempDirectory("search-index")
        var indexDirectory: NIOFSDirectory? = null
        var reader: DirectoryReader? = null

        if (indexDir != null)
          try {
            val indexDirPath = indexDir.toAbsolutePath().toString()
            Files.createDirectories(indexDir)

            //Read required names from rlist and then load resources based off of them
            ResourceUtil.getResourceAsStream(HelpSearch::class.java.classLoader, "search", "rlist")
              .use { resourceList ->
                BufferedReader(InputStreamReader(resourceList)).useLines { lines ->
                  lines.forEach { line ->
                    val path = Paths.get(indexDirPath, line)
                    ResourceUtil.getResourceAsStream(HelpSearch::class.java.classLoader,
                                                     "search", line)
                      ?.use { resourceStream ->
                        path.safeOutputStream().use { resourceOutput ->
                          resourceOutput.write(resourceStream.readAllBytes())
                        }
                      }
                  }
                }
              }

            indexDirectory = NIOFSDirectory(indexDir)
            reader = DirectoryReader.open(indexDirectory)

            val searcher = IndexSearcher(reader)
            val q = SimpleQueryParser(analyzer, mapOf(Pair("contents", 1.0F),
                                                      Pair("title", 1.5F))).parse(query)
            val hits = searcher.search(q,
                                       TopScoreDocCollectorManager(maxHits, maxHits))

            val scorer: Scorer = QueryScorer(q)
            val highlighter = Highlighter(scorer)

            val results = hits.scoreDocs.indices.map { index ->
              val doc = searcher.storedFields().document(hits.scoreDocs[index].doc)
              val contentValue = buildString {
                append(highlighter.getBestFragment(
                  analyzer, "contents", doc.get("contents")
                ))
                append("...")
              }

              HelpSearchResult(
                doc.get("filename"),
                doc.get("title"),
                "",
                doc.get("title"),
                index.toString(),
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
            }
            if (results.isNotEmpty())
              return jacksonObjectMapper().writeValueAsString(HelpSearchResults(results))
          }
          catch (e: Throwable) {
            Logger.getInstance(HelpSearch::class.java).info("Error searching help for $query", e)
          }
          finally {
            indexDirectory?.close()
            reader?.close()
            indexDir.toFile().deleteRecursively()
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
  val _highlightResult: HighlightedResult,
) {
  data class SnippetResult(val content: Content) {
    data class Content(
      val value: String,
      val matchLevel: String,
    )

  }

  data class HelpSearchResultDetails(
    val value: String,
    val matchLevel: String = "full",
    val fullyHighlighted: Boolean = true,
    val matchedWords: List<String> = Collections.emptyList(),
  )

  data class HighlightedResult(
    val url: HelpSearchResultDetails,
    val pageTitle: HelpSearchResultDetails,
    val metaDescription: HelpSearchResultDetails,
    val mainTitle: HelpSearchResultDetails,
    val headings: HelpSearchResultDetails,
  )
}

data class HelpSearchResults(var hits: List<HelpSearchResult>)
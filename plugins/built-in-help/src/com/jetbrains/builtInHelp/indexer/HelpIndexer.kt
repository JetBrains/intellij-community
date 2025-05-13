// Copyright 2000-2025 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.builtInHelp.indexer

import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.FSDirectory
import org.jsoup.Jsoup
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.streams.asSequence

class HelpIndexer
@Throws(IOException::class)
internal constructor(indexDir: String) {

  private val writer: IndexWriter

  init {
    val dir = FSDirectory.open(Paths.get(indexDir))
    val config = IndexWriterConfig(analyzer)
    writer = IndexWriter(dir, config)
  }

  @Throws(IOException::class)
  fun indexDirectory(dirName: String) {
    //It's always a directory, there is no chance that the method is called on a file
    Files.walk(Path.of(dirName)).use { stream ->
      stream
        .filter {
          it.isRegularFile() &&
          it.extension.lowercase(Locale.getDefault()) in setOf("htm", "html")
        }
        .asSequence()
        .forEach { file ->
          try {
            val docIndex = Document()
            val parsedDocument = Jsoup.parse(file, "UTF-8")

            val content = StringBuilder()
            val lineSeparator = System.lineSeparator()
            val articles = parsedDocument.body().getElementsByClass("article")
            val title = parsedDocument.title()

            if (articles.isEmpty()) {
              if (parsedDocument.select("meta[http-equiv=refresh]").isNotEmpty() ||
                  title.contains("You will be redirected shortly")) {
                println("Skipping redirect page: $file ")
              }
              else if (parsedDocument.body().attr("data-template") == "section-page") {
                println("Skipping section page: $file")
              }
              else {
                System.err.println("Could not add: $file because no `<article>` elements are found. Title is '$title'")
              }
            }
            else {
              articles.first()?.children()
                ?.forEach { content.append(it.text()).append(lineSeparator) }

              docIndex.add(TextField("contents", content.toString(), Field.Store.YES))
              docIndex.add(StringField("filename", file.name, Field.Store.YES))
              docIndex.add(StringField("title", title, Field.Store.YES))

              writer.addDocument(docIndex)
              println("Added: $file")
            }
          }
          catch (e: Throwable) {
            System.err.println("Could not add: $file because ${e.message}")
          }
        }
    }
  }

  @Throws(IOException::class)
  fun closeIndex() {
    writer.close()
  }

  companion object {
    private val analyzer = StandardAnalyzer()

    private fun doIndex(dirToStore: String, dirToIndex: String) {
      val indexer = HelpIndexer(dirToStore)
      indexer.indexDirectory(dirToIndex)
      indexer.closeIndex()
    }

    @Throws(IOException::class)
    @JvmStatic
    fun main(args: Array<String>) {
      doIndex(args[0], args[1])
    }
  }
}

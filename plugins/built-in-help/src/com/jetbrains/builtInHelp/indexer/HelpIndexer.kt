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
import kotlin.io.path.*
import kotlin.streams.asSequence

class HelpIndexer
@Throws(IOException::class)
internal constructor(indexDir: String) {

  private val writer: IndexWriter

  init {
    val targetPath = Paths.get(indexDir)
    //Make sure it's empty
    targetPath.toFile().deleteRecursively()
    targetPath.createDirectories()

    val dir = FSDirectory.open(targetPath)
    val config = IndexWriterConfig(analyzer)
    writer = IndexWriter(dir, config)
  }

  @Throws(IOException::class)
  fun indexDirectory(dirName: String) {
    //It's always a directory, there is no chance that the method is called on a file
    val lineSeparator = System.lineSeparator()
    val acceptedFiles = setOf("htm", "html")

    Files.walk(Path.of(dirName)).use { stream ->
      stream
        .filter {
          it.isRegularFile() &&
          it.extension.lowercase(Locale.getDefault()) in acceptedFiles
        }
        .asSequence()
        .forEach { file ->
          try {
            val docIndex = Document()
            val parsedDocument = Jsoup.parse(file, "UTF-8")

            if (parsedDocument.select("meta[http-equiv=refresh]").isNotEmpty()) {
              println("Skipping redirect page: $file")
              return@forEach
            }

            if (parsedDocument.body().attr("data-template") == "section-page") {
              println("Skipping section page: $file")
              return@forEach
            }

            val article = parsedDocument.body().getElementsByClass("article").first()

            if (article == null) {
              println("Skipping: $file because no `<article>` elements are found.")
              return@forEach
            }

            docIndex.add(TextField("contents",
                                   article.children().joinToString(lineSeparator) { it.text() },
                                   Field.Store.YES))
            docIndex.add(StringField("filename",
                                     file.name,
                                     Field.Store.YES))
            docIndex.add(StringField("title",
                                     parsedDocument.title(),
                                     Field.Store.YES))

            writer.addDocument(docIndex)
            println("Added: $file")
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
      //Store the list of generated files for the search to work
      Path.of(dirToStore, "rlist")
        .writeLines(Path.of(dirToStore).walk().map { it.name })
    }

    @Throws(IOException::class)
    @JvmStatic
    fun main(args: Array<String>) {
      doIndex(args[0], args[1])
    }
  }
}

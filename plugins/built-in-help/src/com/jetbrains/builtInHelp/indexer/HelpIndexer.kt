// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.isDirectory

class HelpIndexer
@Throws(IOException::class)
internal constructor(indexDir: String) {

  private val writer: IndexWriter
  private val allowedExtensions = setOf("htm", "html")

  init {
    val dir = FSDirectory.open(Paths.get(indexDir))
    val config = IndexWriterConfig(analyzer)
    writer = IndexWriter(dir, config)
  }

  @Throws(IOException::class)
  fun indexFilesFromDirectory(dirName: String) {

    Files.walk(Paths.get(dirName))
      .filter { !it.isDirectory() && allowedExtensions.contains(it.extension) }
      .map { it.toFile() }
      .forEach { f ->
        try {
          val doc = Document()
          val parsedDocument = Jsoup.parse(f, "UTF-8")

          val content = StringBuilder()
          val lineSeparator = System.lineSeparator()
          parsedDocument.body().getElementsByClass("article")[0].children()
            .filterNot { it.hasAttr("data-swiftype-index") }
            .forEach { content.append(it.text()).append(lineSeparator) }

          doc.add(TextField("contents", content.toString(), Field.Store.YES))
          doc.add(StringField("filename", f.name, Field.Store.YES))
          doc.add(StringField("title", parsedDocument.title(), Field.Store.YES))

          writer.addDocument(doc)
          println("Added: $f")
        }
        catch (e: Throwable) {
          println("Could not add: $f because ${e.message}")
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
      indexer.indexFilesFromDirectory(dirToIndex)
      indexer.closeIndex()
    }

    @Throws(IOException::class)
    @JvmStatic
    fun main(args: Array<String>) {
      if (args.size < 2) {
        println("Usage: HelpIndexer <dirToStore> <dirToIndex>")
        return
      }
      doIndex(args[0], args[1])
    }
  }
}

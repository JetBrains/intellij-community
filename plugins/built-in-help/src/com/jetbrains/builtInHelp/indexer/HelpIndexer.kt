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
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

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
  fun indexFileOrDirectory(fileName: String) {
    val file = Path.of(fileName)
    val queue = if (Files.isRegularFile(file)) {
      if (file.extension.lowercase(Locale.getDefault()) in setOf("htm", "html")) listOf(file)
      else return
    }
    else if (Files.isDirectory(file)) {
      Files.walk(file).use { stream ->
        stream
          .filter { it.isRegularFile() }
          .filter { it.extension.lowercase(Locale.getDefault()) in setOf("htm", "html") }
          .toList()
      }
    }
    else return

    for (f in queue) {
      try {
        val doc = Document()
        val parsedDocument = Jsoup.parse(f, "UTF-8")

        val content = StringBuilder()
        val lineSeparator = System.lineSeparator()
        val articles = parsedDocument.body().getElementsByClass("article")
        val title = parsedDocument.title()
        if (articles.isEmpty()) {
          if (title.contains("You will be redirected shortly")) {
            println("Skipping redirect page: $f ")
          }
          else if (parsedDocument.body().attr("data-template") == "section-page") {
            println("Skipping section page: $f")
          }
          else {
            System.err.println("Could not add: $f because no `<article>` found. Title is '$title'")
          }
          continue
        }
        @Suppress("SpellCheckingInspection")
        articles[0].children()
          .filterNot { it.hasAttr("data-swiftype-index") }
          .forEach { content.append(it.text()).append(lineSeparator) }

        doc.add(TextField("contents", content.toString(), Field.Store.YES))
        doc.add(StringField("filename", f.name, Field.Store.YES))
        doc.add(StringField("title", title, Field.Store.YES))

        writer.addDocument(doc)
        println("Added: $f")
      }
      catch (e: Throwable) {
        System.err.println("Could not add: $f because ${e.message}")
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
      indexer.indexFileOrDirectory(dirToIndex)
      indexer.closeIndex()
    }

    @Throws(IOException::class)
    @JvmStatic
    fun main(args: Array<String>) {
      @Suppress("SpellCheckingInspection")
      val tokens = listOf(
        "<noscript><iframe src=\"//www.googletagmanager.com/ns.html?id=GTM-5P98\" height=\"0\" width=\"0\" style=\"display:none;visibility:hidden\"></iframe></noscript>",
        "</script><script src=\"/help/app/v2/analytics.js\"></script>",
        "<script>(function(w,d,s,l,i){w[l]=w[l]||[];w[l].push({'gtm.start':",
        "new Date().getTime(),event:'gtm.js'});var f=d.getElementsByTagName(s)[0],",
        "j=d.createElement(s),dl=l!='dataLayer'?'&l='+l:'';j.async=true;j.src=",
        "'//www.googletagmanager.com/gtm.js?id='+i+dl;f.parentNode.insertBefore(j,f);",
        "})(window,document,'script','dataLayer','GTM-5P98');")

      val files = Paths.get(args[1]).listDirectoryEntries("*.html")
      files.forEach {
        val original = Files.readString(it, Charsets.UTF_8)
        var contents = original
        for (token in tokens) {
          contents = contents.replace(token, "")
        }
        contents = contents.replace("//resources.jetbrains.com/storage/help-app/", "/help/")
        if (contents != original) {
          println("Removed analytics code from ${it.name}")
          Files.writeString(it, contents, Charsets.UTF_8)
        }
        else {
          println("No analytics code to remove from ${it.name}")
        }
      }

      doIndex(args[0], args[1])
    }
  }
}

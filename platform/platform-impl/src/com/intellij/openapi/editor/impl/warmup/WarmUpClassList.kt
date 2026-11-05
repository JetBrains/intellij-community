// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.warmup

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import java.io.IOException

private val LOG = logger<WarmUpClassList>()

/**
 * The classes of a warm-up, in two sections.
 * The warm-up initializes the [initialize] classes, and only loads and links the [loadOnly] classes.
 */
internal class WarmUpClassList(val initialize: List<String>, val loadOnly: List<String>) {
  companion object {
    /** The marker line of a list file. The classes after it are load-only. */
    const val LOAD_ONLY_MARKER: String = "# Load only"

    val EMPTY: WarmUpClassList = WarmUpClassList(initialize = emptyList(), loadOnly = emptyList())

    /**
     * Parses the lines of a list file. The lines after [LOAD_ONLY_MARKER] name the load-only classes.
     * Blank lines and the other lines that start with `#` are comments.
     */
    fun parse(lines: Sequence<String>): WarmUpClassList {
      val initialize = ArrayList<String>()
      val loadOnly = ArrayList<String>()
      var section = initialize
      for (line in lines.map(String::trim)) {
        when {
          line == LOAD_ONLY_MARKER -> section = loadOnly
          line.isEmpty() || line.startsWith('#') -> continue
          else -> section.add(line)
        }
      }
      return WarmUpClassList(initialize = initialize, loadOnly = loadOnly)
    }

    /** Reads the list file at the [path] of the [classLoader]. An absent or unreadable file gives [EMPTY]. */
    @RequiresBackgroundThread
    fun read(classLoader: ClassLoader, path: String): WarmUpClassList {
      val stream = classLoader.getResourceAsStream(path)
      if (stream == null) {
        LOG.warn("Cannot find the warm-up class list: $path")
        return EMPTY
      }
      return try {
        stream.bufferedReader().use { parse(it.lineSequence()) }
      }
      catch (e: IOException) {
        LOG.warn("Cannot read the warm-up class list: $path", e)
        EMPTY
      }
    }
  }
}

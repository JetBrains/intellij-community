// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.warmup

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.util.coroutines.forEachConcurrent
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException

private val LOG = logger<WarmUpClassList>()

/**
 * The classes of a warm-up, split in various sections:
 * - [loadOnly] - no initialization will be invoked
 * - [initialize]
 * - [plugin] - come from the class loaders of the plugins, and a warm-up only loads.
 */
internal class WarmUpClassList(val initialize: List<String>, val loadOnly: List<String>, val plugin: List<String>) {
  companion object {
    /** The marker line of the classes to initialize. */
    const val INITIALIZE_MARKER: String = "# Initialize"

    /** The marker line of the load-only classes. The lines before the first marker also name load-only classes. */
    const val LOAD_ONLY_MARKER: String = "# Load only"

    /** The marker line of the plugin classes. */
    const val PLUGIN_MARKER: String = "# Plugin classes"

    val EMPTY: WarmUpClassList = WarmUpClassList(initialize = emptyList(), loadOnly = emptyList(), plugin = emptyList())

    /**
     * A marker line starts a section, and the lines before the first marker are load-only.
     * Blank lines and the other lines that start with `#` are ignored.
     */
    fun parse(lines: Sequence<String>): WarmUpClassList {
      val initialize = ArrayList<String>()
      val loadOnly = ArrayList<String>()
      val plugin = ArrayList<String>()
      var section = loadOnly
      for (line in lines.map(String::trim)) {
        when {
          line == INITIALIZE_MARKER -> section = initialize
          line == LOAD_ONLY_MARKER -> section = loadOnly
          line == PLUGIN_MARKER -> section = plugin
          line.isEmpty() || line.startsWith('#') -> continue
          else -> section.add(line)
        }
      }
      return WarmUpClassList(initialize = initialize, loadOnly = loadOnly, plugin = plugin)
    }

    /** Reads the list file at the [path] of the [classLoader]. Fallback -- [EMPTY]. */
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

/** The classes of one sequential batch. */
private const val BATCH_SIZE = 128

/**
 * Loads and links the classes with the [names] through the class loaders that [classLoaderForClass] gives.
 * With [initialize], it also initializes them.
 * The classes load in batches of [BATCH_SIZE], and at most [concurrency] batches load at the same time.
 * A cancellation stops the warm-up at the next class.
 */
@RequiresBackgroundThread
internal suspend inline fun warmUpClasses(
  names: Collection<String>,
  initialize: Boolean,
  concurrency: Int = 1,
  crossinline classLoaderForClass: (String) -> ClassLoader,
) {
  names.chunked(BATCH_SIZE).forEachConcurrent(concurrency) { batch ->
    for (name in batch) {
      load(name, initialize = initialize, classLoader = classLoaderForClass(name))
    }
  }
}

private suspend fun load(name: String, initialize: Boolean, classLoader: ClassLoader) {
  currentCoroutineContext().ensureActive()
  try {
    Class.forName(name, initialize, classLoader).declaredConstructors
  }
  catch (e: ClassNotFoundException) {
    LOG.debug { "Cannot find warm-up class: $name: $e" }
  }
  catch (e: NoClassDefFoundError) {
    LOG.debug { "Cannot link warm-up class: $name: $e" }
  }
  catch (e: Throwable) {
    rethrowControlFlowException(e)
    LOG.warn("Cannot prepare warm-up class: $name", e)
  }
}

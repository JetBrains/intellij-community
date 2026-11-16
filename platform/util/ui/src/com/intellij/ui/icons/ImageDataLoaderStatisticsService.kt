// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalAtomicApi::class, DelicateCoroutinesApi::class)

package com.intellij.ui.icons

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.ui.EDT
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Duration.Companion.seconds


/**
 * A "service" to collect stats on how many icons are loaded.
 *
 * Not a true service because it's a util module that doesn't have the necessary dependencies.
 * But a true service isn't needed here, it's just a debug thing.
 *
 * Only the loads of [CachedImageIcon.loadImage] count. It marks each load with [loadImageStarted] and [loadImageFinished].
 * The loader can add details to the current load with [refineCurrentLoad].
 * Each event counts by the current load on its thread.
 * An event outside a load counts as "outside an icon load", for example the read of the splash image.
 *
 * Each load also counts by its caller: the first stack frame outside the icon code, Swing, AWT and the JDK.
 * Only this service walks the stack, and production code does not create the service.
 *
 * With `-Dimage.data.loader.stats.trace=N`, the service also counts each load by its stack trace.
 * The dump then prints the unique stack traces of each caller with at least N loads on the EDT.
 * Without a number, the service records no stack traces.
 */
internal class ImageDataLoaderStatisticsService {
  companion object {
    fun getInstance(): ImageDataLoaderStatisticsService? = INSTANCE
  }

  private val currentLoad = ThreadLocal<String>()
  private val stats = ConcurrentHashMap<String, Counter>()
  private val events = ConcurrentHashMap<String, ConcurrentHashMap<String, Counter>>()
  private val foundReadsByPath = ConcurrentHashMap<String, Counter>()
  private val missingReadsByPath = ConcurrentHashMap<String, Counter>()
  private val decodedIcons = ConcurrentHashMap<String, Counter>()
  private val loadsByCaller = ConcurrentHashMap<String, Counter>()
  private val tracesByCaller = ConcurrentHashMap<String, ConcurrentHashMap<String, Counter>>()

  // null if the tracing is off
  private val traceLevel: Int? = System.getProperty(TRACE_PROPERTY)?.trim()?.toIntOrNull()

  init {
    GlobalScope.launch { // OK to use the global scope, it's an opt-in, debug-only, one-shot thing
      delay(15.seconds)
      dumpStats()
    }
  }

  /**
   * Starts the current load on this thread. Its name is the class of [loader].
   * Also counts the load by its caller and, if the tracing is on, by its stack trace.
   */
  fun loadImageStarted(loader: ImageDataLoader) {
    currentLoad.set(loader.javaClass.simpleName)
    val site = findLoadSite(withTrace = traceLevel != null)
    loadsByCaller.getOrPut(site.caller) { Counter() }.increment()
    if (site.trace != null) {
      tracesByCaller.getOrPut(site.caller) { ConcurrentHashMap() }.getOrPut(site.trace) { Counter() }.increment()
    }
  }

  /**
   * Counts the current load by its name and ends it.
   */
  fun loadImageFinished() {
    val name = currentLoad.get() ?: return
    stats.getOrPut(name) { Counter() }.increment()
    currentLoad.remove()
  }

  /**
   * Adds the state of [cacheKey] to the name of the current load.
   * Does nothing outside a load, because then the call does not come from [CachedImageIcon.loadImage].
   */
  fun refineCurrentLoad(cacheKey: Int) {
    val name = currentLoad.get() ?: return
    currentLoad.set(name + if (cacheKey == 0) " without a cache key" else " with a cache key")
  }

  /**
   * Counts a read of image bytes through a class loader, a class, or a file path.
   * Also counts the read by its load and its path.
   */
  fun resourceRead(path: String, found: Boolean) {
    count(if (found) FOUND_RESOURCE_READS else MISSING_RESOURCE_READS)
    val readsByPath = if (found) foundReadsByPath else missingReadsByPath
    readsByPath.getOrPut("${currentLoadName()}: $path") { Counter() }.increment()
  }

  /**
   * Counts an icon that a stored descriptor creates, by the New UI data in the descriptor.
   */
  fun iconDecoded(expUIPath: String?, expUICacheKey: Int) {
    val kind = when {
      expUIPath == null -> "a rasterized icon without a New UI path"
      expUICacheKey == 0 -> "a rasterized icon with a New UI path without a cache key"
      else -> "a rasterized icon with a New UI path and a cache key"
    }
    decodedIcons.getOrPut(kind) { Counter() }.increment()
  }

  /**
   * Counts a read of image bytes through a URL connection.
   */
  fun urlRead() {
    count(URL_READS)
  }

  /**
   * Counts a parse of an SVG document that is then rendered.
   */
  fun svgParsedForRendering() {
    count(SVG_PARSES_FOR_RENDERING)
  }

  /**
   * Counts a parse of an SVG document that only gives the shape of an image from the disk cache.
   */
  fun svgParsedForShape() {
    count(SVG_PARSES_FOR_SHAPES)
  }

  fun svgRendered() {
    count(SVG_RENDERS)
  }

  private fun currentLoadName(): String = currentLoad.get() ?: "outside an icon load"

  private fun count(event: String) {
    events.getOrPut(event) { ConcurrentHashMap() }.getOrPut(currentLoadName()) { Counter() }.increment()
  }

  private fun dumpStats() {
    // for logging consistency, take a snapshot
    val snapshot = stats.mapValues { it.value.snapshot() }
    val events = EVENTS.associateWith { event -> events[event].orEmpty().mapValues { it.value.snapshot() } }
    val foundReadsByPath = foundReadsByPath.mapValues { it.value.snapshot() }
    val missingReadsByPath = missingReadsByPath.mapValues { it.value.snapshot() }
    val decodedIcons = decodedIcons.mapValues { it.value.snapshot() }
    val loadsByCaller = loadsByCaller.mapValues { it.value.snapshot() }
    val tracesByCaller = tracesByCaller.mapValues { traces -> traces.value.mapValues { it.value.snapshot() } }

    LOG.info("Loaded ${snapshot.values.sum()} icons in total:")
    for ([load, count] in snapshot.entries.sortedBy { it.key }) {
      LOG.info("  $load: $count")
    }
    for ([event, countsByLoad] in events) {
      LOG.info("$event: ${countsByLoad.values.sum()}")
      for ([load, count] in countsByLoad.entries.sortedByDescending { it.value.total }) {
        LOG.info("  $load: $count")
      }
    }
    logByCount(title = "Found resource reads by path:", counts = foundReadsByPath)
    logByCount(title = "Not found resource reads by path:", counts = missingReadsByPath)
    LOG.info("Decoded icons:")
    for ([kind, count] in decodedIcons.entries.sortedBy { it.key }) {
      LOG.info("  $kind: $count")
    }
    logByCount(title = "Icon loads by caller:", counts = loadsByCaller)

    val traceLevel = traceLevel ?: return
    LOG.info("Icon load stack traces of the callers with at least $traceLevel loads on the EDT:")
    for ([caller, count] in loadsByCaller.entries.sortedWith(BY_COUNT)) {
      if (count.edt < traceLevel) {
        continue
      }
      val traces = tracesByCaller[caller].orEmpty()
      LOG.info("  $caller: $count in ${traces.size} unique stack traces")
      for ([trace, traceCount] in traces.entries.sortedWith(BY_COUNT)) {
        LOG.info("    $traceCount:\n$trace")
      }
    }
  }
}

private const val FOUND_RESOURCE_READS = "Resource reads, found"
private const val MISSING_RESOURCE_READS = "Resource reads, not found"
private const val URL_READS = "URL reads"
private const val SVG_PARSES_FOR_RENDERING = "SVG parses for rendering"
private const val SVG_PARSES_FOR_SHAPES = "SVG parses for shapes"
private const val SVG_RENDERS = "SVG renders"
private const val TRACE_PROPERTY = "image.data.loader.stats.trace"
private val EVENTS = listOf(FOUND_RESOURCE_READS, MISSING_RESOURCE_READS, URL_READS, SVG_PARSES_FOR_RENDERING, SVG_PARSES_FOR_SHAPES, SVG_RENDERS)

private class Counter {
  private val total = AtomicInt(0)
  private val edt = AtomicInt(0)

  fun increment() {
    val _ = total.incrementAndFetch()
    if (EDT.isCurrentThreadEdt()) {
      val _ = edt.incrementAndFetch()
    }
  }

  fun snapshot(): CounterSnapshot = CounterSnapshot(total = total.load(), edt = edt.load())
}

private class CounterSnapshot(@JvmField val total: Int, @JvmField val edt: Int) {
  override fun toString(): String = "$total ($edt on EDT)"
}

private fun Collection<CounterSnapshot>.sum(): CounterSnapshot = CounterSnapshot(total = sumOf { it.total }, edt = sumOf { it.edt })

private val BY_COUNT: Comparator<Map.Entry<String, CounterSnapshot>> =
  compareByDescending<Map.Entry<String, CounterSnapshot>> { it.value.total }.thenBy { it.key }

private fun logByCount(title: String, counts: Map<String, CounterSnapshot>) {
  LOG.info(title)
  for ([key, count] in counts.entries.sortedWith(BY_COUNT)) {
    LOG.info("  $key: $count")
  }
}

private val STACK_WALKER: StackWalker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)

private val ICON_CODE_PREFIXES = listOf(
  "com.intellij.ui.icons.",
  "com.intellij.ui.scale.",
  "com.intellij.ui.svg.",
  "com.intellij.openapi.util.IconLoader",
  "com.intellij.util.IconUtil",
  "javax.swing.",
  "java.",
  "jdk.",
  "sun.",
  "kotlin.",
  "kotlinx.",
)

private val SERVICE_CLASS_NAME: String = ImageDataLoaderStatisticsService::class.java.name

/**
 * The [caller] of a load and, if the tracing is on, its stack [trace].
 */
private class LoadSite(@JvmField val caller: String, @JvmField val trace: String?)

/**
 * Finds the caller of the current load: the first stack frame outside the icon code, Swing, AWT and the JDK, as `Class.method`.
 * A frame of an [Icon] class also counts as the icon code, for example a `LayeredIcon` that paints its layers.
 * With [withTrace], it also returns the stack below this service, one frame per line.
 */
private fun findLoadSite(withTrace: Boolean): LoadSite {
  return STACK_WALKER.walk { frames ->
    val stack = frames.dropWhile { it.className.startsWith(SERVICE_CLASS_NAME) }
    if (withTrace) {
      val stackFrames = stack.toList()
      LoadSite(caller = callerName(stackFrames.firstOrNull { !isIconCode(it) }),
               trace = stackFrames.joinToString(separator = "\n") { "\tat ${it.toStackTraceElement()}" })
    }
    else {
      LoadSite(caller = callerName(stack.filter { !isIconCode(it) }.findFirst().orElse(null)), trace = null)
    }
  }
}

private fun isIconCode(frame: StackWalker.StackFrame): Boolean {
  return ICON_CODE_PREFIXES.any { frame.className.startsWith(it) } || Icon::class.java.isAssignableFrom(frame.declaringClass)
}

private fun callerName(frame: StackWalker.StackFrame?): String {
  return if (frame == null) "unknown" else "${frame.className.substringAfterLast('.')}.${frame.methodName}"
}

private val INSTANCE: ImageDataLoaderStatisticsService? by lazy {
  if (System.getProperty("image.data.loader.stats", "false") == "true") {
    ImageDataLoaderStatisticsService()
  }
  else {
    null
  }
}

private val LOG = logger<ImageDataLoaderStatisticsService>()

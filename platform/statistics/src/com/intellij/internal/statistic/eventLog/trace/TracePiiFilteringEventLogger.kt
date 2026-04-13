// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.trace

import com.intellij.internal.statistic.eventLog.EventLogFile
import com.intellij.internal.statistic.eventLog.EventLogFilesProvider
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.RecorderOptionProvider
import com.intellij.internal.statistic.eventLog.StatisticsEventLogger
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.ListEventField
import com.intellij.internal.statistic.eventLog.events.ObjectEventField
import com.intellij.internal.statistic.eventLog.events.ObjectListEventField
import com.intellij.internal.statistic.eventLog.events.PrimitiveEventField
import com.intellij.openapi.diagnostic.logger
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

internal class TracePiiFilteringEventLogger(
  private val delegateProvider: () -> StatisticsEventLogger,
  recorderOptionsProvider: RecorderOptionProvider? = null,
  private val dataFilter: (EventLogGroup, String, Map<String, Any>) -> Map<String, Any> =
    TraceLlmPiiDataFilter.createFilter(recorderOptionsProvider),
) : StatisticsEventLogger {
  override fun logAsync(group: EventLogGroup, eventId: String, data: Map<String, Any>, isState: Boolean): CompletableFuture<*> {
    return logAsync(group, eventId, { data }, isState)
  }

  override fun logAsync(
    group: EventLogGroup,
    eventId: String,
    dataProvider: () -> Map<String, Any>?,
    isState: Boolean,
  ): CompletableFuture<*> {
    val delegate = delegateProvider()
    return delegate.logAsync(group, eventId, {
      dataProvider()?.let { data ->
        dataFilter(group, eventId, data)
      }
    }, isState)
  }

  override fun computeAsync(computation: (backgroundThreadExecutor: Executor) -> Unit) {
    delegateProvider().computeAsync(computation)
  }

  override fun getActiveLogFile(): EventLogFile? = delegateProvider().getActiveLogFile()

  override fun getLogFilesProvider(): EventLogFilesProvider = delegateProvider().getLogFilesProvider()

  override fun cleanup() {
    delegateProvider().cleanup()
  }

  override fun rollOver() {
    delegateProvider().rollOver()
  }
}

internal object TraceLlmPiiDataFilter {
  private const val LLM_PARAMETERS_RULE = "util#llm_parameters"
  private val llmFieldPathsResolver = TraceLlmFieldPathsResolver()

  fun createFilter(recorderOptionsProvider: RecorderOptionProvider?): (EventLogGroup, String, Map<String, Any>) -> Map<String, Any> {
    val redactor = TracePiiRegexRedactor(recorderOptionsProvider)
    return { group, eventId, data ->
      filter(group, eventId, data, redactor::redact)
    }
  }

  fun filter(
    group: EventLogGroup,
    eventId: String,
    data: Map<String, Any>,
    redactString: (String) -> String = TracePiiRegexRedactor.DEFAULT::redact,
  ): Map<String, Any> {
    val llmFieldPaths = llmFieldPathsResolver.getLlmFieldPaths(group, eventId)
    if (llmFieldPaths.isEmpty()) {
      return data
    }
    return filterByRulePaths(data, llmFieldPaths, redactString)
  }

  private fun filterByRulePaths(
    data: Map<String, Any>,
    llmFieldPaths: Set<String>,
    redactString: (String) -> String = TracePiiRegexRedactor.DEFAULT::redact,
  ): Map<String, Any> {
    var changed = false
    val filteredData = LinkedHashMap<String, Any>(data.size)
    for ((key, value) in data) {
      val filteredValue = filterValue(value = value, currentPath = key, llmFieldPaths = llmFieldPaths, redactString = redactString)
      if (filteredValue != value) {
        changed = true
      }
      filteredData[key] = filteredValue
    }
    return if (changed) filteredData else data
  }

  private fun filterValue(
    value: Any,
    currentPath: String,
    llmFieldPaths: Set<String>,
    redactString: (String) -> String,
  ): Any {
    if (currentPath in llmFieldPaths) {
      return redactWholeValue(value, redactString)
    }
    return when (value) {
      is Map<*, *> -> filterMap(value, currentPath, llmFieldPaths, redactString)
      is List<*> -> filterList(value, currentPath, llmFieldPaths, redactString)
      is Array<*> -> filterArray(value, currentPath, llmFieldPaths, redactString)
      else -> value
    }
  }

  private fun filterMap(
    value: Map<*, *>,
    currentPath: String,
    llmFieldPaths: Set<String>,
    redactString: (String) -> String,
  ): Any {
    var changed = false
    val filtered = LinkedHashMap<Any?, Any?>(value.size)
    for ((key, nestedValue) in value) {
      if (key !is String || nestedValue == null) {
        filtered[key] = nestedValue
        continue
      }

      val nestedPath = "$currentPath.$key"
      val filteredValue = filterValue(nestedValue, nestedPath, llmFieldPaths, redactString)
      if (filteredValue != nestedValue) {
        changed = true
      }
      filtered[key] = filteredValue
    }
    return if (changed) filtered else value
  }

  private fun filterList(
    value: List<*>,
    currentPath: String,
    llmFieldPaths: Set<String>,
    redactString: (String) -> String,
  ): Any {
    var changed = false
    val filtered = ArrayList<Any?>(value.size)
    for (element in value) {
      val filteredElement =
        if (element == null) null
        else filterValue(element, currentPath, llmFieldPaths, redactString)
      if (filteredElement != element) {
        changed = true
      }
      filtered.add(filteredElement)
    }
    return if (changed) filtered else value
  }

  private fun filterArray(
    value: Array<*>,
    currentPath: String,
    llmFieldPaths: Set<String>,
    redactString: (String) -> String,
  ): Any {
    var changed = false
    val filtered = arrayOfNulls<Any?>(value.size)
    for (i in value.indices) {
      val element = value[i]
      val filteredElement = if (element == null) null else filterValue(element, currentPath, llmFieldPaths, redactString)
      if (filteredElement != element) {
        changed = true
      }
      filtered[i] = filteredElement
    }
    return if (changed) filtered else value
  }

  private fun redactWholeValue(value: Any, redactString: (String) -> String): Any {
    return when (value) {
      is String -> redactString(value)
      is Map<*, *> -> {
        var changed = false
        val filtered = LinkedHashMap<Any?, Any?>(value.size)
        for ((key, nestedValue) in value) {
          val filteredValue = if (nestedValue == null) null else redactWholeValue(nestedValue, redactString)
          if (filteredValue != nestedValue) {
            changed = true
          }
          filtered[key] = filteredValue
        }
        if (changed) filtered else value
      }
      is List<*> -> {
        var changed = false
        val filtered = ArrayList<Any?>(value.size)
        for (element in value) {
          val filteredElement = if (element == null) null else redactWholeValue(element, redactString)
          if (filteredElement != element) {
            changed = true
          }
          filtered.add(filteredElement)
        }
        if (changed) filtered else value
      }
      is Array<*> -> {
        var changed = false
        val filtered = arrayOfNulls<Any?>(value.size)
        for (i in value.indices) {
          val element = value[i]
          val filteredElement = if (element == null) null else redactWholeValue(element, redactString)
          if (filteredElement != element) {
            changed = true
          }
          filtered[i] = filteredElement
        }
        if (changed) filtered else value
      }
      else -> value
    }
  }

  private class TraceLlmFieldPathsResolver {
    private val llmFieldPathsByEvent = ConcurrentHashMap<String, Set<String>>()

    fun getLlmFieldPaths(group: EventLogGroup, eventId: String): Set<String> {
      val cacheKey = group.id + "|" + group.version + "|" + eventId
      llmFieldPathsByEvent[cacheKey]?.let { return it }

      val event = group.events.firstOrNull { registeredEvent -> registeredEvent.eventId == eventId } ?: return emptySet()
      val llmFieldPaths = LinkedHashSet<String>()
      for (field in event.getFields()) {
        collectLlmFieldPaths(field = field, parentPath = null, result = llmFieldPaths)
      }

      if (llmFieldPaths.isNotEmpty()) {
        llmFieldPathsByEvent.putIfAbsent(cacheKey, llmFieldPaths)
      }
      return llmFieldPaths
    }

    private fun collectLlmFieldPaths(field: EventField<*>, parentPath: String?, result: MutableSet<String>) {
      val fieldPath = if (parentPath == null) field.name else "$parentPath.${field.name}"
      when (field) {
        is PrimitiveEventField<*> -> {
          if (shouldBePiiFiltered(field.validationRule)) {
            result.add(fieldPath)
          }
        }
        is ListEventField<*> -> {
          if (shouldBePiiFiltered(field.validationRule)) {
            result.add(fieldPath)
          }
        }
        is ObjectEventField -> {
          for (nestedField in field.fields) {
            collectLlmFieldPaths(nestedField, fieldPath, result)
          }
        }
        is ObjectListEventField -> {
          for (nestedField in field.fields) {
            collectLlmFieldPaths(nestedField, fieldPath, result)
          }
        }
      }
    }

    private fun shouldBePiiFiltered(validationRules: List<String>): Boolean {
      return validationRules.any { rule -> rule.contains(LLM_PARAMETERS_RULE) }
    }
  }
}

internal class TracePiiRegexRedactor(
  private val recorderOptionsProvider: RecorderOptionProvider?,
) {
  companion object {
    private val LOG = logger<TracePiiRegexRedactor>()

    private const val REDACTED = "[REDACTED]"
    internal const val TRACE_PII_REGEXES_JSON_OPTION: String = "trace_pii_regexes_json"

    internal val DEFAULT = TracePiiRegexRedactor(null)

    private val jsonMapper: JsonMapper by lazy(LazyThreadSafetyMode.PUBLICATION) {
      JsonMapper.builder().build()
    }

    private fun compileRegex(pattern: String): Regex? {
      return try {
        Regex(pattern)
      }
      catch (e: IllegalArgumentException) {
        LOG.warn("Skipping invalid TRACE PII regex: $pattern", e)
        null
      }
    }
  }

  @Volatile
  private var cachedRemotePatterns: CachedPatterns? = null

  fun redact(value: String): String {
    var redactedValue = value
    for (pattern in resolvePatterns()) {
      redactedValue = pattern.replace(redactedValue, REDACTED)
    }
    return redactedValue
  }

  private fun resolvePatterns(): List<Regex> {
    val rawRules = recorderOptionsProvider
      ?.getStringOption(TRACE_PII_REGEXES_JSON_OPTION)
      ?.takeIf { it.isNotBlank() }
      ?: return emptyList() // no fallback now

    cachedRemotePatterns?.let {
      if (it.rawRules == rawRules) {
        return it.patterns
      }
    }

    synchronized(this) {
      cachedRemotePatterns?.let {
        if (it.rawRules == rawRules) {
          return it.patterns
        }
      }

      val compiledPatterns = loadRemotePatterns(rawRules)
      cachedRemotePatterns = CachedPatterns(rawRules, compiledPatterns)
      return compiledPatterns
    }
  }

  private fun loadRemotePatterns(rawRules: String): List<Regex> {
    val parsedPatterns = parseJsonStringArray(rawRules) ?: return emptyList()
    if (parsedPatterns.isEmpty()) {
      LOG.warn("TRACE PII rules option '$TRACE_PII_REGEXES_JSON_OPTION' is empty, no redaction will be applied")
      return emptyList()
    }

    val compiledPatterns = parsedPatterns.mapNotNull(::compileRegex)
    if (compiledPatterns.isEmpty()) {
      LOG.warn("TRACE PII rules option '$TRACE_PII_REGEXES_JSON_OPTION' has no valid regexes, no redaction will be applied")
    }
    return compiledPatterns
  }

  internal fun parseJsonStringArray(rawRules: String): List<String>? {
    return try {
      jsonMapper.readValue(rawRules, object : TypeReference<List<String>>() {})
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    }
    catch (e: Exception) {
      LOG.warn("Cannot parse TRACE PII rules from option '$TRACE_PII_REGEXES_JSON_OPTION' as JSON string array", e)
      null
    }
  }

  private data class CachedPatterns(val rawRules: String, val patterns: List<Regex>)
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.workspace

import com.google.gson.*
import com.intellij.cce.evaluable.EvaluationStrategy
import com.intellij.cce.evaluable.StrategySerializer
import com.intellij.cce.filter.EvaluationFilterReader
import com.intellij.cce.interpreter.InterpretationOrder
import com.intellij.cce.util.getAs
import com.intellij.cce.util.getIfExists
import com.intellij.cce.workspace.filter.CompareSessionsFilter
import com.intellij.cce.workspace.filter.SessionsFilter
import org.apache.commons.lang3.text.StrSubstitutor
import java.lang.reflect.Type
import java.nio.file.Files
import java.nio.file.Path

object ConfigFactory {
  const val DEFAULT_CONFIG_NAME = "config.json"

  private lateinit var gson: Gson

  private fun defaultConfig(projectPath: String = "", language: String = "Java"): Config =
    Config.build(projectPath, language) {}

  fun <T : EvaluationStrategy> load(path: Path, strategySerializer: StrategySerializer<T>): Config {
    gson = GsonBuilder()
      .serializeNulls()
      .setPrettyPrinting()
      .registerTypeAdapter(SessionsFilter::class.java,
                           SessionFiltersSerializer())
      .registerTypeAdapter(EvaluationStrategy::class.java, strategySerializer)
      .create()
    val configFile = path.toFile()
    if (!configFile.exists()) {
      save(defaultConfig(), path.parent, configFile.name)
      throw IllegalArgumentException("Config file missing. Config created by path: ${configFile.absolutePath}. Fill settings in config.")
    }

    return deserialize(configFile.readText(), strategySerializer)
  }

  fun save(config: Config, directory: Path, name: String = DEFAULT_CONFIG_NAME) {
    val json = serialize(config)
    Files.write(directory.resolve(name), json.toByteArray())
  }

  fun serialize(config: Config): String = gson.toJson(config)

  fun <T : EvaluationStrategy> deserialize(json: String, strategySerializer: StrategySerializer<T>): Config {
    val map = gson.fromJson(json, HashMap<String, Any>().javaClass)
    val languageName = map.getAs<String>("language")
    return Config.build(map.handleEnv("projectPath"), languageName) {
      outputDir = map.handleEnv("outputDir")
      if (map.containsKey("projectName")) {
        projectName = map.handleEnv("projectName")
      }
      deserializeStrategy(map.getIfExists("strategy"), strategySerializer, languageName, this)
      deserializeActionsGeneration(map.getIfExists("actions"), languageName, this)
      deserializeActionsInterpretation(map.getIfExists("interpret"), this)
      deserializeReorderElements(map.getIfExists("reorder"), this)
      deserializeReportGeneration(map.getIfExists("reports"), languageName, this)
    }
  }

  private fun deserializeActionsGeneration(map: Map<String, Any>?, language: String, builder: Config.Builder) {
    if (map == null) return
    builder.evaluationRoots = map.getAs("evaluationRoots")
  }

  private fun deserializeActionsInterpretation(map: Map<String, Any>?, builder: Config.Builder) {
    if (map == null) return
    if (map.containsKey("experimentGroup")) {
      builder.experimentGroup = map.getAs<Double?>("experimentGroup")?.toInt()
    }
    if (map.containsKey("sessionsLimit")) {
      builder.sessionsLimit = map.getAs<Double?>("sessionsLimit")?.toInt()
    }
    builder.sessionProbability = map.getAs("sessionProbability")
    builder.sessionSeed = map.getAs<Double?>("sessionSeed")?.toLong()
    if (map.containsKey("order")) {
      builder.order = InterpretationOrder.valueOf(map.getAs<String>("order"))
    }
    builder.saveLogs = map.getAs("saveLogs")
    if (map.containsKey("saveFeatures")) {
      builder.saveFeatures = map.getAs("saveFeatures")
    }
    if (map.containsKey("saveContent")) {
      builder.saveContent = map.getAs("saveContent")
    }
    if (map.containsKey("logLocationAndItemText")) {
      builder.logLocationAndItemText = map.getAs("logLocationAndItemText")
    }
    builder.trainTestSplit = map.getAs<Double>("trainTestSplit").toInt()
  }

  private fun <T : EvaluationStrategy> deserializeStrategy(map: Map<String, Any>?,
                                                           strategySerializer: StrategySerializer<T>,
                                                           language: String,
                                                           builder: Config.Builder) {
    if (map == null)
      throw IllegalArgumentException("No strategy found in config!")
    builder.strategy = strategySerializer.deserialize(map, language)
  }

  private fun deserializeReorderElements(map: Map<String, Any>?, builder: Config.Builder) {
    if (map == null) return
    builder.useReordering = map.getAs("useReordering")
    if (builder.useReordering) {
      builder.reorderingTitle = map.getAs("title")
      builder.featuresForReordering = map.getAs("features")
      assert(builder.featuresForReordering.isNotEmpty()) {
        "no features for reordering elements"
      }
    }
  }

  private fun deserializeReportGeneration(map: Map<String, Any>?, language: String, builder: Config.Builder) {
    if (map == null) return
    builder.evaluationTitle = map.handleEnv("evaluationTitle")
    if (map.containsKey("defaultMetrics")) {
      builder.defaultMetrics = map.getAs("defaultMetrics")
    }
    val filtersList = map.getAs<List<Map<String, Any>>>("sessionsFilters")
    val filters = mutableListOf<SessionsFilter>()
    filtersList.forEach {
      val name = it.getAs<String>("name")
      filters.add(SessionsFilter(name, EvaluationFilterReader.readFilters(it, language)))
    }
    builder.mergeFilters(filters)
    val comparisonFiltersList = map.getAs<List<Map<String, Any>>>("comparisonFilters")
    val comparisonFilters = mutableListOf<CompareSessionsFilter>()
    comparisonFiltersList.forEach {
      comparisonFilters.add(CompareSessionsFilter.create(it.getAs("filterType"), it.getAs("name"), it.getAs("evaluationType")))
    }
    builder.mergeComparisonFilters(comparisonFilters)
  }

  private fun Map<String, *>.handleEnv(key: String): String = StrSubstitutor.replaceSystemProperties(getAs(key))

  private class SessionFiltersSerializer : JsonSerializer<SessionsFilter> {
    override fun serialize(src: SessionsFilter, typeOfSrc: Type, context: JsonSerializationContext): JsonObject {
      val jsonObject = JsonObject()
      jsonObject.addProperty("name", src.name)
      val filtersObject = JsonObject()
      src.filters.forEach { id, filter -> filtersObject.add(id, filter.toJson()) }
      jsonObject.add("filters", filtersObject)
      return jsonObject
    }
  }
}

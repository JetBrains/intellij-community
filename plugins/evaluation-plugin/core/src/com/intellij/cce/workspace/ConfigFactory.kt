// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.diagnostic.logger
import org.apache.commons.lang3.text.StrSubstitutor
import java.lang.reflect.Type
import java.nio.file.Files
import java.nio.file.Path

object ConfigFactory {
  private val LOG = logger<ConfigFactory>()

  const val DEFAULT_CONFIG_NAME = "config.json"
  private const val NO_LANGUAGE = "NO LANGUAGE PROVIDED"

  private lateinit var gson: Gson

  private fun defaultConfig(): Config =
    Config.build {}

  fun <T : EvaluationStrategy> load(path: Path, strategySerializer: StrategySerializer<T>): Config {
    gson = createGson(strategySerializer)
    val configFile = path.toFile()
    if (!configFile.exists()) {
      save(defaultConfig(), path.parent, configFile.name)
      throw IllegalArgumentException("Config file missing. Config created by path: ${configFile.absolutePath}. Fill settings in config.")
    }

    val configFileContent = configFile.readText()

    return try {
      deserialize(gson, configFileContent, strategySerializer)
    } catch (t: Throwable) {
      LOG.error("Failed to deserialize config. File ${configFile.absolutePath}, content: ${configFileContent}", t)
      throw t
    }
  }

  fun save(config: Config, directory: Path, name: String = DEFAULT_CONFIG_NAME) {
    val json = serialize(config)
    Files.write(directory.resolve(name), json.toByteArray())
  }

  fun serialize(config: Config): String = gson.toJson(config)

  fun <T : EvaluationStrategy> deserialize(gson: Gson, json: String, strategySerializer: StrategySerializer<T>): Config {
    val map = gson.fromJson(json, HashMap<String, Any>().javaClass)
    return Config.build {
      outputDir = map.handleEnv("outputDir")
      deserializeActionsGeneration(
        map.getIfExists("actions"),
        map.getIfExists<String>("projectPath")?.handleEnv(),
        map.getIfExists<String>("projectName")?.handleEnv(),
        map.getIfExists<String>("language"),
        this
      )
      deserializeFileDataset(map.getIfExists("fileDataset"), this)

      deserializeStrategy(map.getIfExists("strategy"), strategySerializer, actions?.language, this)
      deserializeActionsInterpretation(map.getIfExists("interpret"), this)
      deserializeReorderElements(map.getIfExists("reorder"), this)
      deserializeReportGeneration(map.getIfExists("reports"), actions?.language, this)
    }
  }

  fun <T : EvaluationStrategy> createGson(strategySerializer: StrategySerializer<T>): Gson {
    return GsonBuilder()
      .serializeNulls()
      .setPrettyPrinting()
      .registerTypeAdapter(SessionsFilter::class.java,
                           SessionFiltersSerializer())
      .registerTypeAdapter(EvaluationStrategy::class.java, strategySerializer)
      .create()
  }

  private fun deserializeActionsGeneration(
    map: Map<String, Any>?,
    projectPath: String?,
    projectName: String?,
    language: String?,
    builder: Config.Builder
  ) {
    if (map == null && projectPath == null && language == null && projectName == null) {
      return
    }

    if (map != null) {
      val resultProjectPath = projectPath ?: map.handleEnv("projectPath")
      builder.actions = Config.ActionsGeneration(
        resultProjectPath,
        projectName ?: map.getIfExists<String>("projectName")?.handleEnv() ?: resultProjectPath.split('/').last(),
        language ?: map.getAs("language"),
        map.getAs("evaluationRoots"),
        map.getIfExists<List<String>>("ignoreFileNames")?.toSet() ?: emptySet(),
        map.getIfExists<String?>("sourceFile"),
      )
      return
    }

    throw IllegalStateException("Missing 'actions' in config when 'language', 'projectPath' or 'projectName' was provided")
  }

  private fun deserializeFileDataset(
    map: Map<String, Any>?,
    builder: Config.Builder
  ) {
    if (map == null) {
      return
    }

    builder.fileDataset = Config.FileDataset(
      map.getAs("url"),
      map.getIfExists<Double?>("chunkSize")?.toInt(),
    )
  }

  private fun deserializeActionsInterpretation(map: Map<String, Any>?, builder: Config.Builder) {
    if (map == null) return
    map.getIfExists<Double?>("experimentGroup")?.let { builder.experimentGroup = it.toInt() }
    map.getIfExists<Double?>("sessionsLimit")?.let { builder.sessionsLimit = it.toInt() }
    map.getIfExists<Double?>("filesLimit")?.let { builder.filesLimit = it.toInt() }
    map.getIfExists<Double?>("sessionProbability")?.let { builder.sessionProbability = it }
    map.getIfExists<Double?>("sessionSeed")?.let { builder.sessionSeed = it.toLong() }
    map.getIfExists<String?>("order")?.let { builder.order = InterpretationOrder.valueOf(it) }
    map.getIfExists<Boolean?>("saveLogs")?.let { builder.saveLogs = it }
    map.getIfExists<Boolean?>("saveFusLogs")?.let { builder.saveFusLogs = it }
    map.getIfExists<Boolean?>("saveFeatures")?.let { builder.saveFeatures = it }
    map.getIfExists<Boolean?>("saveContent")?.let { builder.saveContent = it }
    map.getIfExists<Boolean?>("logLocationAndItemText")?.let { builder.logLocationAndItemText = it }
    map.getIfExists<Double?>("trainTestSplit")?.let { builder.trainTestSplit = it.toInt() }
    map.getIfExists<String?>("registry")?.let { builder.registry = it }
  }

  private fun <T : EvaluationStrategy> deserializeStrategy(map: Map<String, Any>?,
                                                           strategySerializer: StrategySerializer<T>,
                                                           language: String?,
                                                           builder: Config.Builder) {
    if (map == null)
      throw IllegalArgumentException("No strategy found in config!")
    builder.strategy = strategySerializer.deserialize(map, language ?: NO_LANGUAGE)
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

  private fun deserializeReportGeneration(map: Map<String, Any>?, language: String?, builder: Config.Builder) {
    if (map == null) return
    builder.evaluationTitle = map.handleEnv("evaluationTitle")
    if (map.containsKey("defaultMetrics")) {
      builder.defaultMetrics = map.getAs("defaultMetrics")
    }
    val filtersList = map.getIfExists<List<Map<String, Any>>>("sessionsFilters")
    val filters = mutableListOf<SessionsFilter>()
    filtersList?.forEach {
      val name = it.getAs<String>("name")
      filters.add(SessionsFilter(name, EvaluationFilterReader.readFilters(it, language ?: NO_LANGUAGE)))
    }
    builder.mergeFilters(filters)
    val comparisonFiltersList = map.getIfExists<List<Map<String, Any>>>("comparisonFilters")
    val comparisonFilters = mutableListOf<CompareSessionsFilter>()
    comparisonFiltersList?.forEach {
      comparisonFilters.add(CompareSessionsFilter.create(it.getAs("filterType"), it.getAs("name"), it.getAs("evaluationType")))
    }
    builder.mergeComparisonFilters(comparisonFilters)
  }

  private fun Map<String, *>.handleEnv(key: String): String = StrSubstitutor.replaceSystemProperties(getAs(key))
  private fun String.handleEnv(): String = StrSubstitutor.replaceSystemProperties(this)

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

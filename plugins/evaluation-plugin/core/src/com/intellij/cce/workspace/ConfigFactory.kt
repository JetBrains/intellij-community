package com.intellij.cce.workspace

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.intellij.cce.actions.*
import com.intellij.cce.filter.EvaluationFilter
import com.intellij.cce.filter.EvaluationFilterManager
import com.intellij.cce.workspace.filter.CompareSessionsFilter
import com.intellij.cce.workspace.filter.SessionsFilter
import org.apache.commons.lang.text.StrSubstitutor
import java.lang.reflect.Type
import java.nio.file.Files
import java.nio.file.Path

object ConfigFactory {
  const val DEFAULT_CONFIG_NAME = "config.json"

  private val gson = GsonBuilder()
    .serializeNulls()
    .setPrettyPrinting()
    .registerTypeAdapter(CompletionStrategy::class.java, CompletionStrategySerializer())
    .registerTypeAdapter(SessionsFilter::class.java, SessionFiltersSerializer())
    .create()

  fun defaultConfig(projectPath: String = "", language: String = "Java"): Config =
    Config.build(projectPath, language) {}

  fun load(path: Path): Config {
    val configFile = path.toFile()
    if (!configFile.exists()) {
      save(defaultConfig(), path.parent, configFile.name)
      throw IllegalArgumentException("Config file missing. Config created by path: ${configFile.absolutePath}. Fill settings in config.")
    }

    return deserialize(configFile.readText())
  }

  fun save(config: Config, directory: Path, name: String = DEFAULT_CONFIG_NAME) {
    val json = serialize(config)
    Files.write(directory.resolve(name), json.toByteArray())
  }

  fun serialize(config: Config): String = gson.toJson(config)

  fun deserialize(json: String): Config {
    val map = gson.fromJson(json, HashMap<String, Any>().javaClass)
    val languageName = map.getAs<String>("language")
    return Config.build(map.handleEnv("projectPath"), languageName) {
      outputDir = map.handleEnv("outputDir")
      deserializeActionsGeneration(map.getIfExists("actions"), languageName, this)
      deserializeActionsInterpretation(map.getIfExists("interpret"), this)
      deserializeReorderElements(map.getIfExists("reorder"), this)
      deserializeReportGeneration(map.getIfExists("reports"), languageName, this)
    }
  }

  private fun deserializeActionsGeneration(map: Map<String, Any>?, language: String, builder: Config.Builder) {
    if (map == null) return
    builder.evaluationRoots = map.getAs("evaluationRoots")
    val strategyJson = map.getAs<Map<String, Any>>("strategy")
    CompletionStrategyDeserializer().deserialize(strategyJson, language, builder)
  }

  private fun deserializeActionsInterpretation(map: Map<String, Any>?, builder: Config.Builder) {
    if (map == null) return
    builder.completionType = CompletionType.valueOf(map.getAs("completionType"))
    if (map.containsKey("experimentGroup"))
      builder.experimentGroup = map.getAs<Double?>("experimentGroup")?.toInt()
    if (map.containsKey("sessionsLimit"))
      builder.sessionsLimit = map.getAs<Double?>("sessionsLimit")?.toInt()
    builder.completeTokenProbability = map.getAs("completeTokenProbability")
    builder.completeTokenSeed = map.getAs<Double?>("completeTokenSeed")?.toLong()
    if (map.containsKey("emulationSettings"))
      builder.emulationSettings = gson.fromJson(gson.toJson(map["emulationSettings"]), UserEmulator.Settings::class.java)
    if (map.containsKey("completionGolfSettings"))
      builder.completionGolfSettings = gson.fromJson(gson.toJson(map["completionGolfSettings"]), CompletionGolfEmulation.Settings::class.java)
    builder.saveLogs = map.getAs("saveLogs")
    if (map.containsKey("saveFeatures"))
      builder.saveFeatures = map.getAs("saveFeatures")
    if (map.containsKey("saveContent"))
      builder.saveContent = map.getAs("saveContent")
    if (map.containsKey("logLocationAndItemText"))
      builder.logLocationAndItemText = map.getAs("logLocationAndItemText")
    builder.trainTestSplit = map.getAs<Double>("trainTestSplit").toInt()
  }

  private fun deserializeReorderElements(map: Map<String, Any>?, builder: Config.Builder) {
    if (map == null) return
    builder.useReordering = map.getAs("useReordering")
    builder.reorderingTitle = map.getAs("title")
    builder.featuresForReordering = map.getAs("features")
    assert(!builder.useReordering || builder.featuresForReordering.isNotEmpty()) {
      "no features for reordering elements"
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
      filters.add(SessionsFilter(name, readFilters(it, language)))
    }
    builder.mergeFilters(filters)
    val comparisonFiltersList = map.getAs<List<Map<String, Any>>>("comparisonFilters")
    val comparisonFilters = mutableListOf<CompareSessionsFilter>()
    comparisonFiltersList.forEach {
      comparisonFilters.add(CompareSessionsFilter.create(it.getAs("filterType"), it.getAs("name"), it.getAs("evaluationType")))
    }
    builder.mergeComparisonFilters(comparisonFilters)
  }

  private class CompletionStrategyDeserializer {
    fun deserialize(strategy: Map<String, Any>, language: String, builder: Config.Builder) {
      builder.emulateUser = if (strategy.containsKey("emulateUser")) strategy.getAs("emulateUser") else false
      if (strategy.containsKey("completionGolf")) {
        builder.completionGolfMode = CompletionGolfMode.valueOf(strategy.getAs("completionGolf"))
      }
      builder.contextStrategy = CompletionContext.valueOf(strategy.getAs("context"))
      if (strategy.containsKey("filters")) {
        builder.filters.putAll(readFilters(strategy, language))
      }
      if (!builder.emulateUser) {
        builder.prefixStrategy = getPrefix(strategy)
      }
    }

    private fun getPrefix(strategy: Map<String, Any>): CompletionPrefix {
      val prefix = strategy.getAs<Map<String, Any>>("prefix")
      return when (prefix["name"]) {
        "NoPrefix" -> CompletionPrefix.NoPrefix
        "CapitalizePrefix" -> CompletionPrefix.CapitalizePrefix(prefix.getAs("emulateTyping"))
        "SimplePrefix" -> CompletionPrefix.SimplePrefix(prefix.getAs("emulateTyping"), prefix.getAs<Double>("n").toInt())
        else -> throw IllegalArgumentException("Unknown completion prefix")
      }
    }
  }

  private fun Map<String, *>.handleEnv(key: String): String = StrSubstitutor.replaceSystemProperties(getAs(key))

  private fun readFilters(map: Map<String, Any>, language: String): MutableMap<String, EvaluationFilter> {
    val evaluationFilters = mutableMapOf<String, EvaluationFilter>()
    val filters = map.getAs<Map<String, Any>>("filters")
    for ((id, description) in filters) {
      val configuration = EvaluationFilterManager.getConfigurationById(id)
                          ?: throw IllegalStateException("Unknown filter: $id")
      assert(configuration.isLanguageSupported(language)) { "filter $id is not supported for this language" }
      evaluationFilters[id] = configuration.buildFromJson(description)
    }
    return evaluationFilters
  }

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

  private inline fun <reified T> Map<String, *>.getAs(key: String): T {
    check(key in this.keys) { "$key not found. Existing keys: ${keys.toList()}" }
    val value = this.getValue(key)
    check(value is T) { "Unexpected type in config" }
    return value
  }

  private inline fun <reified T> Map<String, *>.getIfExists(key: String): T? {
    if (key !in this.keys) return null
    val value = this.getValue(key)
    check(value is T) { "Unexpected type in config" }
    return value
  }
}

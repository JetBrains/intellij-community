// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.cost

import com.intellij.agent.workbench.sessions.core.cost.AgentSessionUsageSnapshot
import com.intellij.platform.eel.fs.EelFiles
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.core.ObjectReadContext
import tools.jackson.core.json.JsonFactory
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant

private const val EMBEDDED_LITELLM_PRICING_RESOURCE = "session_costs_pricing/litellm/model_prices_and_context_window.json"
private const val ONLINE_CACHE_FILE_NAME = "litellm-online-pricing.json"
private val ONLINE_CACHE_TTL: Duration = Duration.ofHours(24)
private val CACHE_CREATE_MULTIPLIER = BigDecimal("1.25")
private val CACHE_READ_MULTIPLIER = BigDecimal("0.1")
internal val CACHE_WRITE_1H_MULTIPLIER: BigDecimal = BigDecimal("2.0")

data class LiteLlmPricingEntry(
  @JvmField val displayName: String,
  @JvmField val aliases: List<String>,
  @JvmField val promptTokenPriceUsd: BigDecimal,
  @JvmField val completionTokenPriceUsd: BigDecimal,
  @JvmField val cacheReadTokenPriceUsd: BigDecimal,
  @JvmField val cacheWriteTokenPriceUsd: BigDecimal,
)

data class LiteLlmPricingCatalogSnapshot(
  @JvmField val aliasEntries: List<LiteLlmPricingEntry>,
  @JvmField val directEntries: List<LiteLlmPricingEntry>,
  @JvmField val matchedAliasEntries: Map<String, Int>,
  @JvmField val matchedDirectEntries: Map<String, Int>,
)

data class LiteLlmCachedPricingSnapshot(
  @JvmField val fetchedAt: String,
  @JvmField val body: String,
)

private data class ParsedPricing(
  @JvmField val input: BigDecimal,
  @JvmField val output: BigDecimal,
  @JvmField val cacheCreate: BigDecimal,
  @JvmField val cacheRead: BigDecimal,
)

private data class AliasDefinition(
  @JvmField val displayName: String,
  @JvmField val target: String,
  @JvmField val aliases: List<String>,
)

private data class LegacyFallbackPricing(
  @JvmField val prompt: BigDecimal,
  @JvmField val completion: BigDecimal,
  @JvmField val cacheRead: BigDecimal,
  @JvmField val cacheWrite: BigDecimal,
)

object LiteLlmPriceCatalog {
  private val jsonFactory = JsonFactory()

  private val aliasDefinitions = listOf(
    AliasDefinition("Anthropic: Claude Fable 5", "claude-fable-5", listOf("anthropic/claude-fable-5", "anthropic/claude-5-fable-20260609", "claude-fable-5")),
    AliasDefinition("Anthropic: Claude Mythos 5", "claude-mythos-5", listOf("anthropic/claude-mythos-5", "anthropic/claude-5-mythos-20260609", "claude-mythos-5")),
    AliasDefinition("Anthropic: Claude Opus 4.8", "claude-opus-4-8", listOf("anthropic/claude-opus-4.8", "anthropic/claude-4.8-opus-20260528", "claude-opus-4.8")),
    AliasDefinition("Anthropic: Claude Opus 4.7", "claude-opus-4-7", listOf("anthropic/claude-opus-4.7", "anthropic/claude-4.7-opus-20260416", "claude-opus-4.7")),
    AliasDefinition("Anthropic: Claude Opus 4.6", "claude-opus-4-6", listOf("anthropic/claude-opus-4.6", "anthropic/claude-4.6-opus-20260205", "claude-opus-4.6")),
    AliasDefinition("Anthropic: Claude Opus 4.5", "claude-opus-4-5", listOf("anthropic/claude-opus-4.5", "anthropic/claude-4.5-opus-20251124", "claude-opus-4.5")),
    AliasDefinition("Anthropic: Claude Opus 4.1", "claude-opus-4-1", listOf("anthropic/claude-opus-4.1", "anthropic/claude-4.1-opus-20250805", "claude-opus-4.1")),
    AliasDefinition("Anthropic: Claude Sonnet 4.6", "claude-sonnet-4-6", listOf("anthropic/claude-sonnet-4.6", "anthropic/claude-4.6-sonnet-20260217", "claude-sonnet-4.6")),
    AliasDefinition("Anthropic: Claude Sonnet 4.5", "claude-sonnet-4-5", listOf("anthropic/claude-sonnet-4.5", "anthropic/claude-4.5-sonnet-20250929", "claude-sonnet-4.5")),
    AliasDefinition("Anthropic: Claude Sonnet 4", "claude-sonnet-4-20250514", listOf("anthropic/claude-sonnet-4", "anthropic/claude-4-sonnet-20250522", "claude-sonnet-4")),
    AliasDefinition("Anthropic: Claude Haiku 4.5", "claude-haiku-4-5", listOf("anthropic/claude-haiku-4.5", "anthropic/claude-4.5-haiku-20251001", "claude-haiku-4.5")),
    AliasDefinition("Anthropic: Claude 3.5 Haiku", "claude-3-5-haiku", listOf("anthropic/claude-3.5-haiku", "claude-3.5-haiku")),
    AliasDefinition("OpenAI: GPT-5.5", "gpt-5.5", listOf("openai/gpt-5.5", "openai/gpt-5.5-20260423", "gpt-5.5")),
    AliasDefinition("OpenAI: GPT-5.4", "gpt-5.4", listOf("openai/gpt-5.4", "openai/gpt-5.4-20260305", "gpt-5.4")),
    AliasDefinition("OpenAI: GPT-5.4 Mini", "gpt-5.4-mini", listOf("openai/gpt-5.4-mini", "openai/gpt-5.4-mini-20260317", "gpt-5.4-mini")),
    AliasDefinition("OpenAI: GPT-5 Codex", "gpt-5-codex", listOf("openai/gpt-5-codex", "gpt-5-codex")),
    AliasDefinition("OpenAI: GPT-5 Mini", "gpt-5-mini", listOf("openai/gpt-5-mini", "openai/gpt-5-mini-2025-08-07", "gpt-5-mini")),
    AliasDefinition("OpenAI: GPT-5 Nano", "gpt-5-nano", listOf("openai/gpt-5-nano", "openai/gpt-5-nano-2025-08-07", "gpt-5-nano")),
    AliasDefinition("OpenAI: GPT-5.1 Codex Mini", "gpt-5.1-codex-mini", listOf("openai/gpt-5.1-codex-mini", "openai/gpt-5.1-codex-mini-20251113", "gpt-5.1-codex-mini")),
  )

  private val legacyFallbacks = mapOf(
    "claude-mythos-5" to LegacyFallbackPricing(
      prompt = BigDecimal("0.00001"),
      completion = BigDecimal("0.00005"),
      cacheRead = BigDecimal("0.000001"),
      cacheWrite = BigDecimal("0.0000125"),
    ),
    "claude-3-5-haiku" to LegacyFallbackPricing(
      prompt = BigDecimal("0.0000008"),
      completion = BigDecimal("0.000004"),
      cacheRead = BigDecimal("0.00000008"),
      cacheWrite = BigDecimal("0.000001"),
    ),
  )

  fun embeddedSnapshot(): LiteLlmPricingCatalogSnapshot = parseCatalog(loadEmbeddedPricingJson())

  fun resolveCatalog(
    configDir: Path?,
    clock: Clock = Clock.systemUTC(),
    refreshMissingCache: Boolean = true,
    fetchPricingJson: () -> String,
  ): LiteLlmPricingCatalogSnapshot {
    val embedded = embeddedSnapshot()
    val resolvedConfigDir = configDir ?: return embedded
    val cachePath = cachePath(resolvedConfigDir)
    val cached = readCachedSnapshot(cachePath)
    if (cached == null && !refreshMissingCache) {
      return embedded
    }

    val now = Instant.now(clock)
    if (cached != null && snapshotIsFresh(cached, now)) {
      runCatching { parseCatalog(cached.body) }.getOrNull()?.let { return it }
    }

    val shouldRefresh = cached?.let { !snapshotIsFresh(it, now) } ?: true
    if (shouldRefresh) {
      runCatching(fetchPricingJson).getOrNull()?.let { body ->
        runCatching { parseCatalog(body) }.getOrNull()?.let { catalog ->
          runCatching {
            writeCachedSnapshot(cachePath, LiteLlmCachedPricingSnapshot(fetchedAt = now.toString(), body = body))
          }
          return catalog
        }
      }
    }

    if (cached != null) {
      runCatching { parseCatalog(cached.body) }.getOrNull()?.let { return it }
    }
    return embedded
  }

  fun matchEntry(modelName: String?, snapshot: LiteLlmPricingCatalogSnapshot): LiteLlmPricingEntry? {
    val resolvedModelName = modelName?.trim().orEmpty()
    if (resolvedModelName.isEmpty()) return null

    val normalized = normalizedModelAliases(resolvedModelName)
    if (normalized.isEmpty()) return null

    normalized.firstNotNullOfOrNull { alias -> snapshot.matchedAliasEntries[alias]?.let(snapshot.aliasEntries::get) }?.let { return it }
    findEntry(snapshot.aliasEntries, resolvedModelName)?.let { return it }
    normalized.firstNotNullOfOrNull { alias -> snapshot.matchedDirectEntries[alias]?.let(snapshot.directEntries::get) }?.let { return it }
    return findEntry(snapshot.directEntries, resolvedModelName)
  }

  fun primaryAlias(entry: LiteLlmPricingEntry): String? = entry.aliases.firstOrNull()

  fun calculateCost(entry: LiteLlmPricingEntry, usage: AgentSessionUsageSnapshot): BigDecimal {
    var total = BigDecimal.ZERO
    total += usage.inputTokens.costContribution(entry.promptTokenPriceUsd)
    total += usage.outputTokens.costContribution(entry.completionTokenPriceUsd)
    total += usage.cacheReadTokens.costContribution(entry.cacheReadTokenPriceUsd)
    total += if (usage.cacheWrite5mTokens > 0 || usage.cacheWrite1hTokens > 0) {
      usage.cacheWrite5mTokens.costContribution(entry.cacheWriteTokenPriceUsd) +
      usage.cacheWrite1hTokens.costContribution(entry.promptTokenPriceUsd.multiply(CACHE_WRITE_1H_MULTIPLIER))
    }
    else {
      usage.cacheWriteTokens.costContribution(entry.cacheWriteTokenPriceUsd)
    }
    return total
  }

  fun parseCatalog(jsonText: String): LiteLlmPricingCatalogSnapshot {
    val parsedModels = LinkedHashMap<String, ParsedPricing>()

    jsonFactory.createParser(ObjectReadContext.empty(), jsonText).use { parser ->
      if (parser.nextToken() != JsonToken.START_OBJECT) {
        error("Pricing JSON did not contain any relevant GPT/Claude entries")
      }
      while (parser.nextToken() != JsonToken.END_OBJECT) {
        val model = parser.currentName()
        parser.nextToken()
        if (!isRelevantModelKey(model)) {
          parser.skipChildren()
          continue
        }
        parsePricingObject(parser)?.let { parsedModels[model] = it }
      }
    }

    require(parsedModels.isNotEmpty()) { "Pricing JSON did not contain any relevant GPT/Claude entries" }

    val aliasEntries = aliasDefinitions.mapNotNull { definition ->
      val pricing = findParsedModel(parsedModels, definition.target) ?: legacyFallbackPricing(definition.target) ?: return@mapNotNull null
      LiteLlmPricingEntry(
        displayName = definition.displayName,
        aliases = definition.aliases,
        promptTokenPriceUsd = pricing.input,
        completionTokenPriceUsd = pricing.output,
        cacheReadTokenPriceUsd = pricing.cacheRead,
        cacheWriteTokenPriceUsd = pricing.cacheCreate,
      )
    }
    val directEntries = parsedModels.map { (model, pricing) ->
      LiteLlmPricingEntry(
        displayName = model,
        aliases = listOf(model),
        promptTokenPriceUsd = pricing.input,
        completionTokenPriceUsd = pricing.output,
        cacheReadTokenPriceUsd = pricing.cacheRead,
        cacheWriteTokenPriceUsd = pricing.cacheCreate,
      )
    }

    return LiteLlmPricingCatalogSnapshot(
      aliasEntries = aliasEntries,
      directEntries = directEntries,
      matchedAliasEntries = exactEntryIndex(aliasEntries),
      matchedDirectEntries = exactEntryIndex(directEntries),
    )
  }

  private fun exactEntryIndex(entries: List<LiteLlmPricingEntry>): Map<String, Int> {
    val candidates = LinkedHashMap<String, Int?>()
    entries.forEachIndexed { index, entry ->
      normalizedAliases(entry).forEach { alias ->
        candidates[alias] = when (candidates[alias]) {
          null -> index
          index -> index
          else -> null
        }
      }
    }
    val result = LinkedHashMap<String, Int>()
    for ((alias, index) in candidates) {
      if (index != null) {
        result[alias] = index
      }
    }
    return result
  }

  private fun findEntry(entries: List<LiteLlmPricingEntry>, modelName: String): LiteLlmPricingEntry? {
    val normalized = normalizedModelAliases(modelName)
    if (normalized.isEmpty()) return null

    val exactMatches = entries.filter { entry -> normalizedAliases(entry).any(normalized::contains) }
    if (exactMatches.size == 1) {
      return exactMatches.single()
    }

    val prefixMatches = entries.filter { entry ->
      normalizedAliases(entry).any { candidate -> normalized.any { query -> candidate.startsWith(query) || query.startsWith(candidate) } }
    }
    return prefixMatches.singleOrNull()
  }

  private fun normalizedModelAliases(modelName: String): List<String> {
    val trimmed = modelName.trim()
    if (trimmed.isEmpty()) return emptyList()

    val slashParts = trimmed.split('/').filter(String::isNotEmpty)
    val candidates = ArrayList<String>(1 + slashParts.size)
    candidates += trimmed
    for (index in slashParts.indices) {
      candidates += slashParts.subList(index, slashParts.size).joinToString("/")
    }
    return candidates.map(::normalizeModelName).filter(String::isNotEmpty).distinct()
  }

  private fun normalizeModelName(value: String): String {
    val builder = StringBuilder(value.length)
    var lastSeparator = false
    value.trim().lowercase().forEach { ch ->
      when {
        ch.isLetterOrDigit() -> {
          builder.append(ch)
          lastSeparator = false
        }
        !lastSeparator -> {
          builder.append('-')
          lastSeparator = true
        }
      }
    }
    return builder.toString().trim('-')
  }

  private fun findParsedModel(parsedModels: Map<String, ParsedPricing>, target: String): ParsedPricing? {
    parsedModels[target]?.let { return it }
    val normalizedTarget = normalizeModelName(target)
    return parsedModels.entries.firstNotNullOfOrNull { (model, pricing) ->
      val normalized = normalizeModelName(model)
      pricing.takeIf { normalized == normalizedTarget || normalized.startsWith(normalizedTarget) || normalizedTarget.startsWith(normalized) }
    }
  }

  private fun legacyFallbackPricing(target: String): ParsedPricing? {
    val pricing = legacyFallbacks[target] ?: return null
    return ParsedPricing(
      input = pricing.prompt,
      output = pricing.completion,
      cacheCreate = pricing.cacheWrite,
      cacheRead = pricing.cacheRead,
    )
  }

  private fun parsePricingObject(parser: JsonParser): ParsedPricing? {
    if (parser.currentToken() != JsonToken.START_OBJECT) {
      parser.skipChildren()
      return null
    }

    var compactInput: BigDecimal? = null
    var compactOutput: BigDecimal? = null
    var compactCacheCreate: BigDecimal? = null
    var compactCacheRead: BigDecimal? = null
    var input: BigDecimal? = null
    var output: BigDecimal? = null
    var cacheCreate: BigDecimal? = null
    var cacheRead: BigDecimal? = null

    while (parser.nextToken() != JsonToken.END_OBJECT) {
      val fieldName = parser.currentName()
      parser.nextToken()
      when (fieldName) {
        "i" -> compactInput = parser.readDecimalOrNull()
        "o" -> compactOutput = parser.readDecimalOrNull()
        "cc" -> compactCacheCreate = parser.readDecimalOrNull()
        "cr" -> compactCacheRead = parser.readDecimalOrNull()
        "input_cost_per_token" -> input = parser.readDecimalOrNull()
        "output_cost_per_token" -> output = parser.readDecimalOrNull()
        "cache_creation_input_token_cost" -> cacheCreate = parser.readDecimalOrNull()
        "cache_read_input_token_cost" -> cacheRead = parser.readDecimalOrNull()
        else -> parser.skipChildren()
      }
    }

    if (compactInput != null && compactOutput != null) {
      return ParsedPricing(
        input = compactInput,
        output = compactOutput,
        cacheCreate = compactCacheCreate ?: compactInput.multiply(CACHE_CREATE_MULTIPLIER),
        cacheRead = compactCacheRead ?: compactInput.multiply(CACHE_READ_MULTIPLIER),
      )
    }
    if (input != null && output != null) {
      return ParsedPricing(
        input = input,
        output = output,
        cacheCreate = cacheCreate ?: input.multiply(CACHE_CREATE_MULTIPLIER),
        cacheRead = cacheRead ?: input.multiply(CACHE_READ_MULTIPLIER),
      )
    }
    return null
  }

  fun isRelevantModelKey(model: String): Boolean {
    return listOf(
      "gpt-",
      "openai/",
      "claude-",
      "anthropic.",
      "anthropic/",
      "global.anthropic.",
      "us.anthropic.",
      "eu.anthropic.",
      "au.anthropic.",
      "jp.anthropic.",
    ).any(model::startsWith)
  }

  fun cachePath(configDir: Path): Path = configDir.resolve("session_costs_pricing").resolve(ONLINE_CACHE_FILE_NAME)

  fun readCachedSnapshot(path: Path): LiteLlmCachedPricingSnapshot? {
    val text = runCatching { EelFiles.readString(path) }.getOrNull() ?: return null
    var fetchedAt: String? = null
    var body: String? = null

    jsonFactory.createParser(ObjectReadContext.empty(), text).use { parser ->
      if (parser.nextToken() != JsonToken.START_OBJECT) {
        return null
      }
      while (parser.nextToken() != JsonToken.END_OBJECT) {
        val fieldName = parser.currentName()
        parser.nextToken()
        when (fieldName) {
          "fetched_at" -> fetchedAt = parser.readStringOrNull()
          "body" -> body = parser.readStringOrNull()
          else -> parser.skipChildren()
        }
      }
    }

    val resolvedFetchedAt = fetchedAt ?: return null
    val resolvedBody = body ?: return null
    return LiteLlmCachedPricingSnapshot(fetchedAt = resolvedFetchedAt, body = resolvedBody)
  }

  fun writeCachedSnapshot(cachePath: Path, snapshot: LiteLlmCachedPricingSnapshot) {
    cachePath.parent?.let(Files::createDirectories)
    val payload = buildString(snapshot.body.length + snapshot.fetchedAt.length + 32) {
      append('{')
      append("\"fetched_at\":\"")
      append(snapshot.fetchedAt.escapeJson())
      append("\",\"body\":\"")
      append(snapshot.body.escapeJson())
      append("\"}")
    }
    Files.writeString(cachePath, payload)
  }

  fun snapshotIsFresh(snapshot: LiteLlmCachedPricingSnapshot, now: Instant): Boolean {
    val fetchedAt = runCatching { Instant.parse(snapshot.fetchedAt) }.getOrNull() ?: return false
    return Duration.between(fetchedAt, now) < ONLINE_CACHE_TTL
  }

  private fun normalizedAliases(entry: LiteLlmPricingEntry): List<String> {
    return entry.aliases.flatMap(::normalizedModelAliases)
  }

  private fun loadEmbeddedPricingJson(): String {
    return checkNotNull(LiteLlmPriceCatalog::class.java.classLoader.getResource(EMBEDDED_LITELLM_PRICING_RESOURCE)) {
      "Missing resource: $EMBEDDED_LITELLM_PRICING_RESOURCE"
    }.readText()
  }

  private fun Long.costContribution(pricePerTokenUsd: BigDecimal): BigDecimal {
    if (this == 0L) return BigDecimal.ZERO
    return pricePerTokenUsd.multiply(BigDecimal.valueOf(this))
  }
}

private fun JsonParser.readDecimalOrNull(): BigDecimal? {
  return when (currentToken()) {
    JsonToken.VALUE_STRING -> string?.toBigDecimalOrNull()
    JsonToken.VALUE_NUMBER_FLOAT,
    JsonToken.VALUE_NUMBER_INT,
      -> numberValue?.toString()?.toBigDecimalOrNull()
    JsonToken.VALUE_NULL -> null
    else -> {
      skipChildren()
      null
    }
  }
}

private fun JsonParser.readStringOrNull(): String? {
  return when (currentToken()) {
    JsonToken.VALUE_STRING -> string
    JsonToken.VALUE_NUMBER_FLOAT,
    JsonToken.VALUE_NUMBER_INT,
      -> numberValue?.toString()
    JsonToken.VALUE_NULL -> null
    else -> {
      skipChildren()
      null
    }
  }
}

private fun String.escapeJson(): String {
  val builder = StringBuilder(length + 16)
  for (ch in this) {
    when (ch) {
      '\\' -> builder.append("\\\\")
      '"' -> builder.append("\\\"")
      '\b' -> builder.append("\\b")
      '\u000C' -> builder.append("\\f")
      '\n' -> builder.append("\\n")
      '\r' -> builder.append("\\r")
      '\t' -> builder.append("\\t")
      else -> {
        if (ch.code < 0x20) {
          builder.append("\\u")
          builder.append(ch.code.toString(16).padStart(4, '0'))
        }
        else {
          builder.append(ch)
        }
      }
    }
  }
  return builder.toString()
}

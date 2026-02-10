// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.emmetLikeTemplates

import com.intellij.codeInsight.template.CustomTemplateCallback
import com.intellij.compose.ide.plugin.resources.ResourceType
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlTag

/**
 * Base class for all Compose Resource live template types.
 *
 * Supported syntaxes:
 *
 * [StringTemplateType]&#58;  * `[prefix][name]{[value]}[*multiplier]`
 *
 * [StringArrayType]&#58;  * `[prefix][name][>count]{[value]}[*multiplier]`
 *
 * [PluralsType]&#58;  * `[prefix][name][:mode]{[value]}[*multiplier]`
 *
 * [ItemType]&#58;  * `(item|i){[value]}[*multiplier]`
 *
 * Positional format specifiers:
 * `NUMBER[TYPE]` (e.g. `1` -> `%1$s`)
 */
internal sealed class TemplateType {

  abstract fun matchesKey(parsedKey: String): Boolean

  abstract fun makeTemplateText(parsed: ParsedKey, callback: CustomTemplateCallback, context: TemplateBuilder.Context): String

  companion object {
    fun fromParsedKey(parsedKey: String): TemplateType = ALL_TEMPLATE_TYPE_TYPES.first { it.matchesKey(parsedKey) }
  }
}

internal val ALL_TEMPLATE_TYPE_TYPES = arrayOf(StringArrayType, PluralsType, ItemType, /* fallback */ StringTemplateType)

/**
 * Generates `<string>` resource template.
 *
 * Prefixes: `string.`, `s.`, `.` or none.
 * Format: `[prefix.][name]{[value]}[*multiplier]`
 * Example: `greeting{Hello}` -> `<string name="greeting">Hello</string>`
 */
internal data object StringTemplateType : TemplateType() {
  override fun matchesKey(parsedKey: String): Boolean = true

  override fun makeTemplateText(parsed: ParsedKey, callback: CustomTemplateCallback, context: TemplateBuilder.Context): String {
    val normalizedKey = parsed.parsedKey.normalizeTemplateKey(TemplateKeyNormalizationContext.STRING)
    val name = normalizedKey.asNameAttributeValue()
    return context.makeStringTemplate(name, parsed.text, parsed.repetitions)
  }
}

/**
 * Generates `<string-array>` resource template.
 *
 * Trigger: `>` or prefixes `string-array.`, `sa.`.
 * Format: `[prefix.][name][>count]{[value]}[*multiplier]`
 * Example: `fruits>2` -> `<string-array name="fruits"><item></item><item></item></string-array>`
 */
internal data object StringArrayType : TemplateType() {
  override fun matchesKey(parsedKey: String) = parsedKey.contains(">")
                                               || TemplateKeyNormalizationContext.STRING_ARRAY.matches(parsedKey)

  override fun makeTemplateText(parsed: ParsedKey, callback: CustomTemplateCallback, context: TemplateBuilder.Context): String {
    val normalizedKey = parsed.parsedKey.normalizeTemplateKey(TemplateKeyNormalizationContext.STRING_ARRAY)
    val nameCandidate = normalizedKey.substringBefore('>')
    val name =
      if (TemplateKeyNormalizationContext.STRING_ARRAY.blankAliases.contains(nameCandidate)) "" else nameCandidate.asNameAttributeValue()
    val requestedItemCount = normalizedKey.substringAfter('>').toIntOrNull() ?: 1
    val itemCount = minOf(requestedItemCount, MAX_CHILDREN)
    return context.makeStringArrayTemplate(name, itemCount, parsed.text, parsed.repetitions)
  }
}

/**
 * Generates `<plurals>` resource template.
 *
 * Trigger: `:` or prefixes `plurals.`, `p.`.
 * Format: `[prefix][name][:mode]{[value]}[*multiplier]`
 * Modes: `:c` (Current locale - default), `:a` (All categories)
 * Example: `apples:a` -> `<plurals name="apples">...all items...</plurals>`
 */
internal data object PluralsType : TemplateType() {
  override fun matchesKey(parsedKey: String) = parsedKey.contains(":")
                                               || TemplateKeyNormalizationContext.PLURALS.matches(parsedKey)

  override fun makeTemplateText(parsed: ParsedKey, callback: CustomTemplateCallback, context: TemplateBuilder.Context): String {
    val normalizedKey = parsed.parsedKey.normalizeTemplateKey(TemplateKeyNormalizationContext.PLURALS)
    val nameCandidate = normalizedKey.substringBefore(':')
    val pluralName =
      if (TemplateKeyNormalizationContext.PLURALS.blankAliases.contains(nameCandidate)) "" else nameCandidate.asNameAttributeValue()
    val quantity = normalizedKey.substringAfter(':', "c").ifBlank { "c" }
    val qualifier = callback.file.parent?.name?.substringAfterLast('-', "") ?: ""
    return context.makePluralsTemplate(pluralName, quantity, parsed.text, parsed.repetitions, qualifier)
  }
}

/**
 * Generates `<item>` tags, context-aware for `<plurals>`.
 *
 * Trigger: `i` or `item`.
 * Format: `(item|i){[value]}[*multiplier]`
 * Example: `i{Item}*2` -> `<item>Item</item><item>Item</item>`
 */
internal data object ItemType : TemplateType() {
  override fun matchesKey(parsedKey: String) = parsedKey == "item" || parsedKey == "i"

  override fun makeTemplateText(parsed: ParsedKey, callback: CustomTemplateCallback, context: TemplateBuilder.Context) =
    context.makeItemTemplate(isInsidePlurals(callback), parsed.text, parsed.repetitions)

  private fun isInsidePlurals(callback: CustomTemplateCallback): Boolean {
    val offset = callback.editor.caretModel.offset
    val element = callback.file.findElementAt(offset) ?: return false
    val parentTag = element.parentOfType<XmlTag>() ?: return false
    return parentTag.name == ResourceType.PLURAL_STRING.typeName
  }
}

internal fun String.asNameAttributeValue(): String =
  replace('.', '_').replace('-', '_')
    .let { if (it.isNotEmpty() && it.first().isDigit()) "_$it" else it }


private enum class TemplateKeyNormalizationContext(val blankAliases: Set<String>, val prefixesToRemove: List<String>) {
  STRING(setOf(ResourceType.STRING.typeName, "s"), listOf("${ResourceType.STRING.typeName}.", "s.", ".")),
  STRING_ARRAY(setOf("sa", ResourceType.STRING_ARRAY.typeName), listOf("${ResourceType.STRING_ARRAY.typeName}.", "sa.")),
  PLURALS(setOf("p", ResourceType.PLURAL_STRING.typeName), listOf("${ResourceType.PLURAL_STRING.typeName}.", "p."));

  fun matches(parsedKey: String): Boolean = parsedKey in blankAliases || prefixesToRemove.any { parsedKey.startsWith(it) }
}

/**
 * Normalizes a template key before further parsing.
 *
 * Rules are applied in this order:
 * 1. If this value exactly matches one of [blankAliases], returns an empty string.
 * 2. Otherwise, removes the first matching prefix from [prefixesToRemove].
 * 3. If nothing matches, returns this string unchanged.
 *
 * Prefix priority is defined by the order of [prefixesToRemove].
 */
private fun String.normalizeTemplateKey(context: TemplateKeyNormalizationContext): String {
  if (this in context.blankAliases) return ""

  return context.prefixesToRemove.firstOrNull { prefix -> startsWith(prefix) }?.let { prefix -> this.removePrefix(prefix) } ?: this
}
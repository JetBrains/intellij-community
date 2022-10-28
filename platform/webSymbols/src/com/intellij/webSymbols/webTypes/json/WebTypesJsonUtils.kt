// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.webTypes.json

import com.intellij.webSymbols.*
import com.intellij.webSymbols.WebSymbol.Companion.KIND_CSS_CLASSES
import com.intellij.webSymbols.WebSymbol.Companion.KIND_CSS_FUNCTIONS
import com.intellij.webSymbols.WebSymbol.Companion.KIND_CSS_PROPERTIES
import com.intellij.webSymbols.WebSymbol.Companion.KIND_CSS_PSEUDO_CLASSES
import com.intellij.webSymbols.WebSymbol.Companion.KIND_CSS_PSEUDO_ELEMENTS
import com.intellij.webSymbols.WebSymbol.Companion.KIND_HTML_ATTRIBUTES
import com.intellij.webSymbols.WebSymbol.Companion.KIND_HTML_ELEMENTS
import com.intellij.webSymbols.WebSymbol.Companion.KIND_JS_EVENTS
import com.intellij.webSymbols.WebSymbol.Companion.KIND_JS_PROPERTIES
import com.intellij.webSymbols.WebSymbol.Companion.NAMESPACE_CSS
import com.intellij.webSymbols.WebSymbol.Companion.NAMESPACE_HTML
import com.intellij.webSymbols.WebSymbol.Companion.NAMESPACE_JS
import com.intellij.webSymbols.WebSymbol.Companion.PROP_ARGUMENTS
import com.intellij.webSymbols.WebSymbol.Companion.PROP_DOC_HIDE_PATTERN
import com.intellij.webSymbols.WebSymbol.Companion.PROP_HIDE_FROM_COMPLETION
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.context.WebSymbolsContext
import com.intellij.webSymbols.context.WebSymbolsContextKindRules
import com.intellij.webSymbols.html.WebSymbolHtmlAttributeValue
import com.intellij.webSymbols.query.WebSymbolNameConversionRules
import com.intellij.webSymbols.query.WebSymbolNameConverter
import com.intellij.webSymbols.query.WebSymbolsQueryExecutor
import com.intellij.webSymbols.query.impl.WebSymbolsQueryExecutorImpl.Companion.asSymbolNamespace
import com.intellij.webSymbols.query.impl.WebSymbolsQueryExecutorImpl.Companion.parsePath
import com.intellij.webSymbols.utils.NameCaseUtils
import com.intellij.webSymbols.webTypes.WebTypesSymbolTypeSupport
import com.intellij.webSymbols.webTypes.filters.WebSymbolsFilter
import com.intellij.webSymbols.webTypes.json.NameConversionRulesSingle.NameConverter
import java.util.*
import java.util.function.Function

private fun namespaceOf(host: GenericContributionsHost): SymbolNamespace =
  when (host) {
    is HtmlContributionsHost -> NAMESPACE_HTML
    is CssContributionsHost -> NAMESPACE_CSS
    is JsContributionsHost -> NAMESPACE_JS
    else -> throw IllegalArgumentException(host.toString())
  }

internal fun Contributions.getAllContributions(framework: FrameworkId?): Sequence<Triple<SymbolNamespace, SymbolKind, List<BaseContribution>>> =
  sequenceOf(css, js, html)
    .filter { it != null }
    .flatMap { host -> host.collectDirectContributions(framework).mapWith(namespaceOf(host)) }

internal fun GenericContributionsHost.getAllContributions(framework: FrameworkId?): Sequence<Triple<SymbolNamespace, SymbolKind, List<BaseContribution>>> =
  if (this is BaseContribution)
    sequenceOf(this, css, js, html)
      .filter { it != null }
      .flatMap { host -> host.collectDirectContributions(framework).mapWith(namespaceOf(host)) }
  else
    this.collectDirectContributions(framework).mapWith(namespaceOf(this))

private fun Sequence<Pair<SymbolKind, List<BaseContribution>>>.mapWith(namespace: SymbolNamespace): Sequence<Triple<SymbolNamespace, SymbolKind, List<BaseContribution>>> =
  map {
    if (namespace == NAMESPACE_HTML && it.first == KIND_JS_EVENTS)
      Triple(NAMESPACE_JS, KIND_JS_EVENTS, it.second)
    else Triple(namespace, it.first, it.second)
  }

internal const val KIND_HTML_VUE_LEGACY_COMPONENTS = "\$vue-legacy-components\$"

internal const val VUE_DIRECTIVE_PREFIX = "v-"
internal const val VUE_FRAMEWORK = "vue"
internal const val KIND_HTML_VUE_COMPONENTS = "vue-components"
internal const val KIND_HTML_VUE_COMPONENT_PROPS = "props"
internal const val KIND_HTML_VUE_DIRECTIVES = "vue-directives"
internal const val KIND_HTML_VUE_DIRECTIVE_ARGUMENT = "argument"
internal const val KIND_HTML_VUE_DIRECTIVE_MODIFIERS = "modifiers"

private fun GenericContributionsHost.collectDirectContributions(framework: FrameworkId?): Sequence<Pair<SymbolKind, List<BaseContribution>>> =
  (when (this) {
    is HtmlContributionsHost -> sequenceOf(
      Pair(KIND_HTML_ATTRIBUTES, this.attributes),
      Pair(KIND_HTML_ELEMENTS, this.elements),
      Pair(KIND_JS_EVENTS, this.events)
    ).plus(
      when (this) {
        is Html -> sequenceOf(
          Pair(if (framework == VUE_FRAMEWORK) KIND_HTML_VUE_LEGACY_COMPONENTS else KIND_HTML_ELEMENTS, this.tags)
        )
        is HtmlElement -> sequenceOf(
          Pair(KIND_JS_EVENTS, this.events)
        )
        is HtmlAttribute -> if (this.name.startsWith(VUE_DIRECTIVE_PREFIX) && !this.name.contains(
            ' ') && framework == VUE_FRAMEWORK) {
          sequenceOf(
            Pair(KIND_HTML_VUE_DIRECTIVE_ARGUMENT, this.vueArgument?.toHtmlContribution()?.let { listOf(it) }
                                                   ?: listOf(matchAllHtmlContribution("Vue directive argument"))),
            Pair(KIND_HTML_VUE_DIRECTIVE_MODIFIERS, this.vueModifiers.takeIf { it.isNotEmpty() }?.map { it.toHtmlContribution() }
                                                    ?: listOf(matchAllHtmlContribution("Vue directive modifier")))
          )
        }
        else emptySequence()
        else -> emptySequence()
      }
    )
    is CssContributionsHost -> sequenceOf(
      Pair(KIND_CSS_CLASSES, this.classes),
      Pair(KIND_CSS_FUNCTIONS, this.functions),
      Pair(KIND_CSS_PROPERTIES, this.properties),
      Pair(KIND_CSS_PSEUDO_CLASSES, this.pseudoClasses),
      Pair(KIND_CSS_PSEUDO_ELEMENTS, this.pseudoElements)
    )
    is JsContributionsHost -> sequenceOf(
      Pair(KIND_JS_EVENTS, this.events),
      Pair(KIND_JS_PROPERTIES, this.properties)
    )
    else -> emptySequence()
  })
    .plus(this.additionalProperties.asSequence()
            .map { (name, list) -> Pair(name, list.mapNotNull { it.value as? GenericContribution }) }
            .filter { it.second.isNotEmpty() })

internal val GenericContributionsHost.genericContributions: Map<String, List<GenericContribution>>
  get() =
    this.additionalProperties.asSequence()
      .map { (name, list) -> Pair(name, list.mapNotNull { it.value as? GenericContribution }) }
      .filter { it.second.isNotEmpty() }
      .toMap()

internal val GenericContributionsHost.genericProperties: Map<String, Any>
  get() =
    this.additionalProperties.asSequence()
      .map { (name, list) -> Pair(name, list?.mapNotNull { prop -> prop.value.takeIf { it !is GenericContribution } } ?: emptyList()) }
      .mapNotNull {
        when (it.second.size) {
          0 -> null
          1 -> Pair(it.first, it.second[0])
          else -> it
        }
      }
      .plus(
        when (this) {
          is CssPseudoClass -> sequenceOf(Pair(PROP_ARGUMENTS, this.arguments ?: false))
          is CssPseudoElement -> sequenceOf(Pair(PROP_ARGUMENTS, this.arguments ?: false))
          else -> emptySequence()
        }
      )
      .toMap()

internal fun Reference.getSymbolKind(context: WebSymbol?): WebSymbolQualifiedKind? =
  when (val reference = this.value) {
    is String -> reference
    is ReferenceWithProps -> reference.path
    else -> null
  }
    .let { parsePath(it, context?.namespace) }
    .lastOrNull()
    ?.let {
      if (it.namespace != null)
        WebSymbolQualifiedKind(it.namespace, it.kind)
      else null
    }

internal fun Reference.resolve(name: String?,
                               scope: List<WebSymbolsScope>,
                               queryExecutor: WebSymbolsQueryExecutor,
                               virtualSymbols: Boolean = true,
                               abstractSymbols: Boolean = false): List<WebSymbol> {
  if (name != null && name.isEmpty())
    return emptyList()
  return when (val reference = this.value) {
    is String -> queryExecutor.runNameMatchQuery(
      reference + if (name != null) "/$name" else "",
      virtualSymbols, abstractSymbols, scope = scope)
    is ReferenceWithProps -> {
      val nameConversionRules = reference.createNameConversionRules()
      val matches = queryExecutor.withNameConversionRules(nameConversionRules).runNameMatchQuery(
        (reference.path ?: return emptyList()) + if (name != null) "/$name" else "",
        reference.includeVirtual ?: virtualSymbols,
        reference.includeAbstract ?: abstractSymbols,
        scope = scope)
      if (reference.filter == null) return matches
      val properties = reference.additionalProperties.toMap()
      WebSymbolsFilter.get(reference.filter)
        .filterNameMatches(matches, queryExecutor, scope, properties)
    }
    else -> throw IllegalArgumentException(reference::class.java.name)
  }
}

internal fun Reference.codeCompletion(name: String,
                                      scope: List<WebSymbolsScope>,
                                      queryExecutor: WebSymbolsQueryExecutor,
                                      position: Int = 0,
                                      virtualSymbols: Boolean = true): List<WebSymbolCodeCompletionItem> {
  return when (val reference = this.value) {
    is String -> queryExecutor.runCodeCompletionQuery("$reference/$name", position, virtualSymbols, scope)
    is ReferenceWithProps -> {
      val nameConversionRules = reference.createNameConversionRules()
      val codeCompletions = queryExecutor.withNameConversionRules(nameConversionRules).runCodeCompletionQuery(
        (reference.path ?: return emptyList()) + "/$name", position,
        reference.includeVirtual ?: virtualSymbols,
        scope)
      if (reference.filter == null) return codeCompletions
      val properties = reference.additionalProperties.toMap()
      WebSymbolsFilter.get(reference.filter)
        .filterCodeCompletions(codeCompletions, queryExecutor, scope, properties)
    }
    else -> throw IllegalArgumentException(reference::class.java.name)
  }
}

internal fun EnablementRules.wrap(): WebSymbolsContextKindRules.EnablementRules =
  WebSymbolsContextKindRules.EnablementRules(
    nodePackages,
    fileExtensions,
    ideLibraries,
    fileNamePatterns.mapNotNull { it.toRegex() },
    scriptUrlPatterns.mapNotNull { it.toRegex() }
  )

internal fun DisablementRules.wrap(): WebSymbolsContextKindRules.DisablementRules =
  WebSymbolsContextKindRules.DisablementRules(
    fileExtensions,
    fileNamePatterns.mapNotNull { it.toRegex() },
  )

internal fun BaseContribution.Priority.wrap() =
  WebSymbol.Priority.values()[ordinal]

internal val BaseContribution.attributeValue: HtmlAttributeValue?
  get() =
    (this as? GenericContribution)?.attributeValue
    ?: (this as? HtmlAttribute)?.value

internal val BaseContribution.type: List<Type>?
  get() =
    (this as? TypedContribution)?.type?.takeIf { it.isNotEmpty() }

internal fun DeprecatedHtmlAttributeVueArgument.toHtmlContribution(): BaseContribution {
  val result = GenericHtmlContribution()
  result.name = "Vue directive argument"
  result.description = this.description
  result.docUrl = this.docUrl
  result.pattern = this.pattern
  if (pattern.isMatchAllRegex)
    result.additionalProperties[PROP_DOC_HIDE_PATTERN] = true.toGenericHtmlPropertyValue()
  return result
}

internal fun DeprecatedHtmlAttributeVueModifier.toHtmlContribution(): BaseContribution {
  val result = GenericHtmlContribution()
  result.name = this.name
  result.description = this.description
  result.docUrl = this.docUrl
  result.pattern = this.pattern
  if (pattern.isMatchAllRegex)
    result.additionalProperties[PROP_DOC_HIDE_PATTERN] = true.toGenericHtmlPropertyValue()
  return result
}

internal fun Pattern.toRegex(): Regex? =
  when (val pattern = value) {
    is String -> Regex(pattern, RegexOption.IGNORE_CASE)
    is PatternObject -> if (pattern.caseSensitive == true)
      Regex(pattern.regex)
    else
      Regex(pattern.regex, RegexOption.IGNORE_CASE)
    else -> null
  }

internal val WebTypes.jsTypesSyntaxWithLegacy: WebTypes.JsTypesSyntax?
  get() =
    jsTypesSyntax
    ?: contributions?.html?.typesSyntax
      ?.let { it as? String }
      ?.let {
        try {
          WebTypes.JsTypesSyntax.fromValue(it)
        }
        catch (e: IllegalArgumentException) {
          null
        }
      }

internal val WebTypes.descriptionMarkupWithLegacy: WebTypes.DescriptionMarkup?
  get() =
    descriptionMarkup?.takeIf { it != WebTypes.DescriptionMarkup.NONE }
    ?: contributions?.html?.descriptionMarkup
      ?.let { it as? String }
      ?.let {
        try {
          WebTypes.DescriptionMarkup.fromValue(it)
        }
        catch (e: IllegalArgumentException) {
          null
        }
      }

internal fun HtmlValueType.wrap(): WebSymbolHtmlAttributeValue.Type? =
  when (this.value) {
    "enum" -> WebSymbolHtmlAttributeValue.Type.ENUM
    "of-match" -> WebSymbolHtmlAttributeValue.Type.OF_MATCH
    "string" -> WebSymbolHtmlAttributeValue.Type.STRING
    "boolean" -> WebSymbolHtmlAttributeValue.Type.BOOLEAN
    "number" -> WebSymbolHtmlAttributeValue.Type.NUMBER
    null -> null
    else -> WebSymbolHtmlAttributeValue.Type.COMPLEX
  }

internal fun HtmlValueType.toLangType(): List<Type>? =
  when (val typeValue = this.value) {
    null, "enum", "of-match" -> null
    is List<*> -> typeValue.filterIsInstance<Type>()
    is TypeReference, is String -> listOf(
      Type().also { it.value = typeValue })
    else -> null
  }

internal fun HtmlAttributeValue.Kind.wrap(): WebSymbolHtmlAttributeValue.Kind =
  when (this) {
    HtmlAttributeValue.Kind.NO_VALUE -> WebSymbolHtmlAttributeValue.Kind.NO_VALUE
    HtmlAttributeValue.Kind.PLAIN -> WebSymbolHtmlAttributeValue.Kind.PLAIN
    HtmlAttributeValue.Kind.EXPRESSION -> WebSymbolHtmlAttributeValue.Kind.EXPRESSION
  }

internal fun GenericHtmlContribution.copyLegacyFrom(other: BaseContribution) {
  name = other.name
  pattern = other.pattern
  description = other.description
  docUrl = other.docUrl
  source = other.source
  deprecated = other.deprecated
  if (other is GenericContribution) {
    default = other.default
    required = other.required
  }
  additionalProperties.putAll(this.additionalProperties)
}

private fun matchAllHtmlContribution(name: String): GenericContribution =
  GenericHtmlContribution().also { contribution ->
    contribution.name = name
    contribution.pattern = NamePatternRoot().also {
      it.value = ".*"
    }
    contribution.additionalProperties[PROP_DOC_HIDE_PATTERN] = true.toGenericHtmlPropertyValue()
    contribution.additionalProperties[PROP_HIDE_FROM_COMPLETION] = true.toGenericHtmlPropertyValue()
  }

private val NamePatternRoot?.isMatchAllRegex
  get() = this != null && value.let { it is String && (it == ".*" || it == ".+") }

private fun Any.toGenericHtmlPropertyValue(): GenericHtmlContributions =
  GenericHtmlContributions().also { list ->
    list.add(GenericHtmlContributionOrProperty().also { it.value = this })
  }

private fun ReferenceWithProps.createNameConversionRules(): List<WebSymbolNameConversionRules> {
  val rules = nameConversion ?: return emptyList()
  val lastPath = parsePath(path).lastOrNull()
  if (lastPath?.namespace == null)
    return emptyList()

  val builder = WebSymbolNameConversionRules.builder()

  fun buildConvertersMap(value: Any?, addToBuilder: (WebSymbolQualifiedKind, WebSymbolNameConverter) -> Unit) {
    when (value) {
      is NameConverter -> mergeConverters(listOf(value))?.let {
        addToBuilder(WebSymbolQualifiedKind(lastPath.namespace, lastPath.kind), it)
      }
      is List<*> -> mergeConverters(value.filterIsInstance<NameConverter>())?.let {
        addToBuilder(WebSymbolQualifiedKind(lastPath.namespace, lastPath.kind), it)
      }
      is NameConversionRulesSingle -> buildNameConverters(value.additionalProperties, { mergeConverters(listOf(it)) }, addToBuilder)
      is NameConversionRulesMultiple -> buildNameConverters(value.additionalProperties, { mergeConverters(it) }, addToBuilder)
      else -> throw IllegalArgumentException(value?.toString())
    }
  }

  buildConvertersMap(rules.canonicalNames?.value, builder::addCanonicalNamesRule)
  buildConvertersMap(rules.matchNames?.value, builder::addMatchNamesRule)
  buildConvertersMap(rules.nameVariants?.value, builder::addNameVariantsRule)
  if (builder.isEmpty())
    return emptyList()
  else
    return listOf(builder.build())
}

private fun NameConverter.toFunction(): Function<String, String> =
  when (this) {
    NameConverter.AS_IS -> Function { it }
    NameConverter.LOWERCASE -> Function { it.lowercase(Locale.US) }
    NameConverter.UPPERCASE -> Function { it.uppercase(Locale.US) }
    NameConverter.PASCAL_CASE -> Function { NameCaseUtils.toPascalCase(it) }
    NameConverter.CAMEL_CASE -> Function { NameCaseUtils.toCamelCase(it) }
    NameConverter.KEBAB_CASE -> Function { NameCaseUtils.toKebabCase(it) }
    NameConverter.SNAKE_CASE -> Function { NameCaseUtils.toSnakeCase(it) }
  }

internal fun mergeConverters(converters: List<NameConverter>): WebSymbolNameConverter? {
  if (converters.isEmpty()) return null
  val all = converters.map { it.toFunction() }
  return WebSymbolNameConverter { name ->
    all.map { it.apply(name) }
  }
}

internal fun <T> buildNameConverters(map: Map<String, T>?,
                                     mapper: (T) -> (WebSymbolNameConverter?),
                                     addToBuilder: (WebSymbolQualifiedKind, WebSymbolNameConverter) -> Unit) {
  for ((key, value) in map?.entries ?: return) {
    val path = key.splitToSequence('/')
                 .filter { it.isNotEmpty() }
                 .toList().takeIf { it.size == 2 } ?: continue
    val namespace = path[0].asSymbolNamespace() ?: continue
    val symbolKind = path[1]
    val converter = mapper(value) ?: continue
    addToBuilder(WebSymbolQualifiedKind(namespace, symbolKind), converter)
  }
}

internal fun List<Type>.mapToTypeReferences(): List<WebTypesSymbolTypeSupport.TypeReference> =
  mapNotNull {
    when (val reference = it.value) {
      is String -> WebTypesSymbolTypeSupport.TypeReference(null, reference)
      is TypeReference -> if (reference.name != null)
        WebTypesSymbolTypeSupport.TypeReference(reference.module, reference.name)
      else null
      else -> null
    }
  }

internal fun ContextBase.evaluate(context: WebSymbolsContext): Boolean =
  when (this) {
    is ContextKindName -> context[kind] == name
    is ContextAllOf -> allOf.all { it.evaluate(context) }
    is ContextAnyOf -> anyOf.any { it.evaluate(context) }
    is ContextNot -> !not.evaluate(context)
    else -> throw IllegalStateException(this.javaClass.simpleName)
  }
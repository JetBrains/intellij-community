// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.webTypes.json

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.applyIf
import com.intellij.webSymbols.*
import com.intellij.webSymbols.WebSymbol.Companion.KIND_CSS_CLASSES
import com.intellij.webSymbols.WebSymbol.Companion.KIND_CSS_FUNCTIONS
import com.intellij.webSymbols.WebSymbol.Companion.KIND_CSS_PARTS
import com.intellij.webSymbols.WebSymbol.Companion.KIND_CSS_PROPERTIES
import com.intellij.webSymbols.WebSymbol.Companion.KIND_CSS_PSEUDO_CLASSES
import com.intellij.webSymbols.WebSymbol.Companion.KIND_CSS_PSEUDO_ELEMENTS
import com.intellij.webSymbols.WebSymbol.Companion.KIND_HTML_ATTRIBUTES
import com.intellij.webSymbols.WebSymbol.Companion.KIND_HTML_ELEMENTS
import com.intellij.webSymbols.WebSymbol.Companion.KIND_JS_EVENTS
import com.intellij.webSymbols.WebSymbol.Companion.KIND_JS_PROPERTIES
import com.intellij.webSymbols.WebSymbol.Companion.KIND_JS_SYMBOLS
import com.intellij.webSymbols.WebSymbol.Companion.NAMESPACE_CSS
import com.intellij.webSymbols.WebSymbol.Companion.NAMESPACE_HTML
import com.intellij.webSymbols.WebSymbol.Companion.NAMESPACE_JS
import com.intellij.webSymbols.WebSymbol.Companion.PROP_ARGUMENTS
import com.intellij.webSymbols.WebSymbol.Companion.PROP_DOC_HIDE_PATTERN
import com.intellij.webSymbols.WebSymbol.Companion.PROP_HIDE_FROM_COMPLETION
import com.intellij.webSymbols.WebSymbol.Companion.PROP_KIND
import com.intellij.webSymbols.WebSymbol.Companion.PROP_READ_ONLY
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.context.WebSymbolsContext
import com.intellij.webSymbols.context.WebSymbolsContext.Companion.PKG_MANAGER_RUBY_GEMS
import com.intellij.webSymbols.context.WebSymbolsContext.Companion.PKG_MANAGER_NODE_PACKAGES
import com.intellij.webSymbols.context.WebSymbolsContext.Companion.PKG_MANAGER_SYMFONY_BUNDLES
import com.intellij.webSymbols.context.WebSymbolsContextKindRules
import com.intellij.webSymbols.html.WebSymbolHtmlAttributeValue
import com.intellij.webSymbols.impl.canUnwrapSymbols
import com.intellij.webSymbols.js.WebSymbolJsKind
import com.intellij.webSymbols.query.WebSymbolMatch
import com.intellij.webSymbols.query.WebSymbolNameConversionRules
import com.intellij.webSymbols.query.WebSymbolNameConverter
import com.intellij.webSymbols.query.WebSymbolsQueryExecutor
import com.intellij.webSymbols.utils.NameCaseUtils
import com.intellij.webSymbols.utils.lastWebSymbol
import com.intellij.webSymbols.webTypes.WebTypesJsonOrigin
import com.intellij.webSymbols.webTypes.WebTypesSymbol
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
  sequenceOf(css, html)
    .filter { it != null }
    .flatMap { host -> host.collectDirectContributions(framework).mapWith(namespaceOf(host)) }
    .plus(js?.collectDirectContributions() ?: emptySequence())

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
      Pair(KIND_CSS_PSEUDO_ELEMENTS, this.pseudoElements),
      Pair(KIND_CSS_PARTS, this.parts),
    )
    is JsContributionsHost -> sequenceOf(
      Pair(KIND_JS_EVENTS, this.events),
      Pair(KIND_JS_PROPERTIES, this.properties),
      Pair(KIND_JS_SYMBOLS, this.symbols),
    )
    else -> emptySequence()
  })
    .plus(this.additionalProperties.asSequence()
            .map { (name, list) -> Pair(name, list?.mapNotNull { it?.value as? GenericContribution } ?: emptyList()) }
            .filter { it.second.isNotEmpty() })

private fun JsGlobal.collectDirectContributions(): Sequence<Triple<SymbolNamespace, SymbolKind, List<BaseContribution>>> =
  sequenceOf(
    Triple(NAMESPACE_JS, KIND_JS_EVENTS, this.events),
    Triple(NAMESPACE_JS, KIND_JS_SYMBOLS, this.symbols),
  )
    .filter { it.third.isNotEmpty() }
    .plus(additionalProperties.asSequence()
            .filter { (name, _) -> !WebTypesSymbol.WEB_TYPES_JS_FORBIDDEN_GLOBAL_KINDS.contains(name) }
            .map { (name, list) -> Triple(NAMESPACE_JS, name, list?.mapNotNull { it?.value as? GenericContribution } ?: emptyList()) }
            .filter { it.second.isNotEmpty() })

internal val GenericContributionsHost.genericContributions: Map<String, List<GenericContribution>>
  get() =
    this.additionalProperties.asSequence()
      .map { (name, list) -> Pair(name, list?.mapNotNull { it?.value as? GenericContribution } ?: emptyList()) }
      .filter { it.second.isNotEmpty() }
      .toMap()

internal val GenericContributionsHost.genericProperties: Map<String, Any>
  get() =
    this.additionalProperties.asSequence()
      .map { (name, list) -> Pair(name, list?.mapNotNull { prop -> prop?.value.takeIf { it !is GenericContribution } } ?: emptyList()) }
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
          is JsProperty -> if (this.readOnly == true) sequenceOf(Pair(PROP_READ_ONLY, true)) else emptySequence()
          is JsSymbol -> this.kind?.let { kind -> WebSymbolJsKind.values().firstOrNull { it.name.equals(kind.value(), true) } }
                           ?.let { sequenceOf(Pair(PROP_KIND, it)) }
                         ?: emptySequence()
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
    .let { parseWebTypesPath(it, context) }
    .lastOrNull()
    ?.let {
      WebSymbolQualifiedKind(it.namespace, it.kind)
    }

internal fun Reference.resolve(name: String,
                               scope: List<WebSymbolsScope>,
                               queryExecutor: WebSymbolsQueryExecutor,
                               virtualSymbols: Boolean = true,
                               abstractSymbols: Boolean = false): List<WebSymbol> =
  processWebSymbols(name, scope, queryExecutor, virtualSymbols, abstractSymbols) { path, virtualSymbols2, abstractSymbols2 ->
    runNameMatchQuery(path, virtualSymbols2, abstractSymbols2, false, scope)
  }

internal fun Reference.resolve(scope: List<WebSymbolsScope>,
                               queryExecutor: WebSymbolsQueryExecutor,
                               virtualSymbols: Boolean = true,
                               abstractSymbols: Boolean = false): List<WebSymbol> =
  processWebSymbols(null, scope, queryExecutor, virtualSymbols, abstractSymbols) { path, virtualSymbols2, abstractSymbols2 ->
    if (path.isEmpty()) return@processWebSymbols emptyList()
    val lastSegment = path.last()
    if (lastSegment.name.isEmpty())
      runListSymbolsQuery(path.subList(0, path.size - 1), lastSegment.qualifiedKind,
                          false, virtualSymbols2, abstractSymbols2, false, scope)
    else
      runNameMatchQuery(path, virtualSymbols2, abstractSymbols2, false, scope)
  }

internal fun Reference.list(scope: List<WebSymbolsScope>,
                            queryExecutor: WebSymbolsQueryExecutor,
                            expandPatterns: Boolean,
                            virtualSymbols: Boolean = true,
                            abstractSymbols: Boolean = false): List<WebSymbol> =
  processWebSymbols(null, scope, queryExecutor, virtualSymbols, abstractSymbols) { path, virtualSymbols2, abstractSymbols2 ->
    if (path.isEmpty()) return@processWebSymbols emptyList()
    val lastSegment = path.last()
    runListSymbolsQuery(path.subList(0, path.size - 1), lastSegment.qualifiedKind,
                        expandPatterns, virtualSymbols2, abstractSymbols2, false, scope)
  }

private fun Reference.processWebSymbols(
  name: String?,
  scope: List<WebSymbolsScope>,
  queryExecutor: WebSymbolsQueryExecutor,
  virtualSymbols: Boolean,
  abstractSymbols: Boolean,
  queryRunner: WebSymbolsQueryExecutor.(List<WebSymbolQualifiedName>, Boolean, Boolean) -> List<WebSymbol>
): List<WebSymbol> {
  ProgressManager.checkCanceled()
  return when (val reference = this.value) {
    is String -> queryExecutor.queryRunner(
      parseWebTypesPath(reference, scope.lastWebSymbol).applyIf(name != null) { withLastSegmentName(name ?: "") },
      virtualSymbols, abstractSymbols)
    is ReferenceWithProps -> {
      val nameConversionRules = reference.createNameConversionRules(scope.lastWebSymbol)
      val path = parseWebTypesPath(reference.path ?: return emptyList(), scope.lastWebSymbol)
        .applyIf(name != null) { withLastSegmentName(name ?: "") }
      val matches = queryExecutor.withNameConversionRules(nameConversionRules).queryRunner(
        path, reference.includeVirtual ?: virtualSymbols, reference.includeAbstract ?: abstractSymbols)
      if (reference.filter == null) return matches
      val properties = reference.additionalProperties.toMap()
      WebSymbolsFilter.get(reference.filter)
        .filterNameMatches(matches, queryExecutor, scope, properties)
    }
    else -> throw IllegalArgumentException(reference::class.java.name)
  }.flatMap { symbol ->
    if (symbol is WebSymbolMatch
        && symbol.nameSegments.size == 1
        && symbol.nameSegments[0].let { segment ->
        segment.canUnwrapSymbols()
        && segment.symbols.all { it.name == symbol.name }
      })
      symbol.nameSegments[0].symbols
    else listOf(symbol)
  }
}

internal fun Reference.codeCompletion(name: String,
                                      scope: List<WebSymbolsScope>,
                                      queryExecutor: WebSymbolsQueryExecutor,
                                      position: Int = 0,
                                      virtualSymbols: Boolean = true): List<WebSymbolCodeCompletionItem> {
  return when (val reference = this.value) {
    is String -> queryExecutor.runCodeCompletionQuery(
      parseWebTypesPath("$reference", scope.lastWebSymbol).withLastSegmentName(name), position,
      virtualSymbols, scope
    )
    is ReferenceWithProps -> {
      val nameConversionRules = reference.createNameConversionRules(scope.lastWebSymbol)
      val path = parseWebTypesPath(reference.path ?: return emptyList(), scope.lastWebSymbol).withLastSegmentName(name)
      val codeCompletions = queryExecutor.withNameConversionRules(nameConversionRules)
        .runCodeCompletionQuery(path, position, reference.includeVirtual ?: virtualSymbols, scope)
      if (reference.filter == null) return codeCompletions
      val properties = reference.additionalProperties.toMap()
      WebSymbolsFilter.get(reference.filter)
        .filterCodeCompletions(codeCompletions, queryExecutor, scope, properties)
    }
    else -> throw IllegalArgumentException(reference::class.java.name)
  }
}

internal fun EnablementRules.wrap(): WebSymbolsContextKindRules.EnablementRules =
  WebSymbolsContextKindRules.createEnablementRules {
    pkgManagerDependencies(PKG_MANAGER_NODE_PACKAGES, nodePackages)
    pkgManagerDependencies(PKG_MANAGER_RUBY_GEMS, rubyGems)
    pkgManagerDependencies(PKG_MANAGER_SYMFONY_BUNDLES, symfonyBundles)
    pkgManagerDependencies(additionalProperties)
    projectToolExecutables(projectToolExecutables)
    fileExtensions(fileExtensions)
    ideLibraries(ideLibraries)
    fileNamePatterns(fileNamePatterns.mapNotNull { it.toRegex() })
  }

internal fun DisablementRules.wrap(): WebSymbolsContextKindRules.DisablementRules =
  WebSymbolsContextKindRules.createDisablementRules {
    fileExtensions(fileExtensions)
    fileNamePatterns(fileNamePatterns.mapNotNull { it.toRegex() })
  }

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
    "symbol" -> WebSymbolHtmlAttributeValue.Type.SYMBOL
    "of-match" -> WebSymbolHtmlAttributeValue.Type.OF_MATCH
    "string" -> WebSymbolHtmlAttributeValue.Type.STRING
    "boolean" -> WebSymbolHtmlAttributeValue.Type.BOOLEAN
    "number" -> WebSymbolHtmlAttributeValue.Type.NUMBER
    null -> null
    else -> WebSymbolHtmlAttributeValue.Type.COMPLEX
  }

internal fun HtmlValueType.toLangType(): List<Type>? =
  when (val typeValue = this.value) {
    null, "enum", "of-match", "symbol" -> null
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

private fun ReferenceWithProps.createNameConversionRules(context: WebSymbol?): List<WebSymbolNameConversionRules> {
  val rules = nameConversion ?: return emptyList()
  val lastPath = parseWebTypesPath(path, context).lastOrNull()
  if (lastPath == null)
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
      null -> {}
      else -> throw IllegalArgumentException(value.toString())
    }
  }

  buildConvertersMap(rules.canonicalNames?.value, builder::addCanonicalNamesRule)
  buildConvertersMap(rules.matchNames?.value, builder::addMatchNamesRule)
  buildConvertersMap(rules.nameVariants?.value, builder::addCompletionVariantsRule)
  return if (builder.isEmpty())
    emptyList()
  else
    listOf(builder.build())
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
    val namespace = path[0].asWebTypesSymbolNamespace() ?: continue
    val symbolKind = path[1]
    val converter = mapper(value) ?: continue
    addToBuilder(WebSymbolQualifiedKind(namespace, symbolKind), converter)
  }
}

internal fun List<Type>.mapToTypeReferences(): List<WebSymbolTypeSupport.TypeReference> =
  mapNotNull {
    when (val reference = it.value) {
      is String -> WebSymbolTypeSupport.TypeReference.create(null, reference)
      is TypeReference -> if (reference.name != null)
        WebSymbolTypeSupport.TypeReference.create(reference.module, reference.name)
      else null
      else -> null
    }
  }

internal fun RequiredContextBase?.evaluate(context: WebSymbolsContext): Boolean =
  when (this) {
    null -> true
    is RequiredContextKindName -> context[kind] == name
    is RequiredContextAllOf -> allOf.all { it.evaluate(context) }
    is RequiredContextAnyOf -> anyOf.any { it.evaluate(context) }
    is RequiredContextNot -> !not.evaluate(context)
    else -> throw IllegalStateException(this.javaClass.simpleName)
  }

fun parseWebTypesPath(path: String?, context: WebSymbol?): List<WebSymbolQualifiedName> =
  if (path != null)
    parseWebTypesPath(StringUtil.split(path, "/", true, true), context)
  else
    emptyList()

internal fun List<WebSymbolQualifiedName>.withLastSegmentName(name: String) =
  if (isNotEmpty())
    subList(0, size - 1) + last().copy(name = name)
  else
    this

internal fun String.asWebTypesSymbolNamespace(): SymbolNamespace? =
  takeIf { it == NAMESPACE_JS || it == NAMESPACE_HTML || it == NAMESPACE_CSS }

private fun parseWebTypesPath(path: List<String>, context: WebSymbol?): List<WebSymbolQualifiedName> {
  var i = 0
  var prevNamespace: SymbolNamespace = context?.namespace ?: NAMESPACE_HTML
  val result = mutableListOf<WebSymbolQualifiedName>()
  while (i < path.size) {
    var namespace = path[i].asWebTypesSymbolNamespace()
    if (namespace != null) {
      i++
      prevNamespace = namespace
    }
    else {
      namespace = prevNamespace
    }
    if (i >= path.size) break
    val kind = path[i++]
    val name = if (i >= path.size) "" else path[i++]
    result.add(WebSymbolQualifiedName(namespace, kind, name))
  }
  return result
}

@Suppress("HardCodedStringLiteral")
internal fun BaseContribution.toApiStatus(origin: WebTypesJsonOrigin): WebSymbolApiStatus =
  obsolete?.value?.takeIf { it != false }
    ?.let { msg -> WebSymbolApiStatus.Obsolete((msg as? String)?.let { origin.renderDescription(it) }, obsoleteSince) }
  ?: deprecated?.value?.takeIf { it != false }
    ?.let { msg -> WebSymbolApiStatus.Deprecated((msg as? String)?.let { origin.renderDescription(it) }, deprecatedSince) }
  ?: experimental?.value?.takeIf { it != false }
    ?.let { msg -> WebSymbolApiStatus.Experimental((msg as? String)?.let { origin.renderDescription(it) }, since) }
  ?: since?.let { WebSymbolApiStatus.Stable(it) }
  ?: WebSymbolApiStatus.Stable


internal fun NamePatternDefault.toApiStatus(origin: WebTypesJsonOrigin): WebSymbolApiStatus? =
  deprecated?.value
    ?.takeIf { it != false }
    ?.let { msg -> WebSymbolApiStatus.Deprecated((msg as? String)?.let { origin.renderDescription(it) }) }
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.webTypes.json

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.polySymbols.*
import com.intellij.polySymbols.PolySymbol.Companion.CSS_CLASSES
import com.intellij.polySymbols.PolySymbol.Companion.CSS_FUNCTIONS
import com.intellij.polySymbols.PolySymbol.Companion.CSS_PARTS
import com.intellij.polySymbols.PolySymbol.Companion.CSS_PROPERTIES
import com.intellij.polySymbols.PolySymbol.Companion.CSS_PSEUDO_CLASSES
import com.intellij.polySymbols.PolySymbol.Companion.CSS_PSEUDO_ELEMENTS
import com.intellij.polySymbols.PolySymbol.Companion.HTML_ATTRIBUTES
import com.intellij.polySymbols.PolySymbol.Companion.HTML_ELEMENTS
import com.intellij.polySymbols.PolySymbol.Companion.JS_EVENTS
import com.intellij.polySymbols.PolySymbol.Companion.JS_PROPERTIES
import com.intellij.polySymbols.PolySymbol.Companion.JS_SYMBOLS
import com.intellij.polySymbols.PolySymbol.Companion.NAMESPACE_CSS
import com.intellij.polySymbols.PolySymbol.Companion.NAMESPACE_HTML
import com.intellij.polySymbols.PolySymbol.Companion.NAMESPACE_JS
import com.intellij.polySymbols.PolySymbol.Companion.PROP_ARGUMENTS
import com.intellij.polySymbols.PolySymbol.Companion.PROP_DOC_HIDE_PATTERN
import com.intellij.polySymbols.PolySymbol.Companion.PROP_HIDE_FROM_COMPLETION
import com.intellij.polySymbols.PolySymbol.Companion.PROP_KIND
import com.intellij.polySymbols.PolySymbol.Companion.PROP_READ_ONLY
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.context.PolyContext
import com.intellij.polySymbols.context.PolyContext.Companion.PKG_MANAGER_NODE_PACKAGES
import com.intellij.polySymbols.context.PolyContext.Companion.PKG_MANAGER_RUBY_GEMS
import com.intellij.polySymbols.context.PolyContext.Companion.PKG_MANAGER_SYMFONY_BUNDLES
import com.intellij.polySymbols.context.PolyContextKindRules
import com.intellij.polySymbols.html.PolySymbolHtmlAttributeValue
import com.intellij.polySymbols.impl.canUnwrapSymbols
import com.intellij.polySymbols.js.PolySymbolJsKind
import com.intellij.polySymbols.query.PolySymbolMatch
import com.intellij.polySymbols.query.PolySymbolNameConversionRules
import com.intellij.polySymbols.query.PolySymbolNameConverter
import com.intellij.polySymbols.query.PolySymbolsQueryExecutor
import com.intellij.polySymbols.utils.NameCaseUtils
import com.intellij.polySymbols.utils.PolySymbolTypeSupport
import com.intellij.polySymbols.utils.lastPolySymbol
import com.intellij.polySymbols.utils.namespace
import com.intellij.polySymbols.webTypes.WebTypesJsonOrigin
import com.intellij.polySymbols.webTypes.WebTypesSymbol
import com.intellij.polySymbols.webTypes.filters.PolySymbolsFilter
import com.intellij.polySymbols.webTypes.json.NameConversionRulesSingle.NameConverter
import com.intellij.util.applyIf
import java.util.*
import java.util.function.Function

private fun namespaceOf(host: GenericContributionsHost): PolySymbolNamespace =
  when (host) {
    is HtmlContributionsHost -> NAMESPACE_HTML
    is CssContributionsHost -> NAMESPACE_CSS
    is JsContributionsHost -> NAMESPACE_JS
    else -> throw IllegalArgumentException(host.toString())
  }

internal fun Contributions.getAllContributions(framework: FrameworkId?): Sequence<Triple<PolySymbolNamespace, PolySymbolKind, List<BaseContribution>>> =
  sequenceOf(css, html)
    .filter { it != null }
    .flatMap { host -> host.collectDirectContributions(framework).mapWith(namespaceOf(host)) }
    .plus(js?.collectDirectContributions() ?: emptySequence())

internal fun GenericContributionsHost.getAllContributions(framework: FrameworkId?): Sequence<Triple<PolySymbolNamespace, PolySymbolKind, List<BaseContribution>>> =
  if (this is BaseContribution)
    sequenceOf(this, css, js, html)
      .filter { it != null }
      .flatMap { host -> host.collectDirectContributions(framework).mapWith(namespaceOf(host)) }
  else
    this.collectDirectContributions(framework).mapWith(namespaceOf(this))

private fun Sequence<Pair<PolySymbolKind, List<BaseContribution>>>.mapWith(namespace: PolySymbolNamespace): Sequence<Triple<PolySymbolNamespace, PolySymbolKind, List<BaseContribution>>> =
  map {
    if (namespace == NAMESPACE_HTML && it.first == JS_EVENTS.kind)
      Triple(JS_EVENTS.namespace, JS_EVENTS.kind, it.second)
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

private fun GenericContributionsHost.collectDirectContributions(framework: FrameworkId?): Sequence<Pair<PolySymbolKind, List<BaseContribution>>> =
  (when (this) {
    is HtmlContributionsHost -> sequenceOf(
      Pair(HTML_ATTRIBUTES.kind, this.attributes),
      Pair(HTML_ELEMENTS.kind, this.elements),
      Pair(JS_EVENTS.kind, this.events)
    ).plus(
      when (this) {
        is Html -> sequenceOf(
          Pair(if (framework == VUE_FRAMEWORK) KIND_HTML_VUE_LEGACY_COMPONENTS else HTML_ELEMENTS.kind, this.tags)
        )
        is HtmlElement -> sequenceOf(
          Pair(JS_EVENTS.kind, this.events)
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
      Pair(CSS_CLASSES.kind, this.classes),
      Pair(CSS_FUNCTIONS.kind, this.functions),
      Pair(CSS_PROPERTIES.kind, this.properties),
      Pair(CSS_PSEUDO_CLASSES.kind, this.pseudoClasses),
      Pair(CSS_PSEUDO_ELEMENTS.kind, this.pseudoElements),
      Pair(CSS_PARTS.kind, this.parts),
    )
    is JsContributionsHost -> sequenceOf(
      Pair(JS_EVENTS.kind, this.events),
      Pair(JS_PROPERTIES.kind, this.properties),
      Pair(JS_SYMBOLS.kind, this.symbols),
    )
    else -> emptySequence()
  })
    .plus(this.additionalProperties.asSequence()
            .map { (name, list) -> Pair(name, list?.mapNotNull { it?.value as? GenericContribution } ?: emptyList()) }
            .filter { it.second.isNotEmpty() })

private fun JsGlobal.collectDirectContributions(): Sequence<Triple<PolySymbolNamespace, PolySymbolKind, List<BaseContribution>>> =
  sequenceOf(
    Triple(NAMESPACE_JS, JS_EVENTS.kind, this.events),
    Triple(NAMESPACE_JS, JS_SYMBOLS.kind, this.symbols),
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
          is JsSymbol -> this.kind?.let { kind -> PolySymbolJsKind.values().firstOrNull { it.name.equals(kind.value(), true) } }
                           ?.let { sequenceOf(Pair(PROP_KIND, it)) }
                         ?: emptySequence()
          else -> emptySequence()
        }
      )
      .toMap()

internal fun Reference.getSymbolKind(context: PolySymbol?): PolySymbolQualifiedKind? =
  when (val reference = this.value) {
    is String -> reference
    is ReferenceWithProps -> reference.path
    else -> null
  }
    .let { parseWebTypesPath(it, context) }
    .lastOrNull()
    ?.let {
      PolySymbolQualifiedKind(it.namespace, it.kind)
    }

internal fun Reference.resolve(
  name: String,
  scope: List<PolySymbolsScope>,
  queryExecutor: PolySymbolsQueryExecutor,
  virtualSymbols: Boolean = true,
  abstractSymbols: Boolean = false,
): List<PolySymbol> =
  processPolySymbols(name, scope, queryExecutor, virtualSymbols, abstractSymbols) { path, virtualSymbols2, abstractSymbols2 ->
    runNameMatchQuery(path, virtualSymbols2, abstractSymbols2, false, scope)
  }

internal fun Reference.resolve(
  scope: List<PolySymbolsScope>,
  queryExecutor: PolySymbolsQueryExecutor,
  virtualSymbols: Boolean = true,
  abstractSymbols: Boolean = false,
): List<PolySymbol> =
  processPolySymbols(null, scope, queryExecutor, virtualSymbols, abstractSymbols) { path, virtualSymbols2, abstractSymbols2 ->
    if (path.isEmpty()) return@processPolySymbols emptyList()
    val lastSegment = path.last()
    if (lastSegment.name.isEmpty())
      runListSymbolsQuery(path.subList(0, path.size - 1), lastSegment.qualifiedKind,
                          false, virtualSymbols2, abstractSymbols2, false, scope)
    else
      runNameMatchQuery(path, virtualSymbols2, abstractSymbols2, false, scope)
  }

internal fun Reference.list(
  scope: List<PolySymbolsScope>,
  queryExecutor: PolySymbolsQueryExecutor,
  expandPatterns: Boolean,
  virtualSymbols: Boolean = true,
  abstractSymbols: Boolean = false,
): List<PolySymbol> =
  processPolySymbols(null, scope, queryExecutor, virtualSymbols, abstractSymbols) { path, virtualSymbols2, abstractSymbols2 ->
    if (path.isEmpty()) return@processPolySymbols emptyList()
    val lastSegment = path.last()
    runListSymbolsQuery(path.subList(0, path.size - 1), lastSegment.qualifiedKind,
                        expandPatterns, virtualSymbols2, abstractSymbols2, false, scope)
  }

private fun Reference.processPolySymbols(
  name: String?,
  scope: List<PolySymbolsScope>,
  queryExecutor: PolySymbolsQueryExecutor,
  virtualSymbols: Boolean,
  abstractSymbols: Boolean,
  queryRunner: PolySymbolsQueryExecutor.(List<PolySymbolQualifiedName>, Boolean, Boolean) -> List<PolySymbol>,
): List<PolySymbol> {
  ProgressManager.checkCanceled()
  return when (val reference = this.value) {
    is String -> queryExecutor.queryRunner(
      parseWebTypesPath(reference, scope.lastPolySymbol).applyIf(name != null) { withLastSegmentName(name ?: "") },
      virtualSymbols, abstractSymbols)
    is ReferenceWithProps -> {
      val nameConversionRules = reference.createNameConversionRules(scope.lastPolySymbol)
      val path = parseWebTypesPath(reference.path ?: return emptyList(), scope.lastPolySymbol)
        .applyIf(name != null) { withLastSegmentName(name ?: "") }
      val matches = queryExecutor.withNameConversionRules(nameConversionRules).queryRunner(
        path, reference.includeVirtual ?: virtualSymbols, reference.includeAbstract ?: abstractSymbols)
      if (reference.filter == null) return matches
      val properties = reference.additionalProperties.toMap()
      PolySymbolsFilter.get(reference.filter)
        .filterNameMatches(matches, queryExecutor, scope, properties)
    }
    else -> throw IllegalArgumentException(reference::class.java.name)
  }.flatMap { symbol ->
    if (symbol is PolySymbolMatch
        && symbol.nameSegments.size == 1
        && symbol.nameSegments[0].let { segment ->
        segment.canUnwrapSymbols()
        && segment.symbols.all { it.name == symbol.name }
      })
      symbol.nameSegments[0].symbols
    else listOf(symbol)
  }
}

internal fun Reference.codeCompletion(
  name: String,
  scope: List<PolySymbolsScope>,
  queryExecutor: PolySymbolsQueryExecutor,
  position: Int = 0,
  virtualSymbols: Boolean = true,
): List<PolySymbolCodeCompletionItem> {
  return when (val reference = this.value) {
    is String -> queryExecutor.runCodeCompletionQuery(
      parseWebTypesPath("$reference", scope.lastPolySymbol).withLastSegmentName(name), position,
      virtualSymbols, scope
    )
    is ReferenceWithProps -> {
      val nameConversionRules = reference.createNameConversionRules(scope.lastPolySymbol)
      val path = parseWebTypesPath(reference.path ?: return emptyList(), scope.lastPolySymbol).withLastSegmentName(name)
      val codeCompletions = queryExecutor.withNameConversionRules(nameConversionRules)
        .runCodeCompletionQuery(path, position, reference.includeVirtual ?: virtualSymbols, scope)
      if (reference.filter == null) return codeCompletions
      val properties = reference.additionalProperties.toMap()
      PolySymbolsFilter.get(reference.filter)
        .filterCodeCompletions(codeCompletions, queryExecutor, scope, properties)
    }
    else -> throw IllegalArgumentException(reference::class.java.name)
  }
}

internal fun EnablementRules.wrap(): PolyContextKindRules.EnablementRules =
  PolyContextKindRules.createEnablementRules {
    pkgManagerDependencies(PKG_MANAGER_NODE_PACKAGES, nodePackages)
    pkgManagerDependencies(PKG_MANAGER_RUBY_GEMS, rubyGems)
    pkgManagerDependencies(PKG_MANAGER_SYMFONY_BUNDLES, symfonyBundles)
    pkgManagerDependencies(additionalProperties)
    projectToolExecutables(projectToolExecutables)
    fileExtensions(fileExtensions)
    ideLibraries(ideLibraries)
    fileNamePatterns(fileNamePatterns.mapNotNull { it.toRegex() })
  }

internal fun DisablementRules.wrap(): PolyContextKindRules.DisablementRules =
  PolyContextKindRules.createDisablementRules {
    fileExtensions(fileExtensions)
    fileNamePatterns(fileNamePatterns.mapNotNull { it.toRegex() })
  }

internal fun Priority.wrap(): PolySymbol.Priority =
  when (val value = this.value) {
    Priority.PriorityLevel.LOWEST -> PolySymbol.Priority.LOWEST
    Priority.PriorityLevel.LOW -> PolySymbol.Priority.LOW
    Priority.PriorityLevel.NORMAL -> PolySymbol.Priority.NORMAL
    Priority.PriorityLevel.HIGH -> PolySymbol.Priority.HIGH
    Priority.PriorityLevel.HIGHEST -> PolySymbol.Priority.HIGHEST
    is Number -> PolySymbol.Priority.custom(value.toDouble())
    else -> throw IllegalArgumentException("Unsupported value (${value}) class ${value.javaClass.name})")
  }

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

internal fun HtmlValueType.wrap(): PolySymbolHtmlAttributeValue.Type? =
  when (this.value) {
    "enum" -> PolySymbolHtmlAttributeValue.Type.ENUM
    "symbol" -> PolySymbolHtmlAttributeValue.Type.SYMBOL
    "of-match" -> PolySymbolHtmlAttributeValue.Type.OF_MATCH
    "string" -> PolySymbolHtmlAttributeValue.Type.STRING
    "boolean" -> PolySymbolHtmlAttributeValue.Type.BOOLEAN
    "number" -> PolySymbolHtmlAttributeValue.Type.NUMBER
    null -> null
    else -> PolySymbolHtmlAttributeValue.Type.COMPLEX
  }

internal fun HtmlValueType.toLangType(): List<Type>? =
  when (val typeValue = this.value) {
    null, "enum", "of-match", "symbol" -> null
    is List<*> -> typeValue.filterIsInstance<Type>()
    is TypeReference, is String -> listOf(
      Type().also { it.value = typeValue })
    else -> null
  }

internal fun HtmlAttributeValue.Kind.wrap(): PolySymbolHtmlAttributeValue.Kind =
  when (this) {
    HtmlAttributeValue.Kind.NO_VALUE -> PolySymbolHtmlAttributeValue.Kind.NO_VALUE
    HtmlAttributeValue.Kind.PLAIN -> PolySymbolHtmlAttributeValue.Kind.PLAIN
    HtmlAttributeValue.Kind.EXPRESSION -> PolySymbolHtmlAttributeValue.Kind.EXPRESSION
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

private fun ReferenceWithProps.createNameConversionRules(context: PolySymbol?): List<PolySymbolNameConversionRules> {
  val rules = nameConversion ?: return emptyList()
  val lastPath = parseWebTypesPath(path, context).lastOrNull()
  if (lastPath == null)
    return emptyList()

  val builder = PolySymbolNameConversionRules.builder()

  fun buildConvertersMap(value: Any?, addToBuilder: (PolySymbolQualifiedKind, PolySymbolNameConverter) -> Unit) {
    when (value) {
      is NameConverter -> mergeConverters(listOf(value))?.let {
        addToBuilder(PolySymbolQualifiedKind(lastPath.namespace, lastPath.kind), it)
      }
      is List<*> -> mergeConverters(value.filterIsInstance<NameConverter>())?.let {
        addToBuilder(PolySymbolQualifiedKind(lastPath.namespace, lastPath.kind), it)
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

internal fun mergeConverters(converters: List<NameConverter>): PolySymbolNameConverter? {
  if (converters.isEmpty()) return null
  val all = converters.map { it.toFunction() }
  return PolySymbolNameConverter { name ->
    all.map { it.apply(name) }
  }
}

internal fun <T> buildNameConverters(
  map: Map<String, T>?,
  mapper: (T) -> (PolySymbolNameConverter?),
  addToBuilder: (PolySymbolQualifiedKind, PolySymbolNameConverter) -> Unit,
) {
  for ((key, value) in map?.entries ?: return) {
    val path = key.splitToSequence('/')
                 .filter { it.isNotEmpty() }
                 .toList().takeIf { it.size == 2 } ?: continue
    val namespace = path[0].asWebTypesSymbolNamespace() ?: continue
    val symbolKind = path[1]
    val converter = mapper(value) ?: continue
    addToBuilder(PolySymbolQualifiedKind(namespace, symbolKind), converter)
  }
}

internal fun List<Type>.mapToTypeReferences(): List<PolySymbolTypeSupport.TypeReference> =
  mapNotNull {
    when (val reference = it.value) {
      is String -> PolySymbolTypeSupport.TypeReference.create(null, reference)
      is TypeReference -> if (reference.name != null)
        PolySymbolTypeSupport.TypeReference.create(reference.module, reference.name)
      else null
      else -> null
    }
  }

internal fun RequiredContextBase?.evaluate(context: PolyContext): Boolean =
  when (this) {
    null -> true
    is RequiredContextKindName -> context[kind] == name
    is RequiredContextAllOf -> allOf.all { it.evaluate(context) }
    is RequiredContextAnyOf -> anyOf.any { it.evaluate(context) }
    is RequiredContextNot -> !not.evaluate(context)
    else -> throw IllegalStateException(this.javaClass.simpleName)
  }

fun parseWebTypesPath(path: String?, context: PolySymbol?): List<PolySymbolQualifiedName> =
  if (path != null)
    parseWebTypesPath(StringUtil.split(path, "/", true, true), context)
  else
    emptyList()

internal fun List<PolySymbolQualifiedName>.withLastSegmentName(name: String) =
  if (isNotEmpty())
    subList(0, size - 1) + last().copy(name = name)
  else
    this

internal fun String.asWebTypesSymbolNamespace(): PolySymbolNamespace? =
  takeIf { it == NAMESPACE_JS || it == NAMESPACE_HTML || it == NAMESPACE_CSS }

private fun parseWebTypesPath(path: List<String>, context: PolySymbol?): List<PolySymbolQualifiedName> {
  var i = 0
  var prevNamespace: PolySymbolNamespace = context?.namespace ?: NAMESPACE_HTML
  val result = mutableListOf<PolySymbolQualifiedName>()
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
    result.add(PolySymbolQualifiedName(PolySymbolQualifiedKind(namespace, kind), name))
  }
  return result
}

@Suppress("HardCodedStringLiteral")
internal fun BaseContribution.toApiStatus(origin: WebTypesJsonOrigin): PolySymbolApiStatus =
  obsolete?.value?.takeIf { it != false }
    ?.let { msg -> PolySymbolApiStatus.Obsolete((msg as? String)?.let { origin.renderDescription(it) }, obsoleteSince) }
  ?: deprecated?.value?.takeIf { it != false }
    ?.let { msg -> PolySymbolApiStatus.Deprecated((msg as? String)?.let { origin.renderDescription(it) }, deprecatedSince) }
  ?: experimental?.value?.takeIf { it != false }
    ?.let { msg -> PolySymbolApiStatus.Experimental((msg as? String)?.let { origin.renderDescription(it) }, since) }
  ?: since?.let { PolySymbolApiStatus.Stable(it) }
  ?: PolySymbolApiStatus.Stable


internal fun NamePatternDefault.toApiStatus(origin: WebTypesJsonOrigin): PolySymbolApiStatus? =
  deprecated?.value
    ?.takeIf { it != false }
    ?.let { msg -> PolySymbolApiStatus.Deprecated((msg as? String)?.let { origin.renderDescription(it) }) }
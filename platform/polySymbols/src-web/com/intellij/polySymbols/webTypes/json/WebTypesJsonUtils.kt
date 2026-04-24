// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.webTypes.json

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.polySymbols.PolySymbol.DocHidePatternProperty
import com.intellij.polySymbols.PolySymbol.HideFromCompletionProperty
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolApiStatus
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.PolySymbolModifier
import com.intellij.polySymbols.PolySymbolNamespace
import com.intellij.polySymbols.PolySymbolQualifiedName
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.context.PolyContext
import com.intellij.polySymbols.context.PolyContext.Companion.PKG_MANAGER_NODE_PACKAGES
import com.intellij.polySymbols.context.PolyContext.Companion.PKG_MANAGER_RUBY_GEMS
import com.intellij.polySymbols.context.PolyContext.Companion.PKG_MANAGER_SYMFONY_BUNDLES
import com.intellij.polySymbols.context.PolyContextKindRules
import com.intellij.polySymbols.css.CSS_CLASSES
import com.intellij.polySymbols.css.CSS_FUNCTIONS
import com.intellij.polySymbols.css.CSS_PARTS
import com.intellij.polySymbols.css.CSS_PROPERTIES
import com.intellij.polySymbols.css.CSS_PSEUDO_CLASSES
import com.intellij.polySymbols.css.CSS_PSEUDO_ELEMENTS
import com.intellij.polySymbols.css.NAMESPACE_CSS
import com.intellij.polySymbols.css.PROP_CSS_ARGUMENTS
import com.intellij.polySymbols.framework.FrameworkId
import com.intellij.polySymbols.html.HTML_ATTRIBUTES
import com.intellij.polySymbols.html.HTML_ELEMENTS
import com.intellij.polySymbols.html.NAMESPACE_HTML
import com.intellij.polySymbols.html.PolySymbolHtmlAttributeValue
import com.intellij.polySymbols.impl.canUnwrapSymbols
import com.intellij.polySymbols.js.JS_EVENTS
import com.intellij.polySymbols.js.JS_PROPERTIES
import com.intellij.polySymbols.js.JS_SYMBOLS
import com.intellij.polySymbols.js.JsSymbolKindProperty
import com.intellij.polySymbols.js.JsSymbolSymbolKind
import com.intellij.polySymbols.js.NAMESPACE_JS
import com.intellij.polySymbols.query.PolySymbolMatch
import com.intellij.polySymbols.query.PolySymbolNameConversionRules
import com.intellij.polySymbols.query.PolySymbolNameConverter
import com.intellij.polySymbols.query.PolySymbolQueryExecutor
import com.intellij.polySymbols.query.PolySymbolQueryStack
import com.intellij.polySymbols.utils.NameCaseUtils
import com.intellij.polySymbols.utils.PolySymbolTypeSupport
import com.intellij.polySymbols.utils.namespace
import com.intellij.polySymbols.webTypes.WEB_TYPES_JS_FORBIDDEN_GLOBAL_KINDS
import com.intellij.polySymbols.webTypes.WebTypesJsonOrigin
import com.intellij.polySymbols.webTypes.filters.PolySymbolFilter
import com.intellij.polySymbols.webTypes.json.NameConversionRulesSingle.NameConverter
import com.intellij.util.applyIf
import java.util.Locale
import java.util.function.Function

private fun namespaceOf(host: GenericContributionsHost): PolySymbolNamespace =
  when (host) {
    is HtmlContributionsHost -> NAMESPACE_HTML
    is CssContributionsHost -> NAMESPACE_CSS
    is JsContributionsHost -> NAMESPACE_JS
    else -> throw IllegalArgumentException(host.toString())
  }

internal fun Contributions.getAllContributions(framework: FrameworkId?): Sequence<Pair<PolySymbolKind, List<BaseContribution>>> =
  sequenceOf(css, html)
    .filter { it != null }
    .flatMap { host -> host.collectDirectContributions(framework) }
    .plus(js?.collectDirectContributions() ?: emptySequence())

internal fun GenericContributionsHost.getAllContributions(framework: FrameworkId?): Sequence<Pair<PolySymbolKind, List<BaseContribution>>> =
  if (this is BaseContribution)
    sequenceOf(this, css, js, html)
      .filter { it != null }
      .flatMap { host -> host.collectDirectContributions(framework) }
  else
    this.collectDirectContributions(framework)

internal val HTML_VUE_LEGACY_COMPONENTS = PolySymbolKind[NAMESPACE_HTML, "\$vue-legacy-components\$"]

internal const val VUE_DIRECTIVE_PREFIX = "v-"
internal const val VUE_FRAMEWORK = "vue"
internal val HTML_VUE_COMPONENTS = PolySymbolKind[NAMESPACE_HTML, "vue-components"]
internal val HTML_VUE_COMPONENT_PROPS = PolySymbolKind[NAMESPACE_HTML, "props"]
internal val HTML_VUE_DIRECTIVES = PolySymbolKind[NAMESPACE_HTML, "vue-directives"]
internal val HTML_VUE_DIRECTIVE_ARGUMENT = PolySymbolKind[NAMESPACE_HTML, "argument"]
internal val HTML_VUE_DIRECTIVE_MODIFIERS = PolySymbolKind[NAMESPACE_HTML, "modifiers"]

private fun GenericContributionsHost.collectDirectContributions(framework: FrameworkId?): Sequence<Pair<PolySymbolKind, List<BaseContribution>>> =
  (when (this) {
    is HtmlContributionsHost -> sequenceOf(
      Pair(HTML_ATTRIBUTES, this.attributes),
      Pair(HTML_ELEMENTS, this.elements),
      Pair(JS_EVENTS, this.events)
    ).plus(
      when (this) {
        is Html -> sequenceOf(
          Pair(if (framework == VUE_FRAMEWORK) HTML_VUE_LEGACY_COMPONENTS else HTML_ELEMENTS, this.tags)
        )
        is HtmlElement -> sequenceOf(
          Pair(JS_EVENTS, this.events)
        )
        is HtmlAttribute -> if (this.name.startsWith(VUE_DIRECTIVE_PREFIX) && !this.name.contains(' ')
                                && framework == VUE_FRAMEWORK) {
          sequenceOf(
            Pair(HTML_VUE_DIRECTIVE_ARGUMENT, this.vueArgument?.toHtmlContribution()?.let { listOf(it) }
                                              ?: listOf(matchAllHtmlContribution("Vue directive argument"))),
            Pair(HTML_VUE_DIRECTIVE_MODIFIERS, this.vueModifiers.takeIf { it.isNotEmpty() }?.map { it.toHtmlContribution() }
                                               ?: listOf(matchAllHtmlContribution("Vue directive modifier")))
          )
        }
        else emptySequence()
        else -> emptySequence()
      }
    )
    is CssContributionsHost -> sequenceOf(
      Pair(CSS_CLASSES, this.classes),
      Pair(CSS_FUNCTIONS, this.functions),
      Pair(CSS_PROPERTIES, this.properties),
      Pair(CSS_PSEUDO_CLASSES, this.pseudoClasses),
      Pair(CSS_PSEUDO_ELEMENTS, this.pseudoElements),
      Pair(CSS_PARTS, this.parts),
    )
    is JsContributionsHost -> sequenceOf(
      Pair(JS_EVENTS, this.events),
      Pair(JS_PROPERTIES, this.properties),
      Pair(JS_SYMBOLS, this.symbols),
    )
    else -> emptySequence()
  })
    .plus(this.additionalProperties.asSequence()
            .map { (name, list) ->
              Pair(PolySymbolKind[namespaceOf(this), name],
                   list?.mapNotNull { it?.value as? GenericContribution } ?: emptyList())
            }
            .filter { it.second.isNotEmpty() })

private fun JsGlobal.collectDirectContributions(): Sequence<Pair<PolySymbolKind, List<BaseContribution>>> =
  sequenceOf(
    Pair(JS_EVENTS, this.events),
    Pair(JS_SYMBOLS, this.symbols),
  )
    .filter { it.second.isNotEmpty() }
    .plus(additionalProperties.asSequence()
            .filter { (name, _) -> !WEB_TYPES_JS_FORBIDDEN_GLOBAL_KINDS.contains(name) }
            .map { (name, list) ->
              Pair(PolySymbolKind[NAMESPACE_JS, name],
                   list?.mapNotNull { it?.value as? GenericContribution } ?: emptyList())
            }
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
          is CssPseudoClass -> sequenceOf(Pair(PROP_CSS_ARGUMENTS.name, this.arguments ?: false))
          is CssPseudoElement -> sequenceOf(Pair(PROP_CSS_ARGUMENTS.name, this.arguments ?: false))
          is JsSymbol -> this.kind?.let { kind -> JsSymbolSymbolKind.entries.firstOrNull { it.name.equals(kind.value(), true) } }
                           ?.let { sequenceOf(Pair(JsSymbolKindProperty.name, it)) }
                         ?: emptySequence()
          else -> emptySequence()
        }
      )
      .toMap()

internal fun Reference.getSymbolKind(context: PolySymbol?): PolySymbolKind? =
  when (val reference = this.value) {
    is String -> reference
    is ReferenceWithProps -> reference.path
    else -> null
  }
    .let { parseWebTypesPath(it, context) }
    .lastOrNull()
    ?.kind

internal fun Reference.resolve(
  name: String,
  stack: PolySymbolQueryStack,
  queryExecutor: PolySymbolQueryExecutor,
  virtualSymbols: Boolean = true,
  abstractSymbols: Boolean = false,
): List<PolySymbol> =
  processPolySymbols(name, stack, queryExecutor, virtualSymbols, abstractSymbols) { path, virtualSymbols2, abstractSymbols2 ->
    nameMatchQuery(path) {
      if (!virtualSymbols2) exclude(PolySymbolModifier.VIRTUAL)
      if (!abstractSymbols2) exclude(PolySymbolModifier.ABSTRACT)
      additionalScope(stack)
    }
  }

internal fun Reference.resolve(
  stack: PolySymbolQueryStack,
  queryExecutor: PolySymbolQueryExecutor,
  virtualSymbols: Boolean = true,
  abstractSymbols: Boolean = false,
): List<PolySymbol> =
  processPolySymbols(null, stack, queryExecutor, virtualSymbols, abstractSymbols) { path, virtualSymbols2, abstractSymbols2 ->
    if (path.isEmpty()) return@processPolySymbols emptyList()
    val lastSegment = path.last()
    if (lastSegment.name.isEmpty())
      listSymbolsQuery(path.subList(0, path.size - 1), lastSegment.kind,
                       false) {
        if (!virtualSymbols2) exclude(PolySymbolModifier.VIRTUAL)
        if (!abstractSymbols2) exclude(PolySymbolModifier.ABSTRACT)
        additionalScope(stack)
      }
    else
      nameMatchQuery(path) {
        if (!virtualSymbols2) exclude(PolySymbolModifier.VIRTUAL)
        if (!abstractSymbols2) exclude(PolySymbolModifier.ABSTRACT)
        additionalScope(stack)
      }
  }

internal fun Reference.list(
  stack: PolySymbolQueryStack,
  queryExecutor: PolySymbolQueryExecutor,
  expandPatterns: Boolean,
  virtualSymbols: Boolean = true,
  abstractSymbols: Boolean = false,
): List<PolySymbol> =
  processPolySymbols(null, stack, queryExecutor, virtualSymbols, abstractSymbols) { path, virtualSymbols2, abstractSymbols2 ->
    if (path.isEmpty()) return@processPolySymbols emptyList()
    val lastSegment = path.last()
    listSymbolsQuery(path.subList(0, path.size - 1), lastSegment.kind,
                     expandPatterns) {
      if (!virtualSymbols2) exclude(PolySymbolModifier.VIRTUAL)
      if (!abstractSymbols2) exclude(PolySymbolModifier.ABSTRACT)
      additionalScope(stack)
    }
  }

private fun Reference.processPolySymbols(
  name: String?,
  stack: PolySymbolQueryStack,
  queryExecutor: PolySymbolQueryExecutor,
  virtualSymbols: Boolean,
  abstractSymbols: Boolean,
  queryRunner: PolySymbolQueryExecutor.(List<PolySymbolQualifiedName>, Boolean, Boolean) -> List<PolySymbol>,
): List<PolySymbol> {
  ProgressManager.checkCanceled()
  return when (val reference = this.value) {
    is String -> queryExecutor.queryRunner(
      parseWebTypesPath(reference, stack.lastPolySymbol).applyIf(name != null) { withLastSegmentName(name ?: "") },
      virtualSymbols, abstractSymbols)
    is ReferenceWithProps -> {
      val nameConversionRules = reference.createNameConversionRules(stack.lastPolySymbol)
      val path = parseWebTypesPath(reference.path ?: return emptyList(), stack.lastPolySymbol)
        .applyIf(name != null) { withLastSegmentName(name ?: "") }
      val matches = queryExecutor.withNameConversionRules(nameConversionRules).queryRunner(
        path, reference.includeVirtual ?: virtualSymbols, reference.includeAbstract ?: abstractSymbols)
      if (reference.filter == null) return matches
      val properties = reference.additionalProperties.toMap()
      PolySymbolFilter.get(reference.filter)
        .filterNameMatches(matches, queryExecutor, stack, properties)
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
  stack: PolySymbolQueryStack,
  queryExecutor: PolySymbolQueryExecutor,
  position: Int = 0,
  virtualSymbols: Boolean = true,
): List<PolySymbolCodeCompletionItem> {
  return when (val reference = this.value) {
    is String -> queryExecutor.codeCompletionQuery(
      parseWebTypesPath(reference, stack.lastPolySymbol).withLastSegmentName(name), position
    ) {
      if (!virtualSymbols) exclude(PolySymbolModifier.VIRTUAL)
      exclude(PolySymbolModifier.ABSTRACT)
      additionalScope(stack)
    }
    is ReferenceWithProps -> {
      val nameConversionRules = reference.createNameConversionRules(stack.lastPolySymbol)
      val path = parseWebTypesPath(reference.path ?: return emptyList(), stack.lastPolySymbol).withLastSegmentName(name)
      val codeCompletions = queryExecutor.withNameConversionRules(nameConversionRules)
        .codeCompletionQuery(path, position) {
          if (!(reference.includeVirtual ?: virtualSymbols)) exclude(PolySymbolModifier.VIRTUAL)
          exclude(PolySymbolModifier.ABSTRACT)
          additionalScope(stack)
        }
      if (reference.filter == null) return codeCompletions
      val properties = reference.additionalProperties.toMap()
      PolySymbolFilter.get(reference.filter)
        .filterCodeCompletions(codeCompletions, queryExecutor, stack, properties)
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
    result.additionalProperties[DocHidePatternProperty.name] = true.toGenericHtmlPropertyValue()
  return result
}

internal fun DeprecatedHtmlAttributeVueModifier.toHtmlContribution(): BaseContribution {
  val result = GenericHtmlContribution()
  result.name = this.name
  result.description = this.description
  result.docUrl = this.docUrl
  result.pattern = this.pattern
  if (pattern.isMatchAllRegex)
    result.additionalProperties[DocHidePatternProperty.name] = true.toGenericHtmlPropertyValue()
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
        catch (_: IllegalArgumentException) {
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
        catch (_: IllegalArgumentException) {
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
    contribution.additionalProperties[DocHidePatternProperty.name] = true.toGenericHtmlPropertyValue()
    contribution.additionalProperties[HideFromCompletionProperty.name] = true.toGenericHtmlPropertyValue()
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

  fun buildConvertersMap(value: Any?, addToBuilder: (PolySymbolKind, PolySymbolNameConverter) -> Unit) {
    when (value) {
      is NameConverter -> mergeConverters(listOf(value))?.let {
        addToBuilder(lastPath.kind, it)
      }
      is List<*> -> mergeConverters(value.filterIsInstance<NameConverter>())?.let {
        addToBuilder(lastPath.kind, it)
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
  addToBuilder: (PolySymbolKind, PolySymbolNameConverter) -> Unit,
) {
  for ((key, value) in map?.entries ?: return) {
    val path = key.splitToSequence('/')
                 .filter { it.isNotEmpty() }
                 .toList().takeIf { it.size == 2 } ?: continue
    val namespace = path[0].asWebTypesSymbolNamespace() ?: continue
    val symbolKind = path[1]
    val converter = mapper(value) ?: continue
    addToBuilder(PolySymbolKind[namespace, symbolKind], converter)
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
    subList(0, size - 1) + last().withName(name)
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
    result.add(PolySymbolQualifiedName[namespace, kind, name])
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
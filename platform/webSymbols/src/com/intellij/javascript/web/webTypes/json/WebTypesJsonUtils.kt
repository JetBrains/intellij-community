package com.intellij.javascript.web.webTypes.json

import com.intellij.javascript.web.symbols.*
import com.intellij.javascript.web.symbols.WebSymbol.Companion.KIND_CSS_CLASSES
import com.intellij.javascript.web.symbols.WebSymbol.Companion.KIND_CSS_FUNCTIONS
import com.intellij.javascript.web.symbols.WebSymbol.Companion.KIND_CSS_PROPERTIES
import com.intellij.javascript.web.symbols.WebSymbol.Companion.KIND_CSS_PSEUDO_CLASSES
import com.intellij.javascript.web.symbols.WebSymbol.Companion.KIND_CSS_PSEUDO_ELEMENTS
import com.intellij.javascript.web.symbols.WebSymbol.Companion.KIND_HTML_ATTRIBUTES
import com.intellij.javascript.web.symbols.WebSymbol.Companion.KIND_HTML_ELEMENTS
import com.intellij.javascript.web.symbols.WebSymbol.Companion.KIND_JS_EVENTS
import com.intellij.javascript.web.symbols.WebSymbol.Companion.KIND_JS_PROPERTIES
import com.intellij.javascript.web.symbols.WebSymbol.Companion.PROP_ARGUMENTS
import com.intellij.javascript.web.symbols.WebSymbol.Companion.PROP_DOC_HIDE_PATTERN
import com.intellij.javascript.web.symbols.WebSymbol.Companion.PROP_HIDE_FROM_COMPLETION
import com.intellij.javascript.web.symbols.WebSymbolsContainer.Namespace
import com.intellij.javascript.web.symbols.impl.WebSymbolsRegistryImpl.Companion.parsePath
import com.intellij.javascript.web.webTypes.json.NameConversionRulesSingle.NameConverter
import com.intellij.model.Pointer
import java.util.*
import java.util.function.Function

private fun namespaceOf(host: GenericContributionsHost): Namespace =
  when (host) {
    is HtmlContributionsHost -> Namespace.HTML
    is CssContributionsHost -> Namespace.CSS
    is JsContributionsHost -> Namespace.JS
    else -> throw IllegalArgumentException(host.toString())
  }

fun Contributions.getAllContributions(framework: FrameworkId?): Sequence<Triple<Namespace, SymbolKind, List<BaseContribution>>> =
  sequenceOf(css, js, html)
    .filter { it != null }
    .flatMap { host -> host.collectDirectContributions(framework).mapWith(namespaceOf(host)) }

fun GenericContributionsHost.getAllContributions(framework: FrameworkId?): Sequence<Triple<Namespace, SymbolKind, List<BaseContribution>>> =
  if (this is BaseContribution)
    sequenceOf(this, css, js, html)
      .filter { it != null }
      .flatMap { host -> host.collectDirectContributions(framework).mapWith(namespaceOf(host)) }
  else
    this.collectDirectContributions(framework).mapWith(namespaceOf(this))

private fun Sequence<Pair<SymbolKind, List<BaseContribution>>>.mapWith(namespace: Namespace): Sequence<Triple<Namespace, SymbolKind, List<BaseContribution>>> =
  map {
    if (namespace == Namespace.HTML && it.first == KIND_JS_EVENTS)
      Triple(Namespace.JS, KIND_JS_EVENTS, it.second)
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

internal fun Reference.getSymbolType(context: WebSymbol?): WebSymbol.SymbolType? =
  when (val reference = this.value) {
    is String -> reference
    is ReferenceWithProps -> reference.path
    else -> null
  }
    .let { parsePath(it, context?.namespace) }
    .lastOrNull()
    ?.let {
      if (it.namespace != null)
        WebSymbol.SymbolType(it.namespace, it.kind)
      else null
    }

internal fun Reference.resolve(name: String?,
                               context: List<WebSymbolsContainer>,
                               registry: WebSymbolsRegistry,
                               virtualSymbols: Boolean = true,
                               abstractSymbols: Boolean = false): List<WebSymbol> {
  if (name != null && name.isEmpty())
    return emptyList()
  return when (val reference = this.value) {
    is String -> registry.runNameMatchQuery(
      reference + if (name != null) "/$name" else "",
      virtualSymbols, abstractSymbols, context = context)
    is ReferenceWithProps -> {
      val nameConversionRules = reference.createNameConversionRules(registry.framework)
      val matches = registry.withNameConversionRules(nameConversionRules).runNameMatchQuery(
        (reference.path ?: return emptyList()) + if (name != null) "/$name" else "",
        reference.includeVirtual ?: virtualSymbols,
        reference.includeAbstract ?: abstractSymbols,
        context = context)
      if (reference.filter == null) return matches
      val properties = reference.additionalProperties.toMap()
      WebSymbolsFilter.get(reference.filter)
        .filterNameMatches(matches, registry, context, properties)
    }
    else -> throw IllegalArgumentException(reference::class.java.name)
  }
}

internal fun Reference.codeCompletion(name: String,
                                      context: List<WebSymbolsContainer>,
                                      registry: WebSymbolsRegistry,
                                      position: Int = 0,
                                      virtualSymbols: Boolean = true): List<WebSymbolCodeCompletionItem> {
  return when (val reference = this.value) {
    is String -> registry.runCodeCompletionQuery("$reference/$name", position, virtualSymbols, context)
    is ReferenceWithProps -> {
      val nameConversionRules = reference.createNameConversionRules(registry.framework)
      val codeCompletions = registry.withNameConversionRules(nameConversionRules).runCodeCompletionQuery(
        (reference.path ?: return emptyList()) + "/$name", position,
        reference.includeVirtual ?: virtualSymbols,
        context)
      if (reference.filter == null) return codeCompletions
      val properties = reference.additionalProperties.toMap()
      WebSymbolsFilter.get(reference.filter)
        .filterCodeCompletions(codeCompletions, registry, context, properties)
    }
    else -> throw IllegalArgumentException(reference::class.java.name)
  }
}

internal fun EnablementRules.wrap(): WebFrameworksConfiguration.EnablementRules =
  WebFrameworksConfiguration.EnablementRules(
    nodePackages,
    fileExtensions,
    ideLibraries,
    fileNamePatterns.mapNotNull { it.toRegex() },
    scriptUrlPatterns.mapNotNull { it.toRegex() }
  )

internal fun DisablementRules.wrap(): WebFrameworksConfiguration.DisablementRules =
  WebFrameworksConfiguration.DisablementRules(
    fileExtensions,
    fileNamePatterns.mapNotNull { it.toRegex() },
  )

fun BaseContribution.Priority.wrap() =
  WebSymbol.Priority.values()[ordinal]

val BaseContribution.attributeValue: HtmlAttributeValue?
  get() =
    (this as? GenericContribution)?.attributeValue
    ?: (this as? HtmlAttribute)?.value

val BaseContribution.type: List<Type>?
  get() =
    (this as? TypedContribution)?.type?.takeIf { it.isNotEmpty() }

fun DeprecatedHtmlAttributeVueArgument.toHtmlContribution(): BaseContribution {
  val result = GenericHtmlContribution()
  result.name = "Vue directive argument"
  result.description = this.description
  result.docUrl = this.docUrl
  result.pattern = this.pattern
  if (pattern.isMatchAllRegex)
    result.additionalProperties[PROP_DOC_HIDE_PATTERN] = true.toGenericHtmlPropertyValue()
  return result
}

fun DeprecatedHtmlAttributeVueModifier.toHtmlContribution(): BaseContribution {
  val result = GenericHtmlContribution()
  result.name = this.name
  result.description = this.description
  result.docUrl = this.docUrl
  result.pattern = this.pattern
  if (pattern.isMatchAllRegex)
    result.additionalProperties[PROP_DOC_HIDE_PATTERN] = true.toGenericHtmlPropertyValue()
  return result
}

fun Pattern.toRegex(): Regex? =
  when (val pattern = value) {
    is String -> Regex(pattern, RegexOption.IGNORE_CASE)
    is PatternObject -> if (pattern.caseSensitive == true)
      Regex(pattern.regex)
    else
      Regex(pattern.regex, RegexOption.IGNORE_CASE)
    else -> null
  }

val WebTypes.jsTypesSyntaxWithLegacy: WebTypes.JsTypesSyntax?
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

val WebTypes.descriptionMarkupWithLegacy: WebTypes.DescriptionMarkup?
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

internal fun HtmlValueType.wrap(): WebSymbol.AttributeValueType? =
  when (this.value) {
    "enum" -> WebSymbol.AttributeValueType.ENUM
    "of-match" -> WebSymbol.AttributeValueType.OF_MATCH
    "string" -> WebSymbol.AttributeValueType.STRING
    "boolean" -> WebSymbol.AttributeValueType.BOOLEAN
    "number" -> WebSymbol.AttributeValueType.NUMBER
    null -> null
    else -> WebSymbol.AttributeValueType.JAVASCRIPT
  }

internal fun HtmlValueType.toJSType(): List<Type>? =
  when (val typeValue = this.value) {
    null, "enum", "of-match" -> null
    is List<*> -> typeValue.filterIsInstance<Type>()
    is TypeReference, is String -> listOf(Type().also { it.value = typeValue })
    else -> null
  }

internal fun HtmlAttributeValue.Kind.wrap(): WebSymbol.AttributeValueKind =
  when (this) {
    HtmlAttributeValue.Kind.NO_VALUE -> WebSymbol.AttributeValueKind.NO_VALUE
    HtmlAttributeValue.Kind.PLAIN -> WebSymbol.AttributeValueKind.PLAIN
    HtmlAttributeValue.Kind.EXPRESSION -> WebSymbol.AttributeValueKind.EXPRESSION
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

private fun ReferenceWithProps.createNameConversionRules(framework: FrameworkId?): List<WebSymbolNameConversionRules> {
  val rules = nameConversion ?: return emptyList()
  val lastPath = parsePath(path).lastOrNull()
  if (lastPath?.namespace == null)
    return emptyList()

  @Suppress("UNCHECKED_CAST")
  fun createConvertersMap(value: Any?): Map<Triple<FrameworkId?, Namespace, SymbolKind>, List<NameConverter>> =
    when (value) {
      is NameConverter -> mapOf(Pair(Triple(framework, lastPath.namespace, lastPath.kind), listOf(value)))
      is List<*> -> mapOf(Pair(Triple(framework, lastPath.namespace, lastPath.kind), value as List<NameConverter>))
      is NameConversionRulesSingle -> mapNameConverters(value.additionalProperties, { listOf(it) }, framework).toMap()
      is NameConversionRulesMultiple -> mapNameConverters(value.additionalProperties, { it }, framework).toMap()
      else -> throw IllegalArgumentException(value?.toString())
    }

  val canonicalNames = createConvertersMap(rules.canonicalNames?.value)
  val matchNames = createConvertersMap(rules.matchNames?.value)
  val nameVariants = createConvertersMap(rules.nameVariants?.value)
  if (canonicalNames.isEmpty() && matchNames.isEmpty() && nameVariants.isEmpty()) {
    return emptyList()
  }

  return listOf(ReferenceNameConversionRules(canonicalNames, matchNames, nameVariants))
}

private class ReferenceNameConversionRules(private val canonicalNames: Map<Triple<FrameworkId?, Namespace, SymbolKind>, List<NameConverter>>,
                                           private val matchNames: Map<Triple<FrameworkId?, Namespace, SymbolKind>, List<NameConverter>>,
                                           private val nameVariants: Map<Triple<FrameworkId?, Namespace, SymbolKind>, List<NameConverter>>) : WebSymbolNameConversionRules {
  override val canonicalNamesProviders: Map<Triple<FrameworkId?, Namespace, SymbolKind>, Function<String, List<String>>> =
    buildMap(canonicalNames)

  override val matchNamesProviders: Map<Triple<FrameworkId?, Namespace, SymbolKind>, Function<String, List<String>>> =
    buildMap(matchNames)

  override val nameVariantsProviders: Map<Triple<FrameworkId?, Namespace, SymbolKind>, Function<String, List<String>>> =
    buildMap(nameVariants)

  private fun buildMap(converters: Map<Triple<FrameworkId?, Namespace, SymbolKind>, List<NameConverter>>): Map<Triple<FrameworkId?, Namespace, SymbolKind>, Function<String, List<String>>> =
    converters.takeIf { it.isNotEmpty() }
      ?.mapValues { mergeConverters(it.value) }
    ?: emptyMap()

  override fun createPointer(): Pointer<out WebSymbolNameConversionRules> =
    Pointer.hardPointer(this)

  override fun getModificationCount(): Long = 0

  override fun hashCode(): Int =
    Objects.hash(canonicalNames, matchNames, nameVariants)

  override fun equals(other: Any?): Boolean =
    other === this || other is ReferenceNameConversionRules
    && other.canonicalNames == canonicalNames
    && other.matchNames == matchNames
    && other.nameVariants == nameVariants

}

private fun NameConverter.toFunction(): Function<String, String> =
  when (this) {
    NameConverter.AS_IS -> Function { it }
    NameConverter.LOWERCASE -> Function { it.lowercase(Locale.US) }
    NameConverter.UPPERCASE -> Function { it.uppercase(Locale.US) }
    NameConverter.PASCAL_CASE -> Function { JSStringUtil.toPascalCase(it) }
    NameConverter.CAMEL_CASE -> Function { JSStringUtil.toCamelCase(it) }
    NameConverter.KEBAB_CASE -> Function { JSStringUtil.toKebabCase(it) }
    NameConverter.SNAKE_CASE -> Function { JSStringUtil.toSnakeCase(it) }
  }

internal fun mergeConverters(converters: List<NameConverter>): Function<String, List<String>> {
  val all = converters.map { it.toFunction() }
  return Function { name ->
    all.map { it.apply(name) }
  }
}

internal fun <K, T> mapNameConverters(map: Map<String, T>,
                                      mapper: (T) -> K,
                                      framework: String?): Sequence<Pair<Triple<FrameworkId?, Namespace, String>, K>> =
  map.entries
    .asSequence()
    .mapNotNull { (key, value) ->
      val path = key.splitToSequence('/')
                   .filter { it.isNotEmpty() }
                   .toList().takeIf { it.size == 2 } ?: return@mapNotNull null
      val namespace = Namespace.of(path[0]) ?: return@mapNotNull null
      val symbolKind = path[1]
      val converter = mapper(value) ?: return@mapNotNull null
      Pair(Triple(framework, namespace, symbolKind), converter)
    }

fun WebTypes.getSymbolTypeResolver(project: Project? = null, context: List<VirtualFile> = emptyList()): WebTypesSymbolTypeResolver =
  WebTypesSymbolTypeResolver.get(this, project, context)
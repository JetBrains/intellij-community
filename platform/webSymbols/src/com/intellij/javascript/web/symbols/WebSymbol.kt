package com.intellij.javascript.web.symbols

import com.intellij.javascript.web.symbols.patterns.WebSymbolsPattern
import com.intellij.lang.documentation.DocumentationTarget
import com.intellij.lang.documentation.symbol.DocumentationSymbol
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.model.presentation.PresentableSymbol
import com.intellij.model.presentation.SymbolPresentation
import com.intellij.navigation.NavigatableSymbol
import com.intellij.navigation.NavigationTarget
import com.intellij.navigation.TargetPresentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import java.util.*
import javax.swing.Icon

@ApiStatus.Experimental
/**
 * INAPPLICABLE_JVM_NAME -> https://youtrack.jetbrains.com/issue/KT-31420
 * DEPRECATION -> @JvmDefault
 **/
@Suppress("INAPPLICABLE_JVM_NAME", "DEPRECATION")
interface WebSymbol : WebSymbolsContainer, Symbol, PresentableSymbol, DocumentationSymbol, NavigatableSymbol {

  val origin: WebSymbolsContainer.Origin
  val namespace: WebSymbolsContainer.Namespace
  val kind: SymbolKind

  @JvmDefault
  val matchedName: String
    get() = ""

  @JvmDefault
  override fun getModificationCount(): Long = 0

  override fun createPointer(): Pointer<out WebSymbol>

  @JvmDefault
  val psiContext: PsiElement?
    get() = null

  @JvmDefault
  @get:JvmName("isCompleteMatch")
  val completeMatch: Boolean
    get() = true

  @JvmDefault
  val nameSegments: List<NameSegment>
    get() = listOf(NameSegment(0, matchedName.length, this))

  @JvmDefault
  val contextContainers: Sequence<WebSymbolsContainer>
    get() = sequenceOf(this)

  @JvmDefault
  @get:NlsSafe
  val name: String
    get() = matchedName

  @JvmDefault
  val description: String?
    get() = null

  @JvmDefault
  val descriptionSections: Map<String, String>
    get() = emptyMap()

  @JvmDefault
  val docUrl: String?
    get() = null

  @JvmDefault
  val icon: Icon?
    get() = null

  @JvmDefault
  @get:JvmName("isDeprecated")
  val deprecated: Boolean
    get() = false

  @JvmDefault
  @get:JvmName("isExperimental")
  val experimental: Boolean
    get() = false

  @JvmDefault
  @get:JvmName("isVirtual")
  val virtual: Boolean
    get() = false

  @JvmDefault
  @get:JvmName("isAbstract")
  val abstract: Boolean
    get() = false

  @JvmDefault
  @get:JvmName("isExtension")
  val extension: Boolean
    get() = false

  @JvmDefault
  @get:JvmName("isRequired")
  val required: Boolean?
    get() = null

  @JvmDefault
  val defaultValue: String?
    get() = null

  @JvmDefault
  val priority: Priority?
    get() = null

  @JvmDefault
  val proximity: Int?
    get() = null

  // TODO: rename to `type`
  @JvmDefault
  val jsType: Any?
    get() = null

  @JvmDefault
  val attributeValue: AttributeValue?
    get() = null

  @JvmDefault
  val pattern: WebSymbolsPattern?
    get() = null

  @JvmDefault
  val properties: Map<String, Any>
    get() = emptyMap()

  @JvmDefault
  val documentation: WebSymbolDocumentation?
    get() = WebSymbolDocumentation.create(this)

  @JvmDefault
  override fun getDocumentationTarget(): DocumentationTarget =
    WebSymbolDocumentationTarget(this)

  @JvmDefault
  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> =
    emptyList()

  @JvmDefault
  override fun getSymbolPresentation(): SymbolPresentation {
    @Suppress("HardCodedStringLiteral")
    val description = if (name.contains(' ')) {
      "${name} ${matchedName}"
    }
    else {
      // TODO use kind description provider
      val kindName = kind.replace('-', ' ').lowercase(Locale.US).let {
        when {
          it.endsWith("ies") -> it.substring(0, it.length - 3) + "y"
          it.endsWith("ses") -> it.substring(0, it.length - 2)
          it.endsWith("s") -> it.substring(0, it.length - 1)
          else -> it
        }
      }
      "${namespace.name} $kindName '$matchedName'"
    }
    return SymbolPresentation.create(icon, name, description, description)
  }

  @JvmDefault
  val presentation: TargetPresentation
    get() = symbolPresentation.let {
      TargetPresentation.builder(it.shortDescription)
        .icon(it.icon)
        .presentation()
    }

  @JvmDefault
  fun isEquivalentTo(symbol: Symbol): Boolean =
    this == symbol

  @JvmDefault
  fun adjustNameForRefactoring(registry: WebSymbolsRegistry, newName: String, occurence: String): String =
    registry.namesProvider.adjustRename(namespace, kind, matchedName, newName, occurence)

  @JvmDefault
  fun validateName(name: String): String? = null

  /**
   * null values might be replaced ("shadowed") by sibling WebSymbols while merging. Otherwise, default values are applied as the last step.
   *
   * @see com.intellij.javascript.web.symbols.impl.merge
   */
  interface AttributeValue {
    /** Default: PLAIN */
    @JvmDefault
    val kind: AttributeValueKind?
      get() = null

    @JvmDefault
    val type: AttributeValueType?
      get() = null

    /** Default: true */
    @JvmDefault
    @get:JvmName("isRequired")
    val required: Boolean?
      get() = null

    @JvmDefault
    val default: String?
      get() = null

    // TODO: rename
    @JvmDefault
    val jsType: Any?
      get() = null
  }

  enum class AttributeValueKind {
    PLAIN,
    EXPRESSION,
    NO_VALUE
  }

  enum class AttributeValueType {
    BOOLEAN,
    NUMBER,
    STRING,
    ENUM,
    JAVASCRIPT,
    OF_MATCH
  }

  data class SymbolType(
    val namespace: WebSymbolsContainer.Namespace,
    val kind: SymbolKind,
  )

  class NameSegment(val start: Int,
                    val end: Int,
                    val symbols: List<WebSymbol> = emptyList(),
                    val problem: MatchProblem? = null,
                    @NlsSafe
                    val displayName: String? = null,
                    val matchScore: Int = end - start,
                    symbolTypes: Set<SymbolType>? = null,
                    private val explicitDeprecated: Boolean? = null,
                    private val explicitPriority: Priority? = null,
                    private val explicitProximity: Int? = null) {

    constructor(start: Int, end: Int, symbol: WebSymbol) : this(start, end, listOf(symbol))
    constructor(start: Int, end: Int, vararg symbols: WebSymbol) : this(start, end, symbols.toList())

    init {
      assert(start <= end)
    }

    @get:JvmName("isDeprecated")
    val deprecated: Boolean
      get() = explicitDeprecated ?: symbols.any { it.deprecated }
    val priority: Priority?
      get() = explicitPriority ?: symbols.asSequence().mapNotNull { it.priority }.maxOrNull()
    val proximity: Int?
      get() = explicitProximity ?: symbols.asSequence().mapNotNull { it.proximity }.maxOrNull()

    private val forcedSymbolTypes = symbolTypes

    val symbolTypes: Set<SymbolType>
      get() =
        forcedSymbolTypes
        ?: symbols.asSequence().map { SymbolType(it.namespace, it.kind) }.toSet()

    fun getName(symbol: WebSymbol): String =
      symbol.matchedName.substring(start, end)

    fun withOffset(offset: Int): NameSegment =
      NameSegment(start + offset, end + offset, symbols, problem, displayName,
                  matchScore, symbolTypes, explicitDeprecated, explicitPriority, explicitProximity)

    fun applyProperties(deprecated: Boolean? = null,
                        priority: Priority? = null,
                        proximity: Int? = null,
                        problem: MatchProblem? = null,
                        symbols: List<WebSymbol> = emptyList()): NameSegment =
      NameSegment(start, end, this.symbols + symbols, problem ?: this.problem,
                  displayName, matchScore, symbolTypes,
                  deprecated ?: this.explicitDeprecated, priority ?: this.explicitPriority,
                  proximity ?: this.explicitProximity)

    fun canUnwrapSymbols(): Boolean =
      explicitDeprecated == null
      && problem == null
      && displayName == null
      && matchScore == end - start
      && explicitPriority == null
      && explicitProximity == null
      && symbols.isNotEmpty()

    fun createPointer(): Pointer<NameSegment> =
      NameSegmentPointer(this)

    override fun toString(): String {
      return "<$start:$end${if (problem != null) ":$problem" else ""}-${symbols.size}cs>"
    }

    private class NameSegmentPointer(nameSegment: NameSegment) : Pointer<NameSegment> {

      private val start = nameSegment.start
      private val end = nameSegment.end
      private val symbols = nameSegment.symbols.map { it.createPointer() }
      private val problem = nameSegment.problem

      @NlsSafe
      private val displayName = nameSegment.displayName
      private val matchScore = nameSegment.matchScore
      private val types = nameSegment.symbolTypes
      private val explicitDeprecated = nameSegment.explicitDeprecated
      private val explicitPriority = nameSegment.explicitPriority
      private val explicitProximity = nameSegment.explicitProximity


      override fun dereference(): NameSegment? =
        symbols.map { it.dereference() }
          .takeIf { it.all { symbol -> symbol != null } }
          ?.let {
            @Suppress("UNCHECKED_CAST")
            (NameSegment(start, end, it as List<WebSymbol>, problem, displayName, matchScore,
                         types, explicitDeprecated, explicitPriority, explicitProximity))
          }

    }
  }

  enum class MatchProblem {
    MISSING_REQUIRED_PART,
    UNKNOWN_ITEM,
    DUPLICATE
  }

  enum class Priority(val value: Double) {
    LOWEST(0.0),
    LOW(1.0),
    NORMAL(10.0),
    HIGH(50.0),
    HIGHEST(100.0);
  }

  companion object {
    const val KIND_HTML_ELEMENTS = "elements"
    const val KIND_HTML_ATTRIBUTES = "attributes"
    const val KIND_HTML_ATTRIBUTE_VALUES = "values"
    const val KIND_HTML_SLOTS = "slots"

    const val KIND_CSS_PROPERTIES = "properties"
    const val KIND_CSS_PSEUDO_ELEMENTS = "pseudo-elements"
    const val KIND_CSS_PSEUDO_CLASSES = "pseudo-classes"
    const val KIND_CSS_FUNCTIONS = "functions"
    const val KIND_CSS_CLASSES = "classes"

    const val KIND_JS_EVENTS = "events"
    const val KIND_JS_PROPERTIES = "properties"

    /** Specify language to inject in an HTML element */
    const val PROP_INJECT_LANGUAGE = "inject-language"

    /** Specify to hide pattern section in the documentation */
    const val PROP_DOC_HIDE_PATTERN = "doc-hide-pattern"

    /** Specify to hide item from code completion */
    const val PROP_HIDE_FROM_COMPLETION = "hide-from-completion"

    /**
     * Name of boolean property used by pseudo-elements and pseudo-classes
     * to specify whether they require arguments. Defaults to false.
     **/
    const val PROP_ARGUMENTS = "arguments"

    private fun WebSymbol.findSymbolsAt(offset: Int): List<WebSymbol> =
      nameSegments.takeIf { it.size >= 2 || this is WebSymbolMatch }
        ?.asSequence()
        ?.filter { it.symbols.isNotEmpty() }
        ?.find { it.start <= offset && offset <= it.end }
        ?.let { segment ->
          segment.symbols.flatMap {
            it.findSymbolsAt(offset - segment.start)
          }
        }
      ?: listOf(this)

    val (MatchProblem?).isCritical
      get() = this == MatchProblem.MISSING_REQUIRED_PART || this == MatchProblem.UNKNOWN_ITEM
  }
}
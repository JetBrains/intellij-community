// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.completion.impl

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.*
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolApiStatus
import com.intellij.polySymbols.PolySymbolApiStatus.Companion.isDeprecatedOrObsolete
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItemBuilder
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItemInsertHandler
import com.intellij.polySymbols.impl.scaleToHeight
import com.intellij.polySymbols.query.PolySymbolDefaultIconProvider
import com.intellij.polySymbols.search.PsiSourcedPolySymbol
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

internal data class PolySymbolCodeCompletionItemImpl(
  override val name: String,
  override val offset: Int = 0,
  override val completeAfterInsert: Boolean = false,
  override val completeAfterChars: Set<Char> = emptySet(),
  override val displayName: String? = null,
  override val symbol: PolySymbol? = null,
  override val priority: PolySymbol.Priority? = null,
  override val proximity: Int? = null,
  override val apiStatus: PolySymbolApiStatus = PolySymbolApiStatus.Stable,
  override val aliases: Set<String> = emptySet(),
  override val icon: Icon? = null,
  val typeTextStatic: String? = null,
  val typeTextProvider: (() -> String?)? = null,
  override val tailText: String? = null,
  override val insertHandler: PolySymbolCodeCompletionItemInsertHandler? = null,
  @get:ApiStatus.Internal
  val stopSequencePatternEvaluation: Boolean = false,
) : PolySymbolCodeCompletionItem {

  override val typeText: String? get() = typeTextProvider?.invoke() ?: typeTextStatic

  override fun addToResult(
    parameters: CompletionParameters,
    result: CompletionResultSet,
    baselinePriorityValue: Double,
  ) {
    val completionPrefixMatcher = result.prefixMatcher
    val completionPrefix = completionPrefixMatcher.prefix
    if (completionPrefix.length < offset ||
        completionPrefix.substring(offset) == name)
      return
    val priorityOffset = baselinePriorityValue - PolySymbol.Priority.NORMAL.value
    val deprecatedOrObsolete = apiStatus.isDeprecatedOrObsolete()
    LookupElementBuilder
      .create(wrapSymbolForDocumentation(symbol, parameters.position)?.createPointer() ?: name, name)
      .withLookupStrings(aliases)
      .withIcon(icon?.scaleToHeight(16))
      .withTypeText(typeTextStatic, true)
      .let {
        if (typeTextProvider != null)
          it.withExpensiveRenderer(object : LookupElementRenderer<LookupElement>() {
            override fun renderElement(element: LookupElement, presentation: LookupElementPresentation) {
              element.renderElement(presentation)
              typeTextProvider()?.let { presentation.typeText = it }
            }
          })
        else it
      }
      .withTailText(tailText, true)
      .withBoldness(!deprecatedOrObsolete && priority != null && priority >= PolySymbol.Priority.HIGHEST)
      .withStrikeoutness(deprecatedOrObsolete)
      .let {
        if (displayName != null)
          it.withPresentableText(displayName)
        else it
      }
      .withInsertHandler { insertionContext, completionItem ->
        val invokeCompletion = completeAfterInsert || completeAfterChars.contains(insertionContext.completionChar)
        insertHandler?.prepare(insertionContext, completionItem, invokeCompletion)?.run()
        if (invokeCompletion) {
          insertionContext.setLaterRunnable {
            CodeCompletionHandlerBase(CompletionType.BASIC)
              .invokeCompletion(parameters.originalFile.project, parameters.editor)
          }
        }
      }.let {
        if (completeAfterChars.isNotEmpty())
          it.withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE)
        else it
      }.let {
        val priorityValue = if (deprecatedOrObsolete) PolySymbol.Priority.LOWEST.value
        else (priority ?: PolySymbol.Priority.NORMAL).value + priorityOffset
        PrioritizedLookupElement.withPriority(it, priorityValue)
      }.let {
        PrioritizedLookupElement.withExplicitProximity(it, proximity ?: 0)
      }.let {
        (if (offset > 0) result.withPrefixMatcher(completionPrefixMatcher.cloneWithPrefix(completionPrefix.substring(offset)))
        else result).addElement(it)
      }
  }

  private fun wrapSymbolForDocumentation(symbol: PolySymbol?, location: PsiElement) =
    when (symbol) {
      is PsiSourcedPolySymbol -> PsiSourcedCodeCompletionPolySymbolWithDocumentation(symbol, location)
      is PolySymbol -> CodeCompletionPolySymbolWithDocumentation(symbol, location)
      else -> null
    }

  override fun withName(name: String): PolySymbolCodeCompletionItem =
    copy(name = name)

  override fun withPrefix(prefix: String): PolySymbolCodeCompletionItem =
    if (prefix.isEmpty())
      this
    else
      copy(
        name = prefix + name,
        offset = offset - prefix.length,
        displayName = if (displayName != null) prefix + displayName else null,
        aliases = aliases.asSequence().map { prefix + it }.toSet()
      )

  override fun withOffset(offset: Int): PolySymbolCodeCompletionItem =
    copy(offset = offset)

  override fun withCompleteAfterInsert(completeAfterInsert: Boolean): PolySymbolCodeCompletionItem =
    copy(completeAfterInsert = completeAfterInsert)

  override fun withDisplayName(displayName: String?): PolySymbolCodeCompletionItem =
    copy(displayName = displayName)

  override fun withSymbol(symbol: PolySymbol?): PolySymbolCodeCompletionItem =
    copy(symbol = symbol)

  override fun withPriority(priority: PolySymbol.Priority?): PolySymbolCodeCompletionItem =
    copy(priority = priority)

  override fun withProximity(proximity: Int): PolySymbolCodeCompletionItem =
    copy(proximity = proximity)

  override fun withApiStatus(apiStatus: PolySymbolApiStatus): PolySymbolCodeCompletionItem =
    copy(apiStatus = apiStatus)

  override fun withAliasesReplaced(aliases: Set<String>): PolySymbolCodeCompletionItem =
    copy(aliases = aliases)

  override fun withAliasesAdded(aliases: Set<String>): PolySymbolCodeCompletionItem =
    copy(aliases = this.aliases + aliases)

  override fun withAliasAdded(alias: String): PolySymbolCodeCompletionItem =
    copy(aliases = this.aliases + alias)

  override fun withIcon(icon: Icon?): PolySymbolCodeCompletionItem =
    copy(icon = icon)

  override fun withTypeText(typeText: String?): PolySymbolCodeCompletionItem =
    copy(typeTextStatic = typeText)

  override fun withTypeText(typeTextProvider: () -> String?): PolySymbolCodeCompletionItem =
    copy(typeTextProvider = typeTextProvider)

  override fun withTailText(tailText: String?): PolySymbolCodeCompletionItem =
    copy(tailText = tailText)

  override fun withCompleteAfterChar(char: Char): PolySymbolCodeCompletionItem =
    copy(completeAfterChars = if (!completeAfterInsert) completeAfterChars + char else emptySet())

  override fun withCompleteAfterCharsAdded(chars: List<Char>): PolySymbolCodeCompletionItem =
    copy(completeAfterChars = if (!completeAfterInsert) completeAfterChars + chars else emptySet())

  override fun withInsertHandlerReplaced(insertHandler: PolySymbolCodeCompletionItemInsertHandler?): PolySymbolCodeCompletionItem =
    copy(insertHandler = insertHandler)

  override fun withInsertHandlerAdded(
    insertHandler: InsertHandler<LookupElement>,
    priority: PolySymbol.Priority,
  ): PolySymbolCodeCompletionItem =
    withInsertHandlerAdded(PolySymbolCodeCompletionItemInsertHandler.adapt(insertHandler, priority))

  override fun withInsertHandlerAdded(insertHandler: PolySymbolCodeCompletionItemInsertHandler): PolySymbolCodeCompletionItem =
    copy(insertHandler = CompoundInsertHandler.merge(this.insertHandler, insertHandler))


  fun with(
    name: String = this.name,
    offset: Int = this.offset,
    completeAfterInsert: Boolean = this.completeAfterInsert,
    completeAfterChars: Set<Char> = this.completeAfterChars,
    displayName: String? = this.displayName,
    symbol: PolySymbol? = this.symbol,
    priority: PolySymbol.Priority? = this.priority,
    proximity: Int? = this.proximity,
    apiStatus: PolySymbolApiStatus = this.apiStatus,
    icon: Icon? = this.icon,
    typeText: String? = null,
    tailText: String? = this.tailText,
  ): PolySymbolCodeCompletionItem =
    copy(name = name, offset = offset, completeAfterInsert = completeAfterInsert,
         completeAfterChars = if (!completeAfterInsert) completeAfterChars else emptySet(),
         displayName = displayName, symbol = symbol, priority = priority, proximity = proximity,
         apiStatus = apiStatus, icon = icon, typeTextStatic = typeText ?: typeTextStatic,
         tailText = tailText)

  class BuilderImpl(
    private var name: String,
    override var offset: Int = 0,
    override var symbol: PolySymbol? = null,
  ) : PolySymbolCodeCompletionItemBuilder {

    override var completeAfterInsert: Boolean = false
    override var completeAfterChars: Set<Char> = emptySet()
    override var displayName: String? = null
    override var priority: PolySymbol.Priority? = symbol?.priority
    override var proximity: Int? = null
    override var apiStatus: PolySymbolApiStatus = symbol?.apiStatus ?: PolySymbolApiStatus.Stable
    override var aliases: Set<String> = emptySet()
    override var icon: Icon? = symbol?.let {
      it.icon
      ?: it.origin.defaultIcon
      ?: PolySymbolDefaultIconProvider.get(it.qualifiedKind)
    }
    private var typeTextStatic: String? = null
    private var typeTextProvider: (() -> String?)? = null
    override var tailText: String? = null
    override var insertHandler: PolySymbolCodeCompletionItemInsertHandler? = null
    private var stopSequencePatternEvaluation: Boolean = false

    override val typeText: String? get() = typeTextProvider?.invoke() ?: typeTextStatic

    override fun build(): PolySymbolCodeCompletionItem = PolySymbolCodeCompletionItemImpl(
      name, offset, completeAfterInsert, completeAfterChars, displayName, symbol, priority, proximity,
      apiStatus, aliases, icon, typeTextStatic, typeTextProvider, tailText, insertHandler, stopSequencePatternEvaluation)

    override fun displayName(value: String?): PolySymbolCodeCompletionItemBuilder {
      displayName = value
      return this
    }

    override fun offset(value: Int): PolySymbolCodeCompletionItemBuilder {
      offset = value
      return this
    }

    override fun icon(value: Icon?): PolySymbolCodeCompletionItemBuilder {
      icon = value
      return this
    }

    override fun typeText(value: String?): PolySymbolCodeCompletionItemBuilder {
      typeTextStatic = value
      return this
    }

    override fun typeText(provider: () -> String?): PolySymbolCodeCompletionItemBuilder {
      typeTextProvider = provider
      return this
    }

    override fun tailText(value: String?): PolySymbolCodeCompletionItemBuilder {
      tailText = value
      return this
    }

    override fun completeAfterInsert(value: Boolean): PolySymbolCodeCompletionItemBuilder {
      completeAfterInsert = value
      return this
    }

    override fun completeAfterChars(value: Set<Char>): PolySymbolCodeCompletionItemBuilder {
      completeAfterChars = value
      return this
    }

    override fun priority(value: PolySymbol.Priority?): PolySymbolCodeCompletionItemBuilder {
      priority = value
      return this
    }

    override fun proximity(value: Int?): PolySymbolCodeCompletionItemBuilder {
      proximity = value
      return this
    }

    override fun apiStatus(value: PolySymbolApiStatus): PolySymbolCodeCompletionItemBuilder {
      apiStatus = value
      return this
    }

    override fun aliases(value: Set<String>): PolySymbolCodeCompletionItemBuilder {
      aliases = value
      return this
    }

    override fun symbol(value: PolySymbol?): PolySymbolCodeCompletionItemBuilder {
      symbol = value
      return this
    }

    override fun insertHandler(value: PolySymbolCodeCompletionItemInsertHandler?): PolySymbolCodeCompletionItemBuilder {
      insertHandler = value
      return this
    }

    override fun insertHandler(value: InsertHandler<LookupElement>?): PolySymbolCodeCompletionItemBuilder {
      insertHandler = value?.let { PolySymbolCodeCompletionItemInsertHandler.adapt(it, PolySymbol.Priority.NORMAL) }
      return this
    }

  }

}

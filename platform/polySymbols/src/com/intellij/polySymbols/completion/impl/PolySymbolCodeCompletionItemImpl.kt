// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.completion.impl

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.AutoCompletionPolicy
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
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
  override val typeText: String? = null,
  override val typeTextGreyed: Boolean = true,
  override val tailText: String? = null,
  override val tailTextGreyed: Boolean = true,
  override val caseSensitive: Boolean = true,
  override val asyncCustomizers: List<PolySymbolCodeCompletionItem.() -> PolySymbolCodeCompletionItem> = emptyList(),
  override val insertHandler: PolySymbolCodeCompletionItemInsertHandler? = null,
  @get:ApiStatus.Internal
  val stopSequencePatternEvaluation: Boolean = false,
  val restartCompletionOnAnyPrefixChange: Boolean = false,
  val restartCompletionOnPrefixChange: Set<String> = emptySet(),
) : PolySymbolCodeCompletionItem {

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
    buildLookupElement(parameters.position, priorityOffset, parameters.originalFile.project, parameters.editor)
      .let {
        val result =
          if (offset > 0)
            result.withPrefixMatcher(completionPrefixMatcher.cloneWithPrefix(completionPrefix.substring(offset)))
          else
            result
        if (restartCompletionOnAnyPrefixChange) {
          result.restartCompletionOnAnyPrefixChange()
        }
        else if (restartCompletionOnPrefixChange.isNotEmpty()) {
          restartCompletionOnPrefixChange.forEach { prefix -> result.restartCompletionOnPrefixChange(prefix) }
        }
        result.addElement(it)
      }
  }

  override fun buildLookupElement(location: PsiElement): LookupElement =
    buildLookupElement(location, 0.0, null, null)

  private fun buildLookupElement(location: PsiElement, priorityOffset: Double, project: Project?, editor: Editor?): LookupElement =
    LookupElementBuilder
      .create(wrapSymbolForDocumentation(symbol, location)?.createPointer() ?: name, name)
      .withLookupStrings(aliases)
      .withCaseSensitivity(caseSensitive)
      .withInsertHandler { insertionContext, completionItem ->
        val invokeCompletion = completeAfterInsert || completeAfterChars.contains(insertionContext.completionChar)
        insertHandler?.prepare(insertionContext, completionItem, invokeCompletion)?.run()
        if (invokeCompletion && project != null && editor != null) {
          insertionContext.setLaterRunnable {
            CodeCompletionHandlerBase(CompletionType.BASIC)
              .invokeCompletion(project, editor)
          }
        }
      }
      .withRenderer(object : LookupElementRenderer<LookupElement?>() {
        override fun renderElement(
          element: LookupElement,
          presentation: LookupElementPresentation,
        ) {
          renderPresentation(priorityOffset, presentation)
        }
      })
      .let {
        if (asyncCustomizers.isNotEmpty())
          it.withExpensiveRenderer(object : LookupElementRenderer<LookupElement>() {
            override fun renderElement(element: LookupElement, presentation: LookupElementPresentation) {
              element.renderElement(presentation)
              val completionItem = asyncCustomizers
                .fold(this@PolySymbolCodeCompletionItemImpl as PolySymbolCodeCompletionItem) { item, customizer ->
                  customizer(item)
                }
              (completionItem as PolySymbolCodeCompletionItemImpl).renderPresentation(priorityOffset, presentation)
            }
          })
        else it
      }.let {
        if (completeAfterChars.isNotEmpty())
          it.withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE)
        else it
      }.let {
        val priorityValue = if (apiStatus.isDeprecatedOrObsolete()) PolySymbol.Priority.LOWEST.value
        else (priority ?: PolySymbol.Priority.NORMAL).value + priorityOffset
        PrioritizedLookupElement.withPriority(it, priorityValue)
      }.let {
        PrioritizedLookupElement.withExplicitProximity(it, proximity ?: 0)
      }

  private fun renderPresentation(priorityOffset: Double, presentation: LookupElementPresentation) {
    val deprecatedOrObsolete = apiStatus.isDeprecatedOrObsolete()
    presentation.itemText = displayName ?: name
    presentation.isItemTextBold =
      !deprecatedOrObsolete && priority != null && (priority.value + priorityOffset) >= PolySymbol.Priority.HIGHEST.value
    presentation.isStrikeout = deprecatedOrObsolete
    presentation.icon = icon?.scaleToHeight(16)
    presentation.clearTail()
    if (tailText != null) {
      presentation.appendTailText(tailText, tailTextGreyed)
    }
    presentation.typeText = typeText
    presentation.isTypeGrayed = typeTextGreyed
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
    copy(typeText = typeText)

  override fun withTypeText(typeText: String?, greyed: Boolean): PolySymbolCodeCompletionItem =
    copy(typeText = typeText, typeTextGreyed = greyed)

  override fun withTailText(tailText: String?): PolySymbolCodeCompletionItem =
    copy(tailText = tailText)

  override fun withTailText(tailText: String?, greyed: Boolean): PolySymbolCodeCompletionItem =
    copy(tailText = tailText, tailTextGreyed = greyed)

  override fun withCaseSensitive(value: Boolean): PolySymbolCodeCompletionItem =
    copy(caseSensitive = value)

  override fun withCompleteAfterChar(char: Char): PolySymbolCodeCompletionItem =
    copy(completeAfterChars = if (!completeAfterInsert) completeAfterChars + char else emptySet())

  override fun withCompleteAfterCharsAdded(chars: List<Char>): PolySymbolCodeCompletionItem =
    copy(completeAfterChars = if (!completeAfterInsert) completeAfterChars + chars else emptySet())

  override fun withAsyncCustomizer(asyncCustomizer: PolySymbolCodeCompletionItem.() -> PolySymbolCodeCompletionItem): PolySymbolCodeCompletionItem =
    copy(asyncCustomizers = asyncCustomizers + asyncCustomizer)

  override fun withInsertHandlerReplaced(insertHandler: PolySymbolCodeCompletionItemInsertHandler?): PolySymbolCodeCompletionItem =
    copy(insertHandler = insertHandler)

  override fun withInsertHandlerAdded(
    insertHandler: InsertHandler<LookupElement>,
    priority: PolySymbol.Priority,
  ): PolySymbolCodeCompletionItem =
    withInsertHandlerAdded(PolySymbolCodeCompletionItemInsertHandler.adapt(insertHandler, priority))

  override fun withInsertHandlerAdded(insertHandler: PolySymbolCodeCompletionItemInsertHandler): PolySymbolCodeCompletionItem =
    copy(insertHandler = CompoundInsertHandler.merge(this.insertHandler, insertHandler))

  override fun withCompletionRestartedOnAnyPrefixChange(): PolySymbolCodeCompletionItem =
    copy(restartCompletionOnAnyPrefixChange = true)

  override fun withCompletionRestartedOnPrefixChange(prefix: String): PolySymbolCodeCompletionItem =
    copy(restartCompletionOnPrefixChange = restartCompletionOnPrefixChange + prefix)

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
    typeText: String? = this.typeText,
    tailText: String? = this.tailText,
  ): PolySymbolCodeCompletionItem =
    copy(name = name, offset = offset, completeAfterInsert = completeAfterInsert,
         completeAfterChars = if (!completeAfterInsert) completeAfterChars else emptySet(),
         displayName = displayName, symbol = symbol, priority = priority, proximity = proximity,
         apiStatus = apiStatus, icon = icon, typeText = typeText,
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
      ?: PolySymbolDefaultIconProvider.get(it.kind)
    }
    override var typeText: String? = null
    override var typeTextGreyed: Boolean = true
    override var tailText: String? = null
    override var tailTextGreyed: Boolean = true
    override var caseSensitive: Boolean = true
    override var insertHandler: PolySymbolCodeCompletionItemInsertHandler? = null
    private var asyncCustomizers: MutableList<PolySymbolCodeCompletionItem.() -> PolySymbolCodeCompletionItem> = mutableListOf()
    private var stopSequencePatternEvaluation: Boolean = false
    private var restartCompletionOnAnyPrefixChange: Boolean = false
    private var restartCompletionOnPrefixChange: MutableSet<String> = mutableSetOf()

    override fun build(): PolySymbolCodeCompletionItem = PolySymbolCodeCompletionItemImpl(
      name, offset, completeAfterInsert, completeAfterChars, displayName, symbol, priority, proximity,
      apiStatus, aliases, icon, typeText, typeTextGreyed,
      tailText, tailTextGreyed, caseSensitive, asyncCustomizers, insertHandler, stopSequencePatternEvaluation)

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
      typeText = value
      return this
    }

    override fun typeText(value: String?, greyed: Boolean): PolySymbolCodeCompletionItemBuilder {
      typeText = value
      typeTextGreyed = greyed
      return this
    }

    override fun tailText(value: String?): PolySymbolCodeCompletionItemBuilder {
      tailText = value
      return this
    }

    override fun tailText(value: String?, greyed: Boolean): PolySymbolCodeCompletionItemBuilder {
      tailText = value
      tailTextGreyed = greyed
      return this
    }

    override fun caseSensitive(value: Boolean): PolySymbolCodeCompletionItemBuilder {
      caseSensitive = value
      return this
    }

    override fun asyncCustomizer(value: PolySymbolCodeCompletionItem.() -> PolySymbolCodeCompletionItem): PolySymbolCodeCompletionItemBuilder {
      asyncCustomizers.add(value)
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

    override fun restartCompletionOnPrefixChange(prefix: String): PolySymbolCodeCompletionItemBuilder {
      restartCompletionOnPrefixChange.add(prefix)
      return this
    }

    override fun restartCompletionOnAnyPrefixChange(): PolySymbolCodeCompletionItemBuilder {
      restartCompletionOnAnyPrefixChange = true
      return this
    }

  }

}

package com.intellij.javascript.web.symbols

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.PrioritizedLookupElement.withExplicitProximity
import com.intellij.codeInsight.completion.PrioritizedLookupElement.withPriority
import com.intellij.codeInsight.lookup.AutoCompletionPolicy
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.javascript.web.symbols.impl.CompoundInsertHandler
import com.intellij.javascript.web.symbols.impl.WebSymbolCodeCompletionItemImpl
import com.intellij.javascript.web.symbols.impl.scaleToHeight
import com.intellij.model.Pointer
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus.NonExtendable
import javax.swing.Icon

/**
 * INAPPLICABLE_JVM_NAME -> https://youtrack.jetbrains.com/issue/KT-31420
 * DEPRECATION -> @JvmDefault
 **/
@Suppress("INAPPLICABLE_JVM_NAME", "DEPRECATION")
@NonExtendable
interface WebSymbolCodeCompletionItem {
  val name: String
  val displayName: String?
  val offset: Int
  val icon: Icon?
  val typeText: String?
  val tailText: String?

  @get:JvmName("isCompleteAfterInsert")
  val completeAfterInsert: Boolean
  val completeAfterChars: Set<Char>
  val priority: WebSymbol.Priority?
  val proximity: Int?

  @get:JvmName("isDeprecated")
  val deprecated: Boolean
  val aliases: Set<String>
  val symbol: WebSymbol?
  val insertHandler: WebSymbolCodeCompletionItemInsertHandler?

  @JvmDefault
  fun withPrefix(prefix: String): WebSymbolCodeCompletionItem =
    if (prefix.isEmpty())
      this
    else
      WebSymbolCodeCompletionItemImpl(
        prefix + name,
        offset - prefix.length,
        completeAfterInsert,
        completeAfterChars,
        if (displayName != null) prefix + displayName else null,
        symbol, priority, proximity, deprecated,
        aliases.asSequence().map { prefix + it }.toSet(),
        icon, typeText, tailText, insertHandler,
        stopSequencePatternEvaluation
      )

  @JvmDefault
  fun addToResult(parameters: CompletionParameters,
                  result: CompletionResultSet,
                  baselinePriorityValue: Double = WebSymbol.Priority.NORMAL.value) {
    val completionPrefixMatcher = result.prefixMatcher
    val completionPrefix = completionPrefixMatcher.prefix
    if (completionPrefix.length < offset ||
        completionPrefix.substring(offset) == name)
      return
    val priorityOffset = baselinePriorityValue - WebSymbol.Priority.NORMAL.value
    LookupElementBuilder.create(symbol?.createPointer() ?: name, name)
      .withLookupStrings(aliases)
      .withIcon(icon?.scaleToHeight(16))
      .withTypeText(typeText, true)
      .withTailText(tailText, true)
      .withBoldness(!deprecated && priority == WebSymbol.Priority.HIGHEST)
      .withStrikeoutness(deprecated)
      .let {
        if (displayName != null)
          it.withPresentableText(displayName!!)
        else it
      }
      .let {
        if (completeAfterInsert) {
          it.withInsertHandler { insertionContext, _ ->
            insertionContext.setLaterRunnable {
              CodeCompletionHandlerBase(CompletionType.BASIC)
                .invokeCompletion(parameters.originalFile.project, parameters.editor)
            }
          }
        }
        else if (completeAfterChars.isNotEmpty()) {
          it.withInsertHandler { insertionContext, completionItem ->
            if (completeAfterChars.contains(insertionContext.completionChar)) {
              insertionContext.setLaterRunnable {
                CodeCompletionHandlerBase(CompletionType.BASIC)
                  .invokeCompletion(parameters.originalFile.project, parameters.editor)
              }
            }
            else {
              insertHandler?.prepare(insertionContext, completionItem)?.run()
            }
          }
        }
        else {
          it.withInsertHandler { insertionContext, completionItem ->
            insertHandler?.prepare(insertionContext, completionItem)?.run()
          }
        }
      }.let {
        if (completeAfterChars.isNotEmpty())
          it.withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE)
        else it
      }.let {
        val priorityValue = if (deprecated) WebSymbol.Priority.LOWEST.value
        else (priority ?: WebSymbol.Priority.NORMAL).value + priorityOffset
        withPriority(it, priorityValue)
      }.let {
        withExplicitProximity(it, proximity ?: 0)
      }.let {
        (if (offset > 0) result.withPrefixMatcher(completionPrefixMatcher.cloneWithPrefix(completionPrefix.substring(offset)))
        else result).addElement(it)
      }
  }

  @JvmDefault
  fun withName(name: String): WebSymbolCodeCompletionItem =
    WebSymbolCodeCompletionItemImpl(name, offset, completeAfterInsert, completeAfterChars, displayName, symbol,
                                    priority, proximity, deprecated,
                                    aliases, icon, typeText, tailText, insertHandler,
                                    stopSequencePatternEvaluation)

  @JvmDefault
  fun withOffset(offset: Int): WebSymbolCodeCompletionItem =
    WebSymbolCodeCompletionItemImpl(name, offset, completeAfterInsert, completeAfterChars, displayName, symbol,
                                    priority, proximity, deprecated,
                                    aliases, icon, typeText, tailText, insertHandler,
                                    stopSequencePatternEvaluation)

  @JvmDefault
  fun withCompleteAfterInsert(completeAfterInsert: Boolean): WebSymbolCodeCompletionItem =
    WebSymbolCodeCompletionItemImpl(name, offset, completeAfterInsert, completeAfterChars, displayName, symbol,
                                    priority, proximity, deprecated,
                                    aliases, icon, typeText, tailText, insertHandler,
                                    stopSequencePatternEvaluation)

  @JvmDefault
  fun withDisplayName(displayName: String?): WebSymbolCodeCompletionItem =
    WebSymbolCodeCompletionItemImpl(name, offset, completeAfterInsert, completeAfterChars, displayName, symbol,
                                    priority, proximity, deprecated,
                                    aliases, icon, typeText, tailText, insertHandler,
                                    stopSequencePatternEvaluation)

  @JvmDefault
  fun withSymbol(symbol: WebSymbol?): WebSymbolCodeCompletionItem =
    WebSymbolCodeCompletionItemImpl(name, offset, completeAfterInsert, completeAfterChars, displayName, symbol,
                                    priority, proximity, deprecated,
                                    aliases, icon, typeText, tailText, insertHandler,
                                    stopSequencePatternEvaluation)

  @JvmDefault
  fun withPriority(priority: WebSymbol.Priority?): WebSymbolCodeCompletionItem =
    WebSymbolCodeCompletionItemImpl(name, offset, completeAfterInsert, completeAfterChars, displayName, symbol,
                                    priority, proximity, deprecated,
                                    aliases, icon, typeText, tailText, insertHandler,
                                    stopSequencePatternEvaluation)

  @JvmDefault
  fun withProximity(proximity: Int): WebSymbolCodeCompletionItem =
    WebSymbolCodeCompletionItemImpl(name, offset, completeAfterInsert, completeAfterChars, displayName, symbol,
                                    priority, proximity, deprecated,
                                    aliases, icon, typeText, tailText, insertHandler,
                                    stopSequencePatternEvaluation)

  @JvmDefault
  fun withDeprecated(deprecated: Boolean): WebSymbolCodeCompletionItem =
    WebSymbolCodeCompletionItemImpl(name, offset, completeAfterInsert, completeAfterChars, displayName, symbol,
                                    priority, proximity, deprecated,
                                    aliases, icon, typeText, tailText, insertHandler,
                                    stopSequencePatternEvaluation)

  @JvmDefault
  fun withAliasesReplaced(aliases: Set<String>): WebSymbolCodeCompletionItem =
    WebSymbolCodeCompletionItemImpl(name, offset, completeAfterInsert, completeAfterChars, displayName, symbol,
                                    priority, proximity, deprecated,
                                    aliases, icon, typeText, tailText, insertHandler,
                                    stopSequencePatternEvaluation)

  @JvmDefault
  fun withAliasesAdded(aliases: Set<String>): WebSymbolCodeCompletionItem =
    WebSymbolCodeCompletionItemImpl(name, offset, completeAfterInsert, completeAfterChars, displayName, symbol,
                                    priority, proximity, deprecated,
                                    this.aliases + aliases, icon, typeText, tailText, insertHandler,
                                    stopSequencePatternEvaluation)

  @JvmDefault
  fun withAliasAdded(alias: String): WebSymbolCodeCompletionItem =
    WebSymbolCodeCompletionItemImpl(name, offset, completeAfterInsert, completeAfterChars, displayName, symbol,
                                    priority, proximity, deprecated,
                                    aliases + alias, icon, typeText, tailText, insertHandler,
                                    stopSequencePatternEvaluation)

  @JvmDefault
  fun withIcon(icon: Icon?): WebSymbolCodeCompletionItem =
    WebSymbolCodeCompletionItemImpl(name, offset, completeAfterInsert, completeAfterChars, displayName, symbol,
                                    priority, proximity, deprecated,
                                    aliases, icon, typeText, tailText, insertHandler,
                                    stopSequencePatternEvaluation)

  @JvmDefault
  fun withTypeText(typeText: String?): WebSymbolCodeCompletionItem =
    WebSymbolCodeCompletionItemImpl(name, offset, completeAfterInsert, completeAfterChars, displayName, symbol,
                                    priority, proximity, deprecated,
                                    aliases, icon, typeText, tailText, insertHandler,
                                    stopSequencePatternEvaluation)

  @JvmDefault
  fun withTailText(tailText: String?): WebSymbolCodeCompletionItem =
    WebSymbolCodeCompletionItemImpl(name, offset, completeAfterInsert, completeAfterChars, displayName, symbol,
                                    priority, proximity, deprecated,
                                    aliases, icon, typeText, tailText, insertHandler,
                                    stopSequencePatternEvaluation)

  @JvmDefault
  fun withCompleteAfterChar(char: Char): WebSymbolCodeCompletionItem =
    WebSymbolCodeCompletionItemImpl(name, offset, completeAfterInsert, if (!completeAfterInsert) completeAfterChars + char else emptySet(),
                                    displayName, symbol, priority, proximity, deprecated,
                                    aliases, icon, typeText, tailText, insertHandler,
                                    stopSequencePatternEvaluation)

  @JvmDefault
  fun withCompleteAfterCharsAdded(chars: List<Char>): WebSymbolCodeCompletionItem =
    WebSymbolCodeCompletionItemImpl(name, offset, completeAfterInsert, if (!completeAfterInsert) completeAfterChars + chars else emptySet(),
                                    displayName, symbol, priority, proximity, deprecated,
                                    aliases, icon, typeText, tailText, insertHandler,
                                    stopSequencePatternEvaluation)

  @JvmDefault
  fun withInsertHandlerReplaced(insertHandler: WebSymbolCodeCompletionItemInsertHandler?): WebSymbolCodeCompletionItem =
    WebSymbolCodeCompletionItemImpl(name, offset, completeAfterInsert, completeAfterChars, displayName, symbol,
                                    priority, proximity, deprecated,
                                    aliases, icon, typeText, tailText, insertHandler,
                                    stopSequencePatternEvaluation)

  @JvmDefault
  fun withInsertHandlerAdded(insertHandler: InsertHandler<LookupElement>,
                             priority: WebSymbol.Priority = WebSymbol.Priority.NORMAL): WebSymbolCodeCompletionItem =
    withInsertHandlerAdded(WebSymbolCodeCompletionItemInsertHandler.adapt(insertHandler, priority))

  @JvmDefault
  fun withInsertHandlerAdded(insertHandler: WebSymbolCodeCompletionItemInsertHandler): WebSymbolCodeCompletionItem =
    WebSymbolCodeCompletionItemImpl(name, offset, completeAfterInsert, completeAfterChars, displayName,
                                    symbol, priority, proximity, deprecated,
                                    aliases, icon, typeText, tailText, CompoundInsertHandler.merge(this.insertHandler, insertHandler),
                                    stopSequencePatternEvaluation)

  @JvmDefault
  fun with(name: String = this.name,
           offset: Int = this.offset,
           completeAfterInsert: Boolean = this.completeAfterInsert,
           completeAfterChars: Set<Char> = this.completeAfterChars,
           displayName: String? = this.displayName,
           symbol: WebSymbol? = this.symbol,
           priority: WebSymbol.Priority? = this.priority,
           proximity: Int? = this.proximity,
           deprecated: Boolean = this.deprecated,
           icon: Icon? = this.icon,
           typeText: String? = this.typeText,
           tailText: String? = this.tailText): WebSymbolCodeCompletionItem =
    WebSymbolCodeCompletionItemImpl(name, offset, completeAfterInsert, if (!completeAfterInsert) completeAfterChars else emptySet(),
                                    displayName, symbol, priority, proximity,
                                    deprecated, aliases, icon, typeText, tailText, insertHandler,
                                    stopSequencePatternEvaluation)

  companion object {

    fun create(name: String,
               offset: Int = 0,
               completeAfterInsert: Boolean = false,
               completeAfterChars: Set<Char> = emptySet(),
               displayName: String? = null,
               symbol: WebSymbol? = null,
               priority: WebSymbol.Priority? = symbol?.priority,
               proximity: Int? = symbol?.proximity,
               deprecated: Boolean = symbol?.deprecated ?: false,
               aliases: Set<String> = emptySet(),
               icon: Icon? = symbol?.let {
                 it.icon
                 ?: it.origin.defaultIcon
                 ?: WebSymbolDefaultIconProvider.getDefaultIcon(it.namespace, it.kind)
               },
               typeText: String? = null,
               tailText: String? = null,
               insertHandler: WebSymbolCodeCompletionItemInsertHandler? = null): WebSymbolCodeCompletionItem =
      WebSymbolCodeCompletionItemImpl(name, offset, completeAfterInsert, if (!completeAfterInsert) completeAfterChars else emptySet(),
                                      displayName, symbol, priority, proximity,
                                      deprecated, aliases, icon, typeText, tailText, insertHandler)

    fun getPsiElement(lookupElement: LookupElement): PsiElement? =
      lookupElement.psiElement
      ?: (lookupElement.`object` as? Pointer<*>)
        ?.dereference()
        ?.let { it as? PsiSourcedWebSymbol }
        ?.source

    private val WebSymbolCodeCompletionItem.stopSequencePatternEvaluation
      get() = (this as WebSymbolCodeCompletionItemImpl).stopSequencePatternEvaluation

  }

}
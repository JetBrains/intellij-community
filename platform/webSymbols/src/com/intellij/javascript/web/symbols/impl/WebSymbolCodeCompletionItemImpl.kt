package com.intellij.javascript.web.symbols.impl

import com.intellij.javascript.web.symbols.WebSymbol
import com.intellij.javascript.web.symbols.WebSymbolCodeCompletionItem
import com.intellij.javascript.web.symbols.WebSymbolCodeCompletionItemInsertHandler
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

internal data class WebSymbolCodeCompletionItemImpl(override val name: String,
                                                    override val offset: Int = 0,
                                                    override val completeAfterInsert: Boolean = false,
                                                    override val completeAfterChars: Set<Char> = emptySet(),
                                                    override val displayName: String? = null,
                                                    override val symbol: WebSymbol? = null,
                                                    override val priority: WebSymbol.Priority? = null,
                                                    override val proximity: Int? = null,
                                                    override val deprecated: Boolean = false,
                                                    override val aliases: Set<String> = emptySet(),
                                                    override val icon: Icon? = null,
                                                    override val typeText: String? = null,
                                                    override val tailText: String? = null,
                                                    override val insertHandler: WebSymbolCodeCompletionItemInsertHandler? = null,
                                                    @get:ApiStatus.Internal
                                                    val stopSequencePatternEvaluation: Boolean = false) : WebSymbolCodeCompletionItem
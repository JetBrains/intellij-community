// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsights.impl.base

import com.intellij.openapi.util.text.LineTokenizer
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.lexer.KtTokens

class TemplateTokenSequence(
    private val inputString: String,
    interpolationPrefixLength: Int,
) : Sequence<TemplateChunk> {
    private val templatePrefix: String = "$".repeat(interpolationPrefixLength)
    private val entryPrefixLength: Int = maxOf(templatePrefix.length, 1)
    private val entryPrefix: String = "$".repeat(entryPrefixLength)

    private fun String.guessIsTemplateEntryStart(): Boolean = when {
        startsWith("$entryPrefix{") -> {
            true
        }
        length > entryPrefixLength && substring(0..< entryPrefixLength).all { it == '$' } -> {
            val guessedIdentifier = substring(entryPrefixLength)
            val tokenType = KotlinLexer().apply { start(guessedIdentifier) }.tokenType
            tokenType == KtTokens.IDENTIFIER || tokenType == KtTokens.THIS_KEYWORD
        }
        else -> false
    }

    private fun findTemplateEntryEnd(input: String, from: Int): Int {
        val wrapped = """$templatePrefix"${input.substring(from)}""""
        val lexer = KotlinLexer().apply { start(wrapped) }.apply {
            if (templatePrefix.isNotEmpty()) advance() // template interpolation prefix
            advance() // opening quote
        }

        when (lexer.tokenType) {
            KtTokens.SHORT_TEMPLATE_ENTRY_START -> {
                lexer.advance()
                val tokenType = lexer.tokenType
                return if (tokenType == KtTokens.IDENTIFIER || tokenType == KtTokens.THIS_KEYWORD) {
                    val offsetBeforeEntry = templatePrefix.length + 1 // prefix + quote
                    from + lexer.tokenEnd - offsetBeforeEntry
                } else {
                    -1
                }
            }
            KtTokens.LONG_TEMPLATE_ENTRY_START -> {
                var depth = 0
                while (lexer.tokenType != null) {
                    if (lexer.tokenType == KtTokens.LONG_TEMPLATE_ENTRY_START) {
                        depth++
                    } else if (lexer.tokenType == KtTokens.LONG_TEMPLATE_ENTRY_END) {
                        depth--
                        if (depth == 0) {
                            return from + lexer.currentPosition.offset
                        }
                    }
                    lexer.advance()
                }
                return -1
            }
            else -> return -1
        }
    }

    private suspend fun SequenceScope<TemplateChunk>.yieldLiteral(chunk: String) {
        val splitLines = LineTokenizer.tokenize(chunk, false, false)
        for (i in splitLines.indices) {
            if (i != 0) {
                yield(NewLineChunk)
            }
            splitLines[i].takeIf { it.isNotEmpty() }?.let { yield(LiteralChunk(it)) }
        }
    }

    private fun templateChunkIterator(): Iterator<TemplateChunk> {
        return if (inputString.isEmpty()) emptySequence<TemplateChunk>().iterator()
        else
            iterator {
                var from = 0
                var to = 0
                while (to < inputString.length) {
                    val c = inputString[to]
                    if (c == '\\') {
                        to += 1
                        if (to < inputString.length) to += 1
                        continue
                    } else if (c == '$') {
                        val substring = inputString.substring(to)
                        val guessIsTemplateEntryStart = substring.guessIsTemplateEntryStart()
                        if (guessIsTemplateEntryStart) {
                            if (from < to) yieldLiteral(inputString.substring(from until to))
                            from = to
                            to = findTemplateEntryEnd(inputString, from)
                            if (to != -1) {
                                yield(EntryChunk(inputString.substring(from until to)))
                            } else {
                                to = inputString.length
                                yieldLiteral(inputString.substring(from until to))
                            }
                            from = to
                            continue
                        }
                    }
                    to++
                }
                if (from < to) {
                    yieldLiteral(inputString.substring(from until to))
                }
            }
    }

    override fun iterator(): Iterator<TemplateChunk> = templateChunkIterator()
}

sealed class TemplateChunk
data class LiteralChunk(val text: String) : TemplateChunk()
data class EntryChunk(val text: String) : TemplateChunk()
object NewLineChunk : TemplateChunk()

@TestOnly
fun createTemplateSequenceTokenString(input: String, prefixLength: Int): String {
    return TemplateTokenSequence(input, prefixLength).map {
        when (it) {
            is LiteralChunk -> "LITERAL_CHUNK(${it.text})"
            is EntryChunk -> "ENTRY_CHUNK(${it.text})"
            is NewLineChunk -> "NEW_LINE()"
        }
    }.joinToString(separator = "")
}

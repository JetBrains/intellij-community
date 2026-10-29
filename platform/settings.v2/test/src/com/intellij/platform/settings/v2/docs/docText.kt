// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.docs

import java.nio.file.Path
import kotlin.io.path.readText

/** Reads [file] with every line separator turned into `\n`, so the patterns of the checkers work on any checkout. */
internal fun readDocText(file: Path): String = file.readText().replace("\r\n", "\n")

/** Returns the 1-based line of [offset] in [text]. */
internal fun lineOf(text: String, offset: Int): Int = text.subSequence(0, offset).count { it == '\n' } + 1

/** Returns [text] with its fenced code blocks and inline code spans blanked out, so the offsets stay the same. */
internal fun withoutCode(text: String): String = blank(blank(text, FENCED_CODE), INLINE_CODE)

/** Returns [text] with each match of [pattern] replaced by spaces, keeping the line breaks. */
internal fun blank(text: String, pattern: Regex): String =
  pattern.replace(text) { match -> match.value.replace(NOT_NEWLINE, " ") }

private val FENCED_CODE = Regex("""^[ \t]*```.*?^[ \t]*```""", setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL))
private val INLINE_CODE = Regex("""`[^`\n]+`""")
private val NOT_NEWLINE = Regex("""[^\n]""")

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.docs

import java.nio.file.Path
import kotlin.io.path.readText

/** Reads [file] with every line separator turned into `\n`, so the patterns of the checkers work on any checkout. */
internal fun readDocText(file: Path): String = file.readText().replace("\r\n", "\n")

/** Returns the 1-based line of [offset] in [text]. */
internal fun lineOf(text: String, offset: Int): Int = text.subSequence(0, offset).count { it == '\n' } + 1

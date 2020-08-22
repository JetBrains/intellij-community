// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.util

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil.*

enum class StringKind {

  SINGLE_QUOTED {
    override val lineBreaker: String get() = "\\n' +\n'"
    override fun escape(unescaped: String): String = escapeSymbolsForString(unescaped, true, false)
    override fun unescape(escaped: String): String = unescapeString(escaped)
  },

  TRIPLE_SINGLE_QUOTED {
    override fun escape(unescaped: String): String = escapeSymbolsForString(unescaped, false, false)
    override fun unescape(escaped: String): String = unescapeString(escaped)
  },

  DOUBLE_QUOTED {
    override val lineBreaker: String get() = "\\n\" +\n\""
    override fun escape(unescaped: String): String = escapeSymbolsForGString(unescaped, true, false)
    override fun unescape(escaped: String): String = unescapeString(escaped)
  },

  TRIPLE_DOUBLE_QUOTED {
    override fun escape(unescaped: String): String = escapeSymbolsForGString(unescaped, false, false)
    override fun unescape(escaped: String): String = unescapeString(escaped)
  },

  SLASHY {
    override fun escape(unescaped: String): String = escapeSymbolsForSlashyStrings(unescaped)
    override fun unescape(escaped: String): String = unescapeSlashyString(escaped)
  },

  DOLLAR_SLASHY {
    override fun escape(unescaped: String): String = escapeSymbolsForDollarSlashyStrings(unescaped)
    override fun unescape(escaped: String): String = unescapeDollarSlashyString(escaped)
  };

  @get:NonNls
  open val lineBreaker: String get() = "\n"

  abstract fun escape(unescaped: String): String
  abstract fun unescape(escaped: String): String

  /**
   * Groovy cannot reference Kotlin enum fields via enum class name
   * because Kotlin generates subclasses with the same name as the enum field
   * (i.e. StringKind.SINGLE_QUOTED resolves to StringKind$SINGLE_QUOTED class instead of enum field).
   */
  @ApiStatus.Internal
  @TestOnly
  object TestsOnly {
    @JvmField
    val SINGLE_QUOTED = StringKind.SINGLE_QUOTED
    @JvmField
    val TRIPLE_SINGLE_QUOTED = StringKind.TRIPLE_SINGLE_QUOTED
    @JvmField
    val DOUBLE_QUOTED = StringKind.DOUBLE_QUOTED
    @JvmField
    val TRIPLE_DOUBLE_QUOTED = StringKind.TRIPLE_DOUBLE_QUOTED
    @JvmField
    val SLASHY = StringKind.SLASHY
    @JvmField
    val DOLLAR_SLASHY = StringKind.DOLLAR_SLASHY
  }
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

import org.jetbrains.annotations.Nls

sealed interface WebSymbolApiStatus {

  /**
   * Version of the library, in which the symbol API status was updated
   */
  val since: @Nls String?

  /**
   * Stable symbols are not expected to change in backward incompatible way
   * and are fit for production code.
   */
  interface Stable : WebSymbolApiStatus {
    /**
     * Version of the library, in which the stable symbol was first available
     */
    override val since: @Nls String? get() = null
  }

  /**
   * Usage of deprecated symbols is discouraged, but such symbols are still supported.
   * Usages of deprecated symbols should be migrated as soon as possible.
   */
  interface Deprecated : WebSymbolApiStatus {
    /**
     * Message with HTML markup
     */
    val message: @Nls String? get() = null

    /**
     * Version of the library, in which symbol was first deprecated
     */
    override val since: @Nls String? get() = null
  }

  /**
   * Obsolete symbols are no longer supported.
   * Such symbols should not be used at all.
   */
  interface Obsolete : WebSymbolApiStatus {
    /**
     * Message with HTML markup
     */
    val message: @Nls String? get() = null

    /**
     * Version of the library, in which symbol was first deprecated
     */
    override val since: @Nls String? get() = null
  }

  /**
   * Experimental symbols are expected to be changed, or even removed.
   * Such symbols should not be used in production code.
   */
  interface Experimental : WebSymbolApiStatus {
    /**
     * Message with HTML markup
     */
    val message: @Nls String? get() = null

    /**
     * Version of the library, in which the experimental symbol was first available
     */
    override val since: @Nls String? get() = null
  }

  companion object {

    @JvmField
    val Stable: Stable = object : Stable {}

    @JvmField
    val Experimental: Experimental = object : Experimental {}

    @JvmField
    val Deprecated: Deprecated = object : Deprecated {}

    @JvmField
    val Obsolete: Obsolete = object : Obsolete {}

    @JvmStatic
    fun Stable(since: @Nls String? = null): Stable =
      object : Stable {
        override val since: String?
          get() = since
      }

    @JvmStatic
    fun Experimental(message: @Nls String? = null, since: @Nls String? = null): Experimental =
      object : Experimental {
        override val message: String?
          get() = message
        override val since: String?
          get() = since
      }

    @JvmStatic
    fun Deprecated(message: @Nls String? = null, since: @Nls String? = null): Deprecated =
      object : Deprecated {
        override val message: String?
          get() = message
        override val since: String?
          get() = since
      }

    @JvmStatic
    fun Obsolete(message: @Nls String? = null, since: @Nls String? = null): Obsolete =
      object : Obsolete {
        override val message: String?
          get() = message
        override val since: String?
          get() = since
      }

    @JvmStatic
    fun WebSymbolApiStatus?.isDeprecatedOrObsolete(): Boolean =
      this is Deprecated || this is Obsolete

    fun WebSymbolApiStatus.getMessage(): @Nls String? =
      when (this) {
        is Deprecated -> message
        is Experimental -> message
        is Obsolete -> message
        is Stable -> null
      }

  }
}
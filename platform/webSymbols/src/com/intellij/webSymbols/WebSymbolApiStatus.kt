// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

import org.jetbrains.annotations.Nls

sealed interface WebSymbolApiStatus {

  interface Stable : WebSymbolApiStatus {
    /**
     * Version of the library, in which the stable symbol was first available
     */
    val since: @Nls String? get() = null
  }

  interface Deprecated : WebSymbolApiStatus {
    /**
     * Message with HTML markup
     */
    val message: @Nls String? get() = null

    /**
     * Version of the library, in which symbol was first deprecated
     */
    val since: @Nls String? get() = null
  }

  interface Experimental : WebSymbolApiStatus {
    /**
     * Message with HTML markup
     */
    val message: @Nls String? get() = null

    /**
     * Version of the library, in which the experimental symbol was first available
     */
    val since: @Nls String? get() = null
  }

  companion object {

    val Stable: Stable = object : Stable {}

    val Experimental: Experimental = object : Experimental {}

    val Deprecated: Deprecated = object : Deprecated {}

    fun Stable(since: @Nls String? = null): Stable =
      object : Stable {
        override val since: String?
          get() = since
      }

    fun Experimental(message: @Nls String? = null, since: @Nls String? = null): Experimental =
      object : Experimental {
        override val message: String?
          get() = message
        override val since: String?
          get() = since
      }

    fun Deprecated(message: @Nls String? = null, since: @Nls String? = null): Deprecated =
      object : Deprecated {
        override val message: String?
          get() = message
        override val since: String?
          get() = since
      }
  }
}
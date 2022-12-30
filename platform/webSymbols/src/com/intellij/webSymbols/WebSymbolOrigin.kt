// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

import com.intellij.webSymbols.impl.WebSymbolOriginImpl
import javax.swing.Icon

/*
 * INAPPLICABLE_JVM_NAME -> https://youtrack.jetbrains.com/issue/KT-31420
 * DEPRECATION -> @JvmDefault
 **/
interface WebSymbolOrigin {
  @JvmDefault
  val framework: FrameworkId?
    get() = null

  @JvmDefault
  val library: String?
    get() = null

  @JvmDefault
  val version: String?
    get() = null

  @JvmDefault
  val defaultIcon: Icon?
    get() = null

  @JvmDefault
  val typeSupport: WebSymbolTypeSupport?
    get() = null

  companion object {
    @JvmStatic
    fun create(framework: FrameworkId? = null,
               library: String? = null,
               version: String? = null,
               defaultIcon: Icon? = null,
               typeSupport: WebSymbolTypeSupport? = null): WebSymbolOrigin =
      WebSymbolOriginImpl(framework, library, version, defaultIcon, typeSupport)

    @JvmStatic
    fun empty(): WebSymbolOrigin =
      WebSymbolOriginImpl.empty
  }

}
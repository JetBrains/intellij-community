// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.html

import com.intellij.webSymbols.html.impl.WebSymbolHtmlAttributeValueImpl

/**
 * null values might be replaced ("shadowed") by sibling WebSymbols while merging. Otherwise, default values are applied as the last step.
 */
/*
 * INAPPLICABLE_JVM_NAME -> https://youtrack.jetbrains.com/issue/KT-31420
 **/
@Suppress("INAPPLICABLE_JVM_NAME")
interface WebSymbolHtmlAttributeValue {
  /** Default: PLAIN */
  val kind: Kind?
    get() = null

  val type: Type?
    get() = null

  /** Default: true */
  @get:JvmName("isRequired")
  val required: Boolean?
    get() = null

  val default: String?
    get() = null

  val langType: Any?
    get() = null

  enum class Kind {
    PLAIN,
    EXPRESSION,
    NO_VALUE
  }

  enum class Type {
    BOOLEAN,
    NUMBER,
    STRING,
    ENUM,
    COMPLEX,
    OF_MATCH
  }

  companion object {
    @JvmStatic
    fun create(kind: Kind? = null, type: Type? = null, required: Boolean? = null, default: String? = null, langType: Any? = null): WebSymbolHtmlAttributeValue =
      WebSymbolHtmlAttributeValueImpl(kind, type, required, default, langType)
  }

}
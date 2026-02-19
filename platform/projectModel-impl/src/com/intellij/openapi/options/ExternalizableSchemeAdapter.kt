// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options

import org.jdom.Element

abstract class ExternalizableSchemeAdapter : ExternalizableScheme {
  private var name: String? = null

  override fun getName(): String = name!!

  override fun setName(value: String) {
    name = value
  }

  override fun toString(): String = name!!
}

abstract class BaseSchemeProcessor<SCHEME: Scheme, MUTABLE_SCHEME : SCHEME> : NonLazySchemeProcessor<SCHEME, MUTABLE_SCHEME>()

abstract class NonLazySchemeProcessor<SCHEME: Scheme, MUTABLE_SCHEME : SCHEME> : SchemeProcessor<SCHEME, MUTABLE_SCHEME>() {
  /**
   * @param duringLoad If occurred during [SchemeManager.loadSchemes] call
   * Returns null if an element is not valid.
   */
  @Throws(Exception::class)
  abstract fun readScheme(element: Element, duringLoad: Boolean): MUTABLE_SCHEME?
}
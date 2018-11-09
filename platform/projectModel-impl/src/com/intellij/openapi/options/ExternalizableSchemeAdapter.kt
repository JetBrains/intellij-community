// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options

import org.jdom.Element
import kotlin.properties.Delegates

abstract class ExternalizableSchemeAdapter : ExternalizableScheme {
  private var myName: String by Delegates.notNull()

  override fun getName(): String = myName

  override fun setName(value: String) {
    myName = value
  }

  override fun toString(): String = name
}

abstract class BaseSchemeProcessor<SCHEME, MUTABLE_SCHEME : SCHEME> : NonLazySchemeProcessor<SCHEME, MUTABLE_SCHEME>()

abstract class NonLazySchemeProcessor<SCHEME, MUTABLE_SCHEME : SCHEME> : SchemeProcessor<SCHEME, MUTABLE_SCHEME>() {
  /**
   * @param duringLoad If occurred during [SchemeManager.loadSchemes] call
   * * Returns null if element is not valid.
   */
  @Throws(Exception::class)
  abstract fun readScheme(element: Element, duringLoad: Boolean): MUTABLE_SCHEME?
}
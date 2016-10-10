/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.options

import com.intellij.configurationStore.SchemeExtensionProvider
import org.jdom.Element
import kotlin.properties.Delegates

abstract class ExternalizableSchemeAdapter : ExternalizableScheme {
  private var myName: String by Delegates.notNull()

  override fun getName() = myName

  override fun setName(value: String) {
    myName = value
  }

  override fun toString() = name
}

abstract class BaseSchemeProcessor<SCHEME : Scheme, MUTABLE_SCHEME : SCHEME> : NonLazySchemeProcessor<SCHEME, MUTABLE_SCHEME>() {
}

abstract class NonLazySchemeProcessor<SCHEME : Scheme, MUTABLE_SCHEME : SCHEME> : SchemeProcessor<SCHEME, MUTABLE_SCHEME>() {
  /**
   * @param duringLoad If occurred during [SchemeManager.loadSchemes] call
   * * Returns null if element is not valid.
   */
  @Throws(Exception::class)
  abstract fun readScheme(element: Element, duringLoad: Boolean): MUTABLE_SCHEME?
}
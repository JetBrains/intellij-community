// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.impl

import org.jetbrains.annotations.ApiStatus

/**
 * Property that depends on parent. If property assigned by parent (see [isParentValue]) then parent value is used until
 * the property is unlocked ([parentValue] = null)
 */
@ApiStatus.Internal
internal class ParentDependentProperty<T>(initValue: T) {

  val isParentValue: Boolean
    get() = parentValue != null

  /**
   * Value of property owner
   */
  var value: T = initValue

  /**
   * Value set by parent or null if absent
   */
  var parentValue: T? = null
}

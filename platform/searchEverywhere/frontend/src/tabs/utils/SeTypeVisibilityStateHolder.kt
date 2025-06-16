// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.utils

import com.intellij.ide.util.TypeVisibilityStateHolder
import com.intellij.platform.searchEverywhere.providers.target.SeTypeVisibilityStatePresentation
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeTypeVisibilityStateHolder(elements: List<SeTypeVisibilityStatePresentation>, private val onVisibilityChanged: () -> Unit) : TypeVisibilityStateHolder<SeTypeVisibilityStatePresentation> {
  val elements: List<SeTypeVisibilityStatePresentation> get() = mutableElements
  private val mutableElements = mutableListOf<SeTypeVisibilityStatePresentation>().apply { addAll(elements) }

  override fun setVisible(type: SeTypeVisibilityStatePresentation?, value: Boolean) {
    if (type == null) return
    val index = mutableElements.indexOfFirst { it.name == type.name && it.isEnabled != value }.takeIf { it >= 0 } ?: return
    mutableElements[index] = mutableElements[index].cloneWithEnabled(value)

    onVisibilityChanged()
  }

  override fun isVisible(type: SeTypeVisibilityStatePresentation?): Boolean = type?.isEnabled ?: false
}
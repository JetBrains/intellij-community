// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options

import org.jetbrains.annotations.ApiStatus
import javax.swing.JCheckBox

@ApiStatus.ScheduledForRemoval
@ApiStatus.Internal
@Deprecated("Use BeanConfigurable or other suitable API")
abstract class CheckBoxConfigurable : UnnamedConfigurable {
  protected abstract fun createCheckBox(): JCheckBox
  protected abstract var selectedState: Boolean

  var checkBox: JCheckBox? = null
    private set


  override fun createComponent(): JCheckBox {
    val checkBox = createCheckBox()
    this.checkBox = checkBox
    return checkBox
  }

  override fun isModified(): Boolean {
    val selected = checkBox?.isSelected ?: return false
    return selected != selectedState
  }

  override fun apply() {
    val selected = checkBox?.isSelected ?: return
    selectedState = selected
  }

  override fun reset() {
    checkBox?.isSelected = selectedState
  }

  override fun disposeUIResources() {
    checkBox = null
  }
}

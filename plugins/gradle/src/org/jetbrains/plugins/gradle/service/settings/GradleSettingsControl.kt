// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil
import com.intellij.openapi.externalSystem.util.PaintAwarePanel
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.NonExtendable
abstract class GradleSettingsControl {

  private var component: DialogPanel? = null

  private var parentDisposable: Disposable? = null

  protected abstract fun setupUi(builder: Panel)

  fun fillUi(canvas: PaintAwarePanel, indentLevel: Int) {
    component = panel { setupUi(this) }
    parentDisposable = Disposer.newDisposable()
    component!!.registerValidators(parentDisposable!!)
    canvas.add(component!!, ExternalSystemUiUtil.getFillLineConstraints(indentLevel))
  }

  fun showUi(show: Boolean) {
    component?.isVisible = show
  }

  fun getPreferredFocusedComponent(): JComponent? {
    return component?.preferredFocusedComponent
  }

  fun validate(): Boolean {
    return component?.validateAll()?.all { it.okEnabled } ?: true
  }

  fun isModified(): Boolean {
    return component?.isModified() ?: false
  }

  fun apply() {
    component?.apply()
  }

  fun reset() {
    component?.reset()
  }

  fun disposeUiResources() {
    parentDisposable?.let { Disposer.dispose(it) }
    parentDisposable = null
  }
}
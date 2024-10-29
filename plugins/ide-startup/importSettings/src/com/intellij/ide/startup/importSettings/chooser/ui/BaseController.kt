// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.ui

import com.intellij.openapi.rd.createLifetime
import com.jetbrains.rd.util.lifetime.Lifetime
import org.jetbrains.annotations.Nls
import javax.swing.JButton

internal interface BaseController {
  val lifetime: Lifetime
  fun createButton(name: @Nls String, handler: () -> Unit): JButton
  fun createDefaultButton(name: @Nls String, handler: () -> Unit): JButton
}

internal open class BaseControllerImpl(val dialog: OnboardingDialog) : BaseController {
  override val lifetime: Lifetime = dialog.disposable.createLifetime().createNested()
  override fun createButton(@Nls name: String, handler: () -> Unit): JButton {
    return dialog.createButton(name, handler)
  }

  override fun createDefaultButton(@Nls name: String, handler: () -> Unit): JButton {
    return dialog.createDefaultButton(name, handler)
  }
}
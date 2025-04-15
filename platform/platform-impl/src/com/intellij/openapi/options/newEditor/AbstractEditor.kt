// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.asContextElement
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.awt.AWTEvent
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel

@ApiStatus.Internal
abstract class AbstractEditor internal constructor(parent: Disposable) : JPanel(BorderLayout()), Disposable {
  @Volatile
  @JvmField
  var isDisposed: Boolean = false

  @Suppress("SSBasedInspection")
  @JvmField
  protected val coroutineScope = CoroutineScope(SupervisorJob() + ClientId.current.asContextElement())

  init {
    Disposer.register(parent, this)
  }

  override fun dispose() {
    if (!isDisposed) {
      isDisposed = true
      coroutineScope.cancel()
      disposeOnce()
    }
  }

  protected abstract fun disposeOnce()

  protected abstract fun getApplyAction(): Action?

  protected abstract fun getResetAction(): Action?

  protected abstract fun getHelpTopic(): @NonNls String?

  protected abstract fun apply(): Boolean

  protected open fun cancel(source: AWTEvent?): Boolean = true

  protected abstract fun getPreferredFocusedComponent(): JComponent?

  open fun getDialogInitialSize(): Dimension? = null
}

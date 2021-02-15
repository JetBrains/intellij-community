// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.component

import javax.swing.Action

interface GHErrorPanelModel {
  val errorPrefix: String
  val error: Throwable?
  val errorAction: Action?

  fun addAndInvokeChangeEventListener(listener: () -> Unit)
}

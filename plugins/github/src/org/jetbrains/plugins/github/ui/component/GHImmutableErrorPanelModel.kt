// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.component

import javax.swing.Action

class GHImmutableErrorPanelModel(override val errorPrefix: String,
                                 override val error: Throwable?,
                                 override val errorAction: Action?) : GHErrorPanelModel {

  override fun addAndInvokeChangeEventListener(listener: () -> Unit) = listener()
}

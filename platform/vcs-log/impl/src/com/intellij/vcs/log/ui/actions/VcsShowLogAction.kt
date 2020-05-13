// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions

import com.intellij.vcs.VcsShowToolWindowTabAction
import com.intellij.vcs.log.impl.VcsLogContentProvider

class VcsShowLogAction : VcsShowToolWindowTabAction() {
  override val tabName: String get() = VcsLogContentProvider.TAB_NAME
}
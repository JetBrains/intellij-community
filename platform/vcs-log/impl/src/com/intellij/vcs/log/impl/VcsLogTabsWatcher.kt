// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl

import com.intellij.vcs.log.ui.VcsLogUiEx

internal interface VcsLogTabsWatcher<T : VcsLogWindow> {
  fun setTabSelectedCallback(callback: (String) -> Unit)
  fun createLogTab(ui: VcsLogUiEx): T
  fun isOwnerOf(tab: VcsLogWindow): Boolean
}
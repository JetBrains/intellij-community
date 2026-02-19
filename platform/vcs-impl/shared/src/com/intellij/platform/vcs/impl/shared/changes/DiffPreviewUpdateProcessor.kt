// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.changes

import com.intellij.util.concurrency.annotations.RequiresEdt
import javax.swing.JComponent

interface DiffPreviewUpdateProcessor {
  /**
   * Notify currently shown diff that it's not needed now and cached values can be reset, a.e. before hiding preview panel
   */
  @RequiresEdt
  fun clear()

  /**
   * Get newly requested element for diff and update/create new diff request for it
   * a.e. get selection from some model and check if previously shown diff request need to be replaced or still valid for such selection
   *
   * @param fromModelRefresh Whether refresh was triggered without explicit change of selected item by user.
   * In this case, we might want not to close active viewer while it is in focus.
   */
  @RequiresEdt
  fun refresh(fromModelRefresh: Boolean)

  val component: JComponent
}
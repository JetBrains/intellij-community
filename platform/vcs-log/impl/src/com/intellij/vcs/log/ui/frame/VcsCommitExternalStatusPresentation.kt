// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.frame

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.event.MouseEvent
import javax.swing.Icon

interface VcsCommitExternalStatusPresentation {

  val icon: Icon

  @get:Nls
  val shortDescriptionText: String

  @get:Nls
  val fullDescriptionHtml: String?
    get() = null


  interface Clickable : VcsCommitExternalStatusPresentation {
    fun onClick(e: MouseEvent): Boolean
  }

  /**
   * Commit signatures are separated because they should be displayed in a separate place in details panel
   */
  @ApiStatus.Internal
  interface Signature : VcsCommitExternalStatusPresentation
}

// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.frame

import com.intellij.openapi.util.text.HtmlChunk
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.event.InputEvent
import javax.swing.Icon

interface VcsCommitExternalStatusPresentation {

  val icon: Icon

  @get:Nls
  val text: String

  interface Clickable : VcsCommitExternalStatusPresentation {

    fun clickEnabled(e: InputEvent?): Boolean = true

    fun onClick(e: InputEvent?): Boolean
  }

  /**
   * Commit signatures are separated because they should be displayed in a separate place in details panel
   * Only the first loaded signature will be displayed
   */
  @ApiStatus.Internal
  interface Signature : VcsCommitExternalStatusPresentation {
    @get:Nls
    val description: HtmlChunk?
      get() = null
  }
}

// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ui.cloneDialog

import com.intellij.ui.SimpleTextAttributes
import org.jetbrains.annotations.Nls
import java.awt.event.ActionListener

data class VcsCloneDialogExtensionStatusLine(@Nls val text: String,
                                             val attribute: SimpleTextAttributes,
                                             val actionListener: ActionListener? = null) {

  companion object {
    fun greyText(@Nls text: String): VcsCloneDialogExtensionStatusLine {
      return VcsCloneDialogExtensionStatusLine(text, SimpleTextAttributes.GRAY_ATTRIBUTES)
    }
  }
}
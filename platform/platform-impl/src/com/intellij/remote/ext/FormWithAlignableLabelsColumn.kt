// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote.ext

import com.intellij.ui.components.JBLabel

interface FormWithAlignableLabelsColumn {
  val labelsColumn: List<JBLabel>

  companion object {
    @JvmStatic
    fun FormWithAlignableLabelsColumn.findLabelWithMaxPreferredWidth(): JBLabel? = labelsColumn.maxBy { it.preferredSize.width }
  }
}
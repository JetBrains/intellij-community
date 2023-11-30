// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeStyle

import com.intellij.application.options.CodeStyleImportsBaseUI
import com.intellij.ui.dsl.builder.Panel
import javax.swing.JCheckBox
import javax.swing.JComponent

class GroovyCodeStyleImportsUI(packages: JComponent,
                               importLayout: JComponent,
                               private val cbUseFQClassNamesInJavaDoc: JCheckBox) : CodeStyleImportsBaseUI(packages, importLayout) {

  override fun Panel.fillCustomOptions() {
    row {
      cell(cbUseFQClassNamesInJavaDoc)
    }
  }
}
package com.intellij.cce.dialog.configurable

import com.intellij.cce.EvaluationPluginBundle
import com.intellij.cce.filter.EvaluationFilter
import com.intellij.cce.filter.impl.PackageRegexFilter
import com.intellij.ui.layout.*
import javax.swing.JTextField

class PackageRegexUIConfigurable(previousState: EvaluationFilter, private val layout: LayoutBuilder) : UIConfigurable {
  private val packageRegexTextField = JTextField(
    if (previousState == EvaluationFilter.ACCEPT_ALL) ""
    else (previousState as PackageRegexFilter).regex.pattern)

  override val view: Row = createView()

  override fun build(): EvaluationFilter =
    if (packageRegexTextField.text.isEmpty()) EvaluationFilter.ACCEPT_ALL
    else PackageRegexFilter(packageRegexTextField.text)

  private fun createView(): Row = layout.row {
    cell {
      label(EvaluationPluginBundle.message("evaluation.settings.filters.package.title"))
      packageRegexTextField(growPolicy = GrowPolicy.SHORT_TEXT)
    }
  }
}
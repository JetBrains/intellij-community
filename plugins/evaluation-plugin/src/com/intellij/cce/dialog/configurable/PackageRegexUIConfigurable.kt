package com.intellij.cce.dialog.configurable

import com.intellij.cce.EvaluationPluginBundle
import com.intellij.cce.filter.EvaluationFilter
import com.intellij.cce.filter.impl.PackageRegexFilter
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.columns
import javax.swing.JTextField

class PackageRegexUIConfigurable(previousState: EvaluationFilter, private val panel: Panel) : UIConfigurable {
  private val packageRegexTextField = JTextField(
    if (previousState == EvaluationFilter.ACCEPT_ALL) ""
    else (previousState as PackageRegexFilter).regex.pattern)

  override val view: Row = createView()

  override fun build(): EvaluationFilter =
    if (packageRegexTextField.text.isEmpty()) EvaluationFilter.ACCEPT_ALL
    else PackageRegexFilter(packageRegexTextField.text)

  private fun createView(): Row = panel.row(EvaluationPluginBundle.message("evaluation.settings.filters.package.title")) {
    cell(packageRegexTextField)
      .columns(COLUMNS_MEDIUM)
  }
}
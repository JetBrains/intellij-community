// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.merge.dialog

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.ui.popup.list.ListPopupImpl
import org.jetbrains.annotations.Nls
import java.awt.event.InputEvent

internal class GitOptionsPopupBuilder<T>(private val project: Project,
                                         @Nls private val title: String,
                                         private val optionsProvider: () -> List<T>,
                                         private val renderer: OptionListCellRenderer<T>,
                                         private val selectHandler: (T) -> Unit = {},
                                         private val optionEnabledPredicate: (T) -> Boolean = { true }) {

  fun createPopup(): ListPopup {
    return object : ListPopupImpl(project, createPopupStep(title, optionsProvider())) {

      override fun getListElementRenderer() = renderer

      override fun handleSelect(handleFinalChoices: Boolean) {
        if (handleFinalChoices) {
          handleSelect()
        }
      }

      override fun handleSelect(handleFinalChoices: Boolean, e: InputEvent?) {
        if (handleFinalChoices) {
          handleSelect()
        }
      }

      @Suppress("UNCHECKED_CAST")
      private fun handleSelect() {
        (selectedValues.firstOrNull() as? T)?.let { option ->
          selectHandler(option)
        }

        list.repaint()
      }
    }
  }

  private fun createPopupStep(@Nls title: String, options: List<T>) = object : BaseListPopupStep<T>(title, options) {

    override fun isSelectable(value: T?) = optionEnabledPredicate(value!!)

    override fun onChosen(selectedValue: T?, finalChoice: Boolean) = doFinalStep(Runnable { selectedValue?.let(selectHandler) })
  }
}
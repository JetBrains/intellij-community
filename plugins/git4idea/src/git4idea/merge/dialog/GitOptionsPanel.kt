// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.merge.dialog

import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import javax.swing.JPanel

class GitOptionsPanel<T>(private val optionRemoveHandler: (T) -> Unit,
                         private val optionInfoProvider: (T) -> OptionInfo<T>) : JPanel() {

  init {
    layout = MigLayout(LC().insets("0").noGrid())
    isVisible = false
  }

  fun rerender(selectedOptions: Set<T>) {
    if (selectedOptions.isEmpty()) {
      isVisible = false
      return
    }

    isVisible = true

    val shownOptions = mutableSetOf<T>()

    components.forEach { c ->
      @Suppress("UNCHECKED_CAST")
      val optionButton = c as OptionButton<T>
      val option = optionButton.option

      if (option !in selectedOptions) {
        remove(optionButton)
      }
      else {
        optionButton.isVisible = true
        shownOptions.add(option)
      }
    }

    selectedOptions.forEach { option ->
      if (option !in shownOptions) {
        add(createOptionButton(option))
      }
    }
  }

  private fun createOptionButton(option: T) = OptionButton(option, optionInfoProvider(option).flag) { optionRemoveHandler(option) }
}
// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Key
import com.intellij.ui.layout.*
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import com.intellij.ui.layout.panel as dialogPanel

@ApiStatus.Internal
internal class NewProjectWizardPanelBuilder(private val context: WizardContext) {
  private val panels = ArrayList<DialogPanel>()
  private val straightPanels get() = panels.filter { it.isShowing }

  val preferredFocusedComponent: JComponent?
    get() = straightPanels.firstNotNullOfOrNull { it.preferredFocusedComponent }

  fun panel(init: LayoutBuilder.() -> Unit) =
    dialogPanel(init = init)
      .also { panels.add(it) }
      .apply { registerValidators(context.disposable) }

  fun validate() =
    straightPanels.asSequence()
      .flatMap { it.validateCallbacks }
      .mapNotNull { it() }
      .all { it.okEnabled }

  fun isModified() =
    straightPanels.any { it.isModified() }

  fun apply() =
    straightPanels.forEach(DialogPanel::apply)

  fun reset() =
    panels.forEach(DialogPanel::reset)

  init {
    KEY.set(context, this)
  }

  companion object {
    private val KEY = Key.create<NewProjectWizardPanelBuilder>(NewProjectWizardPanelBuilder::class.java.name)
    fun getInstance(context: WizardContext): NewProjectWizardPanelBuilder = KEY.get(context)
  }
}
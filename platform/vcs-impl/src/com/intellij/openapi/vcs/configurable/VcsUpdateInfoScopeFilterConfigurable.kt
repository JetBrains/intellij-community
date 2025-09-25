// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.configurable

import com.intellij.ide.DataManager
import com.intellij.ide.util.scopeChooser.ScopeChooserConfigurable
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.Box
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

internal class VcsUpdateInfoScopeFilterConfigurable(
  project: Project,
  private val myVcsConfiguration: VcsConfiguration
) : Configurable, NamedScopesHolder.ScopeListener, Disposable {
  private val myCheckbox: JCheckBox
  private val myComboBox: ComboBox<String>
  private val myNamedScopeHolders: Array<NamedScopesHolder>

  init {
    myCheckbox = JCheckBox(VcsBundle.message("settings.filter.update.project.info.by.scope"))
    myComboBox = ComboBox<String>()

    myComboBox.setEnabled(myCheckbox.isSelected)
    myCheckbox.addChangeListener { myComboBox.setEnabled(myCheckbox.isSelected) }

    myNamedScopeHolders = NamedScopesHolder.getAllNamedScopeHolders(project)
    for (holder in myNamedScopeHolders) {
      holder.addScopeListener(this, this)
    }
  }

  override fun scopesChanged() {
    reset()
  }

  @Nls
  override fun getDisplayName(): @Nls String {
    return VcsBundle.message("settings.filter.update.project.info.by.scope")
  }

  override fun createComponent(): JComponent {
    val panel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
    panel.add(myCheckbox)
    panel.add(myComboBox)
    panel.add(Box.createHorizontalStrut(UIUtil.DEFAULT_HGAP))
    panel.add(ActionLink(VcsBundle.message("configurable.vcs.manage.scopes"), ActionListener { _: ActionEvent? ->
      val settings = Settings.KEY.getData(DataManager.getInstance().getDataContext(panel))
      if (settings != null) {
        settings.select(settings.find(ScopeChooserConfigurable.PROJECT_SCOPES))
      }
    }))
    return panel
  }

  override fun isModified(): Boolean {
    return myVcsConfiguration.UPDATE_FILTER_SCOPE_NAME != this.scopeFilterName
  }

  override fun apply() {
    myVcsConfiguration.UPDATE_FILTER_SCOPE_NAME = this.scopeFilterName
  }

  override fun reset() {
    myComboBox.removeAllItems()
    var selection = false
    for (holder in myNamedScopeHolders) {
      for (scope in holder.editableScopes) {
        val name = scope.scopeId
        myComboBox.addItem(name)
        if (!selection && name == myVcsConfiguration.UPDATE_FILTER_SCOPE_NAME) {
          selection = true
        }
      }
    }
    if (selection) {
      myComboBox.item = myVcsConfiguration.UPDATE_FILTER_SCOPE_NAME
    }
    myCheckbox.setSelected(selection)
  }

  override fun dispose() {
  }

  override fun disposeUIResources() {
    Disposer.dispose(this)
  }

  private val scopeFilterName: String?
    get() {
      if (!myCheckbox.isSelected) {
        return null
      }
      return myComboBox.getItem()
    }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.configurable

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.configurable.ShelfProjectConfigurable.Companion.getDefaultShelfPresentationPath
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.text
import com.intellij.ui.layout.selected
import com.intellij.util.ui.JBUI

internal class ShelfStorageConfigurationUi(project: Project, settings: VcsConfiguration) {

  lateinit var useCustomShelfDirectory: JBRadioButton

  @JvmField
  val shelfDirectoryPath = TextFieldWithBrowseButton()
  private lateinit var useDefaultShelfDirectory: JBRadioButton
  lateinit var moveShelvesCheckBox: JBCheckBox

  @JvmField
  val panel = panel {
    buttonsGroup(VcsBundle.message("change.shelves.location.dialog.group.title")) {
      row {
        useCustomShelfDirectory = radioButton(VcsBundle.message("change.shelves.location.dialog.custom.label"), true)
          .gap(RightGap.SMALL)
          .component
        cell(shelfDirectoryPath)
          .text(FileUtil.toSystemDependentName(settings.CUSTOM_SHELF_PATH ?: getDefaultShelfPresentationPath(project)))
          .align(AlignX.FILL)
          .enabledIf(useCustomShelfDirectory.selected)
      }

      row {
        useDefaultShelfDirectory = radioButton(VcsBundle.message("change.shelves.location.dialog.default.label"), false)
          .gap(RightGap.SMALL)
          .component

        label(getDefaultShelfPresentationPath(project))
          .applyToComponent {
            foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
          }
      }
    }.bind(settings::USE_CUSTOM_SHELF_PATH)

    row {
      moveShelvesCheckBox = checkBox(VcsBundle.message("vcs.shelf.move.text"))
        .bindSelected(settings::MOVE_SHELVES)
        .component
    }
  }

  init {
    shelfDirectoryPath.addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFolderDescriptor()
      .withTitle(VcsBundle.message("shelf.tab"))
      .withDescription(VcsBundle.message("change.shelves.location.dialog.location.browser.title")))

  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.options

import com.intellij.application.options.editor.CheckboxDescriptor
import com.intellij.application.options.editor.checkBox
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.openapi.vcs.impl.LineStatusTrackerSettingListener
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.selected

private val vcsSettings get() = VcsApplicationSettings.getInstance()

private val cdShowLSTInGutterCheckBox
  get() = CheckboxDescriptor(ApplicationBundle.message("editor.options.highlight.modified.line"),
                             vcsSettings::SHOW_LST_GUTTER_MARKERS)
private val cdShowLSTInErrorStripesCheckBox
  get() = CheckboxDescriptor(ApplicationBundle.message("editor.options.highlight.modified.line.error.stripe"),
                             vcsSettings::SHOW_LST_ERROR_STRIPE_MARKERS)
private val cdShowWhitespacesInLSTGutterCheckBox
  get() = CheckboxDescriptor(ApplicationBundle.message("editor.options.whitespace.line.color"),
                             vcsSettings::SHOW_WHITESPACES_IN_LST)

class VcsGeneralEditorOptionsExtension : UiDslUnnamedConfigurable.Simple() {
  override fun Panel.createContent() {
    group(ApplicationBundle.message("editor.options.gutter.group")) {
      fun fireLSTSettingsChanged() {
        ApplicationManager.getApplication().messageBus.syncPublisher(LineStatusTrackerSettingListener.TOPIC).settingsUpdated()
      }
      lateinit var showLstGutter: Cell<JBCheckBox>
      row {
        showLstGutter = checkBox(cdShowLSTInGutterCheckBox)
          .onApply(::fireLSTSettingsChanged)
      }

      indent {
        row {
          checkBox(cdShowLSTInErrorStripesCheckBox)
            .onApply(::fireLSTSettingsChanged)
        }
        row {
          checkBox(cdShowWhitespacesInLSTGutterCheckBox)
            .onApply(::fireLSTSettingsChanged)
        }
      }.enabledIf(showLstGutter.selected)
    }
  }
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage

import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.options.BoundCompositeSearchableConfigurable
import com.intellij.openapi.options.Configurable.WithEpDependencies
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class CoverageOptionsConfigurable(private val project: Project) : BoundCompositeSearchableConfigurable<CoverageOptions>(
  CoverageBundle.message("configurable.CoverageOptionsConfigurable.display.name"),
  "reference.project.settings.coverage",
  "coverage"), WithEpDependencies {

  override fun createConfigurables(): List<CoverageOptions> {
    return CoverageOptions.EP_NAME.getExtensions(project)
  }

  override fun createPanel(): DialogPanel {
    val manager = CoverageOptionsProvider.getInstance(project)
    return panel {
      group(CoverageBundle.message("settings.coverage.when.new.coverage.is.gathered")) {
        buttonsGroup {
          row {
            radioButton(CoverageBundle.message("settings.coverage.show.options.before.applying.coverage.to.the.editor"), CoverageOptionsProvider.ASK_ON_NEW_SUITE)
          }
          row {
            radioButton(CoverageBundle.message("settings.coverage.do.not.apply.collected.coverage"), CoverageOptionsProvider.IGNORE_SUITE)
          }
          row {
            radioButton(CoverageBundle.message("settings.coverage.replace.active.suites.with.the.new.one"), CoverageOptionsProvider.REPLACE_SUITE)
          }
          row {
            radioButton(CoverageBundle.message("settings.coverage.add.to.the.active.suites"), CoverageOptionsProvider.ADD_SUITE)
          }
        }.bind(manager::getOptionToReplace, manager::setOptionsToReplace)

        row {
          checkBox(CoverageBundle.message("settings.coverage.activate.coverage.view"))
            .bindSelected(manager::activateViewOnRun, manager::setActivateViewOnRun)
        }
        row {
          checkBox(CoverageBundle.message("settings.coverage.show.in.project.view"))
            .bindSelected(manager::showInProjectView, manager::setShowInProjectView)
        }
      }

      for (configurable in configurables) {
        appendDslConfigurable(configurable)
      }
    }
  }

  override fun getDependencies(): Collection<BaseExtensionPointName<*>?> {
    return listOf(CoverageOptions.EP_NAME)
  }
}

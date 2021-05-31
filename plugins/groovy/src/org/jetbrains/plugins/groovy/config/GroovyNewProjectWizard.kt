// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.config

import com.intellij.ide.LabelAndComponent
import com.intellij.ide.NewModuleStep.Companion.twoColumnRow
import com.intellij.ide.NewProjectWizard
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.ui.layout.*
import org.jetbrains.plugins.groovy.GroovyBundle
import javax.swing.DefaultComboBoxModel

class GroovyNewProjectWizard : NewProjectWizard<GroovyModuleSettings> {
  override val language: String = "Groovy"
  override var settingsFactory = { GroovyModuleSettings() }

  override fun settingsList(settings: GroovyModuleSettings): List<LabelAndComponent> {
    val panel = panel {
      row {
        row {
          label(GroovyBundle.message("label.groovy.library"))
        }
        row {
          twoColumnRow(
            { radioButton(GroovyBundle.message("radio.use.version.from.maven")) },
            { comboBox(DefaultComboBoxModel(arrayOf("3.0.6")), settings::version) }
          )
          twoColumnRow(
            { radioButton(GroovyBundle.message("radio.use.jar.file.from.disk")) },
            { comboBox(DefaultComboBoxModel(), settings::jarPath) }
          )
        }
      }
    }
    return listOf(LabelAndComponent(null, panel))
  }

  override fun setupProject(project: Project, settings: GroovyModuleSettings, context: WizardContext) {
    if (project == null) {
      return
    }
    val builder = context.projectBuilder as? ModuleBuilder ?: return
    builder.contentEntryPath = project.basePath
    builder.name = project.name
    val groovyModuleBuilder = GroovyAwareModuleBuilder()
    groovyModuleBuilder.updateFrom(builder)
    builder.addModuleConfigurationUpdater(object : ModuleBuilder.ModuleConfigurationUpdater() {
      override fun update(module: Module, rootModel: ModifiableRootModel) {
        groovyModuleBuilder.setupRootModel(rootModel)
      }
    })
  }
}

class GroovyModuleSettings {
  var version: String = "1.0"
  var jarPath: String = ""
}

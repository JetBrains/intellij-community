// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.compiler

import com.intellij.compiler.options.JavaCompilersTab
import com.intellij.compiler.server.BuildManager
import com.intellij.ide.DataManager
import com.intellij.openapi.compiler.options.ExcludedEntriesConfigurable
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.module.ModuleManager.Companion.getInstance
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.Configurable.NoScroll
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.dsl.builder.*
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.plugins.groovy.GroovyBundle

class GroovyCompilerConfigurable(private val project: Project) : BoundSearchableConfigurable(
  GroovyBundle.message("configurable.GroovyCompilerConfigurable.display.name"),
  "reference.projectsettings.compiler.groovy", "Groovy compiler"), NoScroll {

  private val config = GroovyCompilerConfiguration.getInstance(project)

  val excludes = createExcludedConfigurable(project)

  override fun createPanel(): DialogPanel {
    return panel {
      row {
        text(GroovyBundle.message("settings.compiler.alternative")) {
          val allSettings = Settings.KEY.getData(DataManager.getInstance().getDataContext(createComponent()))
          val javacConfigurable = allSettings?.find(JavaCompilersTab::class.java)
          if (allSettings != null && javacConfigurable != null) {
            allSettings.select(javacConfigurable)
          }
        }
      }
      row(GroovyBundle.message("settings.compiler.path.to.configscript")) {
        val textField = TextFieldWithBrowseButton()
        cell(textField)
          .align(AlignX.FILL)
          .applyToComponent {
            val descriptor = FileChooserDescriptor(true, false, false, false, false, false).withDescription(GroovyBundle.message("settings.compiler.select.path.to.groovy.compiler.configscript"))
            addBrowseFolderListener(null, descriptor)
          }.onReset {
            textField.text = normalizePath(config.configScript)
          }.onIsModified {
            normalizePath(config.configScript) != normalizePath(textField.text)
          }.onApply {
            config.configScript = normalizePath(textField.text)
          }
      }
      row {
        checkBox(GroovyBundle.message("settings.compiler.invoke.dynamic.support"))
          .bindSelected(config::isInvokeDynamic, config::setInvokeDynamic)
      }
      row {
        cell(excludes.createComponent()!!)
          .label(GroovyBundle.message("settings.compiler.exclude.from.stub.generation"), LabelPosition.TOP)
          .align(Align.FILL)
          .onReset {
            excludes.reset()
          }.onIsModified {
            excludes.isModified
          }.onApply {
            excludes.apply()
          }
      }.resizableRow()
    }
  }

  override fun disposeUIResources() {
    super.disposeUIResources()
    excludes.disposeUIResources()
  }

  override fun apply() {
    super.apply()
    if (!project.isDefault) {
      BuildManager.getInstance().clearState(project)
    }
  }

  private fun normalizePath(path: String): String {
    return FileUtil.toSystemIndependentName(path)
  }

  private fun createExcludedConfigurable(project: Project): ExcludedEntriesConfigurable {
    val configuration = config.excludeFromStubGeneration
    val index = if (project.isDefault) null else ProjectRootManager.getInstance(project).fileIndex
    val descriptor = FileChooserDescriptor(true, true, false, false, false, true)
      .withFileFilter { file -> index == null || !index.isExcluded(file) }
      .withRoots(getInstance(project).modules.flatMap { module ->
        ModuleRootManager.getInstance(module).getSourceRoots(JavaModuleSourceRootTypes.SOURCES)
      })
    return ExcludedEntriesConfigurable(project, descriptor, configuration)
  }
}

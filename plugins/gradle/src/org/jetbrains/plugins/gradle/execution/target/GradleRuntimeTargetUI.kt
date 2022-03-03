// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.execution.target.*
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemExecutionAware.Companion.getExtensions
import com.intellij.openapi.externalSystem.service.execution.TargetEnvironmentConfigurationProvider
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFolderDescriptor
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.ComponentWithBrowseButton.BrowseFolderActionListener
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.layout.*
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionAware
import org.jetbrains.plugins.gradle.util.GradleBundle.message
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.awt.event.ActionListener
import java.util.function.Supplier

class GradleRuntimeTargetUI<C : TargetEnvironmentConfiguration>(private val config: GradleRuntimeTargetConfiguration,
                                                                private val targetType: TargetEnvironmentType<C>,
                                                                private val targetSupplier: Supplier<TargetEnvironmentConfiguration>,
                                                                private val project: Project) :
  BoundConfigurable(config.displayName, config.getRuntimeType().helpTopic) {

  override fun createPanel(): DialogPanel {
    return panel {
      row(message("gradle.target.configurable.home.path.label")) {
        val cellBuilder: CellBuilder<*>
        if (targetType is BrowsableTargetEnvironmentType) {
          cellBuilder = textFieldWithBrowseTargetButton(this, targetType, targetSupplier,
                                                        project,
                                                        message("gradle.target.configurable.home.path.title"),
                                                        config::homePath.toBinding())
        }
        else {
          cellBuilder = textField(config::homePath)
        }
        cellBuilder.comment(message("gradle.target.configurable.home.path.comment"))
      }
    }
  }

  companion object {
    @JvmStatic
    fun targetPathFieldWithBrowseButton(project: Project, @NlsContexts.DialogTitle title: String): TargetPathFieldWithBrowseButton {
      return TargetPathFieldWithBrowseButton().installActionListener(project, title)
    }

    @JvmStatic
    fun TargetPathFieldWithBrowseButton.installActionListener(project: Project,
                                                              @NlsContexts.DialogTitle title: String): TargetPathFieldWithBrowseButton {
      val projectPath = project.guessProjectDir()?.path ?: return addLocalActionListener(project, title)
      var configurationProvider: TargetEnvironmentConfigurationProvider?
      for (executionAware in getExtensions(GradleConstants.SYSTEM_ID)) {
        if (executionAware !is GradleExecutionAware) continue
        configurationProvider = executionAware.getEnvironmentConfigurationProvider(projectPath, false, project)
        if (configurationProvider != null) {
          val configuration = configurationProvider.environmentConfiguration
          val targetType = configuration.getTargetType() as? BrowsableTargetEnvironmentType ?: break
          addTargetActionListener(configurationProvider.pathMapper,
                                  targetType.createBrowser(project, title, TEXT_FIELD_WHOLE_TEXT, textField) { configuration })
          return this
        }
      }
      addLocalActionListener(project, title)
      return this
    }

    private fun TargetPathFieldWithBrowseButton.addLocalActionListener(project: Project,
                                                                       @NlsContexts.DialogTitle title: String): TargetPathFieldWithBrowseButton {
      addTargetActionListener(null, ActionListener(BrowseFolderActionListener(
        title, null, this, project, createSingleFolderDescriptor(), TEXT_FIELD_WHOLE_TEXT
      )::actionPerformed))
      return this
    }
  }
}
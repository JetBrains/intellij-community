// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.ir.runtime.sample

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.remote.BaseExtendableConfiguration.Companion.getTypeImpl
import com.intellij.execution.remote.LanguageRuntimeType
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.*

class SampleLanguageRuntimeType : LanguageRuntimeType<SampleLanguageRuntimeConfiguration>(TYPE_ID) {
  override val icon = AllIcons.FileTypes.Java

  override val displayName = "Java"

  override fun isApplicableTo(runConfig: RunnerAndConfigurationSettings) = true

  override fun createDefaultConfig() = SampleLanguageRuntimeConfiguration()

  override fun createSerializer(config: SampleLanguageRuntimeConfiguration): PersistentStateComponent<*> = config

  override fun createConfigurable(project: Project, config: SampleLanguageRuntimeConfiguration) = SampleLanguageRuntimeUI(config)

  companion object {
    @JvmStatic
    val TYPE_ID = "languageRuntime:sample"
  }

  class SampleLanguageRuntimeUI(val config: SampleLanguageRuntimeConfiguration)
    : BoundConfigurable(config.displayName, config.getTypeImpl().helpTopic) {

    override fun createPanel(): DialogPanel {
      return panel {
        titledRow("Java configuration") {
          row("JDK home path:") {
            textField(config::homePath)
          }
          row("Application folder:") {
            textField(config::applicationFolder)
          }
        }
      }
    }
  }
}
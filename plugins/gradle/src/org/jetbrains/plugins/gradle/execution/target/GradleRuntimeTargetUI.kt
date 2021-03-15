// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.execution.target.*
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.*
import org.jetbrains.plugins.gradle.util.GradleBundle.message
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
          cellBuilder = TargetUIUtil.textFieldWithBrowseButton(this, targetType, targetSupplier,
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
}
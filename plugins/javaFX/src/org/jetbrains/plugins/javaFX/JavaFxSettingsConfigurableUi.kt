// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX

import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.dsl.builder.COLUMNS_LARGE
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel

internal class JavaFxSettingsConfigurableUi {
  lateinit var pathField: TextFieldWithBrowseButton
  var panel = panel {
    row(JavaFXBundle.message("javafx.settings.configurable.path.to.scenebuilder")) {
      pathField = textFieldWithBrowseButton(
        JavaFxSettingsConfigurable.createSceneBuilderDescriptor()
      )
        .columns(COLUMNS_LARGE)
        .component
    }
  }

  fun reset(settings: JavaFxSettings) {
    val pathToSceneBuilder = settings.pathToSceneBuilder
    if (pathToSceneBuilder != null) {
      pathField.setText(FileUtil.toSystemDependentName(pathToSceneBuilder))
    }
  }

  fun apply(settings: JavaFxSettings) {
    settings.pathToSceneBuilder = FileUtil.toSystemIndependentName(pathField.getText().trim())
  }

  fun isModified(settings: JavaFxSettings): Boolean {
    val pathToSceneBuilder = settings.pathToSceneBuilder
    return !Comparing.strEqual(pathToSceneBuilder?.trim(), FileUtil.toSystemIndependentName(pathField.getText().trim()))
  }
}
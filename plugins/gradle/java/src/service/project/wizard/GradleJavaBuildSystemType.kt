// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project.wizard

import com.intellij.ide.projectWizard.generators.JavaBuildSystemType
import com.intellij.ide.projectWizard.generators.JavaBuildSystemWithSettings
import com.intellij.ide.projectWizard.generators.JavaSettings
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.*
import org.jetbrains.plugins.gradle.util.GradleBundle

class GradleJavaBuildSystemType : JavaBuildSystemType<GradleJavaBuildSystemSettings>("Gradle") {
  override var settingsFactory = { GradleJavaBuildSystemSettings() }

  override fun setupProject(project: Project, languageSettings: JavaSettings, settings: GradleJavaBuildSystemSettings) {
    TODO("Not yet implemented")
  }

  override fun advancedSettings(settings: GradleJavaBuildSystemSettings): DialogPanel =
    panel {
      hideableRow(GradleBundle.message("label.project.wizard.new.project.advanced.settings.title")) {
        row {
          cell { label(GradleBundle.message("label.project.wizard.new.project.group.id")) }
          cell {
            textField(settings::groupId)
          }
        }

        row {
          cell { label(GradleBundle.message("label.project.wizard.new.project.artifact.id")) }
          cell {
            textFieldWithBrowseButton(settings::artifactId, GradleBundle.message("label.project.wizard.new.project.artifact.id"), null,
                                      FileChooserDescriptorFactory.createSingleFolderDescriptor())
          }
        }
      }.largeGapAfter()
    }
}

class GradleJavaBuildSystemSettings {
  val propertyGraph: PropertyGraph = PropertyGraph()
  val buildSystemButtons: Lazy<List<JavaBuildSystemWithSettings<out Any?>>> = lazy {
    JavaBuildSystemType.EP_NAME.extensionList.map {
      JavaBuildSystemWithSettings(it)
    }
  }

  val buildSystemProperty: GraphProperty<JavaBuildSystemWithSettings<*>> = propertyGraph.graphProperty {
    buildSystemButtons.value.first()
  }
  var groupId: String = ""
  var artifactId: String = ""
  var version: String = ""
}
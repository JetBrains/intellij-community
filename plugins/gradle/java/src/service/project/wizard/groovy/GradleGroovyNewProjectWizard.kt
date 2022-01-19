// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project.wizard.groovy

import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.dsl.builder.Panel
import org.jetbrains.plugins.gradle.service.project.wizard.GradleNewProjectWizardStep
import org.jetbrains.plugins.gradle.service.project.wizard.generateModuleBuilder
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.config.wizard.*
import java.util.*

class GradleGroovyNewProjectWizard : BuildSystemGroovyNewProjectWizard {

  override val name: String = GradleConstants.SYSTEM_ID.readableName

  override val ordinal: Int = 2

  override fun createStep(parent: GroovyNewProjectWizard.Step): GradleNewProjectWizardStep<GroovyNewProjectWizard.Step> = Step(parent)

  class Step(parent: GroovyNewProjectWizard.Step) :
    GradleNewProjectWizardStep<GroovyNewProjectWizard.Step>(parent) {

    private val addSampleCodeProperty = propertyGraph.graphProperty { false }
    private val groovySdkVersionProperty = propertyGraph.graphProperty<Optional<String>> { Optional.empty() }

    private var groovySdkVersion by groovySdkVersionProperty
    var addSampleCode by addSampleCodeProperty

    override fun setupSettingsUI(builder: Panel) {
      super.setupSettingsUI(builder)
      builder.row(GroovyBundle.message("label.groovy.sdk")) { groovySdkComboBox(groovySdkVersionProperty) }
      builder.addSampleCodeCheckbox(addSampleCodeProperty)
    }

    override fun setupProject(project: Project) {
      val builder = generateModuleBuilder()
      builder.gradleVersion = suggestGradleVersion()

      builder.configureBuildScript {
        it.withGroovyPlugin(groovySdkVersion.orElse(GROOVY_SDK_FALLBACK_VERSION))
        it.withJUnit()
      }

      ExternalProjectsManagerImpl.setupCreatedProject(project)
      builder.commit(project)
      if (addSampleCode) {
        val groovySourcesDirectory = builder.contentEntryPath + "/src/main/groovy"
        val directory = VfsUtil.createDirectoryIfMissing(groovySourcesDirectory)
        if (directory != null) {
          builder.createSampleGroovyCodeFile(project, directory)
        }
      }
    }

  }
}
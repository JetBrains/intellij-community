// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.config.wizard

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logBuildSystemChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logBuildSystemFinished
import com.intellij.ide.projectWizard.NewProjectWizardConstants.Language.GROOVY
import com.intellij.ide.wizard.*
import com.intellij.ide.wizard.LanguageNewProjectWizardData.Companion.languageData
import com.intellij.ide.wizard.language.LanguageGeneratorNewProjectWizard
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.distribution.DistributionInfo
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.SegmentedButton
import icons.JetgroovyIcons

class GroovyNewProjectWizard : LanguageGeneratorNewProjectWizard {

  override val name = GROOVY

  override val icon = JetgroovyIcons.Groovy.Groovy_16x16

  override val ordinal = 200

  override fun createStep(parent: NewProjectWizardStep) = Step(parent)

  class Step(parent: NewProjectWizardStep) :
    AbstractNewProjectWizardMultiStep<Step, BuildSystemGroovyNewProjectWizard>(parent, BuildSystemGroovyNewProjectWizard.EP_NAME),
    LanguageNewProjectWizardData by parent.languageData!!,
    BuildSystemGroovyNewProjectWizardData {

    override val self: Step = this

    override val label: String = JavaUiBundle.message("label.project.wizard.new.project.build.system")

    override val buildSystemProperty: GraphProperty<String> by ::stepProperty
    override var buildSystem: String by ::step

    override val groovySdkProperty = propertyGraph.property<DistributionInfo?>(null)
    override var groovySdk: DistributionInfo? by groovySdkProperty

    override fun createAndSetupSwitcher(builder: Row): SegmentedButton<String> {
      return super.createAndSetupSwitcher(builder)
        .whenItemSelectedFromUi { logBuildSystemChanged() }
    }

    override fun setupProject(project: Project) {
      super.setupProject(project)

      logBuildSystemFinished()
      logGroovySdkFinished(groovySdk)
    }

    init {
      data.putUserData(BuildSystemGroovyNewProjectWizardData.KEY, this)
    }
  }
}

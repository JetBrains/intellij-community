// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.config.wizard

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.projectWizard.NewProjectWizardCollector
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logBuildSystemChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logBuildSystemFinished
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.*
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.distribution.DistributionInfo
import com.intellij.openapi.roots.ui.distribution.LocalDistributionInfo
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils

class GroovyNewProjectWizard : LanguageNewProjectWizard {
  override val name: String = "Groovy"
  override val ordinal = 200

  override fun createStep(parent: NewProjectWizardLanguageStep) = Step(parent)

  class Step(parent: NewProjectWizardLanguageStep) :
    AbstractNewProjectWizardMultiStep<Step, BuildSystemGroovyNewProjectWizard>(parent, BuildSystemGroovyNewProjectWizard.EP_NAME),
    LanguageNewProjectWizardData by parent,
    BuildSystemGroovyNewProjectWizardData {

    override val self: Step = this

    override val label: String = JavaUiBundle.message("label.project.wizard.new.project.build.system")

    override val buildSystemProperty: GraphProperty<String> by ::stepProperty
    override var buildSystem: String by ::step

    override val groovySdkProperty = propertyGraph.property<DistributionInfo?>(null)
    override var groovySdk: DistributionInfo? by groovySdkProperty

    override fun setupProject(project: Project) {
      super.setupProject(project)

      logBuildSystemFinished()
      logGroovySdk(NewProjectWizardCollector.Groovy::logGroovyLibraryFinished)
    }

    init {
      data.putUserData(BuildSystemGroovyNewProjectWizardData.KEY, this)

      buildSystemProperty.afterChange { logBuildSystemChanged() }
      groovySdkProperty.afterChange { logGroovySdk(NewProjectWizardCollector.Groovy::logGroovyLibraryChanged) }
    }

    private fun logGroovySdk(logger: (WizardContext, String, String) -> Unit) {
      when (val sdk = groovySdk) {
        is FrameworkLibraryDistributionInfo -> logger(context, "maven", sdk.version.versionString)
        is LocalDistributionInfo -> GroovyConfigUtils.getInstance().getSDKVersion(sdk.path)
          .takeIf { it != GroovyConfigUtils.UNDEFINED_VERSION }
          ?.let { logger(context, "local", it) }
        else -> {
          com.intellij.openapi.diagnostic.logger<GroovyNewProjectWizard>().error("Unexpected distribution type")
        }
      }
    }
  }
}

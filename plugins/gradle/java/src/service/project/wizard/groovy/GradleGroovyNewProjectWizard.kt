// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project.wizard.groovy

import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logAddSampleCodeChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logAddSampleCodeFinished
import com.intellij.ide.projectWizard.NewProjectWizardConstants.BuildSystem.GRADLE
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStep.Companion.ADD_SAMPLE_CODE_PROPERTY_NAME
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.openapi.observable.util.bindBooleanStorage
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.distribution.LocalDistributionInfo
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.*
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.service.project.wizard.GradleAssetsNewProjectWizardStep
import org.jetbrains.plugins.gradle.service.project.wizard.GradleNewProjectWizardStep
import org.jetbrains.plugins.gradle.service.project.wizard.addGradleGitIgnoreAsset
import org.jetbrains.plugins.gradle.service.project.wizard.addGradleWrapperAsset
import org.jetbrains.plugins.groovy.config.GroovyHomeKind
import org.jetbrains.plugins.groovy.config.wizard.*

class GradleGroovyNewProjectWizard : BuildSystemGroovyNewProjectWizard {

  override val name = GRADLE

  override val ordinal = 200

  override fun createStep(parent: GroovyNewProjectWizard.Step): NewProjectWizardStep =
    Step(parent)
      .nextStep(::AssetsStep)

  class Step(parent: GroovyNewProjectWizard.Step) :
    GradleNewProjectWizardStep<GroovyNewProjectWizard.Step>(parent),
    BuildSystemGroovyNewProjectWizardData by parent {

    private val addSampleCodeProperty = propertyGraph.property(true)
      .bindBooleanStorage(ADD_SAMPLE_CODE_PROPERTY_NAME)

    var addSampleCode by addSampleCodeProperty

    init {
      gradleDsl = GradleDsl.GROOVY
    }

    private fun setupSampleCodeUI(builder: Panel) {
      builder.row {
        checkBox(UIBundle.message("label.project.wizard.new.project.add.sample.code"))
          .bindSelected(addSampleCodeProperty)
          .whenStateChangedFromUi { logAddSampleCodeChanged(it) }
          .onApply { logAddSampleCodeFinished(addSampleCode) }
      }
    }

    override fun setupSettingsUI(builder: Panel) {
      setupJavaSdkUI(builder)
      setupGroovySdkUI(builder)
      setupGradleDslUI(builder)
      setupParentsUI(builder)
      setupSampleCodeUI(builder)
    }

    override fun setupAdvancedSettingsUI(builder: Panel) {
      setupGradleDistributionUI(builder)
      setupGroupIdUI(builder)
      setupArtifactIdUI(builder)
    }
  }

  private class AssetsStep(parent: Step) : GradleAssetsNewProjectWizardStep<Step>(parent) {

    override fun setupAssets(project: Project) {
      if (context.isCreatingNewProject) {
        addGradleGitIgnoreAsset()
        addGradleWrapperAsset(parent.gradleVersionToUse)
      }

      addEmptyDirectoryAsset("src/main/groovy")
      addEmptyDirectoryAsset("src/main/resources")
      addEmptyDirectoryAsset("src/test/groovy")
      addEmptyDirectoryAsset("src/test/resources")

      if (parent.addSampleCode) {
        withGroovySampleCode("src/main/groovy", parent.groupId)
      }

      addOrConfigureSettingsScript {
        if (parent.isCreatingDaemonToolchain) {
          withFoojayPlugin()
        }
      }
      addBuildScript {

        addGroup(parent.groupId)
        addVersion(parent.version)

        when (val groovySdk = parent.groovySdk) {
          null -> {
            withPlugin("groovy")
          }
          is FrameworkLibraryDistributionInfo -> {
            withGroovyPlugin(groovySdk.version.versionString)
          }
          is LocalDistributionInfo -> {
            withPlugin("groovy")
            withMavenCentral()
            when (val groovySdkKind = GroovyHomeKind.fromString(groovySdk.path)) {
              null -> addImplementationDependency(call("files", groovySdk.path))
              else -> addImplementationDependency(call("fileTree", groovySdkKind.jarsPath) {
                for (subdir in groovySdkKind.subPaths) {
                  call("include", subdir)
                }
              })
            }
          }
        }
        withJUnit()
      }
    }
  }
}
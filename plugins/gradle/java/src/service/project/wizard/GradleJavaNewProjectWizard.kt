// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project.wizard

import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logAddSampleCodeChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logAddSampleCodeFinished
import com.intellij.ide.projectWizard.NewProjectWizardConstants.BuildSystem.GRADLE
import com.intellij.ide.projectWizard.generators.BuildSystemJavaNewProjectWizard
import com.intellij.ide.projectWizard.generators.BuildSystemJavaNewProjectWizardData
import com.intellij.ide.projectWizard.generators.JavaNewProjectWizard
import com.intellij.ide.projectWizard.generators.withJavaSampleCodeAsset
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStep.Companion.ADD_SAMPLE_CODE_PROPERTY_NAME
import com.intellij.openapi.observable.util.bindBooleanStorage
import com.intellij.openapi.project.Project
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.whenStateChangedFromUi

internal class GradleJavaNewProjectWizard : BuildSystemJavaNewProjectWizard {

  override val name = GRADLE

  override val ordinal = 200

  override fun createStep(parent: JavaNewProjectWizard.Step): NewProjectWizardStep =
    Step(parent)
      .nextStep(::AssetsStep)

  class Step(parent: JavaNewProjectWizard.Step) :
    GradleNewProjectWizardStep<JavaNewProjectWizard.Step>(parent),
    BuildSystemJavaNewProjectWizardData by parent,
    GradleJavaNewProjectWizardData {

    override val addSampleCodeProperty = propertyGraph.property(true)
      .bindBooleanStorage(ADD_SAMPLE_CODE_PROPERTY_NAME)

    override var addSampleCode by addSampleCodeProperty

    private fun setupSampleCodeUI(builder: Panel) {
      builder.row {
        checkBox(UIBundle.message("label.project.wizard.new.project.add.sample.code"))
          .bindSelected(addSampleCodeProperty)
          .whenStateChangedFromUi { logAddSampleCodeChanged(it) }
          .onApply { logAddSampleCodeFinished(addSampleCode) }
      }
    }

    @Deprecated("The onboarding tips generated unconditionally")
    protected fun setupSampleCodeWithOnBoardingTipsUI(builder: Panel) = Unit

    override fun setupSettingsUI(builder: Panel) {
      setupJavaSdkUI(builder)
      setupGradleDslUI(builder)
      setupParentsUI(builder)
      setupSampleCodeUI(builder)
    }

    override fun setupAdvancedSettingsUI(builder: Panel) {
      setupGradleDistributionUI(builder)
      setupGroupIdUI(builder)
      setupArtifactIdUI(builder)
    }

    init {
      data.putUserData(GradleJavaNewProjectWizardData.KEY, this)
    }
  }

  private class AssetsStep(parent: Step) : GradleAssetsNewProjectWizardStep<Step>(parent) {

    override fun setupAssets(project: Project) {
      if (context.isCreatingNewProject) {
        addGradleGitIgnoreAsset()
        addGradleWrapperAsset(parent.gradleVersionToUse)
      }

      addEmptyDirectoryAsset("src/main/java")
      addEmptyDirectoryAsset("src/main/resources")
      addEmptyDirectoryAsset("src/test/java")
      addEmptyDirectoryAsset("src/test/resources")

      if (parent.addSampleCode) {
        withJavaSampleCodeAsset(project, "src/main/java", parent.groupId, jdkIntent = parent.jdkIntent)
      }

      addOrConfigureSettingsScript {
        if (parent.isCreatingDaemonToolchain) {
          withFoojayPlugin()
        }
      }
      addBuildScript {

        addGroup(parent.groupId)
        addVersion(parent.version)

        withJavaPlugin()
        withJUnit()
      }
    }
  }
}
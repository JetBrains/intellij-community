// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project.wizard

import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logAddSampleCodeChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logAddSampleCodeFinished
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logAddSampleOnboardingTipsChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logAddSampleOnboardingTipsFinished
import com.intellij.ide.projectWizard.NewProjectWizardConstants.BuildSystem.GRADLE
import com.intellij.ide.projectWizard.generators.*
import com.intellij.ide.projectWizard.generators.AssetsOnboardingTips.proposeToGenerateOnboardingTipsByDefault
import com.intellij.ide.starters.local.StandardAssetsProvider
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStep.Companion.ADD_SAMPLE_CODE_PROPERTY_NAME
import com.intellij.openapi.observable.util.bindBooleanStorage
import com.intellij.openapi.project.Project
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.whenStateChangedFromUi
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder

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
    override val generateOnboardingTipsProperty = propertyGraph.property(proposeToGenerateOnboardingTipsByDefault())
      .bindBooleanStorage(NewProjectWizardStep.GENERATE_ONBOARDING_TIPS_NAME)

    override var addSampleCode by addSampleCodeProperty
    override var generateOnboardingTips by generateOnboardingTipsProperty

    private fun setupSampleCodeUI(builder: Panel) {
      builder.row {
        checkBox(UIBundle.message("label.project.wizard.new.project.add.sample.code"))
          .bindSelected(addSampleCodeProperty)
          .whenStateChangedFromUi { logAddSampleCodeChanged(it) }
          .onApply { logAddSampleCodeFinished(addSampleCode) }
      }
    }

    private fun setupSampleCodeWithOnBoardingTipsUI(builder: Panel) {
      builder.indent {
        row {
          checkBox(UIBundle.message("label.project.wizard.new.project.generate.onboarding.tips"))
            .bindSelected(generateOnboardingTipsProperty)
            .whenStateChangedFromUi { logAddSampleOnboardingTipsChanged(it) }
            .onApply { logAddSampleOnboardingTipsFinished(generateOnboardingTips) }
        }
      }.enabledIf(addSampleCodeProperty)
    }

    override fun setupSettingsUI(builder: Panel) {
      setupJavaSdkUI(builder)
      setupGradleDslUI(builder)
      setupParentsUI(builder)
      setupSampleCodeUI(builder)
      setupSampleCodeWithOnBoardingTipsUI(builder)
    }

    override fun setupAdvancedSettingsUI(builder: Panel) {
      setupGradleDistributionUI(builder)
      setupGroupIdUI(builder)
      setupArtifactIdUI(builder)
    }

    override fun setupProject(project: Project) {
      val builder = GradleJavaModuleBuilder()
      setupBuilder(builder)
      builder.configureBuildScript(fun GradleBuildScriptBuilder<*>.() {
        withJavaPlugin()
        withJUnit()
      })
      setupProject(project, builder)
    }

    init {
      data.putUserData(GradleJavaNewProjectWizardData.KEY, this)
    }
  }

  private class AssetsStep(
    private val parent: Step
  ) : AssetsNewProjectWizardStep(parent) {

    override fun setupAssets(project: Project) {
      if (context.isCreatingNewProject) {
        addAssets(StandardAssetsProvider().getGradleIgnoreAssets())
      }

      addEmptyDirectoryAsset("src/main/java")
      addEmptyDirectoryAsset("src/main/resources")
      addEmptyDirectoryAsset("src/test/java")
      addEmptyDirectoryAsset("src/test/resources")

      if (parent.addSampleCode) {
        if (parent.generateOnboardingTips) {
          prepareJavaSampleOnboardingTips(project)
        }
        withJavaSampleCodeAsset("src/main/java", parent.groupId, parent.generateOnboardingTips)
      }
    }
  }
}
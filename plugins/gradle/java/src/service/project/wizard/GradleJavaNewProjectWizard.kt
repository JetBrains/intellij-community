// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project.wizard

import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logAddSampleCodeChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logAddSampleOnboardingTipsChangedEvent
import com.intellij.ide.projectWizard.NewProjectWizardConstants.BuildSystem.GRADLE
import com.intellij.ide.projectWizard.generators.AssetsNewProjectWizardStep
import com.intellij.ide.projectWizard.generators.BuildSystemJavaNewProjectWizard
import com.intellij.ide.projectWizard.generators.BuildSystemJavaNewProjectWizardData
import com.intellij.ide.projectWizard.generators.JavaNewProjectWizard
import com.intellij.ide.starters.local.StandardAssetsProvider
import com.intellij.ide.wizard.NewProjectWizardBaseData
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.name
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.path
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStep.Companion.ADD_SAMPLE_CODE_PROPERTY_NAME
import com.intellij.ide.wizard.chain
import com.intellij.openapi.externalSystem.model.project.ProjectId
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.observable.util.bindBooleanStorage
import com.intellij.openapi.project.Project
import com.intellij.ui.UIBundle
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import org.jetbrains.plugins.gradle.service.project.wizard.GradleJavaNewProjectWizardData.Companion.addSampleCode
import org.jetbrains.plugins.gradle.service.project.wizard.GradleJavaNewProjectWizardData.Companion.gradleData
import org.jetbrains.plugins.gradle.service.project.wizard.GradleJavaNewProjectWizardData.Companion.groupId
import org.jetbrains.plugins.gradle.service.project.wizard.GradleNewProjectWizardData.GradleDsl

internal class GradleJavaNewProjectWizard : BuildSystemJavaNewProjectWizard {

  override val name = GRADLE

  override val ordinal = 200

  override fun createStep(parent: JavaNewProjectWizard.Step) = Step(parent).chain(::AssetsStep)

  class Step(parent: JavaNewProjectWizard.Step) :
    GradleNewProjectWizardStep<JavaNewProjectWizard.Step>(parent),
    BuildSystemJavaNewProjectWizardData by parent,
    GradleJavaNewProjectWizardData {

    override val addSampleCodeProperty = propertyGraph.property(true)
      .bindBooleanStorage(ADD_SAMPLE_CODE_PROPERTY_NAME)
    private val generateOnboardingTipsProperty = propertyGraph.property(AssetsNewProjectWizardStep.proposeToGenerateOnboardingTipsByDefault())
      .bindBooleanStorage(NewProjectWizardStep.GENERATE_ONBOARDING_TIPS_NAME)

    override var addSampleCode by addSampleCodeProperty
    override val generateOnboardingTips by generateOnboardingTipsProperty

    override fun setupSettingsUI(builder: Panel) {
      super.setupSettingsUI(builder)
      lateinit var addSampleCode: Cell<JBCheckBox>
      builder.row {
        addSampleCode = checkBox(UIBundle.message("label.project.wizard.new.project.add.sample.code"))
          .bindSelected(addSampleCodeProperty)
          .whenStateChangedFromUi { logAddSampleCodeChanged(it) }
      }.topGap(TopGap.SMALL)
      builder.indent {
        row {
          checkBox(UIBundle.message("label.project.wizard.new.project.generate.onboarding.tips"))
            .bindSelected(generateOnboardingTipsProperty)
            .whenStateChangedFromUi { logAddSampleOnboardingTipsChangedEvent(it) }
        }
      }.enabledIf(addSampleCode.selected)
    }

    override fun setupProject(project: Project) {
      super.setupProject(project)

      val builder = generateModuleBuilder()
      builder.gradleVersion = suggestGradleVersion()

      builder.configureBuildScript {
        it.withJavaPlugin()
        it.withJUnit()
      }

      ExternalProjectsManagerImpl.setupCreatedProject(project)
      builder.commit(project)
    }

    init {
      data.putUserData(GradleJavaNewProjectWizardData.KEY, this)
    }
  }

  private class AssetsStep(parent: NewProjectWizardStep) : AssetsNewProjectWizardStep(parent) {
    override fun setupAssets(project: Project) {
      outputDirectory = "$path/$name"
      addAssets(StandardAssetsProvider().getGradleIgnoreAssets())
      if (addSampleCode) {
        withJavaSampleCodeAsset("src/main/java", groupId, gradleData.generateOnboardingTips)
      }
    }

    override fun setupProject(project: Project) {
      super.setupProject(project)
      if (gradleData.generateOnboardingTips) {
        prepareTipsInEditor(project)
      }
    }
  }
}

fun <T> GradleNewProjectWizardStep<T>.generateModuleBuilder(): AbstractGradleModuleBuilder
  where T : NewProjectWizardStep, T : NewProjectWizardBaseData = InternalGradleModuleBuilder().apply {
  moduleJdk = sdk
  name = parentStep.name
  contentEntryPath = "${parentStep.path}/${parentStep.name}"

  isCreatingNewProject = context.isCreatingNewProject

  parentProject = parentData
  projectId = ProjectId(groupId, artifactId, version)
  isInheritGroupId = parentData?.group == groupId
  isInheritVersion = parentData?.version == version

  isUseKotlinDsl = gradleDsl == GradleDsl.KOTLIN
}
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project.wizard.statistics

import com.intellij.ide.projectWizard.NewProjectWizardCollector.GROUP
import com.intellij.ide.projectWizard.NewProjectWizardCollector.buildSystemFields
import com.intellij.ide.projectWizard.NewProjectWizardCollector.logBuildSystemEvent
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.project.wizard.GradleNewProjectWizardStep.GradleDsl
import org.jetbrains.plugins.gradle.settings.DistributionType

@ApiStatus.Internal
internal object GradleNewProjectWizardCollector {

  private val gradleDslField = EventFields.Enum<GradleDsl>("gradle_dsl")
  private val gradleDistributionField = EventFields.Enum<DistributionType>("gradle_distribution")
  private val gradleVersionField = EventFields.StringValidatedByCustomRule<GradleVersionValidationRule>("gradle_version")

  // @formatter:off
  private val gradleDslChangedEvent = GROUP.registerVarargEvent("gradle.dsl.changed", *buildSystemFields, gradleDslField)
  private val gradleDslFinishedEvent = GROUP.registerVarargEvent("gradle.dsl.finished", *buildSystemFields, gradleDslField)
  private val gradleDistributionChangedEvent = GROUP.registerVarargEvent("gradle.distribution.changed", *buildSystemFields, gradleDistributionField)
  private val gradleDistributionFinishedEvent = GROUP.registerVarargEvent("gradle.distribution.finished", *buildSystemFields, gradleDistributionField)
  private val gradleVersionChangedEvent = GROUP.registerVarargEvent("gradle.version.changed", *buildSystemFields, gradleVersionField)
  private val gradleVersionFinishedEvent = GROUP.registerVarargEvent("gradle.version.finished", *buildSystemFields, gradleVersionField)
  // @formatter:on

  fun NewProjectWizardStep.logGradleDslChanged(gradleDsl: GradleDsl) =
    gradleDslChangedEvent.logBuildSystemEvent(this, gradleDslField with gradleDsl)

  fun NewProjectWizardStep.logGradleDslFinished(gradleDsl: GradleDsl) =
    gradleDslFinishedEvent.logBuildSystemEvent(this, gradleDslField with gradleDsl)

  fun NewProjectWizardStep.logGradleDistributionChanged(distribution: DistributionType) =
    gradleDistributionChangedEvent.logBuildSystemEvent(this, gradleDistributionField with distribution)

  fun NewProjectWizardStep.logGradleDistributionFinished(distribution: DistributionType) =
    gradleDistributionFinishedEvent.logBuildSystemEvent(this, gradleDistributionField with distribution)

  fun NewProjectWizardStep.logGradleVersionChanged(gradleVersion: String) =
    gradleVersionChangedEvent.logBuildSystemEvent(this, gradleVersionField with gradleVersion)

  fun NewProjectWizardStep.logGradleVersionFinished(gradleVersion: String) =
    gradleVersionFinishedEvent.logBuildSystemEvent(this, gradleVersionField with gradleVersion)
}

internal class GradleVersionValidationRule : CustomValidationRule() {

  override fun getRuleId(): String = ID

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    try {
      GradleVersion.version(data)
      return ValidationResultType.ACCEPTED
    }
    catch (e: IllegalArgumentException) {
      return ValidationResultType.REJECTED
    }
  }

  companion object {
    const val ID = "npw_gradle_version"
  }
}

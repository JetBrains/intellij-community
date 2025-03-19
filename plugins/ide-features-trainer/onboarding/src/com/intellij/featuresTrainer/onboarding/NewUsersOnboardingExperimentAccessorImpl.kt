package com.intellij.featuresTrainer.onboarding

import com.intellij.openapi.components.service
import com.intellij.platform.ide.newUsersOnboarding.NewUsersOnboardingExperiment
import training.learn.NewUsersOnboardingExperimentAccessor

private class NewUsersOnboardingExperimentAccessorImpl : NewUsersOnboardingExperimentAccessor {
  override fun isExperimentEnabled(): Boolean {
    return service<NewUsersOnboardingExperiment>().isEnabled()
  }
}
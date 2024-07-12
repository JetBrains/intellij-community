package com.intellij.featuresTrainer.onboarding

import com.intellij.platform.ide.newUsersOnboarding.NewUsersOnboardingExperiment
import training.learn.NewUsersOnboardingExperimentAccessor

internal class NewUsersOnboardingExperimentAccessorImpl : NewUsersOnboardingExperimentAccessor {
  override fun isExperimentEnabled(): Boolean {
    return NewUsersOnboardingExperiment.getInstance().isEnabled()
  }
}
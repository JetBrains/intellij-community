// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.learn

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Temporary extension point to access the New Users Onboarding experiment state in the Features Trainer logic.
 * Can't access [com.intellij.platform.ide.newUsersOnboarding.NewUsersOnboardingExperiment] directly
 * because IFT can't strictly depend on New Users Onboarding.
 * So, if there is no New Users Onboarding, then there are no implementations of this extension point,
 * and we consider that experiment is disabled.
 *
 * It should be removed once the experiment is finished and default is determined.
 */
@ApiStatus.Internal
interface NewUsersOnboardingExperimentAccessor {
  fun isExperimentEnabled(): Boolean

  companion object {
    internal val EP_NAME: ExtensionPointName<NewUsersOnboardingExperimentAccessor> = ExtensionPointName("training.ift.newUsersOnboardingExperimentAccessor")

    fun isExperimentEnabled(): Boolean = EP_NAME.findFirstSafe { it.isExperimentEnabled() } != null
  }
}
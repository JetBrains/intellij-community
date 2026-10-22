// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.run

import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test

class DevKitJUnitRunnerSelectionPolicyTest : BareTestFixtureTestCase() {
  @Rule @JvmField val projectRule = ProjectRule()

  @After fun tearDown() {
    IntelliJProjectUtil.markAsIntelliJPlatformProject(projectRule.project, null)
  }

  @Test fun otherProjectKeepsTheClassRunner() {
    assertThat(DevKitJUnitRunnerSelectionPolicy().isPackageRunnerPreferred(projectRule.project, null)).isFalse()
  }

  @Test fun intelliJPlatformProjectPrefersThePackageRunner() {
    IntelliJProjectUtil.markAsIntelliJPlatformProject(projectRule.project, true)
    assertThat(DevKitJUnitRunnerSelectionPolicy().isPackageRunnerPreferred(projectRule.project, null)).isTrue()
  }
}

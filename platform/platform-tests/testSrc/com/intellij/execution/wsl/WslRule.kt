// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl

import com.intellij.openapi.util.io.IoTestUtil
import org.junit.Assume
import org.junit.AssumptionViolatedException
import org.junit.rules.ExternalResource

/**
 * Gives access to [WSLDistribution] as [wsl], skips test if WSL not available.
 * Depends on [com.intellij.testFramework.fixtures.TestFixtureRule], so make sure enable it before this class
 * @see WslTestBase
 */
class WslRule : ExternalResource() {
  lateinit var wsl: WSLDistribution
    private set

  override fun before() {
    Assume.assumeTrue("Windows only test", WSLUtil.isSystemCompatible())
    IoTestUtil.assumeWindows()
    IoTestUtil.assumeWslPresence()

    val distro = WslDistributionManager.getInstance().installedDistributions.firstOrNull { it !is WSLDistributionLegacy }
                 ?: throw AssumptionViolatedException("No WSL installed")
    Assume.assumeTrue("Can't reanimate WSL", IoTestUtil.reanimateWslDistribution(distro.id))
    this.wsl = distro
  }
}
// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl

import com.intellij.openapi.util.io.IoTestUtil
import org.junit.Assume.assumeTrue
import org.junit.rules.ExternalResource

/**
 * Provides access to installed and active [distributions][WSLDistribution];
 * skips the test if none are available (unless the `assume` parameter is set to `false`).
 *
 * Given that the rule is rather slow to initialize, it makes sense to use it on a class level.
 *
 * Depends on the application, so make sure [com.intellij.testFramework.fixtures.TestFixtureRule] is initialized beforehand.
 */
class WslRule(private val assume: Boolean = true) : ExternalResource() {
  lateinit var vms: List<WSLDistribution>
    private set

  val wsl: WSLDistribution
    get() = if (vms.isNotEmpty()) vms[0] else throw IllegalStateException("No WSL VMs are available")

  override fun before() {
    if (assume) {
      IoTestUtil.assumeWindows()
      IoTestUtil.assumeWslPresence()
    }

    if (assume || WSLUtil.isSystemCompatible() && WSLDistribution.findWslExe() != null) {
      val candidates = WslDistributionManager.getInstance().installedDistributions
      vms = candidates.filter { it !is WSLDistributionLegacy && IoTestUtil.reanimateWslDistribution(it.id) }
      if (assume) {
        assumeTrue("No alive WSL WMs among ${candidates.map(WSLDistribution::getId)}", vms.isNotEmpty())
      }
    }
    else {
      vms = emptyList()
    }
  }
}

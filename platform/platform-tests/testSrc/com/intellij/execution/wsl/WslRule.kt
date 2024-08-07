// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.testFramework.ensureCorrectVersion
import org.junit.Assume.assumeTrue
import org.junit.rules.ExternalResource
import java.io.IOException
import kotlin.io.path.exists

/**
 * Provides access to installed and active [distributions][WSLDistribution];
 * skips the test if none are available (unless the `assume` parameter is set to `false`).
 *
 * Given that the rule is rather slow to initialize, it makes sense to use it on a class level.
 *
 * Depends on the application, so make sure [com.intellij.testFramework.fixtures.TestFixtureRule] is initialized beforehand.
 */
class WslRule(private val assume: Boolean = true) : ExternalResource() {
  private lateinit var delegate: WslFixture

  val vms: List<WSLDistribution> get() = delegate.vms

  val wsl: WSLDistribution get() = delegate.wsl

  override fun before() {
    delegate = WslFixture.create(assume)
  }
}

class WslFixture private constructor(val vms: List<WSLDistribution>) {
  val wsl: WSLDistribution
    get() = if (vms.isNotEmpty()) vms[0].also { ensureCorrectVersion(it) } else throw IllegalStateException("No WSL VMs are available")

  companion object {
    private val LOG = logger<WslFixture>()

    @JvmStatic
    fun create(assume: Boolean = true): WslFixture {
      if (assume) {
        IoTestUtil.assumeWindows()
        IoTestUtil.assumeWslPresence()
      }

      val vms: List<WSLDistribution>
      if (assume || WSLUtil.isSystemCompatible() && WSLDistribution.findWslExe() != null) {
        val candidates = WslDistributionManager.getInstance().installedDistributions
        vms = candidates.filter {
          IoTestUtil.reanimateWslDistribution(it.id) && fileSystemWorks(it)
        }
        if (assume) {
          assumeTrue("No alive WSL WMs among ${candidates.map(WSLDistribution::getId)}", vms.isNotEmpty())
        }
      }
      else {
        vms = emptyList()
      }
      LOG.info("Using following distros${vms}")
      return WslFixture(vms)
    }

    private fun fileSystemWorks(distro: WSLDistribution): Boolean {
      try {
        return java.nio.file.Path.of(distro.getWindowsPath("/")).exists().also {
          if (!it) {
            LOG.warn("Root of $distro doesn't exist")
          }
        }
      }
      catch (e: IOException) {
        LOG.warn("Failed to check if the filesystem works with $distro", e)
        return false
      }
    }
  }
}

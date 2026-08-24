// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestApplication
internal class PluginManagementPolicyTest {
  private val testDisposable = disposableFixture()

  @Test
  fun `follows the default when no extension is registered`() {
    maskPolicies()

    val policy = PluginManagementPolicy.getInstance()
    assertTrue(policy.isUpgradeAllowed(null, null), "the default allows upgrades")
    assertFalse(policy.isDowngradeAllowed(null, null), "the default denies downgrades")
    assertTrue(policy.canEnablePlugin(null), "the default allows enabling plugin")
    assertTrue(policy.canInstallPlugin(null), "the default allows installing plugin")
    assertTrue(policy.isInstallFromDiskAllowed(), "the default allows installing from disk")
    assertTrue(policy.isPluginAutoUpdateAllowed(), "the default allows plugin auto-update")
  }

  @Test
  fun `a single extension overrides the default`() {
    val policy = FakePolicy(
      downgradeAllowed = true,
      upgradeAllowed = false,
      enableAllowed = false,
      installAllowed = false,
      installFromDiskAllowed = false,
      autoUpdateAllowed = false,
    )
    maskPolicies(policy)

    assertTrue(PluginManagementPolicy.getInstance().isDowngradeAllowed(null, null))
    assertFalse(PluginManagementPolicy.getInstance().isUpgradeAllowed(null, null))
    assertFalse(PluginManagementPolicy.getInstance().canEnablePlugin(null))
    assertFalse(PluginManagementPolicy.getInstance().canInstallPlugin(null))
    assertFalse(PluginManagementPolicy.getInstance().isInstallFromDiskAllowed())
    assertFalse(PluginManagementPolicy.getInstance().isPluginAutoUpdateAllowed())
  }

  private fun maskPolicies(vararg policies: PluginManagementPolicy) {
    ExtensionTestUtil.maskExtensions(PluginManagementPolicy.EP, policies.asList(), testDisposable.get())
  }
}

private class FakePolicy(
  private val upgradeAllowed: Boolean = true,
  private val downgradeAllowed: Boolean = true,
  private val enableAllowed: Boolean = true,
  private val installAllowed: Boolean = true,
  private val installFromDiskAllowed: Boolean = true,
  private val autoUpdateAllowed: Boolean = true,
) : PluginManagementPolicy {
  override fun isUpgradeAllowed(localDescriptor: IdeaPluginDescriptor?, remoteDescriptor: IdeaPluginDescriptor?): Boolean = upgradeAllowed
  override fun isDowngradeAllowed(localDescriptor: IdeaPluginDescriptor?, remoteDescriptor: IdeaPluginDescriptor?): Boolean = downgradeAllowed
  override fun canEnablePlugin(descriptor: IdeaPluginDescriptor?): Boolean = enableAllowed
  override fun canInstallPlugin(descriptor: IdeaPluginDescriptor?): Boolean = installAllowed
  override fun isInstallFromDiskAllowed(): Boolean = installFromDiskAllowed
  override fun isPluginAutoUpdateAllowed(): Boolean = autoUpdateAllowed
}

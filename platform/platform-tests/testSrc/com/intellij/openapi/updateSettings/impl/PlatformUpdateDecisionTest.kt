// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.util.io.IoTestUtil
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class PlatformUpdateDecisionTest {
  // only macOS reads the <distribution> element
  private val migrationBuild by lazy {
    IoTestUtil.assumeMacOS()
    loaded("""
      <build number="271.1234" version="2027.1">
        <button name="Download" url="https://www.jetbrains.com/go/download/" download="true"/>
        ${distributionXml()}
      </build>""")
  }

  private val usualBuild = loaded("""
    <build number="263.200" version="2026.3.1">
      <button name="Download" url="https://www.jetbrains.com/go/download/" download="true"/>
    </build>""")

  private val connectionError = PlatformUpdates.ConnectionError(IOException("offline"))

  @Test
  fun `migration build goes only to the migration offer`() {
    val decision = decide(migrationBuild, userInitiated = false)
    assertNull(decision.announced)
    assertSame(migrationBuild, decision.migration)
    assertFalse(decision.migrationReady)
  }

  @Test
  fun `usual build is announced`() {
    val decision = decide(usualBuild, userInitiated = false)
    assertSame(usualBuild, decision.announced)
    assertNull(decision.migration)
  }

  @Test
  fun `remind later mutes only a background check`() {
    assertNothing(decide(migrationBuild, userInitiated = false, remindLaterActive = true))
    assertNothing(decide(usualBuild, userInitiated = false, remindLaterActive = true))
    assertSame(migrationBuild, decide(migrationBuild, remindLaterActive = true).migration)
    assertSame(usualBuild, decide(usualBuild, remindLaterActive = true).announced)
  }

  @Test
  fun `no update gives nothing`() {
    assertNothing(decide(PlatformUpdates.Empty))
    assertNothing(decide(connectionError))
  }

  @Test
  fun `installed or moved migration gives no migration offer`() {
    for (status in MigrationStatus.entries) {
      for (userInitiated in listOf(true, false)) {
        val decision = decide(migrationBuild, userInitiated, status = status)
        assertNull(decision.announced)
        assertNull(decision.migration)
      }
    }
  }

  @Test
  fun `installed migration keeps a usual update`() {
    val decision = decide(usualBuild, status = MigrationStatus.INSTALLED)
    assertSame(usualBuild, decision.announced)
    assertFalse(decision.migrationReady)
  }

  @Test
  fun `manual check shows the installed migration instead of no updates`() {
    val installed = MigrationStatus.INSTALLED
    assertTrue(decide(PlatformUpdates.Empty, status = installed).migrationReady)
    assertFalse(decide(connectionError, status = installed).migrationReady)
    assertFalse(decide(PlatformUpdates.Empty, status = MigrationStatus.MOVED).migrationReady)
    assertFalse(decide(PlatformUpdates.Empty, userInitiated = false, status = installed).migrationReady)
  }

  @Test
  fun `manual check of a migration build shows the installed migration`() {
    val installed = MigrationStatus.INSTALLED
    assertTrue(decide(migrationBuild, status = installed).migrationReady)
    assertFalse(decide(migrationBuild, userInitiated = false, status = installed).migrationReady)
    assertFalse(decide(migrationBuild, status = MigrationStatus.MOVED).migrationReady)
    assertFalse(decide(migrationBuild, status = null).migrationReady)
  }

  private fun assertNothing(decision: PlatformUpdateDecision) {
    assertNull(decision.announced)
    assertNull(decision.migration)
    assertFalse(decision.migrationReady)
  }

  private fun decide(
    updates: PlatformUpdates,
    userInitiated: Boolean = true,
    remindLaterActive: Boolean = false,
    status: MigrationStatus? = null,
  ): PlatformUpdateDecision = decidePlatformUpdate(updates, userInitiated, remindLaterActive, status)

  private fun loaded(buildXml: String): PlatformUpdates.Loaded {
    val channel = parseUpdateData("""
      <products>
        <product name="GoLand">
          <code>GO</code>
          <channel id="GO_Release" status="release" licensing="release">${buildXml}</channel>
        </product>
      </products>""", "GO")!!.channels.single()
    return PlatformUpdates.Loaded(channel.builds.single(), channel)
  }
}

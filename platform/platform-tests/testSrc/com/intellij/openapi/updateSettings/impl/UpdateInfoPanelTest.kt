// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.updateSettings.UpdateStrategyCustomization
import com.intellij.openapi.util.BuildNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The test versions are taken from `https://www.jetbrains.com/updates/updates.xml`.
 */
class UpdateInfoPanelTest {

  private val INVALID_VERSIONS = listOf(
    "2026.3 EAP",
    "2026.3 EAP1",
    "2026.2 Beta",
    "2026.2 BETA",
    "2023.2 beta",
    "2023.1 Beta 2",
    "2025.3 RC",
    "2025.2 RC2",
    "2023.3.3 RC",
    "2023.2.1 rc",
    "2022.3 Release Candidate",
    "2024.1.1 Release Candidate",
    "2023.1 Release Candidate 2",
    "2022.3.3 Release Candidate 2",
    "2025.2.1 Preview",
    "0.0.20 EAP",
    "2026.2-EAP1 EAP",
    "")

  private val VALID_VERSIONS = mapOf(
    "2023.1" to "2023.1",
    "2023.1.5" to "2023.1",
    "2026.2.0.1" to "2026.2",
  )

  private val releaseChannel = """
    <channel id="IDEA_Release" status="release" licensing="release">
      <build number="262.8665" version="2026.2"><message>2026.2 news</message></build>
      <build number="261.25134" version="2026.1.3"><message>2026.1.3 fixes</message></build>
      <build number="261.22158" version="2026.1"><message>2026.1 news</message></build>
    </channel>"""

  @Test
  fun test_getMajorVersion() {
    for ((version, majorVersion) in VALID_VERSIONS) {
      assertEquals(majorVersion, getMajorVersion(version))
    }

    for (version in INVALID_VERSIONS) {
      assertNull(getMajorVersion(version))
    }
  }

  @Test
  fun `an update inside the major version shows the info of the new build`() {
    assertMessage("2026.1.3 fixes", "IU-261.23567", "2026.1.3")
  }

  @Test
  fun `a new build without a major version shows its own info`() {
    assertMessage("2026.3 EAP news", "IU-253.31033", "2026.3 EAP", """
      <channel id="IDEA_EAP" status="eap" licensing="eap">
        <build number="263.3889" version="2026.3 EAP"><message>2026.3 EAP news</message></build>
      </channel>""")
  }

  @Test
  fun `a new build that is a major build shows its own info`() {
    assertMessage("2026.2 news", "IU-253.31033", "2026.2")
  }

  @Test
  fun `a new build of a minor update shows the info of the major build`() {
    assertMessage("2026.1 news", "IU-253.31033", "2026.1.3")
  }

  private fun assertMessage(
    expected: String,
    currentBuild: String,
    newBuildVersion: String,
    channel: String = releaseChannel,
  ) {
    val updatesInfoText = """
      <products>
        <product name="IntelliJ IDEA">
          <code>IU</code>
          ${channel}
        </product>
      </products>"""
    val updatedChannel = parseUpdateData(updatesInfoText, "IU")!!.channels.single()
    val newBuild = updatedChannel.builds.single { it.version == newBuildVersion }
    val infoBuild = PlatformUpdates.Loaded(newBuild, updatedChannel)
      .infoBuild(BuildNumber.fromString(currentBuild)!!, UpdateStrategyCustomization())
    assertEquals(expected, infoBuild.message)
  }
}

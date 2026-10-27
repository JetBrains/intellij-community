// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.updateSettings.ProductVersionPolicy
import com.intellij.openapi.updateSettings.UpdateStrategyCustomization
import com.intellij.openapi.updateSettings.impl.productMigration.distributionXml
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.JDOMUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// unless stated otherwise, the behavior described in cases is true for 162+
class UpdateStrategyTest {
  @Test
  fun `channel contains no builds`() {
    val result = check("IU-145.258", ChannelStatus.RELEASE, """<channel id="IDEA_Release" status="release" licensing="release"/>""")
    assertTrue(result is PlatformUpdates.Empty)
  }

  @Test
  fun `already on the latest build`() {
    val result = check("IU-145.258", ChannelStatus.RELEASE, """
      <channel id="IDEA15_Release" status="release" licensing="release">
        <build number="143.2332" version="15.0.5"/>
      </channel>
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="145.258" version="2016.1"/>
      </channel>""")
    assertTrue(result is PlatformUpdates.Empty)
  }

  @Test fun `patch exclusions`() {
    val channels = """
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="145.597" version="2016.1.1">
          <patch from="145.596"/>
          <patch from="145.258" exclusions="win,mac,unix"/>
        </build>
      </channel>"""
    assertNotNull(assertLoaded("IU-145.596", ChannelStatus.RELEASE, channels).patches)
    assertNull(assertLoaded("IU-145.258", ChannelStatus.RELEASE, channels).patches)
  }

  @Test
  fun `build without a patch is offered as a manual download`() {
    val result = assertLoaded(
      currentBuild = "IU-262.1234",
      selectedChannel = ChannelStatus.RELEASE,
      testData = """
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="263.1234" version="2026.3.1">
          <button name="Download" url="https://www.jetbrains.com/idea/download/" download="true"/>
        </build>
      </channel>""",
    )
    assertBuild("263.1234", result.newBuild)
    assertNull(result.patches)
    assertEquals("https://www.jetbrains.com/idea/download/", result.newBuild.downloadUrl)
    assertEquals(UpdateMode.MANUAL_DOWNLOAD, getUpdateMode(result, testPatch = null))
  }

  @Test
  fun `build without a patch and a download button is not offered`() {
    val result = assertLoaded(
      currentBuild = "IU-262.1234",
      selectedChannel = ChannelStatus.RELEASE,
      testData = """
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="263.1234" version="2026.3.1">
          <button name="Release notes" url="https://www.jetbrains.com/idea/whatsnew/"/>
        </build>
      </channel>""",
    )
    assertNull(result.patches)
    assertNull(result.newBuild.downloadUrl)
    assertNull(getUpdateMode(result, testPatch = null))
  }

  @Test
  fun `build with a distribution is a manual download for a direct caller`() {
    val result = assertLoaded(
      currentBuild = "IU-263.100",
      selectedChannel = ChannelStatus.RELEASE,
      testData = """
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="271.1234" fullNumber="271.1234.56" version="2027.1">
          <button name="Download" url="https://www.jetbrains.com/idea/download/" download="true"/>
          ${distributionXml()}
        </build>
      </channel>""",
    )
    assertEquals("IU-271.1234.56", result.newBuild.number.asString())
    assertNotNull(result.newBuild.distribution)
    assertEquals(UpdateMode.MANUAL_DOWNLOAD, getUpdateMode(result, testPatch = null))
  }

  @Test
  fun `build with an invalid distribution is offered as a manual download`() {
    val result = assertLoaded(
      currentBuild = "IU-263.100",
      selectedChannel = ChannelStatus.RELEASE,
      testData = """
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="271.1234" version="2027.1">
          <button name="Download" url="https://www.jetbrains.com/idea/download/" download="true"/>
          ${distributionXml(product = null)}
        </build>
      </channel>""",
    )
    assertNull(result.newBuild.distribution)
    assertEquals(UpdateMode.MANUAL_DOWNLOAD, getUpdateMode(result, testPatch = null))
  }

  @Test
  fun `newer build of the same major wins over a migration`() {
    val result = assertLoaded(
      currentBuild = "IU-263.100",
      selectedChannel = ChannelStatus.RELEASE,
      testData = """
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="271.1234" version="2027.1">
          ${distributionXml()}
        </build>
        <build number="263.200" version="2026.3.1">
          <button name="Download" url="https://www.jetbrains.com/idea/download/" download="true"/>
        </build>
      </channel>""",
    )
    assertBuild("263.200", result.newBuild)
    assertEquals(UpdateMode.MANUAL_DOWNLOAD, getUpdateMode(result, testPatch = null))
  }

  @Test fun `order of builds does not matter`() {
    val resultDesc = assertLoaded("IU-143.2332", ChannelStatus.RELEASE, """
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="145.597" version="2016.1.1"/>
        <build number="145.258" version="2016.1"/>
      </channel>""")
    assertBuild("145.597", resultDesc.newBuild)

    val resultAsc = assertLoaded("IU-143.2332", ChannelStatus.RELEASE, """
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="145.258" version="2016.1"/>
        <build number="145.597" version="2016.1.1"/>
      </channel>""")
    assertBuild("145.597", resultAsc.newBuild)
  }

  @Test fun `newer updates are preferred`() {
    val result = assertLoaded("IU-145.258", ChannelStatus.EAP, """
      <channel id="IDEA_EAP" status="eap" licensing="eap">
        <build number="145.596" version="2016.1.1 EAP"/>
      </channel>
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="145.597" version="2016.1.1"/>
      </channel>""")
    assertBuild("145.597", result.newBuild)
  }

  @Test fun `newer updates are preferred over more stable ones`() {
    val result = assertLoaded("IU-145.257", ChannelStatus.EAP, """
      <channel id="IDEA_EAP" status="eap" licensing="eap">
        <build number="145.596" version="2016.1.1 EAP"/>
      </channel>
      <channel id="IDEA_Beta" status="beta" licensing="release">
        <build number="145.257" version="2016.1 RC2"/>
      </channel>
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="145.258" version="2016.1"/>
      </channel>""")
    assertBuild("145.596", result.newBuild)
  }

  @Test fun `newer updates from non-allowed channels are ignored`() {
    val channels = """
      <channel id="IDEA_EAP" status="eap" licensing="eap">
        <build number="145.596" version="2016.1.1 EAP"/>
      </channel>
      <channel id="IDEA_Beta" status="beta" licensing="release">
        <build number="145.257" version="2016.1 RC2"/>
      </channel>
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="145.258" version="2016.1"/>
      </channel>"""
    assertBuild("145.258", assertLoaded("IU-145.256", ChannelStatus.RELEASE, channels).newBuild)
    assertTrue(check("IU-145.258", ChannelStatus.RELEASE, channels) is PlatformUpdates.Empty)
  }

  @Test fun `ignored updates are excluded`() {
    val result = assertLoaded("IU-145.258", ChannelStatus.EAP, """
      <channel id="IDEA_EAP" status="eap" licensing="eap">
        <build number="145.596" version="2016.1.1 EAP"/>
        <build number="145.595" version="2016.1.1 EAP"/>
      </channel>""", listOf("145.596"))
    assertBuild("145.595", result.newBuild)
  }

  @Test fun `ignored same-baseline updates do not hide new major releases`() {
    val result = assertLoaded("IU-145.971", ChannelStatus.RELEASE, """
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="145.1617" version="2016.1.3"/>
        <build number="145.2070" version="2016.1.4"/>
        <build number="171.4424" version="2017.1.3"/>
      </channel>""", listOf("145.1617", "145.2070"))
    assertBuild("171.4424", result.newBuild)
  }

  @Test fun `updates can be targeted for specific builds (different builds)`() {
    val channels = """
      <channel id="IDEA_EAP" status="eap" licensing="eap">
        <build number="145.596" version="2016.1.1 EAP" targetSince="145.595" targetUntil="145.*"/> <!-- this build is not for everyone -->
        <build number="145.595" version="2016.1.1 EAP"/>
      </channel>"""
    assertBuild("145.595", assertLoaded("IU-145.258", ChannelStatus.EAP, channels).newBuild)
    assertBuild("145.596", assertLoaded("IU-145.595", ChannelStatus.EAP, channels).newBuild)
  }

  @Test fun `updates can be targeted for specific builds (same build)`() {
    val channels = """
      <channel id="IDEA_EAP" status="release" licensing="release">
        <build number="163.101" version="2016.3.1" targetSince="163.0" targetUntil="163.*"><message>bug fix</message></build>
        <build number="163.101" version="2016.3.1"><message>new release</message></build>
      </channel>"""
    assertEquals("new release", assertLoaded("IU-145.258", ChannelStatus.RELEASE, channels).newBuild.message)
    assertEquals("bug fix", assertLoaded("IU-163.50", ChannelStatus.RELEASE, channels).newBuild.message)
  }

  @Test fun `updates from the same baseline are preferred (unified channels)`() {
    val result = assertLoaded("IU-143.2287", ChannelStatus.EAP, """
      <channel id="IDEA_EAP" status="eap" licensing="eap">
        <build number="143.2330" version="15.0.5 EAP"/>
        <build number="145.600" version="2016.1.2 EAP"/>
      </channel>
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="143.2332" version="15.0.5"/>
        <build number="145.597" version="2016.1.1"/>
      </channel>""")
    assertBuild("143.2332", result.newBuild)
  }

  /*
   * Since 163.
   */

  @Test fun `updates from the same baseline are preferred (per-release channels)`() {
    val result = assertLoaded("IU-143.2287", ChannelStatus.EAP, """
      <channel id="IDEA_143_EAP" status="eap" licensing="eap">
        <build number="143.2330" version="15.0.5 EAP"/>
      </channel>
      <channel id="IDEA_143_Release" status="release" licensing="release">
        <build number="143.2332" version="15.0.5"/>
      </channel>
      <channel id="IDEA_145_EAP" status="eap" licensing="eap">
        <build number="145.600" version="2016.1.2 EAP"/>
      </channel>
      <channel id="IDEA_Release_145" status="release" licensing="release">
        <build number="145.597" version="2016.1.1"/>
      </channel>""")
    assertBuild("143.2332", result.newBuild)
  }

  @Test fun `cross-baseline updates are perfectly legal`() {
    val result = assertLoaded("IU-143.2332", ChannelStatus.EAP, """
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="143.2332" version="15.0.5"/>
        <build number="145.597" version="2016.1.1"/>
      </channel>""")
    assertBuild("145.597", result.newBuild)
  }

  @Test fun `variable-length build numbers are supported`() {
    val channels = """
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="162.11.10" version="2016.2"/>
      </channel>"""
    assertBuild("162.11.10", assertLoaded("IU-145.597", ChannelStatus.RELEASE, channels).newBuild)
    assertBuild("162.11.10", assertLoaded("IU-162.7.23", ChannelStatus.RELEASE, channels).newBuild)
    assertTrue(check("IU-162.11.11", ChannelStatus.RELEASE, channels) is PlatformUpdates.Empty)

    val result = assertLoaded("IU-162.11.10", ChannelStatus.RELEASE, """
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="162.48" version="2016.2.1 EAP"/>
      </channel>""")
    assertBuild("162.48", result.newBuild)
  }

  @Test fun `for duplicate builds, first matching channel is preferred`() {
    val build = """<build number="163.9166" version="2016.3.1"/>"""
    val eap15 = """<channel id="IDEA15_EAP" status="eap" licensing="eap" majorVersion="15">$build</channel>"""
    val eap = """<channel id="IDEA_EAP" status="eap" licensing="eap" majorVersion="2016">$build</channel>"""
    val beta15 = """<channel id="IDEA15_Beta" status="beta" licensing="release" majorVersion="15">$build</channel>"""
    val beta = """<channel id="IDEA_Beta" status="beta" licensing="release" majorVersion="2016">$build</channel>"""
    val release15 = """<channel id="IDEA15_Release" status="release" licensing="release" majorVersion="15">$build</channel>"""
    val release = """<channel id="IDEA_Release" status="release" licensing="release" majorVersion="2016">$build</channel>"""

    // note: this is a test; in production, release builds should never be proposed via channels with EAP licensing
    assertEquals("IDEA15_EAP",
                 assertLoaded("IU-163.1", ChannelStatus.EAP, (eap15 + eap + beta15 + beta + release15 + release)).updatedChannel.id)
    assertEquals("IDEA_EAP",
                 assertLoaded("IU-163.1", ChannelStatus.EAP, (eap + eap15 + beta + beta15 + release + release15)).updatedChannel.id)
    assertEquals("IDEA15_EAP",
                 assertLoaded("IU-163.1", ChannelStatus.EAP, (release15 + release + beta15 + beta + eap15 + eap)).updatedChannel.id)
    assertEquals("IDEA_EAP",
                 assertLoaded("IU-163.1", ChannelStatus.EAP, (release + release15 + beta + beta15 + eap + eap15)).updatedChannel.id)

    assertEquals("IDEA15_Beta",
                 assertLoaded("IU-163.1", ChannelStatus.BETA, (release15 + release + beta15 + beta + eap15 + eap)).updatedChannel.id)
    assertEquals("IDEA_Beta",
                 assertLoaded("IU-163.1", ChannelStatus.BETA, (release + release15 + beta + beta15 + eap + eap15)).updatedChannel.id)

    assertEquals("IDEA15_Release",
                 assertLoaded("IU-163.1", ChannelStatus.RELEASE, (eap15 + eap + beta15 + beta + release15 + release)).updatedChannel.id)
    assertEquals("IDEA_Release",
                 assertLoaded("IU-163.1", ChannelStatus.RELEASE, (eap + eap15 + beta + beta15 + release + release15)).updatedChannel.id)
    assertEquals("IDEA15_Release",
                 assertLoaded("IU-163.1", ChannelStatus.RELEASE, (release15 + release + beta15 + beta + eap15 + eap)).updatedChannel.id)
    assertEquals("IDEA_Release",
                 assertLoaded("IU-163.1", ChannelStatus.RELEASE, (release + release15 + beta + beta15 + eap + eap15)).updatedChannel.id)
  }

  /*
   * Since 183.
   */

  @Test fun `building linear patch chain`() {
    val result = assertLoaded("IU-182.3569.1", ChannelStatus.EAP, """
      <channel id="IDEA_EAP" status="eap" licensing="eap">
        <build number="182.3684.40" version="2018.2 RC2">
          <patch from="182.3684.2" size="from 1 to 8"/>
        </build>
        <build number="182.3684.2" version="2018.2 RC">
          <patch from="182.3569.1" size="2"/>
        </build>
      </channel>""")
    assertBuild("182.3684.40", result.newBuild)
    assertThat(result.patches?.chain).isEqualTo(listOf("182.3569.1", "182.3684.2", "182.3684.40").map(BuildNumber::fromString))
    assertThat(result.patches?.size).isEqualTo("10")
  }

  @Test fun `building patch chain across channels`() {
    val result = assertLoaded("IU-182.3684.40", ChannelStatus.EAP, """
      <channel id="IDEA_EAP" status="eap" licensing="eap">
        <build number="182.3684.40" version="2018.2 RC2">
          <patch from="182.3684.2"/>
        </build>
      </channel>
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="182.3684.41" version="2018.2">
          <patch from="182.3684.40"/>
        </build>
      </channel>
      <channel id="IDEA_Stable_EAP" status="eap" licensing="release">
        <build number="182.3911.2" version="2018.2.1 EAP">
          <patch from="182.3684.41"/>
        </build>
      </channel>""")
    assertBuild("182.3911.2", result.newBuild)
    assertThat(result.patches?.chain).isEqualTo(listOf("182.3684.40", "182.3684.41", "182.3911.2").map(BuildNumber::fromString))
    assertThat(result.patches?.size).isNull()
  }

  @Test fun `allow ignored builds in the middle of a chain`() {
    val result = assertLoaded("IU-183.3795.13", ChannelStatus.EAP, """
      <channel id="IDEA_EAP" status="eap" licensing="eap">
        <build number="183.4139.22" version="2018.3 EAP">
          <patch from="183.3975.18" size="1"/>
        </build>
        <build number="183.3975.18" version="2018.3 EAP">
          <patch from="183.3795.13" size="1"/>
        </build>
      </channel>""", listOf("183.3975.18"))
    assertBuild("183.4139.22", result.newBuild)
    assertThat(result.patches?.chain).isEqualTo(listOf("183.3795.13", "183.3975.18", "183.4139.22").map(BuildNumber::fromString))
  }

  @Test fun `a product version policy hides a prohibited build and keeps an earlier allowed build`() {
    val result = assertLoaded("IU-262.100", ChannelStatus.RELEASE, POLICY_CHANNELS, policies = listOf(BlockingPolicy("262.102")))
    assertBuild("262.101", result.newBuild)
  }

  @Test fun `prohibited minor updates do not hide an allowed major update`() {
    val policies = listOf(BlockingPolicy("262.101", "262.102"))
    assertBuild("263.100", assertLoaded("IU-262.100", ChannelStatus.RELEASE, POLICY_CHANNELS, policies = policies).newBuild)
  }

  @Test fun `every product version policy must allow a target build`() {
    val policies = listOf(BlockingPolicy("262.102"), BlockingPolicy("262.101"))
    assertBuild("263.100", assertLoaded("IU-262.100", ChannelStatus.RELEASE, POLICY_CHANNELS, policies = policies).newBuild)
  }

  @Test fun `no allowed target build produces no update`() {
    val policies = listOf(BlockingPolicy("262.101", "262.102", "263.100"))
    assertTrue(check("IU-262.100", ChannelStatus.RELEASE, POLICY_CHANNELS, policies = policies) is PlatformUpdates.Empty)
  }

  @Test fun `a product version policy does not enable an unselected channel`() {
    val policies = listOf(BlockingPolicy("262.101", "262.102", "263.100"))
    assertTrue(check("IU-262.100", ChannelStatus.RELEASE, POLICY_CHANNELS, policies = policies) is PlatformUpdates.Empty)
    assertBuild("264.100", assertLoaded("IU-262.100", ChannelStatus.EAP, POLICY_CHANNELS, policies = policies).newBuild)
  }

  @Test fun `ignored builds stay excluded with a product version policy`() {
    val result = assertLoaded("IU-262.100", ChannelStatus.RELEASE, POLICY_CHANNELS, listOf("262.101"), listOf(BlockingPolicy("262.102")))
    assertBuild("263.100", result.newBuild)
  }

  @Test fun `a prohibited intermediate patch does not prohibit an allowed target build`() {
    val result = assertLoaded("IU-262.100", ChannelStatus.RELEASE, POLICY_CHANNELS, policies = listOf(BlockingPolicy("262.101")))
    assertBuild("262.102", result.newBuild)
    assertEquals(listOf("262.100", "262.101", "262.102"), result.patches?.chain?.map { it.asStringWithoutProductCode() })
  }

  @Test fun `a product version policy is asked on every check`() {
    val policy = BlockingPolicy("262.102")
    val strategy = strategy("IU-262.100", ChannelStatus.RELEASE, POLICY_CHANNELS, policies = listOf(policy))
    assertBuild("262.101", (strategy.checkForUpdates() as PlatformUpdates.Loaded).newBuild)
    policy.blockedBuilds = emptySet()
    assertBuild("262.102", (strategy.checkForUpdates() as PlatformUpdates.Loaded).newBuild)
  }

  @Test fun `a product version policy receives the target product code`() {
    val policy = BlockingPolicy()
    check("IU-262.100", ChannelStatus.RELEASE, POLICY_CHANNELS, policies = listOf(policy))
    assertTrue(policy.checkedBuilds.isNotEmpty())
    assertTrue(policy.checkedBuilds.all { it.productCode == "IU" })
  }

  private fun check(
    currentBuild: String,
    selectedChannel: ChannelStatus,
    testData: String,
    ignoredBuilds: List<String> = emptyList(),
    policies: List<ProductVersionPolicy> = emptyList(),
  ): PlatformUpdates = strategy(currentBuild, selectedChannel, testData, ignoredBuilds, policies).checkForUpdates()

  private fun strategy(
    currentBuild: String,
    selectedChannel: ChannelStatus,
    testData: String,
    ignoredBuilds: List<String> = emptyList(),
    policies: List<ProductVersionPolicy> = emptyList(),
  ): UpdateStrategy {
    val updatesInfoText = """
          <products>
            <product name="IntelliJ IDEA">
              <code>IU</code>
              ${testData}
            </product>
          </products>"""
    val settings = UpdateSettings()
    settings.selectedChannelStatus = selectedChannel
    settings.ignoredBuildNumbers += ignoredBuilds
    return UpdateStrategy(
      BuildNumber.fromString(currentBuild)!!,
      // the test gives the OS, because only some systems read the <distribution> element
      parseUpdateData(JDOMUtil.load(updatesInfoText), "IU", distributionOs = "mac"),
      settings,
      UpdateStrategyCustomization(),
      policies,
    )
  }

  private fun assertLoaded(
    currentBuild: String,
    selectedChannel: ChannelStatus,
    testData: String,
    ignoredBuilds: List<String> = emptyList(),
    policies: List<ProductVersionPolicy> = emptyList(),
  ): PlatformUpdates.Loaded {
    val result = check(currentBuild, selectedChannel, testData, ignoredBuilds, policies)
    assertThat(result is PlatformUpdates.Loaded)
    return result as PlatformUpdates.Loaded
  }

  private fun assertBuild(expected: String, build: BuildInfo) =
    assertEquals(expected, build.number.asStringWithoutProductCode())

  private class BlockingPolicy(vararg blockedBuilds: String) : ProductVersionPolicy {
    var blockedBuilds: Set<String> = blockedBuilds.toSet()
    val checkedBuilds = mutableListOf<BuildNumber>()

    override fun isVersionAllowed(build: BuildNumber): Boolean {
      checkedBuilds.add(build)
      return build.asStringWithoutProductCode() !in blockedBuilds
    }
  }

  private companion object {
    const val POLICY_CHANNELS = """
      <channel id="release" status="release" licensing="release">
        <build number="262.101" version="2026.2.1"><patch from="262.100"/></build>
        <build number="262.102" version="2026.2.2"><patch from="262.101"/></build>
        <build number="263.100" version="2026.3"/>
      </channel>
      <channel id="eap" status="eap" licensing="eap">
        <build number="264.100" version="2027.1 EAP"/>
      </channel>"""
  }
}

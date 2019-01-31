// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.updates

import com.intellij.openapi.updateSettings.impl.*
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

// unless stated otherwise, the behavior described in cases is true for 162+
class UpdateStrategyTest : BareTestFixtureTestCase() {
  @Test fun `channel contains no builds`() {
    val result = check("IU-145.258", ChannelStatus.RELEASE, """<channel id="IDEA_Release" status="release" licensing="release"/>""")
    assertNull(result.newBuild)
  }

  @Test fun `already on the latest build`() {
    val result = check("IU-145.258", ChannelStatus.RELEASE, """
      <channel id="IDEA15_Release" status="release" licensing="release">
        <build number="143.2332" version="15.0.5"/>
      </channel>
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="145.258" version="2016.1"/>
      </channel>""")
    assertNull(result.newBuild)
  }

  @Test fun `patch exclusions`() {
    val channels = """
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="145.597" version="2016.1.1">
          <patch from="145.596"/>
          <patch from="145.258" exclusions="win,mac,unix"/>
        </build>
      </channel>"""
    assertNotNull(check("IU-145.596", ChannelStatus.RELEASE, channels).patches)
    assertNull(check("IU-145.258", ChannelStatus.RELEASE, channels).patches)
  }

  @Test fun `order of builds does not matter`() {
    val resultDesc = check("IU-143.2332", ChannelStatus.RELEASE, """
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="145.597" version="2016.1.1"/>
        <build number="145.258" version="2016.1"/>
      </channel>""")
    assertBuild("145.597", resultDesc.newBuild)

    val resultAsc = check("IU-143.2332", ChannelStatus.RELEASE, """
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="145.258" version="2016.1"/>
        <build number="145.597" version="2016.1.1"/>
      </channel>""")
    assertBuild("145.597", resultAsc.newBuild)
  }

  @Test fun `newer updates are preferred`() {
    val result = check("IU-145.258", ChannelStatus.EAP, """
      <channel id="IDEA_EAP" status="eap" licensing="eap">
        <build number="145.596" version="2016.1.1 EAP"/>
      </channel>
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="145.597" version="2016.1.1"/>
      </channel>""")
    assertBuild("145.597", result.newBuild)
  }

  @Test fun `newer updates are preferred over more stable ones`() {
    val result = check("IU-145.257", ChannelStatus.EAP, """
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
    assertBuild("145.258", check("IU-145.256", ChannelStatus.RELEASE, channels).newBuild)
    assertNull(check("IU-145.258", ChannelStatus.RELEASE, channels).newBuild)
  }

  @Test fun `ignored updates are excluded`() {
    val result = check("IU-145.258", ChannelStatus.EAP, """
      <channel id="IDEA_EAP" status="eap" licensing="eap">
        <build number="145.596" version="2016.1.1 EAP"/>
        <build number="145.595" version="2016.1.1 EAP"/>
      </channel>""", listOf("145.596"))
    assertBuild("145.595", result.newBuild)
  }

  @Test fun `ignored same-baseline updates do not hide new major releases`() {
    val result = check("IU-145.971", ChannelStatus.RELEASE, """
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
    assertBuild("145.595", check("IU-145.258", ChannelStatus.EAP, channels).newBuild)
    assertBuild("145.596", check("IU-145.595", ChannelStatus.EAP, channels).newBuild)
  }

  @Test fun `updates can be targeted for specific builds (same build)`() {
    val channels = """
      <channel id="IDEA_EAP" status="release" licensing="release">
        <build number="163.101" version="2016.3.1" targetSince="163.0" targetUntil="163.*"><message>bug fix</message></build>
        <build number="163.101" version="2016.3.1"><message>new release</message></build>
      </channel>"""
    assertEquals("new release", check("IU-145.258", ChannelStatus.RELEASE, channels).newBuild?.message)
    assertEquals("bug fix", check("IU-163.50", ChannelStatus.RELEASE, channels).newBuild?.message)
  }

  @Test fun `updates from the same baseline are preferred (unified channels)`() {
    val result = check("IU-143.2287", ChannelStatus.EAP, """
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
    val result = check("IU-143.2287", ChannelStatus.EAP, """
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
    val result = check("IU-143.2332", ChannelStatus.EAP, """
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
    assertBuild("162.11.10", check("IU-145.597", ChannelStatus.RELEASE, channels).newBuild)
    assertBuild("162.11.10", check("IU-162.7.23", ChannelStatus.RELEASE, channels).newBuild)
    assertNull(check("IU-162.11.11", ChannelStatus.RELEASE, channels).newBuild)

    val result = check("IU-162.11.10", ChannelStatus.RELEASE, """
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
    assertEquals("IDEA15_EAP", check("IU-163.1", ChannelStatus.EAP, (eap15 + eap + beta15 + beta + release15 + release)).updatedChannel?.id)
    assertEquals("IDEA_EAP", check("IU-163.1", ChannelStatus.EAP, (eap + eap15 + beta + beta15 + release + release15)).updatedChannel?.id)
    assertEquals("IDEA15_EAP", check("IU-163.1", ChannelStatus.EAP, (release15 + release + beta15 + beta + eap15 + eap)).updatedChannel?.id)
    assertEquals("IDEA_EAP", check("IU-163.1", ChannelStatus.EAP, (release + release15 + beta + beta15 + eap + eap15)).updatedChannel?.id)

    assertEquals("IDEA15_Beta", check("IU-163.1", ChannelStatus.BETA, (release15 + release + beta15 + beta + eap15 + eap)).updatedChannel?.id)
    assertEquals("IDEA_Beta", check("IU-163.1", ChannelStatus.BETA, (release + release15 + beta + beta15 + eap + eap15)).updatedChannel?.id)

    assertEquals("IDEA15_Release", check("IU-163.1", ChannelStatus.RELEASE, (eap15 + eap + beta15 + beta + release15 + release)).updatedChannel?.id)
    assertEquals("IDEA_Release", check("IU-163.1", ChannelStatus.RELEASE, (eap + eap15 + beta + beta15 + release + release15)).updatedChannel?.id)
    assertEquals("IDEA15_Release", check("IU-163.1", ChannelStatus.RELEASE, (release15 + release + beta15 + beta + eap15 + eap)).updatedChannel?.id)
    assertEquals("IDEA_Release", check("IU-163.1", ChannelStatus.RELEASE, (release + release15 + beta + beta15 + eap + eap15)).updatedChannel?.id)
  }

  /*
   * Since 183.
   */

  @Test fun `building linear patch chain`() {
    val result = check("IU-182.3569.1", ChannelStatus.EAP, """
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
    val result = check("IU-182.3684.40", ChannelStatus.EAP, """
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
    val result = check("IU-183.3795.13", ChannelStatus.EAP, """
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

  //<editor-fold desc="Helpers.">
  private fun check(currentBuild: String,
                    selectedChannel: ChannelStatus,
                    testData: String,
                    ignoredBuilds: List<String> = emptyList()): CheckForUpdateResult {
    val updates = UpdatesInfo(JDOMUtil.load("""
          <products>
            <product name="IntelliJ IDEA">
              <code>IU</code>
              ${testData}
            </product>
          </products>"""))
    val settings = UpdateSettings()
    settings.selectedChannelStatus = selectedChannel
    settings.ignoredBuildNumbers += ignoredBuilds
    val result = UpdateStrategy(BuildNumber.fromString(currentBuild), updates, settings).checkForUpdates()
    assertEquals(UpdateStrategy.State.LOADED, result.state)
    return result
  }

  private fun assertBuild(expected: String, build: BuildInfo?) {
    assertEquals(expected, build?.number?.asStringWithoutProductCode())
  }
  //</editor-fold>
}
/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.updates

import com.intellij.openapi.updateSettings.impl.*
import com.intellij.openapi.util.BuildNumber
import com.intellij.util.loadElement
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UpdateStrategyTest {
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
    val result = check("IU-145.258", ChannelStatus.RELEASE, """
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="145.597" version="2016.1.1">
          <patch from="145.596"/>
          <patch from="145.258" exclusions="win,mac,unix"/>
        </build>
      </channel>""")
    assertNotNull(result.findPatchForBuild(BuildNumber.fromString("145.596")))
    assertNull(result.findPatchForBuild(BuildNumber.fromString("145.258")))
  }

  @Test fun `order of builds does not matter`() {
    val resultDesc = check("IU-143.2332", ChannelStatus.RELEASE, """
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="145.597" version="2016.1.1"/>
        <build number="145.258" version="2016.1"/>
      </channel>""")
    assertEquals("145.597", resultDesc.newBuild?.number.toString())

    val resultAsc = check("IU-143.2332", ChannelStatus.RELEASE, """
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="145.258" version="2016.1"/>
        <build number="145.597" version="2016.1.1"/>
      </channel>""")
    assertEquals("145.597", resultAsc.newBuild?.number.toString())
  }

  @Test fun `newer updates are preferred`() {
    val result = check("IU-145.258", ChannelStatus.EAP, """
      <channel id="IDEA_EAP" status="eap" licensing="eap">
        <build number="145.596" version="2016.1.1 EAP"/>
      </channel>
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="145.597" version="2016.1.1"/>
      </channel>""")
    assertEquals("145.597", result.newBuild?.number.toString())
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
    assertEquals("145.596", result.newBuild?.number.toString())
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
    assertEquals("145.258", check("IU-145.256", ChannelStatus.RELEASE, channels).newBuild?.number.toString())
    assertNull(check("IU-145.258", ChannelStatus.RELEASE, channels).newBuild)
  }

  @Test fun `ignored updates are excluded`() {
    val result = check("IU-145.258", ChannelStatus.EAP, """
      <channel id="IDEA_EAP" status="eap" licensing="eap">
        <build number="145.596" version="2016.1.1 EAP"/>
        <build number="145.595" version="2016.1.1 EAP"/>
      </channel>""", listOf("145.596"))
    assertEquals("145.595", result.newBuild?.number.toString())
  }

  @Test fun `updates can be targeted for specific builds`() {
    val channels = """
      <channel id="IDEA_EAP" status="eap" licensing="eap">
        <build number="145.596" version="2016.1.1 EAP" targetSince="145.595" targetUntil="145.*"/> <!-- this build is not for everyone -->
        <build number="145.595" version="2016.1.1 EAP"/>
      </channel>"""
    assertEquals("145.595", check("IU-145.258", ChannelStatus.EAP, channels).newBuild?.number.toString())
    assertEquals("145.596", check("IU-145.595", ChannelStatus.EAP, channels).newBuild?.number.toString())
  }

  @Test fun `updates from the same baseline are preferred`() {
    val result = check("IU-143.2287", ChannelStatus.RELEASE, """
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="143.2332" version="15.0.5"/>
        <build number="145.597" version="2016.1.1"/>
      </channel>""")
    assertEquals("143.2332", result.newBuild?.number.toString())
  }

  @Test fun `cross-baseline updates are perfectly legal`() {
    val result = check("IU-143.2332", ChannelStatus.EAP, """
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="143.2332" version="15.0.5"/>
        <build number="145.597" version="2016.1.1"/>
      </channel>""")
    assertEquals("145.597", result.newBuild?.number.toString())
  }

  @Test fun `variable-length build numbers are supported`() {
    var result = check("IU-143.2332", ChannelStatus.RELEASE, """
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="143.2332.10" version="15.0.5"/>
      </channel>""")
    assertEquals("143.2332.10", result.newBuild?.number.toString())

    result = check("IU-143.2332.10", ChannelStatus.RELEASE, """
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="143.2333" version="15.0.5"/>
      </channel>""")
    assertEquals("143.2333", result.newBuild?.number.toString())

    result = check("IU-143.2332.9", ChannelStatus.RELEASE, """
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="143.2332.10" version="15.0.5"/>
      </channel>""")
    assertEquals("143.2332.10", result.newBuild?.number.toString())

    result = check("IU-143.2332.11", ChannelStatus.RELEASE, """
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="143.2332.10" version="15.0.5"/>
      </channel>""")
    assertNull(result.newBuild)
  }
  
  @Test fun `year-based build numbers are supported`() {
    var result = check("IU-145.100", ChannelStatus.RELEASE, """
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="2016.1.10.10" version="15.0.5"/>
      </channel>""")
    assertEquals("2016.1.10.10", result.newBuild?.number.toString())

    result = check("IU-2016.1.10.10", ChannelStatus.RELEASE, """
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="2017.1.1.1" version="15.0.5"/>
      </channel>""")
    assertEquals("2017.1.1.1", result.newBuild?.number.toString())

    result = check("IU-2016.1.10.10", ChannelStatus.RELEASE, """
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="2016.2.10.10" version="15.0.5"/>
      </channel>""")
    assertEquals("2016.2.10.10", result.newBuild?.number.toString())

    result = check("IU-2016.1.10.10", ChannelStatus.RELEASE, """
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="2016.1.11.10" version="15.0.5"/>
      </channel>""")
    assertEquals("2016.1.11.10", result.newBuild?.number.toString())

    result = check("IU-2016.1.10.10", ChannelStatus.RELEASE, """
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="2016.1.10.11" version="15.0.5"/>
      </channel>""")
    assertEquals("2016.1.10.11", result.newBuild?.number.toString())

    result = check("IU-2016.1.10.10", ChannelStatus.RELEASE, """
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="2016.1.10.9" version="15.0.5"/>
      </channel>""")
    assertNull(result.newBuild)

    // e.g. android-studio format
    result = check("IU-2016.1.10.10.20161111", ChannelStatus.RELEASE, """
      <channel id="IDEA_Release" status="release" licensing="release">
        <build number="IU-2016.1.10.10.20161112" version="15.0.5"/>
      </channel>""")
    assertEquals("IU-2016.1.10.10.20161112", result.newBuild?.number.toString())
  }

  private fun check(currentBuild: String,
                    selectedChannel: ChannelStatus,
                    testData: String,
                    ignoredBuilds: List<String> = emptyList()): CheckForUpdateResult {
    val updates = UpdatesInfo(loadElement("""
      <products>
        <product name="IntelliJ IDEA">
          <code>IU</code>
          ${testData}
        </product>
      </products>"""))
    val settings = object : UserUpdateSettings {
      override fun getSelectedChannelStatus() = selectedChannel
      override fun getIgnoredBuildNumbers() = ignoredBuilds
    }
    val result = UpdateStrategy(BuildNumber.fromString(currentBuild), updates, settings).checkForUpdates()
    assertEquals(UpdateStrategy.State.LOADED, result.state)
    return result
  }
}
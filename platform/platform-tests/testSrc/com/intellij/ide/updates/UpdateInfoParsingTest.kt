// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.updates

import com.intellij.openapi.updateSettings.impl.ChannelStatus
import com.intellij.openapi.updateSettings.impl.UpdateChannel
import com.intellij.openapi.updateSettings.impl.parseDistribution
import com.intellij.openapi.updateSettings.impl.parseUpdateData
import com.intellij.openapi.updateSettings.impl.productMigration.distributionXml
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.IoTestUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.IOException
import java.net.URL
import java.text.SimpleDateFormat

class UpdateInfoParsingTest {
  @Test fun liveJetBrainsUpdateFile() {
    try {
      assertNotNull(parseUpdateData(URL("https://www.jetbrains.com/updates/updates.xml").readText(), "IC"))
    }
    catch (e: IOException) {
      assumeTrue(e.toString(), false)
    }
  }

  @Test fun liveAndroidUpdateFile() {
    try {
      assertNotNull(parseUpdateData(URL("https://dl.google.com/android/studio/patches/updates.xml").readText(), "AI"))
    }
    catch (e: IOException) {
      assumeTrue(e.toString(), false)
    }
  }

  @Test fun emptyChannels() {
    val updates = """
      <products>
        <product name="IntelliJ IDEA">
          <code>IU</code>
          <code>IC</code>
        </product>
      </products>""".trimIndent()
    val ultimate = parseUpdateData(updates, "IU")!!
    assertEquals("IntelliJ IDEA", ultimate.name)
    assertEquals(0, ultimate.channels.size)
    val community = parseUpdateData(updates, "IC")!!
    assertEquals("IntelliJ IDEA", community.name)
    assertEquals(0, community.channels.size)
  }

  @Test fun oneProductOnly() {
    val product = parseUpdateData("""
        <products>
          <product name="IntelliJ IDEA">
            <code>IU</code>
  
            <channel id="idea90" name="IntelliJ IDEA 9 updates" status="release" url="https://www.jetbrains.com/idea/whatsnew">
              <build number="95.627" version="9.0.4">
                <message>IntelliJ IDEA 9.0.4 is available. Please visit https://www.jetbrains.com/idea to learn more and download it.</message>
                <patch from="95.429" size="2"/>
              </build>
            </channel>
  
            <channel id="IDEA10EAP" name="IntelliJ IDEA X EAP" status="eap" licensing="eap" url="http://confluence.jetbrains.net/display/IDEADEV/IDEA+X+EAP">
              <build number="98.520" version="10" releaseDate="20110403">
                <message>IntelliJ IDEA X RC is available. Please visit http://confluence.jetbrains.net/display/IDEADEV/IDEA+X+EAP to learn more.</message>
                <button name="Download" url="http://www.jetbrains.com/idea" download="true"/>
              </build>
            </channel>
          </product>
        </products>""".trimIndent(), "IU")!!

    assertEquals("IntelliJ IDEA", product.name)
    assertEquals(2, product.channels.size)

    val channel = product.channels.find { it.id == "IDEA10EAP" }!!
    assertEquals(ChannelStatus.EAP, channel.status)
    assertEquals(UpdateChannel.Licensing.EAP, channel.licensing)
    assertEquals(1, channel.builds.size)

    val build = channel.builds[0]
    assertEquals("98.520", build.number.asStringWithoutProductCode())
    assertEquals("2011-04-03", SimpleDateFormat("yyyy-MM-dd").format(build.releaseDate))
    assertNotNull(build.downloadUrl)
    assertEquals(0, build.patches.size)

    assertEquals(1, product.channels.find { it.id == "idea90" }!!.builds[0].patches.size)
  }

  @Test
  fun targetRanges() {
    val product = parseUpdateData("""
        <products>
          <product name="IntelliJ IDEA">
            <code>IU</code>
            <channel id="IDEA_EAP" status="eap">
              <build number="2016.2.123" version="2016.2" targetSince="0" targetUntil="145.*"/>
              <build number="2016.2.123" version="2016.2" targetSince="2016.1" targetUntil="2016.1.*"/>
              <build number="2016.1.11" version="2016.1"/>
            </channel>
          </product>
        </products>""".trimIndent(), "IU")
    assertEquals(2, product!!.channels[0].builds.count { it.target != null })
  }

  @Test
  fun fullBuildNumbers() {
    val buildInfo = parseUpdateData("""
        <products>
          <product name="IntelliJ IDEA">
            <code>IU</code>
            <channel id="IDEA_EAP" status="eap">
              <build number="162.100" fullNumber="162.100.1" version="2016.2">
                 <patch from="162.99" fullFrom="162.99.2" size="1"/>
              </build>
            </channel>
          </product>
        </products>""".trimIndent(), "IU")!!.channels[0].builds[0]
    assertEquals("162.100.1", buildInfo.number.asStringWithoutProductCode())
    assertEquals("162.99.2", buildInfo.patches[0].fromBuild.asStringWithoutProductCode())
  }

  @Test
  fun disableMachineId() {
    val product = parseUpdateData("""
      <products>
        <product name="IntelliJ IDEA" disableMachineId="true">
          <code>IU</code>
          <channel id="IDEA_EAP" status="eap">
            <build number="162.100" fullNumber="162.100.1" version="2016.2">
               <patch from="162.99" fullFrom="162.99.2" size="1"/>
            </build>
          </channel>
        </product>
      </products>""".trimIndent(), "IU")!!
    assertTrue(product.disableMachineId)
  }

  @Test
  fun distribution() {
    IoTestUtil.assumeMacOS()
    val builds = parseUpdateData("""
      <products>
        <product name="GoLand">
          <code>GO</code>
          <channel id="GO_Release" status="release">
            <build number="271.1234" fullNumber="271.1234.56" version="2027.1">
              ${distributionXml()}
            </build>
            <build number="263.200" version="2026.3.2"/>
          </channel>
        </product>
      </products>""".trimIndent(), "GO")!!.channels[0].builds

    val distribution = builds[0].distribution!!
    assertEquals("JB", distribution.product)
    assertEquals("https://www.jetbrains.com/go-to-unified.json", distribution.manifestUrl)
    assertTrue(distribution.fileUrl, distribution.fileUrl.startsWith("https://download.jetbrains.com/unified-ide/unified-ide-"))

    assertNull(builds[1].distribution)
  }

  @Test
  fun invalidDistribution() {
    val macFile = """<file os="mac" arch="aarch64" url="https://download.jetbrains.com/unified-ide.dmg"/>"""
    fun parse(attributes: String, files: String = macFile) =
      parseDistribution(JDOMUtil.load("<distribution ${attributes}>${files}</distribution>"), os = "mac", arch = "aarch64")

    val valid = parse("""product="JB" manifest="https://www.jetbrains.com/m.json"""")
    assertEquals("https://download.jetbrains.com/unified-ide.dmg", valid?.fileUrl)
    assertNull(parse("""manifest="https://www.jetbrains.com/m.json""""))
    assertNull(parse("""product="" manifest="https://www.jetbrains.com/m.json""""))
    assertNull(parse("""product="JB""""))
    assertNull(parse("""product="JB" manifest="""""))
    assertNull(parse("""product="JB" manifest="https://www.jetbrains.com/m.json"""", """<file os="mac" arch="aarch64" url=""/>"""))
  }

  @Test
  fun distributionWithoutFileForThisPlatform() {
    val distribution = JDOMUtil.load("""
      <distribution product="JB" manifest="https://www.jetbrains.com/m.json">
        <file os="mac" arch="aarch64" url="https://download.jetbrains.com/unified-ide-aarch64.dmg"/>
        <file os="mac" url="https://download.jetbrains.com/unified-ide.dmg"/>
      </distribution>""".trimIndent())
    assertNotNull(parseDistribution(distribution, os = "mac", arch = "aarch64"))
    assertNull(parseDistribution(distribution, os = "mac", arch = "x64"))
  }

  @Test
  fun distributionIsIgnoredOnOtherSystems() {
    val distribution = JDOMUtil.load("""
      <distribution product="JB" manifest="https://www.jetbrains.com/m.json">
        <file os="win" arch="x64" url="https://download.jetbrains.com/unified-ide.exe"/>
        <file os="unix" arch="x64" url="https://download.jetbrains.com/unified-ide.tar.gz"/>
      </distribution>""".trimIndent())
    assertNull(parseDistribution(distribution, os = "win", arch = "x64"))
    assertNull(parseDistribution(distribution, os = "unix", arch = "x64"))
  }

  @Test
  fun distributionIsIgnoredWithKillSwitch() {
    val distribution = JDOMUtil.load("""
      <distribution product="JB" manifest="https://www.jetbrains.com/m.json">
        <file os="mac" arch="aarch64" url="https://download.jetbrains.com/unified-ide-aarch64.dmg"/>
      </distribution>""".trimIndent())
    assertNull(parseDistribution(distribution, os = "mac", arch = "aarch64", isMigrationDisabled = true))
    assertNotNull(parseDistribution(distribution, os = "mac", arch = "aarch64", isMigrationDisabled = false))
  }
}

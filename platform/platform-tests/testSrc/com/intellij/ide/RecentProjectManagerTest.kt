// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.configurationStore.deserializeInto
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.assertions.Assertions.assertThat
import junit.framework.TestCase.assertFalse
import org.intellij.lang.annotations.Language
import org.junit.ClassRule
import org.junit.Test
import kotlin.test.assertTrue

class RecentProjectManagerTest {
  companion object {
    @ClassRule
    @JvmField
    val appRule = ApplicationRule()
  }

  // IDEA-298050
  @Test
  fun `state modification tracker updated on groups expand`() {
    val manager = RecentProjectsManagerBase()

    @Language("XML")
    val element = JDOMUtil.load("""
    <application>
      <component name="RecentDirectoryProjectsManager">
        <option name="recentPaths">
          <list>
            <option value="/home/WebstormProjects/untitled" />
            <option value="/home/WebstormProjects/conference-data" />
          </list>
        </option>
        <option name="pid" value="" />
        <option name="additionalInfo">
          <map>
            <entry key="/home/WebstormProjects/conference-data">
              <value>
                <RecentProjectMetaInfo>
                  <option name="build" value="WS-191.8026.39" />
                  <option name="productionCode" value="WS" />
                  <option name="projectOpenTimestamp" value="1572355647642" />
                  <option name="buildTimestamp" value="1564385774770" />
                </RecentProjectMetaInfo>
              </value>
            </entry>
            <entry key="/home/WebstormProjects/new-react-app-for-testing">
              <value>
                <RecentProjectMetaInfo>
                  <option name="build" value="WS-191.8026.39" />
                  <option name="productionCode" value="WS" />
                  <option name="projectOpenTimestamp" value="1571662310725" />
                  <option name="buildTimestamp" value="1564385807237" />
                </RecentProjectMetaInfo>
              </value>
            </entry>
            <entry key="/home/WebstormProjects/untitled">
              <value>
                <RecentProjectMetaInfo>
                  <option name="build" value="WS-191.8026.39" />
                  <option name="productionCode" value="WS" />
                  <option name="projectOpenTimestamp" value="1574357146611" />
                  <option name="buildTimestamp" value="1564385803063" />
                </RecentProjectMetaInfo>
              </value>
            </entry>
          </map>
        </option>
        <option name="groups">
      <list>
        <ProjectGroup>
          <option name="name" value="TEST" />
          <option name="projects">
            <list>
              <option value="/home/WebstormProjects/conference-data" />
            </list>
          </option>
        </ProjectGroup>
        <ProjectGroup>
          <option name="name" value="TEST2" />
          <option name="projects">
            <list>
              <option value="/home/WebstormProjects/new-react-app-for-testing" />
            </list>
          </option>
        </ProjectGroup>
        <ProjectGroup>
          <option name="name" value="TEST3" />
        </ProjectGroup>
      </list>
    </option>
      </component>
    </application>
    """.trimIndent())
    val state = RecentProjectManagerState()
    element.getChild("component")!!.deserializeInto(state)
    manager.loadState(state)

    val initialModCounter = state.modificationCount

    val groups = manager.groups
    assertTrue(groups.all { !it.isExpanded })
    groups.first().isExpanded = true

    val newModCounter = state.modificationCount
    assertTrue(
      newModCounter > initialModCounter,
      "Modification counter didn't change on group expand. Old counter: $initialModCounter, new counter: $newModCounter"
    )
  }

  // IDEA-298050
  @Test
  fun `state modification tracker updated on groups expand when one was expanded before`() {
    val manager = RecentProjectsManagerBase()

    @Language("XML")
    val element = JDOMUtil.load("""
    <application>
      <component name="RecentDirectoryProjectsManager">
        <option name="recentPaths">
          <list>
            <option value="/home/WebstormProjects/untitled" />
            <option value="/home/WebstormProjects/conference-data" />
          </list>
        </option>
        <option name="pid" value="" />
        <option name="additionalInfo">
          <map>
            <entry key="/home/WebstormProjects/conference-data">
              <value>
                <RecentProjectMetaInfo>
                  <option name="build" value="WS-191.8026.39" />
                  <option name="productionCode" value="WS" />
                  <option name="projectOpenTimestamp" value="1572355647642" />
                  <option name="buildTimestamp" value="1564385774770" />
                </RecentProjectMetaInfo>
              </value>
            </entry>
            <entry key="/home/WebstormProjects/new-react-app-for-testing">
              <value>
                <RecentProjectMetaInfo>
                  <option name="build" value="WS-191.8026.39" />
                  <option name="productionCode" value="WS" />
                  <option name="projectOpenTimestamp" value="1571662310725" />
                  <option name="buildTimestamp" value="1564385807237" />
                </RecentProjectMetaInfo>
              </value>
            </entry>
            <entry key="/home/WebstormProjects/untitled">
              <value>
                <RecentProjectMetaInfo>
                  <option name="build" value="WS-191.8026.39" />
                  <option name="productionCode" value="WS" />
                  <option name="projectOpenTimestamp" value="1574357146611" />
                  <option name="buildTimestamp" value="1564385803063" />
                </RecentProjectMetaInfo>
              </value>
            </entry>
          </map>
        </option>
        <option name="groups">
      <list>
        <ProjectGroup>
          <option name="name" value="TEST" />
          <option name="projects">
            <list>
              <option value="/home/WebstormProjects/conference-data" />
            </list>
          </option>
        </ProjectGroup>
        <ProjectGroup>
          <option name="expanded" value="true" />
          <option name="name" value="TEST2" />
          <option name="projects">
            <list>
              <option value="/home/WebstormProjects/new-react-app-for-testing" />
            </list>
          </option>
        </ProjectGroup>
        <ProjectGroup>
          <option name="name" value="TEST3" />
        </ProjectGroup>
      </list>
    </option>
      </component>
    </application>
    """.trimIndent())
    val state = RecentProjectManagerState()
    element.getChild("component")!!.deserializeInto(state)
    manager.loadState(state)

    val initialModCounter = state.modificationCount

    val groups = manager.groups
    assertFalse(groups.first().isExpanded)
    assertTrue(groups[1].isExpanded)

    groups.first().isExpanded = true

    val newModCounter = state.modificationCount
    assertTrue(
      newModCounter > initialModCounter,
      "Modification counter didn't change on group expand. Old counter: $initialModCounter, new counter: $newModCounter"
    )
  }

  @Test
  fun `ignore projects in additionalInfo if not in recentPaths`() {
    val manager = RecentProjectsManagerBase()

    val element = JDOMUtil.load("""
    <application>
      <component name="RecentDirectoryProjectsManager">
        <option name="recentPaths">
          <list>
            <option value="/home/WebstormProjects/untitled" />
            <option value="/home/WebstormProjects/conference-data" />
          </list>
        </option>
        <option name="pid" value="" />
        <option name="additionalInfo">
          <map>
            <entry key="/home/WebstormProjects/conference-data">
              <value>
                <RecentProjectMetaInfo>
                  <option name="build" value="WS-191.8026.39" />
                  <option name="productionCode" value="WS" />
                  <option name="projectOpenTimestamp" value="1572355647642" />
                  <option name="buildTimestamp" value="1564385774770" />
                </RecentProjectMetaInfo>
              </value>
            </entry>
            <entry key="/home/WebstormProjects/new-react-app-for-testing">
              <value>
                <RecentProjectMetaInfo>
                  <option name="build" value="WS-191.8026.39" />
                  <option name="productionCode" value="WS" />
                  <option name="projectOpenTimestamp" value="1571662310725" />
                  <option name="buildTimestamp" value="1564385807237" />
                </RecentProjectMetaInfo>
              </value>
            </entry>
            <entry key="/home/WebstormProjects/untitled">
              <value>
                <RecentProjectMetaInfo>
                  <option name="build" value="WS-191.8026.39" />
                  <option name="productionCode" value="WS" />
                  <option name="projectOpenTimestamp" value="1574357146611" />
                  <option name="buildTimestamp" value="1564385803063" />
                </RecentProjectMetaInfo>
              </value>
            </entry>
            <entry key="/home/WebstormProjects/untitled2">
              <value>
                <RecentProjectMetaInfo>
                  <option name="build" value="WS-191.8026.39" />
                  <option name="productionCode" value="WS" />
                  <option name="projectOpenTimestamp" value="1574416340298" />
                  <option name="buildTimestamp" value="1564385805606" />
                </RecentProjectMetaInfo>
              </value>
            </entry>
          </map>
        </option>
      </component>
    </application>
    """.trimIndent())
    val state = RecentProjectManagerState()
    element.getChild("component")!!.deserializeInto(state)
    manager.loadState(state)
    assertThat(manager.state.additionalInfo.keys.joinToString("\n")).isEqualTo("""
      /home/WebstormProjects/conference-data
      /home/WebstormProjects/untitled
    """.trimIndent())
    @Suppress("DEPRECATION")
    assertThat(manager.state.recentPaths).isEmpty()
  }

  @Test
  fun `use order of recentPaths`() {
    val manager = RecentProjectsManagerBase()

    val element = JDOMUtil.load("""
      <application>
  <component name="RecentDirectoryProjectsManager">
    <option name="recentPaths">
      <list>
        <option value="/home/boo/Documents/macosSwiftApp" />
        <option value="/home/boo/Downloads/tester" />
        <option value="/home/boo/Downloads/RLBasicsWithSwift" />
        <option value="/home/boo/Documents/iOSSwiftTabbedApp" />
        <option value="/home/boo/Downloads/xcode83betaswift-2 copy" />
        <option value="/home/boo/Downloads/iOS" />
        <option value="/home/boo/Downloads/SwiftTabbedApp" />
        <option value="/home/boo/Documents/NewProjBridging" />
        <option value="/home/boo/Documents/BasicOc" />
        <option value="/home/boo/Documents/test_performance/LotOfPods" />
        <option value="/home/boo/Documents/AllTargetsXcode11" />
        <option value="/home/boo/Documents/BasicSwift" />
        <option value="/home/boo/Downloads/WordPress-iOS" />
        <option value="/home/boo/Documents/iosSingleSwift" />
        <option value="/home/boo/Downloads/CocoaConferences 2" />
        <option value="/home/boo/Documents/AllTargetsXcode103" />
        <option value="/home/boo/Documents/newproj201924" />
        <option value="/home/boo/Documents/BrightFuturesTestProj" />
        <option value="/home/boo/Documents/GoogleTestTests" />
      </list>
    </option>
    <option name="pid" value="" />
    <option name="additionalInfo">
      <map>
        <entry key="/home/boo/Documents/AllTargetsXcode103">
          <value>
            <RecentProjectMetaInfo>
              <option name="build" value="OC-192.7142.40" />
              <option name="productionCode" value="OC" />
              <option name="binFolder" value="/app/bin" />
              <option name="projectOpenTimestamp" value="1572464701329" />
              <option name="buildTimestamp" value="1572424192550" />
            </RecentProjectMetaInfo>
          </value>
        </entry>
        <entry key="/home/boo/Documents/AllTargetsXcode11">
          <value>
            <RecentProjectMetaInfo>
              <option name="build" value="OC-192.7142.40" />
              <option name="productionCode" value="OC" />
              <option name="binFolder" value="/app/bin" />
              <option name="projectOpenTimestamp" value="1574080992714" />
              <option name="buildTimestamp" value="1572424187281" />
            </RecentProjectMetaInfo>
          </value>
        </entry>
        <entry key="/home/boo/Documents/BasicOc">
          <value>
            <RecentProjectMetaInfo>
              <option name="build" value="OC-192.7142.54" />
              <option name="productionCode" value="OC" />
              <option name="binFolder" value="/app/bin" />
              <option name="projectOpenTimestamp" value="1574684942778" />
              <option name="buildTimestamp" value="1573809737599" />
            </RecentProjectMetaInfo>
          </value>
        </entry>
        <entry key="/home/boo/Documents/BasicSwift">
          <value>
            <RecentProjectMetaInfo>
              <option name="build" value="OC-192.7142.40" />
              <option name="productionCode" value="OC" />
              <option name="binFolder" value="/app/bin" />
              <option name="projectOpenTimestamp" value="1573756936051" />
              <option name="buildTimestamp" value="1572424142023" />
            </RecentProjectMetaInfo>
          </value>
        </entry>
        <entry key="/home/boo/Documents/BrightFuturesTestProj">
          <value>
            <RecentProjectMetaInfo>
              <option name="build" value="OC-192.7142.40" />
              <option name="productionCode" value="OC" />
              <option name="binFolder" value="/app/bin" />
              <option name="projectOpenTimestamp" value="1572429822369" />
              <option name="buildTimestamp" value="1572424166312" />
            </RecentProjectMetaInfo>
          </value>
        </entry>
        <entry key="/home/boo/Documents/GoogleTestTests">
          <value>
            <RecentProjectMetaInfo>
              <option name="build" value="OC-192.6817.17" />
              <option name="productionCode" value="OC" />
              <option name="binFolder" value="/app/bin" />
              <option name="projectOpenTimestamp" value="1571921596983" />
              <option name="buildTimestamp" value="1569327129141" />
            </RecentProjectMetaInfo>
          </value>
        </entry>
        <entry key="/home/boo/Documents/NewProjBridging">
          <value>
            <RecentProjectMetaInfo>
              <option name="build" value="OC-192.7142.54" />
              <option name="productionCode" value="OC" />
              <option name="binFolder" value="/app/bin" />
              <option name="projectOpenTimestamp" value="1574771900633" />
              <option name="buildTimestamp" value="1573809725318" />
            </RecentProjectMetaInfo>
          </value>
        </entry>
        <entry key="/home/boo/Documents/iOSSwiftTabbedApp">
          <value>
            <RecentProjectMetaInfo>
              <option name="build" value="OC-192.7142.54" />
              <option name="productionCode" value="OC" />
              <option name="binFolder" value="/app/bin" />
              <option name="projectOpenTimestamp" value="1575896734989" />
              <option name="buildTimestamp" value="1573809763448" />
            </RecentProjectMetaInfo>
          </value>
        </entry>
        <entry key="/home/boo/Documents/iosSingleSwift">
          <value>
            <RecentProjectMetaInfo>
              <option name="build" value="OC-192.7142.40" />
              <option name="productionCode" value="OC" />
              <option name="binFolder" value="/app/bin" />
              <option name="projectOpenTimestamp" value="1573049774371" />
              <option name="buildTimestamp" value="1572424145779" />
            </RecentProjectMetaInfo>
          </value>
        </entry>
        <entry key="/home/boo/Documents/macosSwiftApp">
          <value>
            <RecentProjectMetaInfo>
              <option name="build" value="OC-192.7142.54" />
              <option name="productionCode" value="OC" />
              <option name="binFolder" value="/app/bin" />
              <option name="projectOpenTimestamp" value="1579175388001" />
              <option name="buildTimestamp" value="1573809737924" />
            </RecentProjectMetaInfo>
          </value>
        </entry>
        <entry key="/home/boo/Documents/newSingleViewXC103eap">
          <value>
            <RecentProjectMetaInfo>
              <option name="build" value="OC-192.6817.17" />
              <option name="productionCode" value="OC" />
              <option name="binFolder" value="/app/bin" />
              <option name="projectOpenTimestamp" value="1571314481851" />
              <option name="buildTimestamp" value="1569327149819" />
            </RecentProjectMetaInfo>
          </value>
        </entry>
        <entry key="/home/boo/Documents/newproj201924">
          <value>
            <RecentProjectMetaInfo>
              <option name="build" value="OC-192.7142.40" />
              <option name="productionCode" value="OC" />
              <option name="binFolder" value="/app/bin" />
              <option name="projectOpenTimestamp" value="1572431637302" />
              <option name="buildTimestamp" value="1572424176426" />
            </RecentProjectMetaInfo>
          </value>
        </entry>
        <entry key="/home/boo/Documents/test_performance/LotOfPods">
          <value>
            <RecentProjectMetaInfo>
              <option name="build" value="OC-192.7142.54" />
              <option name="productionCode" value="OC" />
              <option name="binFolder" value="/app/bin" />
              <option name="projectOpenTimestamp" value="1574684915629" />
              <option name="buildTimestamp" value="1573809737599" />
            </RecentProjectMetaInfo>
          </value>
        </entry>
        <entry key="/home/boo/Downloads/CocoaConferences 2">
          <value>
            <RecentProjectMetaInfo>
              <option name="build" value="OC-192.7142.40" />
              <option name="productionCode" value="OC" />
              <option name="binFolder" value="/app/bin" />
              <option name="projectOpenTimestamp" value="1572529865352" />
              <option name="buildTimestamp" value="1572424181156" />
            </RecentProjectMetaInfo>
          </value>
        </entry>
        <entry key="/home/boo/Downloads/RLBasicsWithSwift">
          <value>
            <RecentProjectMetaInfo>
              <option name="build" value="OC-192.7142.54" />
              <option name="productionCode" value="OC" />
              <option name="binFolder" value="/app/bin" />
              <option name="projectOpenTimestamp" value="1576074883019" />
              <option name="buildTimestamp" value="1573809726760" />
            </RecentProjectMetaInfo>
          </value>
        </entry>
        <entry key="/home/boo/Downloads/SwiftTabbedApp">
          <value>
            <RecentProjectMetaInfo>
              <option name="build" value="OC-192.7142.54" />
              <option name="productionCode" value="OC" />
              <option name="binFolder" value="/app/bin" />
              <option name="projectOpenTimestamp" value="1574783065457" />
              <option name="buildTimestamp" value="1573809754773" />
            </RecentProjectMetaInfo>
          </value>
        </entry>
        <entry key="/home/boo/Downloads/WordPress-iOS">
          <value>
            <RecentProjectMetaInfo>
              <option name="build" value="OC-192.7142.40" />
              <option name="productionCode" value="OC" />
              <option name="binFolder" value="/app/bin" />
              <option name="projectOpenTimestamp" value="1573740056455" />
              <option name="buildTimestamp" value="1572424172238" />
            </RecentProjectMetaInfo>
          </value>
        </entry>
        <entry key="/home/boo/Downloads/iOS">
          <value>
            <RecentProjectMetaInfo>
              <option name="build" value="OC-192.7142.54" />
              <option name="productionCode" value="OC" />
              <option name="binFolder" value="/app/bin" />
              <option name="projectOpenTimestamp" value="1575745861387" />
              <option name="buildTimestamp" value="1573809773068" />
            </RecentProjectMetaInfo>
          </value>
        </entry>
        <entry key="/home/boo/Downloads/tester">
          <value>
            <RecentProjectMetaInfo>
              <option name="build" value="OC-192.7142.54" />
              <option name="productionCode" value="OC" />
              <option name="binFolder" value="/app/bin" />
              <option name="projectOpenTimestamp" value="1576075411776" />
              <option name="buildTimestamp" value="1573809726760" />
            </RecentProjectMetaInfo>
          </value>
        </entry>
        <entry key="/home/boo/Downloads/xcode83betaswift-2 copy">
          <value>
            <RecentProjectMetaInfo>
              <option name="build" value="OC-192.7142.54" />
              <option name="productionCode" value="OC" />
              <option name="binFolder" value="/app/bin" />
              <option name="projectOpenTimestamp" value="1575896694506" />
              <option name="buildTimestamp" value="1573809763448" />
            </RecentProjectMetaInfo>
          </value>
        </entry>
      </map>
    </option>
  </component>
</application>
      """.trimIndent())
    val state = RecentProjectManagerState()
    element.getChild("component")!!.deserializeInto(state)
    manager.loadState(state)
    assertThat(manager.getRecentPaths()).containsExactly("/home/boo/Documents/macosSwiftApp",
        "/home/boo/Downloads/tester",
        "/home/boo/Downloads/RLBasicsWithSwift",
        "/home/boo/Documents/iOSSwiftTabbedApp",
        "/home/boo/Downloads/xcode83betaswift-2 copy",
        "/home/boo/Downloads/iOS",
        "/home/boo/Downloads/SwiftTabbedApp",
        "/home/boo/Documents/NewProjBridging",
        "/home/boo/Documents/BasicOc",
        "/home/boo/Documents/test_performance/LotOfPods",
        "/home/boo/Documents/AllTargetsXcode11",
        "/home/boo/Documents/BasicSwift",
        "/home/boo/Downloads/WordPress-iOS",
        "/home/boo/Documents/iosSingleSwift",
        "/home/boo/Downloads/CocoaConferences 2",
        "/home/boo/Documents/AllTargetsXcode103",
        "/home/boo/Documents/newproj201924",
        "/home/boo/Documents/BrightFuturesTestProj",
        "/home/boo/Documents/GoogleTestTests")
  }

  @Test
  fun `validate a lot of recent opened projects`() {
    val manager = RecentProjectsManagerBase()

    val entries = StringBuilder()
    val openedProjectCount = 2000
    for (i in 0 until openedProjectCount) {
      //language=XML
      entries.append("""
          <entry key="/home/boo/project-$i">
            <value>
              <RecentProjectMetaInfo opened="true">
              </RecentProjectMetaInfo>
            </value>
          </entry>
      """.trimIndent())
    }

    @Language("XML")
    val element = JDOMUtil.load("""
      <application>
  <component name="RecentDirectoryProjectsManager">
    <option name="additionalInfo">
      <map>
        $entries
      </map>
    </option>
  </component>
</application>
      """.trimIndent())
    val state = RecentProjectManagerState()
    element.getChild("component")!!.deserializeInto(state)
    manager.loadState(state)
    assertThat(manager.getRecentPaths().first()).isEqualTo("/home/boo/project-1999")
    assertThat(manager.getRecentPaths()).hasSize(openedProjectCount)
  }

  @Test
  fun `validate a lot of recent projects`() {
    val manager = RecentProjectsManagerBase()

    val entries = StringBuilder()
    val openedProjectCount = 2000
    for (i in 0 until openedProjectCount) {
      //language=XML
      entries.append("""
          <entry key="/home/boo/project-$i">
            <value>
              <RecentProjectMetaInfo opened="${i % 2 == 0}">
              </RecentProjectMetaInfo>
            </value>
          </entry>
      """.trimIndent())
    }

    @Language("XML")
    val element = JDOMUtil.load("""
      <application>
  <component name="RecentDirectoryProjectsManager">
    <option name="additionalInfo">
      <map>
        $entries
      </map>
    </option>
  </component>
</application>
      """.trimIndent())
    val state = RecentProjectManagerState()
    element.getChild("component")!!.deserializeInto(state)
    manager.loadState(state)
    assertThat(manager.getRecentPaths().first()).isEqualTo("/home/boo/project-1998")
    assertThat(manager.getRecentPaths()).hasSize(openedProjectCount / 2)
  }

  @Test
  fun `remove old recent projects`() {
    val manager = RecentProjectsManagerBase()

    val entries = StringBuilder()
    val openedProjectCount = 60
    for (i in 0 until openedProjectCount) {
      //language=XML
      entries.append("""
          <entry key="/home/boo/project-$i">
            <value>
              <RecentProjectMetaInfo>
              </RecentProjectMetaInfo>
            </value>
          </entry>
      """.trimIndent())
    }

    @Language("XML")
    val element = JDOMUtil.load("""
      <application>
  <component name="RecentDirectoryProjectsManager">
    <option name="additionalInfo">
      <map>
        $entries
      </map>
    </option>
  </component>
</application>
      """.trimIndent())
    val state = RecentProjectManagerState()
    element.getChild("component")!!.deserializeInto(state)
    manager.loadState(state)
    assertThat(manager.getRecentPaths().joinToString(separator = "\n")).isEqualTo(Array(50) { "/home/boo/project-${it + 10}" }.reversed().joinToString(separator = "\n"))
  }
}
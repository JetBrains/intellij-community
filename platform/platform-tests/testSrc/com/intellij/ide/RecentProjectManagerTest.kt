// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.configurationStore.deserializeInto
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.assertions.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Test

class RecentProjectManagerTest {
  companion object {
    @ClassRule
    @JvmField
    val appRule = ApplicationRule()
  }

  @Test
  fun `migrate open paths - no additional info`() {
    val manager = RecentProjectsManagerBase()

    val element = JDOMUtil.load("""
      <application>
    <component name="RecentProjectsManager">
      <option name="openPaths">
        <list>
          <option value="/IdeaProjects/untitled" />
        </list>
      </option>
    </component>
  </application>
    """.trimIndent())
    val state = RecentProjectManagerState()
    element.getChild("component")!!.deserializeInto(state)
    manager.loadState(state)
    assertThat(manager.state.additionalInfo.keys.joinToString("\n")).isEqualTo("/IdeaProjects/untitled")
    @Suppress("DEPRECATION")
    assertThat(manager.state.recentPaths.joinToString("\n")).isEqualTo("/IdeaProjects/untitled")
  }

  @Test
  fun `migrate open paths - 1 additional info`() {
    val manager = RecentProjectsManagerBase()

    val element = JDOMUtil.load("""
      <application>
    <component name="RecentProjectsManager">
      <option name="openPaths">
        <list>
          <option value="/IdeaProjects/untitled" />
        </list>
      </option>
          <option name="additionalInfo">
      <map>
        <entry key="/IdeaProjects/untitled">
          <value>
            <RecentProjectMetaInfo>
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
    assertThat(manager.state.additionalInfo.keys.joinToString("\n")).isEqualTo("/IdeaProjects/untitled")
    @Suppress("DEPRECATION")
    assertThat(manager.state.recentPaths.joinToString("\n")).isEqualTo("/IdeaProjects/untitled")
  }

  @Test
  fun `migrate open paths - 2 additional info`() {
    val manager = RecentProjectsManagerBase()

    val element = JDOMUtil.load("""
    <application>
      <component name="RecentProjectsManager">
        <option name="openPaths">
          <list>
            <option value="/IdeaProjects/untitled" />
          </list>
        </option>
            <option name="additionalInfo">
        <map>
          <entry key="/IdeaProjects/untitled">
            <value>
              <RecentProjectMetaInfo>
              </RecentProjectMetaInfo>
            </value>
          </entry>
          <entry key="/IdeaProjects/untitled2">
            <value>
              <RecentProjectMetaInfo>
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
      /IdeaProjects/untitled2
      /IdeaProjects/untitled
    """.trimIndent())
    @Suppress("DEPRECATION")
    assertThat(manager.state.recentPaths.joinToString("\n")).isEqualTo("""
      /IdeaProjects/untitled
      /IdeaProjects/untitled2
    """.trimIndent())
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
    assertThat(manager.state.recentPaths.joinToString("\n")).isEqualTo("""
      /home/WebstormProjects/untitled
      /home/WebstormProjects/conference-data
    """.trimIndent())
  }
}
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
    assertThat(manager.state!!.additionalInfo.keys.joinToString("\n")).isEqualTo("/IdeaProjects/untitled")
    @Suppress("DEPRECATION")
    assertThat(manager.state!!.recentPaths.joinToString("\n")).isEqualTo("/IdeaProjects/untitled")
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
    assertThat(manager.state!!.additionalInfo.keys.joinToString("\n")).isEqualTo("/IdeaProjects/untitled")
    @Suppress("DEPRECATION")
    assertThat(manager.state!!.recentPaths.joinToString("\n")).isEqualTo("/IdeaProjects/untitled")
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
    assertThat(manager.state!!.additionalInfo.keys.joinToString("\n")).isEqualTo("""
      /IdeaProjects/untitled2
      /IdeaProjects/untitled
    """.trimIndent())
    @Suppress("DEPRECATION")
    assertThat(manager.state!!.recentPaths.joinToString("\n")).isEqualTo("""
      /IdeaProjects/untitled
      /IdeaProjects/untitled2
    """.trimIndent())
  }
}
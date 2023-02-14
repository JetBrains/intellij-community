// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings

import com.intellij.configurationStore.deserialize
import com.intellij.openapi.updateSettings.impl.UpdateOptions
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import org.junit.Test

class UpdateOptionsTest : BareTestFixtureTestCase() {
  @Test fun `auto check is enabled`() {
    // it is an important default value (true), so ensure that it is not inadvertently modified
    assertThat(UpdateSettings().isCheckNeeded).isTrue
  }

  @Test fun deserialization() {
    JDOMUtil.load("""
        <component name="UpdatesConfigurable">
          <enabledExternalComponentSources>
            <item value="Android SDK" />
          </enabledExternalComponentSources>
          <option name="externalUpdateChannels">
            <map>
              <entry key="Android SDK" value="Stable Channel" />
            </map>
          </option>
          <knownExternalComponentSources>
            <item value="Android SDK" />
          </knownExternalComponentSources>
          <option name="UPDATE_CHANNEL_TYPE" value="eap" />
        </component>""".trimIndent()
    ).deserialize(UpdateOptions::class.java)
  }
}

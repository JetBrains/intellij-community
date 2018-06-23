// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings

import com.intellij.configurationStore.deserialize
import com.intellij.openapi.updateSettings.impl.UpdateOptions
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.loadElement
import org.junit.ClassRule
import org.junit.Test

class UpdateOptionsTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule()
  }

  @Test
  fun `auto check is enabled`() {
    // it is quite important default value (true), so, ensure that it is not modified without changing test expectation (as an additional check)
    assertThat(UpdateOptions().isCheckNeeded).isTrue()
  }

  @Test
  fun test() {
    loadElement("""
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
      </component>""").deserialize(UpdateOptions::class.java)
  }
}
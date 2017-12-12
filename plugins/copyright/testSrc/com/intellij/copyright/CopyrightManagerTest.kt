// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.copyright

import com.intellij.configurationStore.SchemeManagerFactoryBase
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.assertions.Assertions.catchThrowable
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.util.io.write
import com.maddyhome.idea.copyright.CopyrightProfile
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

internal class CopyrightManagerTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule()
  }

  @JvmField
  @Rule
  val fsRule = InMemoryFsRule()

  @Test
  fun serialize() {
    val scheme = CopyrightProfile()
    scheme.name = "test"
    assertThat(scheme.writeScheme()).isEqualTo("""
    <copyright>
      <option name="myName" value="test" />
    </copyright>""".trimIndent())
  }

  @Test
  fun serializeEmpy() {
    val scheme = CopyrightProfile()
    assertThat(scheme.writeScheme()).isEqualTo("""<copyright />""")
  }

  @Test
  fun loadSchemes() {
    val schemeFile = fsRule.fs.getPath("copyright/openapi.xml")
    val schemeData = """
      <component name="CopyrightManager">
        <copyright>
          <option name="notice" value="Copyright 2000-&amp;#36;today.year JetBrains s.r.o.&#10;&#10;Licensed under the Apache License, Version 2.0 (the &quot;License&quot;);&#10;you may not use this file except in compliance with the License.&#10;You may obtain a copy of the License at&#10;&#10;http://www.apache.org/licenses/LICENSE-2.0&#10;&#10;Unless required by applicable law or agreed to in writing, software&#10;distributed under the License is distributed on an &quot;AS IS&quot; BASIS,&#10;WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.&#10;See the License for the specific language governing permissions and&#10;limitations under the License." />
          <option name="keyword" value="Copyright" />
          <option name="allowReplaceKeyword" value="JetBrains" />
          <option name="myName" value="openapi" />
          <option name="myLocal" value="true" />
        </copyright>
      </component>""".trimIndent()
    schemeFile.write(schemeData)
    val schemeManagerFactory = SchemeManagerFactoryBase.TestSchemeManagerFactory(fsRule.fs.getPath(""))
    val profileManager = CopyrightManager(projectRule.project, schemeManagerFactory,
                                          isSupportIprProjects = false /* otherwise scheme will be not loaded from our memory fs */)
    profileManager.loadSchemes()

    val copyrights = profileManager.getCopyrights()
    assertThat(copyrights).hasSize(1)
    val scheme = copyrights.first()
    assertThat(scheme.schemeState).isEqualTo(null)
    assertThat(scheme.name).isEqualTo("openapi")
  }

  @Test
  fun empty() {
    val schemeFile = fsRule.fs.getPath("copyright/openapi.xml")
    val schemeData = """
      <component name="CopyrightManager">
        <copyright />
      </component>""".trimIndent()
    schemeFile.write(schemeData)
    val schemeManagerFactory = SchemeManagerFactoryBase.TestSchemeManagerFactory(fsRule.fs.getPath(""))
    val profileManager = CopyrightManager(projectRule.project, schemeManagerFactory,
                                          isSupportIprProjects = false /* otherwise scheme will be not loaded from our memory fs */)
    val catchThrowable = catchThrowable { profileManager.loadSchemes() }
    assertThat(catchThrowable)
      .isInstanceOf(AssertionError::class.java)
      .hasMessageStartingWith("Cannot read scheme openapi.xml")
      .hasCauseExactlyInstanceOf(RuntimeException::class.java)
  }
}
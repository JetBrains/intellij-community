// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util

import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.assertions.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jdom.Element
import org.jdom.IllegalDataException
import org.jdom.Text
import org.junit.Test
import java.io.File

internal class JDOMUtilTest {
  @Test
  fun testBadHost() {
    JDOMUtil.loadDocument(File(PlatformTestUtil.getPlatformTestDataPath() + File.separator + "tools" + File.separator + "badHost.xml"))
  }

  @Test
  fun testLegalize() {
    val badString = String(charArrayOf(0xffff.toChar()))
    val str = "${badString}start${badString}end$badString"
    checkIfBad(str)

    val legalized = JDOMUtil.legalizeText(str)
    Element("test").text = legalized

    assertThat(legalized).isEqualTo("0xFFFFstart0xFFFFend0xFFFF")
  }

  @Test
  fun deepMerge() {
    assertThat(JDOMUtil.deepMerge(
      JDOMUtil.load("""<project version="4">
            <component name="ProjectModuleManager">
              <modules>
                <module fileurl="f1" />
                <module fileurl="f2" />
              </modules>
            </component>
          </project>"""),
      JDOMUtil.load("""<project version="4">
        <component name="ProjectModuleManager">
          <modules>
            <module fileurl="f3" />
            <module fileurl="f4" />
          </modules>
        </component>
      </project>""")))
      .isEqualTo("""<project version="4">
  <component name="ProjectModuleManager">
    <modules>
      <module fileurl="f1" />
      <module fileurl="f2" />
      <module fileurl="f3" />
      <module fileurl="f4" />
    </modules>
  </component>
</project>""")
  }

  @Test
  fun `deepMerge override empty tag`() {
    assertThat(JDOMUtil.deepMerge(
      JDOMUtil.load("""<component name="CompilerConfiguration">
              <bytecodeTargetLevel />
            </component>"""),
      JDOMUtil.load("""<component name="ExternalCompilerConfiguration">
              <bytecodeTargetLevel>
                <module name="my-app" target="1.5" />
              </bytecodeTargetLevel>
            </component>""")))
      .isEqualTo("""
      <component name="ExternalCompilerConfiguration">
        <bytecodeTargetLevel>
          <module name="my-app" target="1.5" />
        </bytecodeTargetLevel>
      </component>""")
  }

  @Test
  fun testBillionLaughs() {
    assertThatThrownBy {
      JDOMUtil.loadDocument(File(PlatformTestUtil.getPlatformTestDataPath() + File.separator + "tools" + File.separator + "BillionLaughs.xml"))
    }.hasMessageContaining("""The entity "lol9" was referenced, but not declared.""")
  }

  private fun checkIfBad(str: String) {
    assertThatThrownBy {
      Element("test").text = str
    }.isInstanceOf(IllegalDataException::class.java)
  }

  @Test
  fun cdata() {
    val element = JDOMUtil.load("""
    <boo>
      <help><![CDATA[create controller name index-action-included[=1]]]></help>
    </boo>
    """.trimIndent().reader())
    assertThat(element.content).hasSize(1)
    val helpElementContent = (element.content.first() as Element).content
    assertThat(helpElementContent).hasSize(1)
    assertThat((helpElementContent.first() as Text).text).isEqualTo("create controller name index-action-included[=1]")
    assertThat(element).isEqualTo("""
    <boo>
      <help>create controller name index-action-included[=1]</help>
    </boo>""")
  }

  @Test
  fun space() {
    val element = JDOMUtil.load("""
    <plist>
      <string>&lt;fieldset id=&quot;1 / [[:alpha:]]+|() / (?1:_:\L$0)/g}&quot; 2:class = &quot; 3: }&quot;}&gt;&lt;legend&gt;1:TM_SELECTED_TEXT}&lt;/legend&gt; '$'}0&lt;/fieldset&gt;</string>
    </plist>
    """.trimIndent().reader())
    val content = element.getChild("string").content
    assertThat(content).hasSize(1)
    assertThat((content[0] as Text).text).isEqualTo("""<fieldset id="1 / [[:alpha:]]+|() / (?1:_:\L${'$'}0)/g}" 2:class = " 3: }"}><legend>1:TM_SELECTED_TEXT}</legend> '${'$'}'}0</fieldset>""")
  }
}

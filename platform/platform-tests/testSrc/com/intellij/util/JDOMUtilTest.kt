// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.JDOMUtil.MergeAttribute
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.assertions.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jdom.Element
import org.jdom.IllegalDataException
import org.jdom.JDOMException
import org.jdom.Text
import org.junit.Test
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.assertEquals

internal class JDOMUtilTest {
  @Test
  fun testBadHost() {
    JDOMUtil.load(Paths.get(PlatformTestUtil.getPlatformTestDataPath(), "tools/badHost.xml"))
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
  fun `lt in attribute value`() {
    assertThat(JDOMUtil.write(Element("test").setAttribute("hello", "dog < cat"))).isEqualTo("""<test hello="dog &lt; cat" />""")
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
  fun `test reduce children`() {
    val element = JDOMUtil.load("""
      |  <component name="CompilerConfiguration">
      |    <bytecodeTargetLevel target="1.7">
      |      <module name="module1" target="11" />
      |    </bytecodeTargetLevel>
      |    <bytecodeTargetLevel>
      |      <module name="module2" target="13" />
      |      <module name="module3" target="14" />
      |    </bytecodeTargetLevel>
      |  </component>""".trimMargin()
    )
    assertThat(JDOMUtil.reduceChildren("bytecodeTargetLevel", element))
      .isEqualTo("""
        |  <bytecodeTargetLevel target="1.7">
        |    <module name="module1" target="11" />
        |    <module name="module2" target="13" />
        |    <module name="module3" target="14" />
        |  </bytecodeTargetLevel>""".trimMargin()
      )
    assertThat(element)
      .isEqualTo("""
        |  <component name="CompilerConfiguration">
        |    <bytecodeTargetLevel target="1.7">
        |      <module name="module1" target="11" />
        |      <module name="module2" target="13" />
        |      <module name="module3" target="14" />
        |    </bytecodeTargetLevel>
        |  </component>""".trimMargin()
      )
  }

  @Test
  fun testBillionLaughs() {
    val loaded = JDOMUtil.load(
      Path.of(PlatformTestUtil.getPlatformTestDataPath() + File.separator + "tools" + File.separator + "BillionLaughs.xml"))
    assertThat(JDOMUtil.write(loaded)).isEqualTo("<lolz />")
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


  @Test
  fun `set&get location`() {
    val location = Point(1, 2)
    val element = Element("state")
    assertThat(JDOMUtil.setLocation(element, location)).isSameAs(element)
    assertThat(JDOMUtil.getLocation(element)).isEqualTo(location).isNotSameAs(location)
    assertElementText(element, """<state x="1" y="2" />""")
  }

  @Test
  fun `set&get location with custom name`() {
    val location = Point(1, 2)
    val element = Element("state")
    assertThat(JDOMUtil.setLocation(element, "myX", "myY", location)).isSameAs(element)
    assertThat(JDOMUtil.getLocation(element, "myX", "myY")).isEqualTo(location).isNotSameAs(location)
    assertElementText(element, """<state myX="1" myY="2" />""")
  }

  @Test
  fun `set&get location with invalid attribute`() {
    val element = JDOMUtil.setLocation(Element("state"), Point(1, 1))
    assertThat(element.setAttribute("x", "x")).isSameAs(element)
    assertThat(JDOMUtil.getBounds(element)).isNull()
    assertElementText(element, """<state x="x" y="1" />""")
  }

  @Test
  fun `set&get location without required attribute`() {
    val element = JDOMUtil.setLocation(Element("state"), Point(1, 1))
    assertThat(element.removeAttribute("y")).isTrue()
    assertThat(JDOMUtil.getBounds(element)).isNull()
    assertElementText(element, """<state x="1" />""")
  }


  @Test
  fun `set&get size`() {
    val size = Dimension(3, 4)
    val element = Element("state")
    assertThat(JDOMUtil.setSize(element, size)).isSameAs(element)
    assertThat(JDOMUtil.getSize(element)).isEqualTo(size).isNotSameAs(size)
    assertElementText(element, """<state width="3" height="4" />""")
  }

  @Test
  fun `set&get size with custom name`() {
    val size = Dimension(3, 4)
    val element = Element("state")
    assertThat(JDOMUtil.setSize(element, "myW", "myH", size)).isSameAs(element)
    assertThat(JDOMUtil.getSize(element, "myW", "myH")).isEqualTo(size).isNotSameAs(size)
    assertElementText(element, """<state myW="3" myH="4" />""")
  }

  @Test
  fun `set&get size with invalid attribute`() {
    val element = JDOMUtil.setSize(Element("state"), Dimension(1, 1))
    assertThat(element.setAttribute("height", "height")).isSameAs(element)
    assertThat(JDOMUtil.getSize(element)).isNull()
    assertElementText(element, """<state width="1" height="height" />""")
  }

  @Test
  fun `set&get size without required attribute`() {
    val element = JDOMUtil.setSize(Element("state"), Dimension(1, 1))
    assertThat(element.removeAttribute("height")).isTrue()
    assertThat(JDOMUtil.getSize(element)).isNull()
    assertElementText(element, """<state width="1" />""")
  }

  @Test
  fun `set&get empty size`() {
    val element = JDOMUtil.setSize(Element("state"), Dimension(0, 0))
    assertThat(JDOMUtil.getSize(element)).isNull()
    assertElementText(element, """<state width="0" height="0" />""")
  }


  @Test
  fun `set&get bounds`() {
    val bounds = Rectangle(1, 2, 3, 4)
    val element = Element("state")
    assertThat(JDOMUtil.setBounds(element, bounds)).isSameAs(element)
    assertThat(JDOMUtil.getBounds(element)).isEqualTo(bounds).isNotSameAs(bounds)
    assertElementText(element, """<state x="1" y="2" width="3" height="4" />""")
  }

  @Test
  fun `set&get bounds with custom name`() {
    val bounds = Rectangle(1, 2, 3, 4)
    val element = Element("state")
    assertThat(JDOMUtil.setBounds(element, "myX", "myY", "myW", "myH", bounds)).isSameAs(element)
    assertThat(JDOMUtil.getBounds(element, "myX", "myY", "myW", "myH")).isEqualTo(bounds).isNotSameAs(bounds)
    assertElementText(element, """<state myX="1" myY="2" myW="3" myH="4" />""")
  }

  @Test
  fun `set&get bounds with invalid attribute`() {
    val element = JDOMUtil.setBounds(Element("state"), Rectangle(1, 1, 1, 1))
    assertThat(element.setAttribute("height", "height")).isSameAs(element)
    assertThat(JDOMUtil.getBounds(element)).isNull()
    assertElementText(element, """<state x="1" y="1" width="1" height="height" />""")
  }

  @Test
  fun `set&get bounds without required attribute`() {
    val element = JDOMUtil.setBounds(Element("state"), Rectangle(1, 1, 1, 1))
    assertThat(element.removeAttribute("height")).isTrue()
    assertThat(JDOMUtil.getBounds(element)).isNull()
    assertElementText(element, """<state x="1" y="1" width="1" />""")
  }

  @Test
  fun `set&get empty bounds`() {
    val element = JDOMUtil.setBounds(Element("state"), Rectangle(1, 1, 0, 0))
    assertThat(JDOMUtil.getBounds(element)).isNull()
    assertElementText(element, """<state x="1" y="1" width="0" height="0" />""")
  }

  @Test
  fun `equals and hashCode`() {
    fun generateElement(data: Int): Element {
      val s = Integer.toBinaryString(data).padStart(7, '0')
      val element = Element("e${s.substring(0, 1)}")
      val attributeName = s.substring(1, 3)
      val attributeValue = s.substring(3, 5)
      if (attributeName != "00" || attributeValue != "00") {
        element.setAttribute("a$attributeName", if (attributeValue == "00") "" else attributeValue)
      }
      val subTag = s.substring(5, 7)
      if (subTag == "01") {
        element.addContent(Element("subTag"))
      }
      else if (subTag != "00") {
        element.addContent(Text(subTag))
      }
      return element
    }
    for (data1 in 0 .. 31) {
      val element1 = generateElement(data1)
      for (data2 in 0..31) {
        val element2 = generateElement(data2)
        if (data1 == data2) {
          assertThat(element1).isEqualTo(element2)
          assertThat(JDOMUtil.hashCode(element1, false)).isEqualTo(JDOMUtil.hashCode(element2, false))
        }
        else {
          assertThat(JDOMUtil.areElementsEqual(element1, element2)).isFalse()
        }
      }
    }
  }

  @Test
  fun mergeWithAttributes() {
    val res = JDOMUtil.load(("<hello><data attr='1'><HiThere></HiThere></data></hello>").toByteArray())
    val res2 = JDOMUtil.load(("<hello><data attr='1' additional='2'><HiThere></HiThere></data></hello>").toByteArray())
    JDOMUtil.deepMergeWithAttributes(res, res2, listOf(MergeAttribute("data", "attr")))
    assertEquals("""|<hello>
                    |  <data attr="1" additional="2">
                    |    <HiThere />
                    |  </data>
                    |</hello>""".trimMargin(), JDOMUtil.write(res))
  }

  @Test(expected = JDOMException::class)
  fun `handling of UncheckedStreamException for unsupported symbol`() {
    val testRoot = File(PathManagerEx.getCommunityHomePath(), "platform/platform-tests/testData/vfs/encoding/DegreeSignWin1251.xml")
    JDOMUtil.load(testRoot)
  }

  private fun assertElementText(actual: Element, expected: String) {
    assertThat(JDOMUtil.createOutputter("").outputString(actual)).isEqualTo(expected)
  }
}

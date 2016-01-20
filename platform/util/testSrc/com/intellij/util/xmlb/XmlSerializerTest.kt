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
package com.intellij.util.xmlb

import com.intellij.openapi.util.JDOMExternalizableStringList
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.SmartList
import com.intellij.util.xmlb.annotations.*
import com.intellij.util.xmlb.annotations.AbstractCollection
import junit.framework.AssertionFailedError
import junit.framework.TestCase
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.jdom.Element
import org.junit.Test
import java.util.*
import java.util.concurrent.atomic.AtomicReference

internal open class BeanWithPublicFields(var INT_V: Int = 1, var STRING_V: String? = "hello") : Comparable<BeanWithPublicFields> {
  override fun compareTo(other: BeanWithPublicFields) = StringUtil.compare(STRING_V, other.STRING_V, false)
}

internal class BeanWithTextAnnotation {
  var INT_V: Int = 1
  @Text var STRING_V: String = "hello"

  constructor(INT_V: Int, STRING_V: String) {
    this.INT_V = INT_V
    this.STRING_V = STRING_V
  }

  constructor() {
  }
}

internal class BeanWithProperty {
  var name: String = "James"

  constructor() {
  }

  constructor(name: String) {
    this.name = name
  }
}

internal class XmlSerializerTest {
  @Test fun EmptyBeanSerialization() {
    @Tag("bean")
    class EmptyBean

    doSerializerTest("<bean />", EmptyBean())
  }

  @Tag("Bean")
  class EmptyBeanWithCustomName

  @Test fun EmptyBeanSerializationWithCustomName() {
    doSerializerTest("<Bean />", EmptyBeanWithCustomName())
  }

  @Test fun PublicFieldSerialization() {
    val bean = BeanWithPublicFields()

    doSerializerTest("<BeanWithPublicFields>\n  <option name=\"INT_V\" value=\"1\" />\n  <option name=\"STRING_V\" value=\"hello\" />\n</BeanWithPublicFields>", bean)

    bean.INT_V = 2
    bean.STRING_V = "bye"

    doSerializerTest("<BeanWithPublicFields>\n  <option name=\"INT_V\" value=\"2\" />\n  <option name=\"STRING_V\" value=\"bye\" />\n</BeanWithPublicFields>", bean)
  }


  private class BeanWithPublicFieldsDescendant(var NEW_S: String? = "foo") : BeanWithPublicFields()

  @Test fun publicFieldSerializationWithInheritance() {
    val bean = BeanWithPublicFieldsDescendant()

    doSerializerTest("""<BeanWithPublicFieldsDescendant>
  <option name="INT_V" value="1" />
  <option name="NEW_S" value="foo" />
  <option name="STRING_V" value="hello" />
</BeanWithPublicFieldsDescendant>""", bean)

    bean.INT_V = 2
    bean.STRING_V = "bye"
    bean.NEW_S = "bar"

    doSerializerTest("""<BeanWithPublicFieldsDescendant>
  <option name="INT_V" value="2" />
  <option name="NEW_S" value="bar" />
  <option name="STRING_V" value="bye" />
</BeanWithPublicFieldsDescendant>""", bean)
  }

  private class BeanWithSubBean {
    var BEAN1: EmptyBeanWithCustomName? = EmptyBeanWithCustomName()
    var BEAN2: BeanWithPublicFields? = BeanWithPublicFields()
  }

  @Test fun SubBeanSerialization() {
    val bean = BeanWithSubBean()
    doSerializerTest("<BeanWithSubBean>\n" + "  <option name=\"BEAN1\">\n" + "    <Bean />\n" + "  </option>\n" + "  <option name=\"BEAN2\">\n" + "    <BeanWithPublicFields>\n" + "      <option name=\"INT_V\" value=\"1\" />\n" + "      <option name=\"STRING_V\" value=\"hello\" />\n" + "    </BeanWithPublicFields>\n" + "  </option>\n" + "</BeanWithSubBean>", bean)
    bean.BEAN2!!.INT_V = 2
    bean.BEAN2!!.STRING_V = "bye"

    doSerializerTest("<BeanWithSubBean>\n" + "  <option name=\"BEAN1\">\n" + "    <Bean />\n" + "  </option>\n" + "  <option name=\"BEAN2\">\n" + "    <BeanWithPublicFields>\n" + "      <option name=\"INT_V\" value=\"2\" />\n" + "      <option name=\"STRING_V\" value=\"bye\" />\n" + "    </BeanWithPublicFields>\n" + "  </option>\n" + "</BeanWithSubBean>", bean)
  }

  @Test fun SubBeanSerializationAndSkipDefaults() {
    val bean = BeanWithSubBean()
    doSerializerTest("<BeanWithSubBean />", bean, SkipDefaultsSerializationFilter())
  }

  class BeanWithEquals {
    var STRING_V: String = "hello"

    override fun equals(other: Any?): Boolean {
      // any instance of this class is equal
      return this === other || (other != null && javaClass == other.javaClass)
    }
  }

  class BeanWithSubBeanWithEquals {
    var BEAN1: EmptyBeanWithCustomName = EmptyBeanWithCustomName()
    var BEAN2: BeanWithEquals = BeanWithEquals()
  }

  @Test fun SubBeanWithEqualsSerializationAndSkipDefaults() {
    val bean = BeanWithSubBeanWithEquals()
    val filter = SkipDefaultsSerializationFilter()
    doSerializerTest("<BeanWithSubBeanWithEquals />", bean, filter)

    bean.BEAN2.STRING_V = "new"
    doSerializerTest("<BeanWithSubBeanWithEquals />", bean, filter)
  }

  @Test fun NullFieldValue() {
    val bean1 = BeanWithPublicFields()

    doSerializerTest("<BeanWithPublicFields>\n" + "  <option name=\"INT_V\" value=\"1\" />\n" + "  <option name=\"STRING_V\" value=\"hello\" />\n" + "</BeanWithPublicFields>", bean1)

    bean1.STRING_V = null

    doSerializerTest("<BeanWithPublicFields>\n" + "  <option name=\"INT_V\" value=\"1\" />\n" + "  <option name=\"STRING_V\" />\n" + "</BeanWithPublicFields>", bean1)

    val bean2 = BeanWithSubBean()
    bean2.BEAN1 = null
    bean2.BEAN2 = null

    doSerializerTest("<BeanWithSubBean>\n" + "  <option name=\"BEAN1\" />\n" + "  <option name=\"BEAN2\" />\n" + "</BeanWithSubBean>", bean2)

  }

  private class BeanWithList {
    var VALUES: List<String> = ArrayList(Arrays.asList("a", "b", "c"))
  }

  @Test fun ListSerialization() {
    val bean = BeanWithList()

    doSerializerTest("<BeanWithList>\n" + "  <option name=\"VALUES\">\n" + "    <list>\n" + "      <option value=\"a\" />\n" + "      <option value=\"b\" />\n" + "      <option value=\"c\" />\n" + "    </list>\n" + "  </option>\n" + "</BeanWithList>", bean)

    bean.VALUES = ArrayList(Arrays.asList("1", "2", "3"))

    doSerializerTest("<BeanWithList>\n" + "  <option name=\"VALUES\">\n" + "    <list>\n" + "      <option value=\"1\" />\n" + "      <option value=\"2\" />\n" + "      <option value=\"3\" />\n" + "    </list>\n" + "  </option>\n" + "</BeanWithList>", bean)
  }

  internal class BeanWithSet {
    var VALUES: Set<String> = LinkedHashSet(Arrays.asList("a", "b", "w"))
  }

  @Test fun SetSerialization() {
    val bean = BeanWithSet()
    doSerializerTest("<BeanWithSet>\n" + "  <option name=\"VALUES\">\n" + "    <set>\n" + "      <option value=\"a\" />\n" + "      <option value=\"b\" />\n" + "      <option value=\"w\" />\n" + "    </set>\n" + "  </option>\n" + "</BeanWithSet>", bean)
    bean.VALUES = LinkedHashSet(Arrays.asList("1", "2", "3"))

    doSerializerTest("<BeanWithSet>\n" + "  <option name=\"VALUES\">\n" + "    <set>\n" + "      <option value=\"1\" />\n" + "      <option value=\"2\" />\n" + "      <option value=\"3\" />\n" + "    </set>\n" + "  </option>\n" + "</BeanWithSet>", bean)
  }

  private data class BeanWithOption(@OptionTag("path") var PATH: String? = null)

  @Test fun OptionTag() {
    val bean = BeanWithOption()
    bean.PATH = "123"
    doSerializerTest("<BeanWithOption>\n" + "  <option name=\"path\" value=\"123\" />\n" + "</BeanWithOption>", bean)
  }

  private data class BeanWithCustomizedOption(@OptionTag(tag = "setting", nameAttribute = "key", valueAttribute = "saved") var PATH: String? = null)

  @Test fun CustomizedOptionTag() {
    val bean = BeanWithCustomizedOption()
    bean.PATH = "123"
    doSerializerTest("<BeanWithCustomizedOption>\n" + "  <setting key=\"PATH\" saved=\"123\" />\n" + "</BeanWithCustomizedOption>", bean)
  }

  @Test fun PropertySerialization() {
    val bean = BeanWithProperty()

    doSerializerTest("<BeanWithProperty>\n" + "  <option name=\"name\" value=\"James\" />\n" + "</BeanWithProperty>", bean)

    bean.name = "Bond"

    doSerializerTest("<BeanWithProperty>\n" + "  <option name=\"name\" value=\"Bond\" />\n" + "</BeanWithProperty>", bean)
  }

  private class BeanWithFieldWithTagAnnotation {
    @Tag("name") var STRING_V: String = "hello"
  }

  @Test fun ParallelDeserialization() {
    val e = Element("root").addContent(Element("name").setText("x"))
    XmlSerializer.deserialize<BeanWithArray>(e, BeanWithArray::class.java)//to initialize XmlSerializerImpl.ourBindings
    val exc = AtomicReference<AssertionFailedError>()
    val threads = Array(5) {
      Thread(Runnable {
        try {
          for (j in 0..9) {
            val bean = e.deserialize<BeanWithFieldWithTagAnnotation>()
            assertThat(bean).isNotNull()
            assertThat(bean.STRING_V).isEqualTo("x")
          }
        }
        catch (e: AssertionFailedError) {
          exc.set(e)
        }
      }, "XmlSerializerTest#testParallelDeserialization-$it")
    }
    for (thread in threads) {
      thread.start()
    }
    for (thread in threads) {
      thread.join()
    }
    exc.get()?.let { throw it }
  }

  @Test fun FieldWithTagAnnotation() {
    val bean = BeanWithFieldWithTagAnnotation()
    doSerializerTest("<BeanWithFieldWithTagAnnotation>\n" + "  <name>hello</name>\n" + "</BeanWithFieldWithTagAnnotation>", bean)
    bean.STRING_V = "bye"
    doSerializerTest("<BeanWithFieldWithTagAnnotation>\n" + "  <name>bye</name>\n" + "</BeanWithFieldWithTagAnnotation>", bean)
  }

  @Test fun EscapeCharsInTagText() {
    val bean = BeanWithFieldWithTagAnnotation()
    bean.STRING_V = "a\nb\"<"

    doSerializerTest("<BeanWithFieldWithTagAnnotation>\n" + "  <name>a\nb&quot;&lt;</name>\n" + "</BeanWithFieldWithTagAnnotation>", bean)
  }

  @Test fun EscapeCharsInAttributeValue() {
    val bean = BeanWithPropertiesBoundToAttribute()
    bean.name = "a\nb\"<"
    doSerializerTest("<BeanWithPropertiesBoundToAttribute count=\"3\" name=\"a&#10;b&quot;&lt;\" />", bean)
  }

  @Test fun ShuffledDeserialize() {
    var bean: BeanWithPublicFields? = BeanWithPublicFields()
    bean!!.INT_V = 987
    bean.STRING_V = "1234"

    val element = bean.serialize(null)

    val node = element.children.get(0)
    element.removeContent(node)
    element.addContent(node)

    bean = XmlSerializer.deserialize<BeanWithPublicFields>(element, bean.javaClass)
    assert(bean != null)
    TestCase.assertEquals(987, bean!!.INT_V)
    TestCase.assertEquals("1234", bean.STRING_V)
  }

  @Test fun FilterSerializer() {
    val bean = BeanWithPublicFields()
    assertSerializer(bean, "<BeanWithPublicFields>\n" + "  <option name=\"INT_V\" value=\"1\" />\n" + "</BeanWithPublicFields>", object : SerializationFilter {
      override fun accepts(accessor: Accessor, bean: Any) = accessor.name.startsWith("I")
    })
  }

  data class BeanWithArray(var ARRAY_V: Array<String> = arrayOf("a", "b"))

  @Test fun Array() {
    val bean = BeanWithArray()
    doSerializerTest("<BeanWithArray>\n" + "  <option name=\"ARRAY_V\">\n" + "    <array>\n" + "      <option value=\"a\" />\n" + "      <option value=\"b\" />\n" + "    </array>\n" + "  </option>\n" + "</BeanWithArray>", bean)

    bean.ARRAY_V = arrayOf("1", "2", "3", "")
    doSerializerTest("<BeanWithArray>\n" + "  <option name=\"ARRAY_V\">\n" + "    <array>\n" + "      <option value=\"1\" />\n" + "      <option value=\"2\" />\n" + "      <option value=\"3\" />\n" + "      <option value=\"\" />\n" + "    </array>\n" + "  </option>\n" + "</BeanWithArray>", bean)
  }

  @Test fun Transient() {
    @Tag("bean")
    class Bean {
      var INT_V: Int = 1
        @Transient
        get

      @Transient fun getValue(): String = "foo"
    }

    doSerializerTest("<bean />", Bean())
  }

  private class BeanWithArrayWithoutTagName {
    @AbstractCollection(surroundWithTag = false) var V: Array<String> = arrayOf("a")
  }

  @Test fun ArrayAnnotationWithoutTagNAmeGivesError() {
    val bean = BeanWithArrayWithoutTagName()

    try {
      doSerializerTest("<BeanWithArrayWithoutTagName><option name=\"V\"><option value=\"a\"/></option></BeanWithArrayWithoutTagName>", bean)
    }
    catch (e: XmlSerializationException) {
      return
    }


    TestCase.fail("No Exception")
  }

  @Test fun arrayAnnotationWithElementTag() {
    @Tag("bean") class Bean {
      @AbstractCollection(elementTag = "vvalue", elementValueAttribute = "v")
      var v = arrayOf("a", "b")
    }

    val bean = Bean()

    doSerializerTest("""<bean>
  <option name="v">
    <array>
      <vvalue v="a" />
      <vvalue v="b" />
    </array>
  </option>
</bean>""", bean)

    bean.v = arrayOf("1", "2", "3")

    doSerializerTest("<bean>\n" + "  <option name=\"v\">\n" + "    <array>\n" + "      <vvalue v=\"1\" />\n" + "      <vvalue v=\"2\" />\n" + "      <vvalue v=\"3\" />\n" + "    </array>\n" + "  </option>\n" + "</bean>", bean)
  }

  @Test fun arrayWithoutTag() {
    @Tag("bean")
    class Bean {
      @AbstractCollection(elementTag = "vvalue", elementValueAttribute = "v", surroundWithTag = false)
      var v = arrayOf("a", "b")
      var INT_V = 1
    }

    val bean = Bean()

    doSerializerTest("""<bean>
  <option name="INT_V" value="1" />
  <option name="v">
    <vvalue v="a" />
    <vvalue v="b" />
  </option>
</bean>""", bean)

    bean.v = arrayOf("1", "2", "3")

    doSerializerTest("""<bean>
  <option name="INT_V" value="1" />
  <option name="v">
    <vvalue v="1" />
    <vvalue v="2" />
    <vvalue v="3" />
  </option>
</bean>""", bean)
  }

  @Test fun PropertyWithoutTagWithPrimitiveType() {
    @Tag("bean")
    class BeanWithPropertyWithoutTagOnPrimitiveValue {
      @Property(surroundWithTag = false)
      var INT_V = 1
    }

    val bean = BeanWithPropertyWithoutTagOnPrimitiveValue()
    try {
      doSerializerTest("<bean><name>hello</name></bean>", bean)
    }
    catch (e: XmlSerializationException) {
      return
    }

    TestCase.fail("No Exception")
  }

  @Test fun PropertyWithoutTag() {
    @Tag("bean")
    class BeanWithPropertyWithoutTag {
      @Property(surroundWithTag = false)
      var BEAN1 = BeanWithPublicFields()
      var INT_V = 1
    }

    val bean = BeanWithPropertyWithoutTag()

    doSerializerTest("""<bean>
  <option name="INT_V" value="1" />
  <BeanWithPublicFields>
    <option name="INT_V" value="1" />
    <option name="STRING_V" value="hello" />
  </BeanWithPublicFields>
</bean>""", bean)

    bean.INT_V = 2
    bean.BEAN1.STRING_V = "junk"

    doSerializerTest("""<bean>
  <option name="INT_V" value="2" />
  <BeanWithPublicFields>
    <option name="INT_V" value="1" />
    <option name="STRING_V" value="junk" />
  </BeanWithPublicFields>
</bean>""", bean)
  }

  @Tag("bean")
  class BeanWithArrayWithoutAllsTag {
    @Property(surroundWithTag = false)
    @AbstractCollection(elementTag = "vvalue", elementValueAttribute = "v", surroundWithTag = false)
    var v = arrayOf("a", "b")

    var intV = 1
  }

  @Test fun arrayWithoutAllTags() {
    val bean = BeanWithArrayWithoutAllsTag()

    doSerializerTest("""<bean>
  <option name="intV" value="1" />
  <vvalue v="a" />
  <vvalue v="b" />
</bean>""", bean)

    bean.intV = 2
    bean.v = arrayOf("1", "2", "3")

    doSerializerTest("""<bean>
  <option name="intV" value="2" />
  <vvalue v="1" />
  <vvalue v="2" />
  <vvalue v="3" />
</bean>""", bean)
  }

  @Test fun arrayWithoutAllTags2() {
    @Tag("bean")
    class BeanWithArrayWithoutAllTag2 {
      @Property(surroundWithTag = false)
      @AbstractCollection(elementTag = "vvalue", elementValueAttribute = "", surroundWithTag = false)
      var v = arrayOf("a", "b")
      var intV = 1
    }

    val bean = BeanWithArrayWithoutAllTag2()

    doSerializerTest("""<bean>
  <option name="intV" value="1" />
  <vvalue>a</vvalue>
  <vvalue>b</vvalue>
</bean>""", bean)

    bean.intV = 2
    bean.v = arrayOf("1", "2", "3")

    doSerializerTest("""<bean>
  <option name="intV" value="2" />
  <vvalue>1</vvalue>
  <vvalue>2</vvalue>
  <vvalue>3</vvalue>
</bean>""", bean)
  }

  @Test fun deserializeFromFormattedXML() {
    val bean = JDOMUtil.loadDocument("<bean>\n" + "  <option name=\"intV\" value=\"2\"/>\n" + "  <vvalue v=\"1\"/>\n" + "  <vvalue v=\"2\"/>\n" + "  <vvalue v=\"3\"/>\n" + "</bean>").rootElement.deserialize<BeanWithArrayWithoutAllsTag>()
    assertThat(bean.intV).isEqualTo(2)
    assertThat("[1, 2, 3]").isEqualTo(Arrays.asList(*bean.v).toString())
  }

  @Test fun polymorphicArray() {
    @Tag("bean")
    class BeanWithPolymorphicArray {
      @AbstractCollection(elementTypes = arrayOf(BeanWithPublicFields::class, BeanWithPublicFieldsDescendant::class))
      var v = arrayOf<BeanWithPublicFields>()
    }

    val bean = BeanWithPolymorphicArray()

    doSerializerTest("<bean>\n  <option name=\"v\">\n    <array />\n  </option>\n</bean>", bean)

    bean.v = arrayOf(BeanWithPublicFields(), BeanWithPublicFieldsDescendant(), BeanWithPublicFields())

    doSerializerTest("""<bean>
  <option name="v">
    <array>
      <BeanWithPublicFields>
        <option name="INT_V" value="1" />
        <option name="STRING_V" value="hello" />
      </BeanWithPublicFields>
      <BeanWithPublicFieldsDescendant>
        <option name="INT_V" value="1" />
        <option name="NEW_S" value="foo" />
        <option name="STRING_V" value="hello" />
      </BeanWithPublicFieldsDescendant>
      <BeanWithPublicFields>
        <option name="INT_V" value="1" />
        <option name="STRING_V" value="hello" />
      </BeanWithPublicFields>
    </array>
  </option>
</bean>""", bean)
  }

  private class BeanWithPropertiesBoundToAttribute {
    @Attribute("count")
    var COUNT = 3
    @Attribute("name")
    var name = "James"
    @Attribute("occupation")
    var occupation: String? = null
  }

  @Test fun BeanWithPrimitivePropertyBoundToAttribute() {
    val bean = BeanWithPropertiesBoundToAttribute()

    doSerializerTest("<BeanWithPropertiesBoundToAttribute count=\"3\" name=\"James\" />", bean)

    bean.COUNT = 10
    bean.name = "Bond"

    doSerializerTest("<BeanWithPropertiesBoundToAttribute count=\"10\" name=\"Bond\" />", bean)
  }


  private class BeanWithPropertyFilter {
    @Property(filter = PropertyFilterTest::class) var STRING_V: String = "hello"
  }

  private class PropertyFilterTest : SerializationFilter {
    override fun accepts(accessor: Accessor, bean: Any): Boolean {
      return accessor.read(bean) != "skip"
    }
  }

  @Test fun PropertyFilter() {
    val bean = BeanWithPropertyFilter()

    doSerializerTest("<BeanWithPropertyFilter>\n" + "  <option name=\"STRING_V\" value=\"hello\" />\n" + "</BeanWithPropertyFilter>", bean)

    bean.STRING_V = "bye"

    doSerializerTest("<BeanWithPropertyFilter>\n" + "  <option name=\"STRING_V\" value=\"bye\" />\n" + "</BeanWithPropertyFilter>", bean)

    bean.STRING_V = "skip"

    assertSerializer(bean, "<BeanWithPropertyFilter />", null)
  }

  private class BeanWithJDOMElement {
    var STRING_V: String = "hello"
    @Tag("actions") var actions: org.jdom.Element? = null
  }

  @Test fun SerializeJDOMElementField() {
    val element = BeanWithJDOMElement()
    element.STRING_V = "a"
    element.actions = Element("x").addContent(Element("a")).addContent(Element("b"))
    assertSerializer(element, "<BeanWithJDOMElement>\n" + "  <option name=\"STRING_V\" value=\"a\" />\n" + "  <actions>\n" + "    <a />\n" + "    <b />\n" + "  </actions>\n" + "</BeanWithJDOMElement>", null)

    element.actions = null
    assertSerializer(element, "<BeanWithJDOMElement>\n" + "  <option name=\"STRING_V\" value=\"a\" />\n" + "</BeanWithJDOMElement>", null)
  }

  @Test fun DeserializeJDOMElementField() {
    val bean = XmlSerializer.deserialize<BeanWithJDOMElement>(JDOMUtil.loadDocument("<BeanWithJDOMElement><option name=\"STRING_V\" value=\"bye\"/><actions><action/><action/></actions></BeanWithJDOMElement>").rootElement, BeanWithJDOMElement::class.java)!!

    TestCase.assertEquals("bye", bean.STRING_V)
    TestCase.assertNotNull(bean.actions)
    TestCase.assertEquals(2, bean.actions!!.getChildren("action").size)
  }

  class BeanWithJDOMElementArray {
    var STRING_V: String = "hello"
    @Tag("actions") var actions: Array<org.jdom.Element>? = null
  }

  @Test fun JDOMElementArrayField() {
    val text = "<BeanWithJDOMElementArray>\n" + "  <option name=\"STRING_V\" value=\"bye\" />\n" + "  <actions>\n" + "    <action />\n" + "    <action />\n" + "  </actions>\n" + "  <actions>\n" + "    <action />\n" + "  </actions>\n" + "</BeanWithJDOMElementArray>"
    val bean = XmlSerializer.deserialize<BeanWithJDOMElementArray>(JDOMUtil.loadDocument(text).rootElement, BeanWithJDOMElementArray::class.java)!!


    TestCase.assertEquals("bye", bean.STRING_V)
    TestCase.assertNotNull(bean.actions)
    TestCase.assertEquals(2, bean.actions!!.size)
    TestCase.assertEquals(2, bean.actions!![0].children.size)
    TestCase.assertEquals(1, bean.actions!![1].children.size)

    assertSerializer(bean, text, null)

    bean.actions = null
    val newText = "<BeanWithJDOMElementArray>\n" + "  <option name=\"STRING_V\" value=\"bye\" />\n" + "</BeanWithJDOMElementArray>"
    doSerializerTest(newText, bean)

    bean.actions = emptyArray()
    doSerializerTest(newText, bean)
  }

  @Test fun TextAnnotation() {
    val bean = BeanWithTextAnnotation()

    doSerializerTest("<BeanWithTextAnnotation>\n" + "  <option name=\"INT_V\" value=\"1\" />\n" + "  hello\n" + "</BeanWithTextAnnotation>", bean)

    bean.INT_V = 2
    bean.STRING_V = "bye"

    doSerializerTest("<BeanWithTextAnnotation>\n" + "  <option name=\"INT_V\" value=\"2\" />\n" + "  bye\n" + "</BeanWithTextAnnotation>", bean)
  }

  private class BeanWithEnum {
    enum class TestEnum {
      VALUE_1,
      VALUE_2,
      VALUE_3
    }

    var FLD = TestEnum.VALUE_1
  }

  @Test fun Enums() {
    val bean = BeanWithEnum()

    doSerializerTest("<BeanWithEnum>\n" + "  <option name=\"FLD\" value=\"VALUE_1\" />\n" + "</BeanWithEnum>", bean)

    bean.FLD = BeanWithEnum.TestEnum.VALUE_3

    doSerializerTest("<BeanWithEnum>\n" + "  <option name=\"FLD\" value=\"VALUE_3\" />\n" + "</BeanWithEnum>", bean)
  }

  @Test fun setKeysInMap() {
    @Tag("bean")
    class BeanWithSetKeysInMap {
      var myMap = LinkedHashMap<Collection<String>, String>()
    }

    val bean = BeanWithSetKeysInMap()
    bean.myMap.put(LinkedHashSet(Arrays.asList("a", "b", "c")), "letters")
    bean.myMap.put(LinkedHashSet(Arrays.asList("1", "2", "3")), "numbers")

    val bb = doSerializerTest("<bean>\n  <option name=\"myMap\">\n    <map>\n      <entry value=\"letters\">\n        <key>\n          <set>\n            <option value=\"a\" />\n            <option value=\"b\" />\n            <option value=\"c\" />\n          </set>\n        </key>\n      </entry>\n      <entry value=\"numbers\">\n        <key>\n          <set>\n            <option value=\"1\" />\n            <option value=\"2\" />\n            <option value=\"3\" />\n          </set>\n        </key>\n      </entry>\n    </map>\n  </option>\n</bean>", bean)

    for (collection in bb.myMap.keys) {
      assertThat(collection).isInstanceOf(Set::class.java)
    }
  }

  @Test fun conversionFromTextToAttribute() {
    @Tag("condition")
    class ConditionBean {
      @Attribute("expression")
      var newCondition: String? = null
      @Text
      var oldCondition: String? = null
    }

    @Tag("bean")
    class Bean {
      @Property(surroundWithTag = false)
      var conditionBean = ConditionBean()
    }

    var bean = Bean()
    bean.conditionBean.oldCondition = "2+2"
    doSerializerTest("<bean>\n  <condition>2+2</condition>\n</bean>", bean)

    bean = Bean()
    bean.conditionBean.newCondition = "2+2"
    doSerializerTest("<bean>\n  <condition expression=\"2+2\" />\n" + "</bean>", bean)
  }

  @Test fun deserializeInto() {
    val bean = BeanWithPublicFields()
    bean.STRING_V = "zzz"

    val xml = "<BeanWithPublicFields><option name=\"INT_V\" value=\"999\"/></BeanWithPublicFields>"
    XmlSerializer.deserializeInto(bean, JDOMUtil.loadDocument(xml).rootElement)

    TestCase.assertEquals(999, bean.INT_V)
    TestCase.assertEquals("zzz", bean.STRING_V)
  }

  private class BeanWithConverter {
    private class MyConverter : Converter<Ref<String>>() {
      override fun fromString(value: String): Ref<String>? {
        return Ref.create(value)
      }

      override fun toString(o: Ref<String>): String {
        return StringUtil.notNullize(o.get())
      }
    }

    @Attribute(converter = MyConverter::class)
    var foo: Ref<String>? = null

    @OptionTag(converter = MyConverter::class)
    var bar: Ref<String>? = null
  }

  @Test fun Converter() {
    val bean = BeanWithConverter()
    doSerializerTest("<BeanWithConverter>\n" + "  <option name=\"bar\" />\n" + "</BeanWithConverter>", bean)

    bean.foo = Ref.create("testValue")
    doSerializerTest("<BeanWithConverter foo=\"testValue\">\n" + "  <option name=\"bar\" />\n" + "</BeanWithConverter>", bean)

    bean.foo = Ref.create<String>()
    bean.bar = Ref.create("testValue2")
    doSerializerTest("<BeanWithConverter foo=\"\">\n" + "  <option name=\"bar\" value=\"testValue2\" />\n" + "</BeanWithConverter>", bean)
  }

  @Test fun ConverterUsingSkipDefaultsFilter() {
    val bean = BeanWithConverter()
    doSerializerTest("<BeanWithConverter />", bean, SkipDefaultsSerializationFilter())

    bean.foo = Ref.create("testValue")
    doSerializerTest("<BeanWithConverter foo=\"testValue\" />", bean, SkipDefaultsSerializationFilter())

    bean.foo = Ref.create<String>()
    bean.bar = Ref.create("testValue2")
    doSerializerTest("<BeanWithConverter foo=\"\">\n" + "  <option name=\"bar\" value=\"testValue2\" />\n" + "</BeanWithConverter>", bean)
  }

  private class BeanWithDefaultAttributeName {
    @Attribute fun getFoo(): String {
      return "foo"
    }

    fun setFoo(@Suppress("UNUSED_PARAMETER") value: String) {
    }
  }

  @Test fun DefaultAttributeName() {
    val bean = BeanWithDefaultAttributeName()
    doSerializerTest("<BeanWithDefaultAttributeName foo=\"foo\" />", bean)
  }

  private class Bean2 {
    @Attribute
    var ab: String? = null

    @Attribute
    var module: String? = null

    @Attribute
    var ac: String? = null
  }

  @Test fun Ordered() {
    val bean = Bean2()
    bean.module = "module"
    bean.ab = "ab"
    doSerializerTest("<Bean2 ab=\"ab\" module=\"module\" />", bean, SkipDefaultsSerializationFilter())

    checkSmartSerialization(Bean2(), "<Bean2 module=\"1\" ab=\"2\" ac=\"32\" />")
    checkSmartSerialization(Bean2(), "<Bean2 ab=\"2\" module=\"1\" ac=\"32\" />")
    checkSmartSerialization(Bean2(), "<Bean2 ac=\"2\" module=\"1\" ab=\"32\" />")
    checkSmartSerialization(Bean2(), "<Bean2 ac=\"2\" ab=\"32\" />")
    checkSmartSerialization(Bean2(), "<Bean2 ac=\"2\" ab=\"32\" module=\"\" />")
  }

  @Tag("b")
  private class Bean3 {
    @Suppress("DEPRECATED_SYMBOL_WITH_MESSAGE") var list: JDOMExternalizableStringList = JDOMExternalizableStringList()
  }

  @Tag("b")
  private class Bean4 {
    @CollectionBean
    val list = SmartList<String>()
  }

  @Test fun testJDOMExternalizableStringList() {
    val bean = Bean3()
    bean.list.add("one")
    bean.list.add("two")
    bean.list.add("three")
    doSerializerTest("<b>\n" + "  <list>\n" + "    <item value=\"one\" />\n" + "    <item value=\"two\" />\n" + "    <item value=\"three\" />\n" + "  </list>\n" + "</b>", bean, SkipDefaultsSerializationFilter())
  }

  @Test fun CollectionBean() {
    val bean = Bean4()
    bean.list.add("one")
    bean.list.add("two")
    bean.list.add("three")
    doSerializerTest("<b>\n" + "  <list>\n" + "    <item value=\"one\" />\n" + "    <item value=\"two\" />\n" + "    <item value=\"three\" />\n" + "  </list>\n" + "</b>", bean, SkipDefaultsSerializationFilter())
  }

  @Test fun CollectionBeanReadJDOMExternalizableStringList() {
    @Suppress("DEPRECATED_SYMBOL_WITH_MESSAGE")
    val list = JDOMExternalizableStringList()
    list.add("one")
    list.add("two")
    list.add("three")

    val value = Element("value")
    list.writeExternal(value)
    val o = XmlSerializer.deserialize<Bean4>(Element("state").addContent(Element("option").setAttribute("name", "myList").addContent(value)), Bean4::class.java)!!
    assertSerializer(o, "<b>\n" + "  <list>\n" + "    <item value=\"one\" />\n" + "    <item value=\"two\" />\n" + "    <item value=\"three\" />\n" + "  </list>\n" + "</b>", SkipDefaultsSerializationFilter())
  }

  @Test fun cdataAfterNewLine() {
    @Tag("bean")
    data class Bean(@Tag var description: String? = null)

    var bean = XmlSerializer.deserialize(JDOMUtil.load("""<bean>
  <description>
    <![CDATA[
    <h4>Node.js integration</h4>
    ]]>
  </description>
</bean>""".reader()), Bean::class.java)!!
    assertThat(bean.description).isEqualToIgnoringWhitespace("<h4>Node.js integration</h4>")

    bean = XmlSerializer.deserialize(JDOMUtil.load("""<bean><description><![CDATA[<h4>Node.js integration</h4>]]></description></bean>""".reader()), Bean::class.java)!!
    assertThat(bean.description).isEqualTo("<h4>Node.js integration</h4>")
  }

  private fun checkSmartSerialization(bean: XmlSerializerTest.Bean2, serialized: String) {
    val serializer = SmartSerializer()
    serializer.readExternal(bean, JDOMUtil.loadDocument(serialized).rootElement)
    val serializedState = Element("Bean2")
    serializer.writeExternal(bean, serializedState)
    assertThat(JDOMUtil.writeElement(serializedState)).isEqualTo(serialized)
  }
}

private val XML_PREFIX = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"

private fun assertSerializer(bean: Any, expected: String, filter: SerializationFilter?, description: String = "Serialization failure"): Element {
  val element = bean.serialize(filter)
  var actual = JDOMUtil.writeElement(element, "\n").trim()
  if (!expected.startsWith(XML_PREFIX) && actual.startsWith(XML_PREFIX)) {
    actual = actual.substring(XML_PREFIX.length).trim()
  }

  assertThat(actual).`as`(description).isEqualTo(expected)
  return element
}

internal fun <T: Any> doSerializerTest(@Language("XML") expectedText: String, bean: T, filter: SerializationFilter? = null): T {
  val expectedTrimmed = expectedText.trimIndent()
  val element = assertSerializer(bean, expectedTrimmed, filter)

  //test deserializer
  val o = XmlSerializer.deserialize(element, bean.javaClass)!!
  assertSerializer(o, expectedTrimmed, filter, "Deserialization failure")
  return o
}

fun <T : Any> T.serialize(filter: SerializationFilter? = SkipDefaultValuesSerializationFilters()): Element = XmlSerializer.serialize(this, filter)

inline fun <reified T: Any> Element.deserialize(): T = XmlSerializer.deserialize(this, T::class.java)!!

fun Element.toByteArray(): ByteArray {
  val out = BufferExposingByteArrayOutputStream(512)
  JDOMUtil.writeParent(this, out, "\n")
  return out.toByteArray()
}
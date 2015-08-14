/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import gnu.trove.THashMap
import junit.framework.AssertionFailedError
import junit.framework.TestCase
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.jdom.Element
import org.jdom.JDOMException
import org.junit.Test
import java.io.IOException
import java.util.ArrayList
import java.util.Arrays
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.concurrent.atomic.AtomicReference

public class XmlSerializerTest {
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

  open class BeanWithPublicFields(var INT_V: Int = 1, var STRING_V: String? = "hello") : Comparable<BeanWithPublicFields> {
    override fun compareTo(other: BeanWithPublicFields) = StringUtil.compare(STRING_V, other.STRING_V, false)
  }

  @Test fun PublicFieldSerialization() {
    val bean = BeanWithPublicFields()

    doSerializerTest("<BeanWithPublicFields>\n" + "  <option name=\"INT_V\" value=\"1\" />\n" + "  <option name=\"STRING_V\" value=\"hello\" />\n" + "</BeanWithPublicFields>", bean)

    bean.INT_V = 2
    bean.STRING_V = "bye"

    doSerializerTest("<BeanWithPublicFields>\n" + "  <option name=\"INT_V\" value=\"2\" />\n" + "  <option name=\"STRING_V\" value=\"bye\" />\n" + "</BeanWithPublicFields>", bean)
  }


  class BeanWithPublicFieldsDescendant(var NEW_S: String = "foo") : BeanWithPublicFields()

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

  class BeanWithSubBean {
    public var BEAN1: EmptyBeanWithCustomName? = EmptyBeanWithCustomName()
    public var BEAN2: BeanWithPublicFields? = BeanWithPublicFields()
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
    public var STRING_V: String = "hello"

    override fun equals(other: Any?): Boolean {
      // any instance of this class is equal
      return this === other || (other != null && javaClass == other.javaClass)
    }
  }

  class BeanWithSubBeanWithEquals {
    public var BEAN1: EmptyBeanWithCustomName = EmptyBeanWithCustomName()
    public var BEAN2: BeanWithEquals = BeanWithEquals()
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

  class BeanWithList {
    public var VALUES: List<String> = ArrayList(Arrays.asList("a", "b", "c"))
  }

  @Test fun ListSerialization() {
    val bean = BeanWithList()

    doSerializerTest("<BeanWithList>\n" + "  <option name=\"VALUES\">\n" + "    <list>\n" + "      <option value=\"a\" />\n" + "      <option value=\"b\" />\n" + "      <option value=\"c\" />\n" + "    </list>\n" + "  </option>\n" + "</BeanWithList>", bean)

    bean.VALUES = ArrayList(Arrays.asList("1", "2", "3"))

    doSerializerTest("<BeanWithList>\n" + "  <option name=\"VALUES\">\n" + "    <list>\n" + "      <option value=\"1\" />\n" + "      <option value=\"2\" />\n" + "      <option value=\"3\" />\n" + "    </list>\n" + "  </option>\n" + "</BeanWithList>", bean)
  }

  public class BeanWithSet {
    public var VALUES: Set<String> = LinkedHashSet(Arrays.asList("a", "b", "w"))
  }

  @Test fun SetSerialization() {
    val bean = BeanWithSet()
    doSerializerTest("<BeanWithSet>\n" + "  <option name=\"VALUES\">\n" + "    <set>\n" + "      <option value=\"a\" />\n" + "      <option value=\"b\" />\n" + "      <option value=\"w\" />\n" + "    </set>\n" + "  </option>\n" + "</BeanWithSet>", bean)
    bean.VALUES = LinkedHashSet(Arrays.asList("1", "2", "3"))

    doSerializerTest("<BeanWithSet>\n" + "  <option name=\"VALUES\">\n" + "    <set>\n" + "      <option value=\"1\" />\n" + "      <option value=\"2\" />\n" + "      <option value=\"3\" />\n" + "    </set>\n" + "  </option>\n" + "</BeanWithSet>", bean)
  }

  public class BeanWithMap {
    public var VALUES: MutableMap<String, String> = LinkedHashMap()

    init {
      VALUES.put("a", "1")
      VALUES.put("b", "2")
      VALUES.put("c", "3")
    }
  }

  @Test fun MapSerialization() {
    val bean = BeanWithMap()
    doSerializerTest("<BeanWithMap>\n" + "  <option name=\"VALUES\">\n" + "    <map>\n" + "      <entry key=\"a\" value=\"1\" />\n" + "      <entry key=\"b\" value=\"2\" />\n" + "      <entry key=\"c\" value=\"3\" />\n" + "    </map>\n" + "  </option>\n" + "</BeanWithMap>", bean)
    bean.VALUES.clear()
    bean.VALUES.put("1", "a")
    bean.VALUES.put("2", "b")
    bean.VALUES.put("3", "c")

    doSerializerTest("<BeanWithMap>\n" + "  <option name=\"VALUES\">\n" + "    <map>\n" + "      <entry key=\"1\" value=\"a\" />\n" + "      <entry key=\"2\" value=\"b\" />\n" + "      <entry key=\"3\" value=\"c\" />\n" + "    </map>\n" + "  </option>\n" + "</BeanWithMap>", bean)
  }


  public class BeanWithMapWithAnnotations {
    Property(surroundWithTag = false)
    MapAnnotation(surroundWithTag = false, entryTagName = "option", keyAttributeName = "name", valueAttributeName = "value")
    public var VALUES: MutableMap<String, String> = LinkedHashMap()

    init {
      VALUES.put("a", "1")
      VALUES.put("b", "2")
      VALUES.put("c", "3")
    }
  }

  @Test fun MapSerializationWithAnnotations() {
    val bean = BeanWithMapWithAnnotations()
    doSerializerTest("<BeanWithMapWithAnnotations>\n" + "  <option name=\"a\" value=\"1\" />\n" + "  <option name=\"b\" value=\"2\" />\n" + "  <option name=\"c\" value=\"3\" />\n" + "</BeanWithMapWithAnnotations>", bean)
    bean.VALUES.clear()
    bean.VALUES.put("1", "a")
    bean.VALUES.put("2", "b")
    bean.VALUES.put("3", "c")

    doSerializerTest("<BeanWithMapWithAnnotations>\n" + "  <option name=\"1\" value=\"a\" />\n" + "  <option name=\"2\" value=\"b\" />\n" + "  <option name=\"3\" value=\"c\" />\n" + "</BeanWithMapWithAnnotations>", bean)
  }


  public class BeanWithMapWithBeanValue {
    public var VALUES: MutableMap<String, BeanWithProperty> = LinkedHashMap()
  }

  @Test fun MapWithBeanValue() {
    val bean = BeanWithMapWithBeanValue()

    bean.VALUES.put("a", BeanWithProperty("James"))
    bean.VALUES.put("b", BeanWithProperty("Bond"))
    bean.VALUES.put("c", BeanWithProperty("Bill"))

    doSerializerTest("<BeanWithMapWithBeanValue>\n" + "  <option name=\"VALUES\">\n" + "    <map>\n" + "      <entry key=\"a\">\n" + "        <value>\n" + "          <BeanWithProperty>\n" + "            <option name=\"name\" value=\"James\" />\n" + "          </BeanWithProperty>\n" + "        </value>\n" + "      </entry>\n" + "      <entry key=\"b\">\n" + "        <value>\n" + "          <BeanWithProperty>\n" + "            <option name=\"name\" value=\"Bond\" />\n" + "          </BeanWithProperty>\n" + "        </value>\n" + "      </entry>\n" + "      <entry key=\"c\">\n" + "        <value>\n" + "          <BeanWithProperty>\n" + "            <option name=\"name\" value=\"Bill\" />\n" + "          </BeanWithProperty>\n" + "        </value>\n" + "      </entry>\n" + "    </map>\n" + "  </option>\n" + "</BeanWithMapWithBeanValue>", bean)
  }


  public class BeanWithMapWithBeanValue2 {
    MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false)
    public var values: Map<String, BeanWithProperty> = THashMap()
  }

  @Test fun MapWithBeanValueUsingSkipDefaultsFilter() {
    val bean = BeanWithMapWithBeanValue2()
    doSerializerTest("<BeanWithMapWithBeanValue2 />", bean, SkipDefaultsSerializationFilter())
  }

  data class BeanWithOption(@OptionTag("path") public var PATH: String? = null)

  @Test fun OptionTag() {
    val bean = BeanWithOption()
    bean.PATH = "123"
    doSerializerTest("<BeanWithOption>\n" + "  <option name=\"path\" value=\"123\" />\n" + "</BeanWithOption>", bean)
  }

  data class BeanWithCustomizedOption(@OptionTag(tag = "setting", nameAttribute = "key", valueAttribute = "saved") public var PATH: String? = null)

  @Test fun CustomizedOptionTag() {
    val bean = BeanWithCustomizedOption()
    bean.PATH = "123"
    doSerializerTest("<BeanWithCustomizedOption>\n" + "  <setting key=\"PATH\" saved=\"123\" />\n" + "</BeanWithCustomizedOption>", bean)
  }

  class BeanWithProperty {
    public var name: String = "James"

    public constructor() {
    }

    public constructor(name: String) {
      this.name = name
    }
  }

  @Test fun PropertySerialization() {
    val bean = BeanWithProperty()

    doSerializerTest("<BeanWithProperty>\n" + "  <option name=\"name\" value=\"James\" />\n" + "</BeanWithProperty>", bean)

    bean.name = "Bond"

    doSerializerTest("<BeanWithProperty>\n" + "  <option name=\"name\" value=\"Bond\" />\n" + "</BeanWithProperty>", bean)
  }

  class BeanWithFieldWithTagAnnotation {
    Tag("name")
    public var STRING_V: String = "hello"
  }

  @Test fun ParallelDeserialization() {
    val e = Element("root").addContent(Element("name").setText("x"))
    XmlSerializer.deserialize<BeanWithArray>(e, javaClass<BeanWithArray>())//to initialize XmlSerializerImpl.ourBindings
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

    val node = element.getChildren().get(0)
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
      override fun accepts(accessor: Accessor, bean: Any) = accessor.getName().startsWith("I")
    })
  }

  data class BeanWithArray(public var ARRAY_V: Array<String> = arrayOf("a", "b"))

  @Test fun Array() {
    val bean = BeanWithArray()
    doSerializerTest("<BeanWithArray>\n" + "  <option name=\"ARRAY_V\">\n" + "    <array>\n" + "      <option value=\"a\" />\n" + "      <option value=\"b\" />\n" + "    </array>\n" + "  </option>\n" + "</BeanWithArray>", bean)

    bean.ARRAY_V = arrayOf("1", "2", "3", "")
    doSerializerTest("<BeanWithArray>\n" + "  <option name=\"ARRAY_V\">\n" + "    <array>\n" + "      <option value=\"1\" />\n" + "      <option value=\"2\" />\n" + "      <option value=\"3\" />\n" + "      <option value=\"\" />\n" + "    </array>\n" + "  </option>\n" + "</BeanWithArray>", bean)
  }

  @Test fun Transient() {
    @Tag("bean")
    class Bean {
      public var INT_V: Int = 1
        @Transient
        get

      public @Transient fun getValue(): String = "foo"
    }

    doSerializerTest("<bean />", Bean())
  }

  public class BeanWithArrayWithoutTagName {
    AbstractCollection(surroundWithTag = false)
    public var V: Array<String> = arrayOf("a")
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
    val bean = JDOMUtil.loadDocument("<bean>\n" + "  <option name=\"intV\" value=\"2\"/>\n" + "  <vvalue v=\"1\"/>\n" + "  <vvalue v=\"2\"/>\n" + "  <vvalue v=\"3\"/>\n" + "</bean>").getRootElement().deserialize<BeanWithArrayWithoutAllsTag>()
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

    doSerializerTest("<bean>\n" + "  <option name=\"v\">\n" + "    <array />\n" + "  </option>\n" + "</bean>", bean)

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

  class BeanWithPropertiesBoundToAttribute {
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


  class BeanWithPropertyFilter {
    Property(filter = PropertyFilterTest::class)
    public var STRING_V: String = "hello"
  }

  class PropertyFilterTest : SerializationFilter {
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

  class BeanWithJDOMElement {
    public var STRING_V: String = "hello"
    Tag("actions")
    public var actions: org.jdom.Element? = null
  }

  @Test fun SerializeJDOMElementField() {
    val element = BeanWithJDOMElement()
    element.STRING_V = "a"
    element.actions = Element("x").addContent(Element("a")).addContent(Element("b"))
    assertSerializer(element, "<BeanWithJDOMElement>\n" + "  <option name=\"STRING_V\" value=\"a\" />\n" + "  <actions>\n" + "    <a />\n" + "    <b />\n" + "  </actions>\n" + "</BeanWithJDOMElement>", null)

    element.actions = null
    assertSerializer(element, "<BeanWithJDOMElement>\n" + "  <option name=\"STRING_V\" value=\"a\" />\n" + "</BeanWithJDOMElement>", null)
  }

  throws(Exception::class)
  @Test fun DeserializeJDOMElementField() {


    val bean = XmlSerializer.deserialize<BeanWithJDOMElement>(JDOMUtil.loadDocument("<BeanWithJDOMElement><option name=\"STRING_V\" value=\"bye\"/><actions><action/><action/></actions></BeanWithJDOMElement>").getRootElement(), javaClass<BeanWithJDOMElement>())


    TestCase.assertEquals("bye", bean.STRING_V)
    TestCase.assertNotNull(bean.actions)
    TestCase.assertEquals(2, bean.actions!!.getChildren("action").size())
  }

  class BeanWithJDOMElementArray {
    public var STRING_V: String = "hello"
    Tag("actions")
    public var actions: Array<org.jdom.Element>? = null
  }

  @Test fun JDOMElementArrayField() {
    val text = "<BeanWithJDOMElementArray>\n" + "  <option name=\"STRING_V\" value=\"bye\" />\n" + "  <actions>\n" + "    <action />\n" + "    <action />\n" + "  </actions>\n" + "  <actions>\n" + "    <action />\n" + "  </actions>\n" + "</BeanWithJDOMElementArray>"
    val bean = XmlSerializer.deserialize<BeanWithJDOMElementArray>(JDOMUtil.loadDocument(text).getRootElement(), javaClass<BeanWithJDOMElementArray>())


    TestCase.assertEquals("bye", bean.STRING_V)
    TestCase.assertNotNull(bean.actions)
    TestCase.assertEquals(2, bean.actions!!.size())
    TestCase.assertEquals(2, bean.actions!![0].getChildren().size())
    TestCase.assertEquals(1, bean.actions!![1].getChildren().size())

    assertSerializer(bean, text, null)

    bean.actions = null
    val newText = "<BeanWithJDOMElementArray>\n" + "  <option name=\"STRING_V\" value=\"bye\" />\n" + "</BeanWithJDOMElementArray>"
    doSerializerTest(newText, bean)

    bean.actions = emptyArray()
    doSerializerTest(newText, bean)
  }

  class BeanWithTextAnnotation {
    public var INT_V: Int = 1
    Text
    public var STRING_V: String = "hello"

    public constructor(INT_V: Int, STRING_V: String) {
      this.INT_V = INT_V
      this.STRING_V = STRING_V
    }

    public constructor() {
    }
  }

  @Test fun TextAnnotation() {
    val bean = BeanWithTextAnnotation()

    doSerializerTest("<BeanWithTextAnnotation>\n" + "  <option name=\"INT_V\" value=\"1\" />\n" + "  hello\n" + "</BeanWithTextAnnotation>", bean)

    bean.INT_V = 2
    bean.STRING_V = "bye"

    doSerializerTest("<BeanWithTextAnnotation>\n" + "  <option name=\"INT_V\" value=\"2\" />\n" + "  bye\n" + "</BeanWithTextAnnotation>", bean)
  }

  class BeanWithEnum {
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

  @Test fun SetKeysInMap() {
    @Tag("bean")
    class BeanWithSetKeysInMap {
      var myMap = LinkedHashMap<Collection<String>, String>()
    }

    val bean = BeanWithSetKeysInMap()
    bean.myMap.put(LinkedHashSet(Arrays.asList("a", "b", "c")), "letters")
    bean.myMap.put(LinkedHashSet(Arrays.asList("1", "2", "3")), "numbers")

    val bb = doSerializerTest("<bean>\n" + "  <option name=\"myMap\">\n" + "    <map>\n" + "      <entry value=\"letters\">\n" + "        <key>\n" + "          <set>\n" + "            <option value=\"a\" />\n" + "            <option value=\"b\" />\n" + "            <option value=\"c\" />\n" + "          </set>\n" + "        </key>\n" + "      </entry>\n" + "      <entry value=\"numbers\">\n" + "        <key>\n" + "          <set>\n" + "            <option value=\"1\" />\n" + "            <option value=\"2\" />\n" + "            <option value=\"3\" />\n" + "          </set>\n" + "        </key>\n" + "      </entry>\n" + "    </map>\n" + "  </option>\n" + "</bean>", bean)

    for (collection in bb.myMap.keySet()) {
      assertThat(collection).isInstanceOf(javaClass<Set<Any>>())
    }
  }

  @Test fun ConversionFromTextToAttribute() {
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
    XmlSerializer.deserializeInto(bean, JDOMUtil.loadDocument(xml).getRootElement())

    TestCase.assertEquals(999, bean.INT_V)
    TestCase.assertEquals("zzz", bean.STRING_V)
  }

  @Test fun mapWithNotSurroundingKeyAndValue() {
    @Tag("bean")
    class Bean {
      @Tag("map")
      @MapAnnotation(surroundWithTag = false, entryTagName = "pair", surroundKeyWithTag = false, surroundValueWithTag = false)
      var MAP = LinkedHashMap<BeanWithPublicFields, BeanWithTextAnnotation>()
    }

    val bean = Bean()

    bean.MAP.put(BeanWithPublicFields(1, "a"), BeanWithTextAnnotation(2, "b"))
    bean.MAP.put(BeanWithPublicFields(3, "c"), BeanWithTextAnnotation(4, "d"))
    bean.MAP.put(BeanWithPublicFields(5, "e"), BeanWithTextAnnotation(6, "f"))

    doSerializerTest("<bean>\n" + "  <map>\n" + "    <pair>\n" + "      <BeanWithPublicFields>\n" + "        <option name=\"INT_V\" value=\"1\" />\n" + "        <option name=\"STRING_V\" value=\"a\" />\n" + "      </BeanWithPublicFields>\n" + "      <BeanWithTextAnnotation>\n" + "        <option name=\"INT_V\" value=\"2\" />\n" + "        b\n" + "      </BeanWithTextAnnotation>\n" + "    </pair>\n" + "    <pair>\n" + "      <BeanWithPublicFields>\n" + "        <option name=\"INT_V\" value=\"3\" />\n" + "        <option name=\"STRING_V\" value=\"c\" />\n" + "      </BeanWithPublicFields>\n" + "      <BeanWithTextAnnotation>\n" + "        <option name=\"INT_V\" value=\"4\" />\n" + "        d\n" + "      </BeanWithTextAnnotation>\n" + "    </pair>\n" + "    <pair>\n" + "      <BeanWithPublicFields>\n" + "        <option name=\"INT_V\" value=\"5\" />\n" + "        <option name=\"STRING_V\" value=\"e\" />\n" + "      </BeanWithPublicFields>\n" + "      <BeanWithTextAnnotation>\n" + "        <option name=\"INT_V\" value=\"6\" />\n" + "        f\n" + "      </BeanWithTextAnnotation>\n" + "    </pair>\n" + "  </map>\n" + "</bean>", bean)
  }

  @Test fun MapAtTopLevel() {
    @Tag("bean")
    class BeanWithMapAtTopLevel {
      @Property(surroundWithTag = false)
      @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false)
      var map = LinkedHashMap<String, String>()

      var option: String? = null
    }

    val bean = BeanWithMapAtTopLevel()
    bean.map.put("a", "b")
    bean.option = "xxx"
    doSerializerTest("""<bean>
  <option name="option" value="xxx" />
  <entry key="a" value="b" />
</bean>""", bean)
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
    Attribute
    public fun getFoo(): String {
      return "foo"
    }

    public fun setFoo(value: String) {
    }
  }

  @Test fun DefaultAttributeName() {
    val bean = BeanWithDefaultAttributeName()
    doSerializerTest("<BeanWithDefaultAttributeName foo=\"foo\" />", bean)
  }

  class Bean2 {
    Attribute
    var ab: String? = null

    Attribute
    var module: String? = null

    Attribute
    var ac: String? = null
  }

  throws(IOException::class, JDOMException::class)
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
  class Bean3 {
    @suppress("DEPRECATED_SYMBOL_WITH_MESSAGE")
    public var list: JDOMExternalizableStringList = JDOMExternalizableStringList()
  }

  Tag("b")
  class Bean4 {
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
    @suppress("DEPRECATED_SYMBOL_WITH_MESSAGE")
    val list = JDOMExternalizableStringList()
    list.add("one")
    list.add("two")
    list.add("three")

    val value = Element("value")
    list.writeExternal(value)
    val o = XmlSerializer.deserialize<Bean4>(Element("state").addContent(Element("option").setAttribute("name", "myList").addContent(value)), javaClass<Bean4>())
    assertSerializer(o, "<b>\n" + "  <list>\n" + "    <item value=\"one\" />\n" + "    <item value=\"two\" />\n" + "    <item value=\"three\" />\n" + "  </list>\n" + "</b>", SkipDefaultsSerializationFilter())
  }

  @Test fun beanWithMapWithSetValue() {
    @Tag("bean")
    class BeanWithMapWithSetValue {
      @MapAnnotation(entryTagName = "entry-tag", keyAttributeName = "key-attr", surroundWithTag = false)
      var myValues = LinkedHashMap<String, Set<String>>()
    }

    val bean = BeanWithMapWithSetValue()

    bean.myValues.put("a", LinkedHashSet(Arrays.asList("first1", "second1")))
    bean.myValues.put("b", LinkedHashSet(Arrays.asList("first2", "second2")))

    doSerializerTest("<bean>\n" + "  <option name=\"myValues\">\n" + "    <entry-tag key-attr=\"a\">\n" + "      <value>\n" + "        <set>\n" + "          <option value=\"first1\" />\n" + "          <option value=\"second1\" />\n" + "        </set>\n" + "      </value>\n" + "    </entry-tag>\n" + "    <entry-tag key-attr=\"b\">\n" + "      <value>\n" + "        <set>\n" + "          <option value=\"first2\" />\n" + "          <option value=\"second2\" />\n" + "        </set>\n" + "      </value>\n" + "    </entry-tag>\n" + "  </option>\n" + "</bean>", bean)
  }

  @Test fun cdataAfterNewLine() {
    @Tag("bean")
    @data class Bean(@Tag var description: String? = null)

    var bean = XmlSerializer.deserialize(JDOMUtil.load("""<bean>
  <description>
    <![CDATA[
    <h4>Node.js integration</h4>
    ]]>
  </description>
</bean>""".reader), javaClass<Bean>())
    assertThat(bean.description).isEqualToIgnoringWhitespace("<h4>Node.js integration</h4>")

    bean = XmlSerializer.deserialize(JDOMUtil.load("""<bean><description><![CDATA[<h4>Node.js integration</h4>]]></description></bean>""".reader), javaClass<Bean>())
    assertThat(bean.description).isEqualTo("<h4>Node.js integration</h4>")
  }

  companion object {
    private val XML_PREFIX = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"

    private fun checkSmartSerialization(bean: Bean2, serialized: String) {
      val serializer = SmartSerializer()
      serializer.readExternal(bean, JDOMUtil.loadDocument(serialized).getRootElement())
      val serializedState = Element("Bean2")
      serializer.writeExternal(bean, serializedState)
      TestCase.assertEquals(serialized, JDOMUtil.writeElement(serializedState))
    }

    private fun <T> doSerializerTest(Language("XML") expectedText: String, bean: T, filter: SerializationFilter? = null): T {
      val element = assertSerializer(bean, expectedText, filter)

      //test deserializer
      val o = XmlSerializer.deserialize(element, bean.javaClass)
      assertSerializer(o, expectedText, filter, "Deserialization failure")
      return o
    }

    private fun assertSerializer(bean: Any, expected: String, filter: SerializationFilter?, description: String = "Serialization failure"): Element {
      val element = bean.serialize(filter)
      var actual = JDOMUtil.writeElement(element, "\n").trim()
      if (!expected.startsWith(XML_PREFIX) && actual.startsWith(XML_PREFIX)) {
        actual = actual.substring(XML_PREFIX.length()).trim()
      }

      assertThat(actual).`as`(description).isEqualTo(expected)
      return element
    }
  }
}

public fun <T : Any> T.serialize(filter: SerializationFilter? = SkipDefaultValuesSerializationFilters()): Element = XmlSerializer.serialize(this, filter)

public inline fun <reified T: Any> Element.deserialize(): T = XmlSerializer.deserialize(this, javaClass<T>())

public fun Element.toByteArray(): ByteArray {
  val out = BufferExposingByteArrayOutputStream(512)
  JDOMUtil.writeParent(this, out, "\n")
  return out.toByteArray()
}
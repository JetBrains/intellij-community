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

import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import gnu.trove.THashMap
import org.junit.Test
import java.util.*

internal class XmlSerializerMapTest {
  @Test fun beanValueUsingSkipDefaultsFilter() {
    @Tag("bean")
    class BeanWithMapWithBeanValue2 {
      @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false) var values: Map<String, BeanWithProperty> = THashMap()
    }

    val bean = BeanWithMapWithBeanValue2()
    doSerializerTest("<bean />", bean, SkipDefaultsSerializationFilter())
  }

  @Test fun mapAtTopLevel() {
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
    doSerializerTest("""
    <bean>
      <option name="option" value="xxx" />
      <entry key="a" value="b" />
    </bean>""", bean)
  }

  @Test fun notSurroundingKeyAndValue() {
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

    doSerializerTest("""
    <bean>
      <map>
        <pair>
          <BeanWithPublicFields>
            <option name="INT_V" value="1" />
            <option name="STRING_V" value="a" />
          </BeanWithPublicFields>
          <BeanWithTextAnnotation>
            <option name="INT_V" value="2" />
            b
          </BeanWithTextAnnotation>
        </pair>
        <pair>
          <BeanWithPublicFields>
            <option name="INT_V" value="3" />
            <option name="STRING_V" value="c" />
          </BeanWithPublicFields>
          <BeanWithTextAnnotation>
            <option name="INT_V" value="4" />
            d
          </BeanWithTextAnnotation>
        </pair>
        <pair>
          <BeanWithPublicFields>
            <option name="INT_V" value="5" />
            <option name="STRING_V" value="e" />
          </BeanWithPublicFields>
          <BeanWithTextAnnotation>
            <option name="INT_V" value="6" />
            f
          </BeanWithTextAnnotation>
        </pair>
      </map>
    </bean>""", bean)
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

    doSerializerTest("""
    <bean>
      <option name="myValues">
        <entry-tag key-attr="a">
          <value>
            <set>
              <option value="first1" />
              <option value="second1" />
            </set>
          </value>
        </entry-tag>
        <entry-tag key-attr="b">
          <value>
            <set>
              <option value="first2" />
              <option value="second2" />
            </set>
          </value>
        </entry-tag>
      </option>
    </bean>""", bean)
  }

  private class BeanWithMap {
    var VALUES: MutableMap<String, String> = LinkedHashMap()

    init {
      VALUES.put("a", "1")
      VALUES.put("b", "2")
      VALUES.put("c", "3")
    }
  }

  @Test fun serialization() {
    val bean = BeanWithMap()
    doSerializerTest("<BeanWithMap>\n  <option name=\"VALUES\">\n    <map>\n      <entry key=\"a\" value=\"1\" />\n      <entry key=\"b\" value=\"2\" />\n      <entry key=\"c\" value=\"3\" />\n    </map>\n  </option>\n</BeanWithMap>", bean)
    bean.VALUES.clear()
    bean.VALUES.put("1", "a")
    bean.VALUES.put("2", "b")
    bean.VALUES.put("3", "c")

    doSerializerTest("<BeanWithMap>\n  <option name=\"VALUES\">\n    <map>\n      <entry key=\"1\" value=\"a\" />\n      <entry key=\"2\" value=\"b\" />\n      <entry key=\"3\" value=\"c\" />\n    </map>\n  </option>\n</BeanWithMap>", bean)
  }


  private class BeanWithMapWithAnnotations {
    @Property(surroundWithTag = false)
    @MapAnnotation(surroundWithTag = false, entryTagName = "option", keyAttributeName = "name", valueAttributeName = "value")
    var VALUES: MutableMap<String, String> = LinkedHashMap()

    init {
      VALUES.put("a", "1")
      VALUES.put("b", "2")
      VALUES.put("c", "3")
    }
  }

  @Test fun serializationWithAnnotations() {
    val bean = BeanWithMapWithAnnotations()
    doSerializerTest("<BeanWithMapWithAnnotations>\n  <option name=\"a\" value=\"1\" />\n  <option name=\"b\" value=\"2\" />\n  <option name=\"c\" value=\"3\" />\n</BeanWithMapWithAnnotations>", bean)
    bean.VALUES.clear()
    bean.VALUES.put("1", "a")
    bean.VALUES.put("2", "b")
    bean.VALUES.put("3", "c")

    doSerializerTest("<BeanWithMapWithAnnotations>\n  <option name=\"1\" value=\"a\" />\n  <option name=\"2\" value=\"b\" />\n  <option name=\"3\" value=\"c\" />\n</BeanWithMapWithAnnotations>", bean)
  }

  private class BeanWithMapWithBeanValue {
    var VALUES: MutableMap<String, BeanWithProperty> = LinkedHashMap()
  }

  @Test fun withBeanValue() {
    val bean = BeanWithMapWithBeanValue()

    bean.VALUES.put("a", BeanWithProperty("James"))
    bean.VALUES.put("b", BeanWithProperty("Bond"))
    bean.VALUES.put("c", BeanWithProperty("Bill"))

    doSerializerTest("<BeanWithMapWithBeanValue>\n  <option name=\"VALUES\">\n    <map>\n      <entry key=\"a\">\n        <value>\n          <BeanWithProperty>\n            <option name=\"name\" value=\"James\" />\n          </BeanWithProperty>\n        </value>\n      </entry>\n      <entry key=\"b\">\n        <value>\n          <BeanWithProperty>\n            <option name=\"name\" value=\"Bond\" />\n          </BeanWithProperty>\n        </value>\n      </entry>\n      <entry key=\"c\">\n        <value>\n          <BeanWithProperty>\n            <option name=\"name\" value=\"Bill\" />\n          </BeanWithProperty>\n        </value>\n      </entry>\n    </map>\n  </option>\n</BeanWithMapWithBeanValue>", bean)
  }
}
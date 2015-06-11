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

package com.intellij.util.xmlb;

import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.*;
import gnu.trove.THashMap;
import junit.framework.AssertionFailedError;
import junit.framework.TestCase;
import org.intellij.lang.annotations.Language;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author mike
 */
public class XmlSerializerTest extends TestCase {
  private static final String XML_PREFIX = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

  public static class EmptyBean {
  }

  public void testEmptyBeanSerialization() {
    doSerializerTest("<EmptyBean />", new EmptyBean());
  }

  @Tag("Bean")
  public static class EmptyBeanWithCustomName {
  }

  public void testEmptyBeanSerializationWithCustomName() {
    doSerializerTest("<Bean />", new EmptyBeanWithCustomName());
  }

  public static class BeanWithPublicFields implements Comparable<BeanWithPublicFields> {
    public int INT_V = 1;
    public String STRING_V = "hello";

    public BeanWithPublicFields(final int INT_V, final String STRING_V) {
      this.INT_V = INT_V;
      this.STRING_V = STRING_V;
    }

    public BeanWithPublicFields() {
    }

    @Override
    public int compareTo(final BeanWithPublicFields o) {
      return STRING_V.compareTo(o.STRING_V);
    }
  }

  public void testPublicFieldSerialization() {
    BeanWithPublicFields bean = new BeanWithPublicFields();

    doSerializerTest(
      "<BeanWithPublicFields>\n" +
      "  <option name=\"INT_V\" value=\"1\" />\n" +
      "  <option name=\"STRING_V\" value=\"hello\" />\n" +
      "</BeanWithPublicFields>", bean);

    bean.INT_V = 2;
    bean.STRING_V = "bye";

    doSerializerTest(
      "<BeanWithPublicFields>\n" +
      "  <option name=\"INT_V\" value=\"2\" />\n" +
      "  <option name=\"STRING_V\" value=\"bye\" />\n" +
      "</BeanWithPublicFields>", bean);
  }


  public static class BeanWithPublicFieldsDescendant extends BeanWithPublicFields {
    public String NEW_S = "foo";
  }

  public void testPublicFieldSerializationWithInheritance() {
    BeanWithPublicFieldsDescendant bean = new BeanWithPublicFieldsDescendant();

    doSerializerTest(
      "<BeanWithPublicFieldsDescendant>\n" +
      "  <option name=\"NEW_S\" value=\"foo\" />\n" +
      "  <option name=\"INT_V\" value=\"1\" />\n" +
      "  <option name=\"STRING_V\" value=\"hello\" />\n" +
      "</BeanWithPublicFieldsDescendant>",
      bean);

    bean.INT_V = 2;
    bean.STRING_V = "bye";
    bean.NEW_S = "bar";

    doSerializerTest(
      "<BeanWithPublicFieldsDescendant>\n" +
      "  <option name=\"NEW_S\" value=\"bar\" />\n" +
      "  <option name=\"INT_V\" value=\"2\" />\n" +
      "  <option name=\"STRING_V\" value=\"bye\" />\n" +
      "</BeanWithPublicFieldsDescendant>",
      bean);
  }

  public static class BeanWithSubBean {
    public EmptyBeanWithCustomName BEAN1 = new EmptyBeanWithCustomName();
    public BeanWithPublicFields BEAN2 = new BeanWithPublicFields();
  }

  public void testSubBeanSerialization() {
    BeanWithSubBean bean = new BeanWithSubBean();
    doSerializerTest(
      "<BeanWithSubBean>\n" +
      "  <option name=\"BEAN1\">\n" +
      "    <Bean />\n" +
      "  </option>\n" +
      "  <option name=\"BEAN2\">\n" +
      "    <BeanWithPublicFields>\n" +
      "      <option name=\"INT_V\" value=\"1\" />\n" +
      "      <option name=\"STRING_V\" value=\"hello\" />\n" +
      "    </BeanWithPublicFields>\n" +
      "  </option>\n" +
      "</BeanWithSubBean>",
      bean);
    bean.BEAN2.INT_V = 2;
    bean.BEAN2.STRING_V = "bye";

    doSerializerTest(
      "<BeanWithSubBean>\n" +
      "  <option name=\"BEAN1\">\n" +
      "    <Bean />\n" +
      "  </option>\n" +
      "  <option name=\"BEAN2\">\n" +
      "    <BeanWithPublicFields>\n" +
      "      <option name=\"INT_V\" value=\"2\" />\n" +
      "      <option name=\"STRING_V\" value=\"bye\" />\n" +
      "    </BeanWithPublicFields>\n" +
      "  </option>\n" +
      "</BeanWithSubBean>",
      bean);
  }

  public void testSubBeanSerializationAndSkipDefaults() {
    BeanWithSubBean bean = new BeanWithSubBean();
    doSerializerTest(
      "<BeanWithSubBean />",
      bean, new SkipDefaultsSerializationFilter());
  }

  public final static class BeanWithEquals {
    public String STRING_V = "hello";

    public BeanWithEquals() {
    }

    @Override
    public boolean equals(Object o) {
      // any instance of this class is equal
      return this == o || (o != null && getClass() == o.getClass());
    }
  }

  public static class BeanWithSubBeanWithEquals {
    public EmptyBeanWithCustomName BEAN1 = new EmptyBeanWithCustomName();
    public BeanWithEquals BEAN2 = new BeanWithEquals();
  }

  public void testSubBeanWithEqualsSerializationAndSkipDefaults() {
    BeanWithSubBeanWithEquals bean = new BeanWithSubBeanWithEquals();
    SkipDefaultsSerializationFilter filter = new SkipDefaultsSerializationFilter();
    doSerializerTest(
      "<BeanWithSubBeanWithEquals />",
      bean, filter);

    bean.BEAN2.STRING_V = "new";
    doSerializerTest(
      "<BeanWithSubBeanWithEquals />",
      bean, filter);
  }

  public void testNullFieldValue() {
    BeanWithPublicFields bean1 = new BeanWithPublicFields();

    doSerializerTest(
      "<BeanWithPublicFields>\n" +
      "  <option name=\"INT_V\" value=\"1\" />\n" +
      "  <option name=\"STRING_V\" value=\"hello\" />\n" +
      "</BeanWithPublicFields>",
      bean1);

    bean1.STRING_V = null;

    doSerializerTest(
      "<BeanWithPublicFields>\n" +
      "  <option name=\"INT_V\" value=\"1\" />\n" +
      "  <option name=\"STRING_V\" />\n" +
      "</BeanWithPublicFields>", bean1);

    BeanWithSubBean bean2 = new BeanWithSubBean();
    bean2.BEAN1 = null;
    bean2.BEAN2 = null;

    doSerializerTest(
      "<BeanWithSubBean>\n" +
      "  <option name=\"BEAN1\" />\n" +
      "  <option name=\"BEAN2\" />\n" +
      "</BeanWithSubBean>", bean2);

  }

  public static class BeanWithList {
    public List<String> VALUES = new ArrayList<String>(Arrays.asList("a", "b", "c"));
  }

  public void testListSerialization() {
    BeanWithList bean = new BeanWithList();

    doSerializerTest(
      "<BeanWithList>\n" +
      "  <option name=\"VALUES\">\n" +
      "    <list>\n" +
      "      <option value=\"a\" />\n" +
      "      <option value=\"b\" />\n" +
      "      <option value=\"c\" />\n" +
      "    </list>\n" +
      "  </option>\n" +
      "</BeanWithList>",
      bean);

    bean.VALUES = new ArrayList<String>(Arrays.asList("1", "2", "3"));

    doSerializerTest(
      "<BeanWithList>\n" +
      "  <option name=\"VALUES\">\n" +
      "    <list>\n" +
      "      <option value=\"1\" />\n" +
      "      <option value=\"2\" />\n" +
      "      <option value=\"3\" />\n" +
      "    </list>\n" +
      "  </option>\n" +
      "</BeanWithList>",
      bean);
  }

  public static class BeanWithSet {
    public Set<String> VALUES = new LinkedHashSet<String>(Arrays.asList("a", "b", "w"));
  }

  public void testSetSerialization() {
    BeanWithSet bean = new BeanWithSet();
    doSerializerTest(
      "<BeanWithSet>\n" +
      "  <option name=\"VALUES\">\n" +
      "    <set>\n" +
      "      <option value=\"a\" />\n" +
      "      <option value=\"b\" />\n" +
      "      <option value=\"w\" />\n" +
      "    </set>\n" +
      "  </option>\n" +
      "</BeanWithSet>",
      bean);
    bean.VALUES = new LinkedHashSet<String>(Arrays.asList("1", "2", "3"));

    doSerializerTest(
      "<BeanWithSet>\n" +
      "  <option name=\"VALUES\">\n" +
      "    <set>\n" +
      "      <option value=\"1\" />\n" +
      "      <option value=\"2\" />\n" +
      "      <option value=\"3\" />\n" +
      "    </set>\n" +
      "  </option>\n" +
      "</BeanWithSet>",
      bean);
  }

  public static class BeanWithMap {
    public Map<String, String> VALUES = new LinkedHashMap<String, String>();

    {
      VALUES.put("a", "1");
      VALUES.put("b", "2");
      VALUES.put("c", "3");
    }
  }

  public void testMapSerialization() {
    BeanWithMap bean = new BeanWithMap();
    doSerializerTest(
      "<BeanWithMap>\n" +
      "  <option name=\"VALUES\">\n" +
      "    <map>\n" +
      "      <entry key=\"a\" value=\"1\" />\n" +
      "      <entry key=\"b\" value=\"2\" />\n" +
      "      <entry key=\"c\" value=\"3\" />\n" +
      "    </map>\n" +
      "  </option>\n" +
      "</BeanWithMap>",
      bean);
    bean.VALUES.clear();
    bean.VALUES.put("1", "a");
    bean.VALUES.put("2", "b");
    bean.VALUES.put("3", "c");

    doSerializerTest(
      "<BeanWithMap>\n" +
      "  <option name=\"VALUES\">\n" +
      "    <map>\n" +
      "      <entry key=\"1\" value=\"a\" />\n" +
      "      <entry key=\"2\" value=\"b\" />\n" +
      "      <entry key=\"3\" value=\"c\" />\n" +
      "    </map>\n" + "  </option>\n" +
      "</BeanWithMap>",
      bean);
  }


  public static class BeanWithMapWithAnnotations {
    @Property(surroundWithTag = false)
    @MapAnnotation(
      surroundWithTag = false,
      entryTagName = "option",
      keyAttributeName = "name",
      valueAttributeName = "value"
    )
    public Map<String, String> VALUES = new LinkedHashMap<String, String>();

    {
      VALUES.put("a", "1");
      VALUES.put("b", "2");
      VALUES.put("c", "3");
    }
  }

  public void testMapSerializationWithAnnotations() {
    BeanWithMapWithAnnotations bean = new BeanWithMapWithAnnotations();
    doSerializerTest(
      "<BeanWithMapWithAnnotations>\n" +
      "  <option name=\"a\" value=\"1\" />\n" +
      "  <option name=\"b\" value=\"2\" />\n" +
      "  <option name=\"c\" value=\"3\" />\n" +
      "</BeanWithMapWithAnnotations>",
      bean);
    bean.VALUES.clear();
    bean.VALUES.put("1", "a");
    bean.VALUES.put("2", "b");
    bean.VALUES.put("3", "c");

    doSerializerTest(
      "<BeanWithMapWithAnnotations>\n" +
      "  <option name=\"1\" value=\"a\" />\n" +
      "  <option name=\"2\" value=\"b\" />\n" +
      "  <option name=\"3\" value=\"c\" />\n" +
      "</BeanWithMapWithAnnotations>",
      bean);
  }


  public static class BeanWithMapWithBeanValue {
    public Map<String, BeanWithProperty> VALUES = new LinkedHashMap<String, BeanWithProperty>();
  }

  public void testMapWithBeanValue() {
    BeanWithMapWithBeanValue bean = new BeanWithMapWithBeanValue();

    bean.VALUES.put("a", new BeanWithProperty("James"));
    bean.VALUES.put("b", new BeanWithProperty("Bond"));
    bean.VALUES.put("c", new BeanWithProperty("Bill"));

    doSerializerTest(
      "<BeanWithMapWithBeanValue>\n" +
      "  <option name=\"VALUES\">\n" +
      "    <map>\n" +
      "      <entry key=\"a\">\n" +
      "        <value>\n" +
      "          <BeanWithProperty>\n" +
      "            <option name=\"name\" value=\"James\" />\n" +
      "          </BeanWithProperty>\n" +
      "        </value>\n" +
      "      </entry>\n" +
      "      <entry key=\"b\">\n" +
      "        <value>\n" +
      "          <BeanWithProperty>\n" +
      "            <option name=\"name\" value=\"Bond\" />\n" +
      "          </BeanWithProperty>\n" +
      "        </value>\n" +
      "      </entry>\n" +
      "      <entry key=\"c\">\n" +
      "        <value>\n" +
      "          <BeanWithProperty>\n" +
      "            <option name=\"name\" value=\"Bill\" />\n" +
      "          </BeanWithProperty>\n" +
      "        </value>\n" +
      "      </entry>\n" +
      "    </map>\n" +
      "  </option>\n" +
      "</BeanWithMapWithBeanValue>",
      bean);
  }


  public static class BeanWithMapWithBeanValue2 {
    @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false)
    public Map<String, BeanWithProperty> values = new THashMap<String, BeanWithProperty>();
  }


  public void testMapWithBeanValueUsingSkipDefaultsFilter() {
    BeanWithMapWithBeanValue2 bean = new BeanWithMapWithBeanValue2();
    doSerializerTest(
      "<BeanWithMapWithBeanValue2 />",
      bean, new SkipDefaultsSerializationFilter());
  }

  public static class BeanWithOption {
    @OptionTag("path")
    public String PATH;
  }

  public void testOptionTag() {
    BeanWithOption bean = new BeanWithOption();
    bean.PATH = "123";
    doSerializerTest("<BeanWithOption>\n" +
                     "  <option name=\"path\" value=\"123\" />\n" +
                     "</BeanWithOption>", bean);
  }

  public static class BeanWithCustomizedOption {
    @OptionTag(tag = "setting", nameAttribute = "key", valueAttribute = "saved")
    public String PATH;
  }

  public void testCustomizedOptionTag() {
    BeanWithCustomizedOption bean = new BeanWithCustomizedOption();
    bean.PATH = "123";
    doSerializerTest("<BeanWithCustomizedOption>\n" +
                     "  <setting key=\"PATH\" saved=\"123\" />\n" +
                     "</BeanWithCustomizedOption>", bean);
  }

  public static class BeanWithProperty {
    private String name = "James";

    public BeanWithProperty() {
    }

    public BeanWithProperty(final String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }


    public void setName(String name) {
      this.name = name;
    }
  }

  public void testPropertySerialization() {
    BeanWithProperty bean = new BeanWithProperty();

    doSerializerTest(
      "<BeanWithProperty>\n" +
      "  <option name=\"name\" value=\"James\" />\n" +
      "</BeanWithProperty>",
      bean);

    bean.setName("Bond");

    doSerializerTest(
      "<BeanWithProperty>\n" +
      "  <option name=\"name\" value=\"Bond\" />\n" +
      "</BeanWithProperty>", bean);
  }

  public static class BeanWithFieldWithTagAnnotation {
    @Tag("name")
    public String STRING_V = "hello";
  }

  public void testParallelDeserialization() throws InterruptedException {
    final Element e = new Element("root").addContent(new Element("name").setText("x"));
    XmlSerializer.deserialize(e, BeanWithArray.class);//to initialize XmlSerializerImpl.ourBindings
    Thread[] threads = new Thread[5];
    final AtomicReference<AssertionFailedError> exc = new AtomicReference<AssertionFailedError>();
    for (int i = 0; i < threads.length; i++) {
      threads[i] = new Thread("XmlSerializerTest#testParallelDeserialization-" + i) {
        @Override
        public void run() {
          try {
            for (int j = 0; j < 10; j++) {
              BeanWithFieldWithTagAnnotation bean = XmlSerializer.deserialize(e, BeanWithFieldWithTagAnnotation.class);
              assertNotNull(bean);
              assertEquals("x", bean.STRING_V);
            }
          }
          catch (AssertionFailedError e) {
            exc.set(e);
          }
        }
      };
    }
    for (Thread thread : threads) {
      thread.start();
    }
    for (Thread thread : threads) {
      thread.join();
    }
    AssertionFailedError error = exc.get();
    if (error != null) {
      throw error;
    }
  }

  public void testFieldWithTagAnnotation() {
    BeanWithFieldWithTagAnnotation bean = new BeanWithFieldWithTagAnnotation();

    doSerializerTest(
      "<BeanWithFieldWithTagAnnotation>\n" +
      "  <name>hello</name>\n" +
      "</BeanWithFieldWithTagAnnotation>",
      bean);

    bean.STRING_V = "bye";

    doSerializerTest(
      "<BeanWithFieldWithTagAnnotation>\n" +
      "  <name>bye</name>\n" +
      "</BeanWithFieldWithTagAnnotation>", bean);
  }

  public void testEscapeCharsInTagText() {
    BeanWithFieldWithTagAnnotation bean = new BeanWithFieldWithTagAnnotation();
    bean.STRING_V = "a\nb\"<";

    doSerializerTest(
      "<BeanWithFieldWithTagAnnotation>\n" +
      "  <name>a\nb&quot;&lt;</name>\n" +
      "</BeanWithFieldWithTagAnnotation>", bean);
  }

  public void testEscapeCharsInAttributeValue() {
    final BeanWithPropertiesBoundToAttribute bean = new BeanWithPropertiesBoundToAttribute();
    bean.name = "a\nb\"<";
    doSerializerTest("<BeanWithPropertiesBoundToAttribute count=\"3\" name=\"a&#10;b&quot;&lt;\" />", bean);
  }

  public void testShuffledDeserialize() {
    BeanWithPublicFields bean = new BeanWithPublicFields();
    bean.INT_V = 987;
    bean.STRING_V = "1234";

    Element element = serialize(bean, null);

    Element node = element.getChildren().get(0);
    element.removeContent(node);
    element.addContent(node);

    bean = XmlSerializer.deserialize(element, bean.getClass());
    assert bean != null;
    assertEquals(987, bean.INT_V);
    assertEquals("1234", bean.STRING_V);
  }

  public void testFilterSerializer() {
    BeanWithPublicFields bean = new BeanWithPublicFields();
    assertSerializer(bean,
                     "<BeanWithPublicFields>\n" +
                     "  <option name=\"INT_V\" value=\"1\" />\n" +
                     "</BeanWithPublicFields>",
                     new SerializationFilter() {
      @Override
      public boolean accepts(@NotNull Accessor accessor, @NotNull Object bean) {
        return accessor.getName().startsWith("I");
      }
    });
  }

  public static class BeanWithArray {
    public String[] ARRAY_V = new String[] {"a", "b"};
  }

  public void testArray() {
    BeanWithArray bean = new BeanWithArray();
    doSerializerTest(
      "<BeanWithArray>\n" +
      "  <option name=\"ARRAY_V\">\n" +
      "    <array>\n" +
      "      <option value=\"a\" />\n" +
      "      <option value=\"b\" />\n" +
      "    </array>\n" +
      "  </option>\n" +
      "</BeanWithArray>", bean);

    bean.ARRAY_V = new String[] {"1", "2", "3", ""};
    doSerializerTest(
      "<BeanWithArray>\n" +
      "  <option name=\"ARRAY_V\">\n" +
      "    <array>\n" +
      "      <option value=\"1\" />\n" +
      "      <option value=\"2\" />\n" +
      "      <option value=\"3\" />\n" +
      "      <option value=\"\" />\n" +
      "    </array>\n" + "  </option>\n" +
      "</BeanWithArray>", bean);
  }

  public static class BeanWithTransient {
    @Transient
    public int INT_V = 1;

    @Transient
    public String getValue() {
      return "foo";
    }
  }
  public void testTransient() {
    final BeanWithTransient bean = new BeanWithTransient();
    doSerializerTest("<BeanWithTransient />", bean);
  }

  public static class BeanWithArrayWithoutTagName {
    @AbstractCollection(surroundWithTag = false)
    public String[] V = new String[]{"a"};
  }

  public void testArrayAnnotationWithoutTagNAmeGivesError() {
    final BeanWithArrayWithoutTagName bean = new BeanWithArrayWithoutTagName();

    try {
      doSerializerTest("<BeanWithArrayWithoutTagName><option name=\"V\"><option value=\"a\"/></option></BeanWithArrayWithoutTagName>", bean);
    }
    catch (XmlSerializationException e) {
      return;
    }

    fail("No Exception");
  }

  public static class BeanWithArrayWithElementTagName {
    @AbstractCollection(elementTag = "vvalue", elementValueAttribute = "v")
    public String[] V = new String[]{"a", "b"};
  }
  public void testArrayAnnotationWithElementTag() {
    final BeanWithArrayWithElementTagName bean = new BeanWithArrayWithElementTagName();

    doSerializerTest(
      "<BeanWithArrayWithElementTagName>\n" +
      "  <option name=\"V\">\n" +
      "    <array>\n" +
      "      <vvalue v=\"a\" />\n" +
      "      <vvalue v=\"b\" />\n" +
      "    </array>\n" +
      "  </option>\n" +
      "</BeanWithArrayWithElementTagName>",
      bean);

    bean.V = new String[] {"1", "2", "3"};

    doSerializerTest(
      "<BeanWithArrayWithElementTagName>\n" +
      "  <option name=\"V\">\n" +
      "    <array>\n" +
      "      <vvalue v=\"1\" />\n" +
      "      <vvalue v=\"2\" />\n" +
      "      <vvalue v=\"3\" />\n" +
      "    </array>\n" +
      "  </option>\n" +
      "</BeanWithArrayWithElementTagName>", bean);
  }

  public static class BeanWithArrayWithoutTag {
    @AbstractCollection(elementTag = "vvalue", elementValueAttribute = "v", surroundWithTag = false)
    public String[] V = new String[]{"a", "b"};
    public int INT_V = 1;
  }
  public void testArrayWithoutTag() {
    final BeanWithArrayWithoutTag bean = new BeanWithArrayWithoutTag();

    doSerializerTest(
      "<BeanWithArrayWithoutTag>\n" +
      "  <option name=\"V\">\n" +
      "    <vvalue v=\"a\" />\n" +
      "    <vvalue v=\"b\" />\n" +
      "  </option>\n" +
      "  <option name=\"INT_V\" value=\"1\" />\n" +
      "</BeanWithArrayWithoutTag>", bean);

    bean.V = new String[] {"1", "2", "3"};

    doSerializerTest(
      "<BeanWithArrayWithoutTag>\n" +
      "  <option name=\"V\">\n" +
      "    <vvalue v=\"1\" />\n" +
      "    <vvalue v=\"2\" />\n" +
      "    <vvalue v=\"3\" />\n" +
      "  </option>\n" +
      "  <option name=\"INT_V\" value=\"1\" />\n" +
      "</BeanWithArrayWithoutTag>", bean);
  }


  public static class BeanWithPropertyWithoutTagOnPrimitiveValue {
    @Property(surroundWithTag = false)
    public int INT_V = 1;
  }
  public void testPropertyWithoutTagWithPrimitiveType() {
    final BeanWithPropertyWithoutTagOnPrimitiveValue bean = new BeanWithPropertyWithoutTagOnPrimitiveValue();

    try {
      doSerializerTest("<BeanWithFieldWithTagAnnotation><name>hello</name></BeanWithFieldWithTagAnnotation>", bean);
    }
    catch (XmlSerializationException e) {
      return;
    }

    fail("No Exception");
  }

  public static class BeanWithPropertyWithoutTag {
    @Property(surroundWithTag = false)
    public BeanWithPublicFields BEAN1 = new BeanWithPublicFields();
    public int INT_V = 1;
  }
  public void testPropertyWithoutTag() {
    final BeanWithPropertyWithoutTag bean = new BeanWithPropertyWithoutTag();

    doSerializerTest(
      "<BeanWithPropertyWithoutTag>\n" +
      "  <BeanWithPublicFields>\n" +
      "    <option name=\"INT_V\" value=\"1\" />\n" +
      "    <option name=\"STRING_V\" value=\"hello\" />\n" +
      "  </BeanWithPublicFields>\n" +
      "  <option name=\"INT_V\" value=\"1\" />\n" +
      "</BeanWithPropertyWithoutTag>",
      bean);

    bean.INT_V = 2;
    bean.BEAN1.STRING_V = "junk";

    doSerializerTest(
      "<BeanWithPropertyWithoutTag>\n" +
      "  <BeanWithPublicFields>\n" +
      "    <option name=\"INT_V\" value=\"1\" />\n" +
      "    <option name=\"STRING_V\" value=\"junk\" />\n" +
      "  </BeanWithPublicFields>\n" +
      "  <option name=\"INT_V\" value=\"2\" />\n" +
      "</BeanWithPropertyWithoutTag>", bean);
  }


  public static class BeanWithArrayWithoutAllsTag {
    @Property(surroundWithTag = false)
    @AbstractCollection(elementTag = "vvalue", elementValueAttribute = "v", surroundWithTag = false)
    public String[] V = new String[]{"a", "b"};

    public int INT_V = 1;
  }

  public void testArrayWithoutAllTags() {
    final BeanWithArrayWithoutAllsTag bean = new BeanWithArrayWithoutAllsTag();

    doSerializerTest(
      "<BeanWithArrayWithoutAllsTag>\n" +
      "  <vvalue v=\"a\" />\n" +
      "  <vvalue v=\"b\" />\n" +
      "  <option name=\"INT_V\" value=\"1\" />\n" +
      "</BeanWithArrayWithoutAllsTag>", bean);

    bean.INT_V = 2;
    bean.V = new String[] {"1", "2", "3"};

    doSerializerTest(
      "<BeanWithArrayWithoutAllsTag>\n" +
      "  <vvalue v=\"1\" />\n" +
      "  <vvalue v=\"2\" />\n" +
      "  <vvalue v=\"3\" />\n" +
      "  <option name=\"INT_V\" value=\"2\" />\n" +
      "</BeanWithArrayWithoutAllsTag>", bean);
  }

  public static class BeanWithArrayWithoutAllsTag2 {
    @Property(surroundWithTag = false)
    @AbstractCollection(elementTag = "vvalue", elementValueAttribute = "", surroundWithTag = false)
    public String[] V = new String[]{"a", "b"};
    public int INT_V = 1;
  }
  public void testArrayWithoutAllTags2() {
    final BeanWithArrayWithoutAllsTag2 bean = new BeanWithArrayWithoutAllsTag2();

    doSerializerTest(
      "<BeanWithArrayWithoutAllsTag2>\n" +
      "  <vvalue>a</vvalue>\n" +
      "  <vvalue>b</vvalue>\n" +
      "  <option name=\"INT_V\" value=\"1\" />\n" +
      "</BeanWithArrayWithoutAllsTag2>", bean);

    bean.INT_V = 2;
    bean.V = new String[] {"1", "2", "3"};

    doSerializerTest(
      "<BeanWithArrayWithoutAllsTag2>\n" +
      "  <vvalue>1</vvalue>\n" +
      "  <vvalue>2</vvalue>\n" +
      "  <vvalue>3</vvalue>\n" +
      "  <option name=\"INT_V\" value=\"2\" />\n" +
      "</BeanWithArrayWithoutAllsTag2>", bean);
  }

  public void testDeserializeFromFormattedXML() throws Exception {
    String xml = "<BeanWithArrayWithoutAllsTag>\n" +
                 "  <option name=\"INT_V\" value=\"2\"/>\n" +
                 "  <vvalue v=\"1\"/>\n" +
                 "  <vvalue v=\"2\"/>\n" +
                 "  <vvalue v=\"3\"/>\n" +
                 "</BeanWithArrayWithoutAllsTag>";

    BeanWithArrayWithoutAllsTag bean = XmlSerializer.deserialize(JDOMUtil.loadDocument(xml).getRootElement(), BeanWithArrayWithoutAllsTag.class);

    assertEquals(2, bean.INT_V);
    assertEquals("[1, 2, 3]", Arrays.asList(bean.V).toString());
  }


  public static class BeanWithPolymorphicArray {
    @AbstractCollection(elementTypes = {BeanWithPublicFields.class, BeanWithPublicFieldsDescendant.class})
    public BeanWithPublicFields[] V = new BeanWithPublicFields[] {};
  }

  public void testPolymorphicArray() {
    final BeanWithPolymorphicArray bean = new BeanWithPolymorphicArray();

    doSerializerTest(
      "<BeanWithPolymorphicArray>\n" +
      "  <option name=\"V\">\n" +
      "    <array />\n" +
      "  </option>\n" +
      "</BeanWithPolymorphicArray>", bean);

    bean.V = new BeanWithPublicFields[] {new BeanWithPublicFields(), new BeanWithPublicFieldsDescendant(), new BeanWithPublicFields()};

    doSerializerTest(
      "<BeanWithPolymorphicArray>\n" +
      "  <option name=\"V\">\n" +
      "    <array>\n" +
      "      <BeanWithPublicFields>\n" +
      "        <option name=\"INT_V\" value=\"1\" />\n" +
      "        <option name=\"STRING_V\" value=\"hello\" />\n" +
      "      </BeanWithPublicFields>\n" +
      "      <BeanWithPublicFieldsDescendant>\n" +
      "        <option name=\"NEW_S\" value=\"foo\" />\n" +
      "        <option name=\"INT_V\" value=\"1\" />\n" +
      "        <option name=\"STRING_V\" value=\"hello\" />\n" +
      "      </BeanWithPublicFieldsDescendant>\n" +
      "      <BeanWithPublicFields>\n" +
      "        <option name=\"INT_V\" value=\"1\" />\n" +
      "        <option name=\"STRING_V\" value=\"hello\" />\n" +
      "      </BeanWithPublicFields>\n" +
      "    </array>\n" +
      "  </option>\n" +
      "</BeanWithPolymorphicArray>", bean);
  }


  public static class BeanWithPropertiesBoundToAttribute {
    @Attribute( "count")
    public int COUNT = 3;
    @Attribute("name")
    public String name = "James";
    @Attribute("occupation")
    public String occupation;
  }
  public void testBeanWithPrimitivePropertyBoundToAttribute() {
    final BeanWithPropertiesBoundToAttribute bean = new BeanWithPropertiesBoundToAttribute();

    doSerializerTest("<BeanWithPropertiesBoundToAttribute count=\"3\" name=\"James\" />", bean);

    bean.COUNT = 10;
    bean.name = "Bond";

    doSerializerTest("<BeanWithPropertiesBoundToAttribute count=\"10\" name=\"Bond\" />", bean);
  }


  public static class BeanWithPropertyFilter {
    @Property(
      filter = PropertyFilterTest.class
    )
    public String STRING_V = "hello";
  }
  public static class PropertyFilterTest implements SerializationFilter {
    @Override
    public boolean accepts(@NotNull Accessor accessor, @NotNull Object bean) {
      return !accessor.read(bean).equals("skip");
    }
  }
  public void testPropertyFilter() {
    BeanWithPropertyFilter bean = new BeanWithPropertyFilter();

    doSerializerTest(
      "<BeanWithPropertyFilter>\n" +
      "  <option name=\"STRING_V\" value=\"hello\" />\n" +
      "</BeanWithPropertyFilter>", bean);

    bean.STRING_V = "bye";

    doSerializerTest(
      "<BeanWithPropertyFilter>\n" +
      "  <option name=\"STRING_V\" value=\"bye\" />\n" +
      "</BeanWithPropertyFilter>", bean);

    bean.STRING_V = "skip";

    assertSerializer(bean, "<BeanWithPropertyFilter />", null);
  }

  public static class BeanWithJDOMElement {
    public String STRING_V = "hello";
    @Tag("actions")
    public org.jdom.Element actions;
  }

  public void testSerializeJDOMElementField() {
    BeanWithJDOMElement element = new BeanWithJDOMElement();
    element.STRING_V = "a";
    element.actions = new Element("x").addContent(new Element("a")).addContent(new Element("b"));
    assertSerializer(element, "<BeanWithJDOMElement>\n" +
                              "  <option name=\"STRING_V\" value=\"a\" />\n" +
                              "  <actions>\n" +
                              "    <a />\n" +
                              "    <b />\n" +
                              "  </actions>\n" +
                              "</BeanWithJDOMElement>", null);

    element.actions = null;
    assertSerializer(element, "<BeanWithJDOMElement>\n" +
                              "  <option name=\"STRING_V\" value=\"a\" />\n" +
                              "</BeanWithJDOMElement>", null);
  }

  public void testDeserializeJDOMElementField() throws Exception {


    final BeanWithJDOMElement bean = XmlSerializer.deserialize(JDOMUtil.loadDocument(
      "<BeanWithJDOMElement><option name=\"STRING_V\" value=\"bye\"/><actions><action/><action/></actions></BeanWithJDOMElement>"
    ).getRootElement(), BeanWithJDOMElement.class);


    assertEquals("bye", bean.STRING_V);
    assertNotNull(bean.actions);
    assertEquals(2, bean.actions.getChildren("action").size());
  }

  public static class BeanWithJDOMElementArray {
    public String STRING_V = "hello";
    @Tag("actions")
    public org.jdom.Element[] actions;
  }

  public void testJDOMElementArrayField() throws Exception {
    String text = "<BeanWithJDOMElementArray>\n" +
                  "  <option name=\"STRING_V\" value=\"bye\" />\n" +
                  "  <actions>\n" +
                  "    <action />\n" +
                  "    <action />\n" +
                  "  </actions>\n" +
                  "  <actions>\n" +
                  "    <action />\n" +
                  "  </actions>\n" +
                  "</BeanWithJDOMElementArray>";
    final BeanWithJDOMElementArray bean = XmlSerializer.deserialize(JDOMUtil.loadDocument(text
    ).getRootElement(), BeanWithJDOMElementArray.class);


    assertEquals("bye", bean.STRING_V);
    assertNotNull(bean.actions);
    assertEquals(2, bean.actions.length);
    assertEquals(2, bean.actions[0].getChildren().size());
    assertEquals(1, bean.actions[1].getChildren().size());

    assertSerializer(bean, text, null);

    bean.actions = null;
    String newText = "<BeanWithJDOMElementArray>\n" +
                     "  <option name=\"STRING_V\" value=\"bye\" />\n" +
                     "</BeanWithJDOMElementArray>";
    doSerializerTest(newText, bean);

    bean.actions = new Element[0];
    doSerializerTest(newText, bean);
  }

  public static class BeanWithTextAnnotation {
    public int INT_V = 1;
    @Text
    public String STRING_V = "hello";

    public BeanWithTextAnnotation(final int INT_V, final String STRING_V) {
      this.INT_V = INT_V;
      this.STRING_V = STRING_V;
    }

    public BeanWithTextAnnotation() {
    }
  }

  public void testTextAnnotation() {
    BeanWithTextAnnotation bean = new BeanWithTextAnnotation();

    doSerializerTest(
      "<BeanWithTextAnnotation>\n" +
      "  <option name=\"INT_V\" value=\"1\" />\n" +
      "  hello\n" +
      "</BeanWithTextAnnotation>", bean);

    bean.INT_V = 2;
    bean.STRING_V = "bye";

    doSerializerTest(
      "<BeanWithTextAnnotation>\n" +
      "  <option name=\"INT_V\" value=\"2\" />\n" +
      "  bye\n" +
      "</BeanWithTextAnnotation>", bean);
  }


  public static enum TestEnum {
    VALUE_1,
    VALUE_2,
    VALUE_3;
  }

  public static class BeanWithEnum {
    public TestEnum FLD = TestEnum.VALUE_1;
  }

  public void testEnums() {
    BeanWithEnum bean = new BeanWithEnum();

    doSerializerTest(
      "<BeanWithEnum>\n" + "  <option name=\"FLD\" value=\"VALUE_1\" />\n" + "</BeanWithEnum>", bean);

    bean.FLD = TestEnum.VALUE_3;

    doSerializerTest(
      "<BeanWithEnum>\n" + "  <option name=\"FLD\" value=\"VALUE_3\" />\n" + "</BeanWithEnum>", bean);
  }

  public static class BeanWithSetKeysInMap {
    public Map<Collection<String>, String> myMap = new LinkedHashMap<Collection<String>, String>();
  }

  public void testSetKeysInMap() {
    final BeanWithSetKeysInMap bean = new BeanWithSetKeysInMap();
    bean.myMap.put(new LinkedHashSet<String>(Arrays.asList("a", "b", "c")), "letters");
    bean.myMap.put(new LinkedHashSet<String>(Arrays.asList("1", "2", "3")), "numbers");

    BeanWithSetKeysInMap bb = doSerializerTest(
      "<BeanWithSetKeysInMap>\n" +
      "  <option name=\"myMap\">\n" +
      "    <map>\n" +
      "      <entry value=\"letters\">\n" +
      "        <key>\n" +
      "          <set>\n" +
      "            <option value=\"a\" />\n" +
      "            <option value=\"b\" />\n" +
      "            <option value=\"c\" />\n" +
      "          </set>\n" +
      "        </key>\n" +
      "      </entry>\n" +
      "      <entry value=\"numbers\">\n" +
      "        <key>\n" +
      "          <set>\n" +
      "            <option value=\"1\" />\n" +
      "            <option value=\"2\" />\n" +
      "            <option value=\"3\" />\n" +
      "          </set>\n" +
      "        </key>\n" +
      "      </entry>\n" +
      "    </map>\n" +
      "  </option>\n" +
      "</BeanWithSetKeysInMap>",
      bean);

    for (Collection<String> collection : bb.myMap.keySet()) {
      assertTrue(collection instanceof Set);
    }
  }

  public static class ConversionFromTextToAttributeBean {
    @Property(surroundWithTag = false)
    public ConditionBean myConditionBean = new ConditionBean();
  }
  @Tag("condition")
  public static class ConditionBean {
    @Attribute("expression")
    public String myNewCondition;
    @Text
    public String myOldCondition;
  }

  public void testConversionFromTextToAttribute() {
    ConversionFromTextToAttributeBean bean = new ConversionFromTextToAttributeBean();
    bean.myConditionBean.myOldCondition = "2+2";
    doSerializerTest("<ConversionFromTextToAttributeBean>\n" +
                     "  <condition>2+2</condition>\n" +
                     "</ConversionFromTextToAttributeBean>", bean);

    bean = new ConversionFromTextToAttributeBean();
    bean.myConditionBean.myNewCondition = "2+2";
    doSerializerTest("<ConversionFromTextToAttributeBean>\n" +
                     "  <condition expression=\"2+2\" />\n" +
                     "</ConversionFromTextToAttributeBean>", bean);
  }

  public void testDeserializeInto() throws Exception {
    BeanWithPublicFields bean = new BeanWithPublicFields();
    bean.STRING_V = "zzz";

    String xml = "<BeanWithPublicFields><option name=\"INT_V\" value=\"999\"/></BeanWithPublicFields>";
    XmlSerializer.deserializeInto(bean, JDOMUtil.loadDocument(xml).getRootElement());

    assertEquals(999, bean.INT_V);
    assertEquals("zzz", bean.STRING_V);
  }

  public static class BeanWithMapWithoutSurround {
    @Tag("map")
    @MapAnnotation(surroundWithTag = false, entryTagName = "pair", surroundKeyWithTag = false, surroundValueWithTag = false)
    public Map<BeanWithPublicFields, BeanWithTextAnnotation> MAP = new LinkedHashMap<BeanWithPublicFields, BeanWithTextAnnotation>();
  }

  public void testMapWithNotSurroundingKeyAndValue() {
    BeanWithMapWithoutSurround bean = new BeanWithMapWithoutSurround();

    bean.MAP.put(new BeanWithPublicFields(1, "a"), new BeanWithTextAnnotation(2, "b"));
    bean.MAP.put(new BeanWithPublicFields(3, "c"), new BeanWithTextAnnotation(4, "d"));
    bean.MAP.put(new BeanWithPublicFields(5, "e"), new BeanWithTextAnnotation(6, "f"));

    doSerializerTest(
      "<BeanWithMapWithoutSurround>\n" +
      "  <map>\n" +
      "    <pair>\n" +
      "      <BeanWithPublicFields>\n" +
      "        <option name=\"INT_V\" value=\"1\" />\n" +
      "        <option name=\"STRING_V\" value=\"a\" />\n" +
      "      </BeanWithPublicFields>\n" +
      "      <BeanWithTextAnnotation>\n" +
      "        <option name=\"INT_V\" value=\"2\" />\n" +
      "        b\n" +
      "      </BeanWithTextAnnotation>\n" +
      "    </pair>\n" + "    <pair>\n" +
      "      <BeanWithPublicFields>\n" +
      "        <option name=\"INT_V\" value=\"3\" />\n" +
      "        <option name=\"STRING_V\" value=\"c\" />\n" +
      "      </BeanWithPublicFields>\n" +
      "      <BeanWithTextAnnotation>\n" +
      "        <option name=\"INT_V\" value=\"4\" />\n" +
      "        d\n" +
      "      </BeanWithTextAnnotation>\n" +
      "    </pair>\n" +
      "    <pair>\n" +
      "      <BeanWithPublicFields>\n" +
      "        <option name=\"INT_V\" value=\"5\" />\n" +
      "        <option name=\"STRING_V\" value=\"e\" />\n" +
      "      </BeanWithPublicFields>\n" +
      "      <BeanWithTextAnnotation>\n" +
      "        <option name=\"INT_V\" value=\"6\" />\n" +
      "        f\n" +
      "      </BeanWithTextAnnotation>\n" +
      "    </pair>\n" +
      "  </map>\n" +
      "</BeanWithMapWithoutSurround>",
      bean);
  }

  public static class BeanWithMapAtTopLevel {
    @Property(surroundWithTag = false)
    @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false)
    public Map<String, String> map = new LinkedHashMap<String, String>();

    public String option;
  }

  public void testMapAtTopLevel() {
    BeanWithMapAtTopLevel bean = new BeanWithMapAtTopLevel();
    bean.map.put("a", "b");
    bean.option = "xxx";
    doSerializerTest("<BeanWithMapAtTopLevel>\n" +
                     "  <entry key=\"a\" value=\"b\" />\n" +
                     "  <option name=\"option\" value=\"xxx\" />\n" +
                     "</BeanWithMapAtTopLevel>", bean);
  }

  private static class BeanWithConverter {
    private static class MyConverter extends Converter<Ref<String>> {
      @Nullable
      @Override
      public Ref<String> fromString(@NotNull String value) {
        return Ref.create(value);
      }

      @NotNull
      @Override
      public String toString(@NotNull Ref<String> o) {
        return StringUtil.notNullize(o.get());
      }
    }

    @Attribute(converter = MyConverter.class)
    public Ref<String> foo;

    @OptionTag(converter = MyConverter.class)
    public Ref<String> bar;
  }

  public void testConverter() {
    BeanWithConverter bean = new BeanWithConverter();
    doSerializerTest("<BeanWithConverter>\n" +
                     "  <option name=\"bar\" />\n" +
                     "</BeanWithConverter>", bean);

    bean.foo = Ref.create("testValue");
    doSerializerTest("<BeanWithConverter foo=\"testValue\">\n" +
                     "  <option name=\"bar\" />\n" +
                     "</BeanWithConverter>", bean);

    bean.foo = Ref.create();
    bean.bar = Ref.create("testValue2");
    doSerializerTest("<BeanWithConverter foo=\"\">\n" +
                     "  <option name=\"bar\" value=\"testValue2\" />\n" +
                     "</BeanWithConverter>", bean);
  }

  public void testConverterUsingSkipDefaultsFilter() {
    BeanWithConverter bean = new BeanWithConverter();
    doSerializerTest("<BeanWithConverter />", bean, new SkipDefaultsSerializationFilter());

    bean.foo = Ref.create("testValue");
    doSerializerTest("<BeanWithConverter foo=\"testValue\" />", bean, new SkipDefaultsSerializationFilter());

    bean.foo = Ref.create();
    bean.bar = Ref.create("testValue2");
    doSerializerTest("<BeanWithConverter foo=\"\">\n" +
                     "  <option name=\"bar\" value=\"testValue2\" />\n" +
                     "</BeanWithConverter>", bean);
  }

  private static class BeanWithDefaultAttributeName {
    @Attribute
    public String getFoo() {
      return "foo";
    }

    public void setFoo(@SuppressWarnings("UnusedParameters") String value) {
    }
  }

  public void testDefaultAttributeName() {
    BeanWithDefaultAttributeName bean = new BeanWithDefaultAttributeName();
    doSerializerTest("<BeanWithDefaultAttributeName foo=\"foo\" />", bean);
  }

  static class Bean2 {
    @Attribute
    public String ab;

    @Attribute
    public String module;

    @Attribute
    public String ac;
  }

  public void testOrdered() throws IOException, JDOMException {
    Bean2 bean = new Bean2();
    bean.module = "module";
    bean.ab = "ab";
    doSerializerTest("<Bean2 ab=\"ab\" module=\"module\" />", bean, new SkipDefaultsSerializationFilter());

    checkSmartSerialization(new Bean2(), "<Bean2 module=\"1\" ab=\"2\" ac=\"32\" />");
    checkSmartSerialization(new Bean2(), "<Bean2 ab=\"2\" module=\"1\" ac=\"32\" />");
    checkSmartSerialization(new Bean2(), "<Bean2 ac=\"2\" module=\"1\" ab=\"32\" />");
    checkSmartSerialization(new Bean2(), "<Bean2 ac=\"2\" ab=\"32\" />");
    checkSmartSerialization(new Bean2(), "<Bean2 ac=\"2\" ab=\"32\" module=\"\" />");
  }

  @SuppressWarnings("deprecation")
  @Tag("b")
  static class Bean3 {
    public JDOMExternalizableStringList list = new JDOMExternalizableStringList();
  }

  @Tag("b")
  static class Bean4 {
    @CollectionBean
    public final List<String> list = new SmartList<String>();
  }

  @SuppressWarnings("deprecation")
  public void testJDOMExternalizableStringList() throws IOException, JDOMException {
    Bean3 bean = new Bean3();
    bean.list.add("one");
    bean.list.add("two");
    bean.list.add("three");
    doSerializerTest("<b>\n" +
                     "  <list>\n" +
                     "    <item value=\"one\" />\n" +
                     "    <item value=\"two\" />\n" +
                     "    <item value=\"three\" />\n" +
                     "  </list>\n" +
                     "</b>", bean, new SkipDefaultsSerializationFilter());
  }

  public void testCollectionBean() throws IOException, JDOMException {
    Bean4 bean = new Bean4();
    bean.list.add("one");
    bean.list.add("two");
    bean.list.add("three");
    doSerializerTest("<b>\n" +
                     "  <list>\n" +
                     "    <item value=\"one\" />\n" +
                     "    <item value=\"two\" />\n" +
                     "    <item value=\"three\" />\n" +
                     "  </list>\n" +
                     "</b>", bean, new SkipDefaultsSerializationFilter());
  }

  public void testCollectionBeanReadJDOMExternalizableStringList() throws IOException, JDOMException {
    @SuppressWarnings("deprecation")
    JDOMExternalizableStringList list = new JDOMExternalizableStringList();
    list.add("one");
    list.add("two");
    list.add("three");

    Element value = new Element("value");
    list.writeExternal(value);
    Bean4 o = XmlSerializer.deserialize(new Element("state").addContent(new Element("option").setAttribute("name", "myList").addContent(value)), Bean4.class);
    assertSerializer(o, "<b>\n" +
                        "  <list>\n" +
                        "    <item value=\"one\" />\n" +
                        "    <item value=\"two\" />\n" +
                        "    <item value=\"three\" />\n" +
                        "  </list>\n" +
                        "</b>", "Deserialization failure", new SkipDefaultsSerializationFilter());
  }

  private static void checkSmartSerialization(@NotNull Bean2 bean, @NotNull String serialized) throws IOException, JDOMException {
    SmartSerializer serializer = new SmartSerializer();
    serializer.readExternal(bean, JDOMUtil.loadDocument(serialized).getRootElement());
    Element serializedState = new Element("Bean2");
    serializer.writeExternal(bean, serializedState);
    assertEquals(serialized, JDOMUtil.writeElement(serializedState));
  }

  //---------------------------------------------------------------------------------------------------
  private static Element assertSerializer(Object bean, String expected, SerializationFilter filter) {
    return assertSerializer(bean, expected, "Serialization failure", filter);
  }

  private static <T> T doSerializerTest(@Language("XML") String expectedText, T bean) {
    return doSerializerTest(expectedText, bean, null);
  }

  private static <T> T doSerializerTest(@Language("XML") String expectedText, T bean, @Nullable SerializationFilter filter) {
    Element element = assertSerializer(bean, expectedText, filter);

    //test deserializer
    @SuppressWarnings("unchecked")
    Class<T> aClass = (Class<T>)bean.getClass();
    T o = XmlSerializer.deserialize(element, aClass);
    assertSerializer(o, expectedText, "Deserialization failure", filter);
    return o;
  }

  private static Element assertSerializer(Object bean, String expectedText, String message, SerializationFilter filter) throws XmlSerializationException {
    Element element = serialize(bean, filter);
    String actualString = JDOMUtil.writeElement(element, "\n").trim();
    if (!expectedText.startsWith(XML_PREFIX)) {
      if (actualString.startsWith(XML_PREFIX)) actualString = actualString.substring(XML_PREFIX.length()).trim();
    }

    assertEquals(message, expectedText, actualString);
    return element;
  }

  public static class BeanWithMapWithSetValue {
    @MapAnnotation(entryTagName = "entry-tag", keyAttributeName = "key-attr", surroundWithTag = false)
    public Map<String, Set<String>> myValues = new LinkedHashMap<String, Set<String>>();
  }

  public void testBeanWithMapWithSetValue() {
    BeanWithMapWithSetValue bean = new BeanWithMapWithSetValue();

    bean.myValues.put("a", new LinkedHashSet<String>(Arrays.asList("first1", "second1")));
    bean.myValues.put("b", new LinkedHashSet<String>(Arrays.asList("first2", "second2")));

    doSerializerTest(
      "<BeanWithMapWithSetValue>\n" +
      "  <option name=\"myValues\">\n" +
      "    <entry-tag key-attr=\"a\">\n" +
      "      <value>\n" +
      "        <set>\n" +
      "          <option value=\"first1\" />\n" +
      "          <option value=\"second1\" />\n" +
      "        </set>\n" +
      "      </value>\n" +
      "    </entry-tag>\n" +
      "    <entry-tag key-attr=\"b\">\n" +
      "      <value>\n" +
      "        <set>\n" +
      "          <option value=\"first2\" />\n" +
      "          <option value=\"second2\" />\n" +
      "        </set>\n" +
      "      </value>\n" +
      "    </entry-tag>\n" +
      "  </option>\n" +
      "</BeanWithMapWithSetValue>",
      bean);
  }

  private static Element serialize(Object bean, SerializationFilter filter) {
    return XmlSerializer.serialize(bean, filter);
  }
}
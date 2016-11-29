/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.util.config;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.Assertion;
import com.intellij.util.NewInstanceFactory;
import junit.framework.TestCase;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.List;

public class ExternalizablePropertyTest extends TestCase {
  private final ExternalizablePropertyContainer myContainer = new ExternalizablePropertyContainer();
  private static final ListProperty<MockJDOMExternalizable> PROPERTY =
    new ListProperty<>("list");
  private final Assertion CHECK = new Assertion();
  private static final Externalizer.FactoryBased<MockJDOMExternalizable> EXTERNALIZER = Externalizer.FactoryBased.create(NewInstanceFactory.fromClass(MockJDOMExternalizable.class));

  public void testListProperty() {
    assertFalse(PROPERTY.getIterator(myContainer).hasNext());
    List defaultValue = PROPERTY.get(myContainer);
    assertEquals(0, defaultValue.size());
    ArrayList modifiableList = PROPERTY.getModifiableList(myContainer);
    assertSame(modifiableList, PROPERTY.get(myContainer));
    assertEquals(0, modifiableList.size());
    MockJDOMExternalizable item = new MockJDOMExternalizable();
    modifiableList.add(item);
    assertSame(item, PROPERTY.getIterator(myContainer).next());
  }

  public void testReadList() throws InvalidDataException {
    Element containerElement = new Element("xxx");
    Element listElement = new Element("list");
    containerElement.addContent(listElement);
    listElement.addContent(new Element("unknown"));
    listElement.addContent(new Element("item"));
    listElement.addContent(new Element("unknown"));
    myContainer.registerProperty(PROPERTY, "item", EXTERNALIZER);
    myContainer.readExternal(containerElement);
    List<MockJDOMExternalizable> list = PROPERTY.get(myContainer);
    assertEquals(1, list.size());
  }

  public void testDoesntWriteUnknownProperty() throws WriteExternalException {
    StringProperty property = new StringProperty("unknown", "");
    property.set(myContainer, "abc");
    assertEquals("abc", property.get(myContainer));

    Element element = new Element("element");
    myContainer.writeExternal(element);
    CHECK.size(0, element.getChildren());
  }

  public void testWriteList() throws WriteExternalException {
    myContainer.registerProperty(PROPERTY, "item", EXTERNALIZER);
    PROPERTY.getModifiableList(myContainer).add(new MockJDOMExternalizable());
    Element containerElement = new Element("xxx");
    myContainer.writeExternal(containerElement);

    List children = containerElement.getChildren();
    assertEquals(1, children.size());
    Element listElement = (Element)children.get(0);
    assertEquals("list", listElement.getName());
    children = listElement.getChildren();
    assertEquals(1, children.size());
    Element itemElement = (Element)children.get(0);
    assertEquals("item", itemElement.getName());
  }

  public static class MockJDOMExternalizable implements JDOMExternalizable {
    @Override
    public void readExternal(Element element) throws InvalidDataException {
    }

    @Override
    public void writeExternal(Element element) throws WriteExternalException {
    }
  }
}

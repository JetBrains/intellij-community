/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ReflectionUtil;
import org.jdom.Element;
import org.jdom.IllegalDataException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@SuppressWarnings({"HardCodedStringLiteral"})
@Deprecated
public class JDOMExternalizableStringList extends ArrayList<String> implements JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.JDOMExternalizableStringList");

  private static final String ATTR_LIST = "list";
  private static final String ATTR_LISTSIZE = "size";
  private static final String ATTR_ITEM = "item";
  private static final String ATTR_INDEX = "index";
  private static final String ATTR_CLASS = "class";
  private static final String ATTR_VALUE = "itemvalue";

  public JDOMExternalizableStringList(int initialCapacity) {
    super(initialCapacity);
  }

  public JDOMExternalizableStringList() {
  }

  public JDOMExternalizableStringList(@NotNull Collection<String> c) {
    super(c);
  }

  @Override
  public void readExternal(Element element) {
    readList(this, element);
  }

  public static void readList(@NotNull List<String> strings, Element element) {
    strings.clear();

    Class callerClass = null;
    for (Element listElement : element.getChildren(ATTR_LIST)) {
      if (callerClass == null) {
        callerClass = ReflectionUtil.findCallerClass(2);
        assert callerClass != null;
      }
      final ClassLoader classLoader = callerClass.getClassLoader();
      for (Element listItemElement : listElement.getChildren(ATTR_ITEM)) {
        if (!ATTR_ITEM.equals(listItemElement.getName())) {
          throw new IllegalDataException("Unable to read list item. Unknown element found: " + listItemElement.getName());
        }
        String itemClassString = listItemElement.getAttributeValue(ATTR_CLASS);
        Class itemClass;
        try {
          itemClass = itemClassString == null ? String.class : Class.forName(itemClassString, true, classLoader);
        }
        catch (ClassNotFoundException ex) {
          throw new IllegalDataException("Unable to read list item: unable to load class: " + itemClassString + " \n" + ex.getMessage());
        }

        String listItem = listItemElement.getAttributeValue(ATTR_VALUE);

        LOG.assertTrue(String.class.equals(itemClass));

        strings.add(listItem);
      }
    }
  }

  @Override
  public void writeExternal(Element element) {
    writeList(this, element);
  }

  private static void writeList(@NotNull List<String> strings, @NotNull Element element) {
    int listSize = strings.size();
    Element listElement = new Element(ATTR_LIST);
    listElement.setAttribute(ATTR_LISTSIZE, Integer.toString(listSize));
    element.addContent(listElement);
    for (int i = 0; i < listSize; i++) {
      String listItem = strings.get(i);
      if (listItem != null) {
        Element itemElement = new Element(ATTR_ITEM);
        itemElement.setAttribute(ATTR_INDEX, Integer.toString(i));
        itemElement.setAttribute(ATTR_CLASS, listItem.getClass().getName());
        itemElement.setAttribute(ATTR_VALUE, DefaultJDOMExternalizer.filterXMLCharacters(listItem));
        listElement.addContent(itemElement);
      }
    }
  }
}

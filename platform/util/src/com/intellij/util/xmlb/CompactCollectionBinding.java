// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.serialization.MutableAccessor;
import com.intellij.util.xml.dom.XmlElement;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @see com.intellij.util.xmlb.annotations.CollectionBean
 */
final class CompactCollectionBinding extends NotNullDeserializeBinding implements NestedBinding {
  private final String name;
  private final MutableAccessor accessor;

  CompactCollectionBinding(@NotNull MutableAccessor accessor) {
    this.accessor = accessor;
    name = accessor.getName();
  }

  @Override
  public @NotNull MutableAccessor getAccessor() {
    return accessor;
  }

  @Override
  public @NotNull Object serialize(@NotNull Object o, @Nullable Object context, @Nullable SerializationFilter filter) {
    Element result = new Element(name);
    @SuppressWarnings("unchecked")
    List<String> list = (List<String>)o;
    if (list.isEmpty()) {
      return result;
    }

    for (String item : list) {
      result.addContent(new Element("item").setAttribute("value", JDOMUtil.removeControlChars(item)));
    }
    return result;
  }

  @Override
  public @NotNull Object deserialize(Object context, @NotNull Element element) {
    @SuppressWarnings("unchecked")
    List<String> list = (List<String>)context;
    list.clear();
    if (element.getName().equals(name)) {
      for (Element item : element.getChildren("item")) {
        ContainerUtil.addIfNotNull(list, item.getAttributeValue("value"));
      }
    }
    else {
      // JDOMExternalizableStringList format
      Element value = element.getChild("value");
      if (value != null) {
        value = value.getChild("list");
      }
      if (value != null) {
        for (Element item : value.getChildren("item")) {
          ContainerUtil.addIfNotNull(list, item.getAttributeValue("itemvalue"));
        }
      }
    }
    return list;
  }

  @Override
  public @NotNull Object deserialize(Object context, @NotNull XmlElement element) {
    @SuppressWarnings("unchecked")
    List<String> list = (List<String>)context;
    list.clear();
    if (element.name.equals(name)) {
      for (XmlElement item : element.children) {
        if (item.name.equals("item")) {
          String v = item.attributes.get("value");
          if (v != null) {
            list.add(v);
          }
        }
      }
    }
    else {
      // JDOMExternalizableStringList format
      XmlElement value = element.getChild("value");
      if (value != null) {
        value = value.getChild("list");
      }
      if (value != null) {
        for (XmlElement item : value.children) {
          if (item.name.equals("item")) {
            String v = item.getAttributeValue("itemvalue");
            if (v != null) {
              list.add(v);
            }
          }
        }
      }
    }
    return list;
  }

  @Override
  public boolean isBoundTo(@NotNull Element element) {
    String elementName = element.getName();
    if (isNameEqual(elementName)) {
      return true;
    }
    else if (elementName.equals(Constants.OPTION)) {
      // JDOMExternalizableStringList format
      return isNameEqual(element.getAttributeValue(Constants.NAME));
    }
    return false;
  }

  @Override
  public boolean isBoundTo(@NotNull XmlElement element) {
    String elementName = element.name;
    if (isNameEqual(elementName)) {
      return true;
    }
    else if (elementName.equals(Constants.OPTION)) {
      // JDOMExternalizableStringList format
      return isNameEqual(element.getAttributeValue(Constants.NAME));
    }
    return false;
  }

  private boolean isNameEqual(@Nullable String value) {
    if (value == null) {
      return false;
    }
    else if (value.equals(name)) {
      return true;
    }
    else if (value.length() == (name.length() + 2) && value.startsWith("my")) {
      return Character.isUpperCase(value.charAt(2)) && value.regionMatches(true, 2, name, 0, name.length());
    }
    else {
      return false;
    }
  }
}

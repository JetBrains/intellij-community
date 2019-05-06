// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @see com.intellij.util.xmlb.annotations.CollectionBean
 */
class CompactCollectionBinding extends NotNullDeserializeBinding {
  private final String name;

  protected CompactCollectionBinding(@NotNull MutableAccessor accessor) {
    super(accessor);

    name = myAccessor.getName();
  }

  @Nullable
  @Override
  public Object serialize(@NotNull Object o, @Nullable Object context, @Nullable SerializationFilter filter) {
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

  @NotNull
  @Override
  public Object deserialize(Object context, @NotNull Element element) {
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

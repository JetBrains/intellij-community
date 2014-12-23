/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("deprecation")
class JDOMExternalizableStringListBinding extends Binding {
  private final String myName;

  protected JDOMExternalizableStringListBinding(@NotNull Accessor accessor) {
    super(accessor);

    myName = myAccessor.getName();
  }

  @Nullable
  @Override
  public Object serialize(Object o, @Nullable Object context, SerializationFilter filter) {
    Element result = new Element(myName);
    JDOMExternalizableStringList list = (JDOMExternalizableStringList)o;
    if (list == null || list.isEmpty()) {
      return result;
    }

    for (String item : list) {
      result.addContent(new Element("item").setAttribute("value", item));
    }
    return result;
  }

  @Nullable
  @Override
  public Object deserialize(Object context, @NotNull Object node) {
    JDOMExternalizableStringList list = (JDOMExternalizableStringList)context;
    list.clear();
    for (Element item : ((Element)node).getChildren("item")) {
      ContainerUtil.addIfNotNull(list, item.getAttributeValue("value"));
    }
    return list;
  }

  @Override
  public boolean isBoundTo(Object node) {
    return node instanceof Element && ((Element)node).getName().equals(myName);
  }

  @Override
  public Class getBoundNodeType() {
    return Element.class;
  }
}

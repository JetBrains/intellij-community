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

package com.intellij.util.xmlb;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import org.jdom.Attribute;
import org.jdom.Content;
import org.jdom.Element;
import org.jdom.Text;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;

import static com.intellij.util.xmlb.Constants.*;

class MapBinding implements Binding {
  private final Binding myKeyBinding;
  private final Binding myValueBinding;
  private final MapAnnotation myMapAnnotation;
  private static final Comparator<Object> KEY_COMPARATOR = new Comparator<Object>() {
    public int compare(final Object o1, final Object o2) {
      if (o1 instanceof Comparable && o2 instanceof Comparable) {
        Comparable c1 = (Comparable)o1;
        Comparable c2 = (Comparable)o2;
        return c1.compareTo(c2);
      }

      return 0;
    }
  };


  public MapBinding(ParameterizedType type, Accessor accessor) {
    Type[] arguments = type.getActualTypeArguments();
    Type keyType = arguments[0];
    Type valueType = arguments[1];

    myKeyBinding = XmlSerializerImpl.getBinding(keyType);
    myValueBinding = XmlSerializerImpl.getBinding(valueType);
    myMapAnnotation = XmlSerializerImpl.findAnnotation(accessor.getAnnotations(), MapAnnotation.class);
  }

  public Object serialize(Object o, Object context, SerializationFilter filter) {
    Map map = (Map)o;

    Element m;

    if (myMapAnnotation == null || myMapAnnotation.surroundWithTag()) {
      m = new Element(Constants.MAP);
    }
    else {
      m = (Element)context;
    }

    final Set keySet = map.keySet();
    final Object[] keys = ArrayUtil.toObjectArray(keySet);

    if (myMapAnnotation == null || myMapAnnotation.sortBeforeSave()) {
      Arrays.sort(keys, KEY_COMPARATOR);
    }

    for (Object k : keys) {
      Object v = map.get(k);

      Element entry = new Element(getEntryAttributeName());
      m.addContent(entry);

      Object kNode = myKeyBinding.serialize(k, entry, filter);

      if (kNode instanceof Text) {
        Text text = (Text)kNode;
        entry.setAttribute(getKeyAttributeValue(), text.getText());
      }
      else {
        if (myMapAnnotation != null && !myMapAnnotation.surroundKeyWithTag()) {
          entry.addContent((Content)kNode);
        }
        else {
          Element key = new Element(getKeyAttributeValue());
          entry.addContent(key);
          key.addContent((Content)kNode);
        }
      }

      Object vNode = myValueBinding.serialize(v, entry, filter);
      if (vNode instanceof Text) {
        Text text = (Text)vNode;
        entry.setAttribute(getValueAttributeName(), text.getText());
      }
      else {
        if (myMapAnnotation != null && !myMapAnnotation.surroundValueWithTag()) {
          entry.addContent((Element)vNode);
        }
        else {
          Element value = new Element(getValueAttributeName());
          entry.addContent(value);
          value.addContent((Content)vNode);
        }
      }
    }

    return m;
  }

  private String getEntryAttributeName() {
    return myMapAnnotation == null ? ENTRY : myMapAnnotation.entryTagName();
  }

  private String getValueAttributeName() {
    return myMapAnnotation == null ? VALUE : myMapAnnotation.valueAttributeName();
  }

  private String getKeyAttributeValue() {
    return myMapAnnotation == null ? KEY : myMapAnnotation.keyAttributeName();
  }

  public Object deserialize(Object o, @NotNull Object... nodes) {
    Map map = (Map)o;
    map.clear();

    final Object[] childNodes;

    if (myMapAnnotation == null || myMapAnnotation.surroundWithTag()) {
      assert nodes.length == 1;
      Element m = (Element)nodes[0];
      childNodes = JDOMUtil.getContent(m);
    }
    else {
      childNodes = nodes;
    }


    for (Object childNode : childNodes) {
      if (XmlSerializerImpl.isIgnoredNode(childNode)) continue;
      
      Element entry = (Element)childNode;

      Object k = null;
      Object v = null;

      assert entry.getName().equals(getEntryAttributeName());

      Attribute keyAttr = entry.getAttribute(getKeyAttributeValue());
      if (keyAttr != null) {
        k = myKeyBinding.deserialize(o, keyAttr);
      }
      else {
        if (myMapAnnotation != null && !myMapAnnotation.surroundKeyWithTag()) {
          final Object[] children = JDOMUtil.getContent(entry);
          for (Object child : children) {
            if (myKeyBinding.isBoundTo(child)) {
              k = myKeyBinding.deserialize(o, child);
              break;
            }
          }

          assert k != null : "no key found";
        }
        else {
          final Object keyNode = entry.getChildren(getKeyAttributeValue()).get(0);
          k = myKeyBinding.deserialize(o, JDOMUtil.getContent((Element)keyNode));
        }
      }

      Attribute valueAttr = entry.getAttribute(getValueAttributeName());
      if (valueAttr != null) {
        v = myValueBinding.deserialize(o, valueAttr);
      }
      else {
        if (myMapAnnotation != null && !myMapAnnotation.surroundValueWithTag()) {
          final Object[] children = JDOMUtil.getContent(entry);
          for (Object child : children) {
            if (myValueBinding.isBoundTo(child)) {
              v = myValueBinding.deserialize(o, child);
              break;
            }
          }

          assert v != null : "no value found";
        }
        else {
          final Object valueNode = entry.getChildren(getValueAttributeName()).get(0);
          v = myValueBinding.deserialize(o, XmlSerializerImpl.getNotIgnoredContent((Element)valueNode));
        }
      }

      //noinspection unchecked
      map.put(k, v);
    }

    return map;
  }

  public boolean isBoundTo(Object node) {
    if (!(node instanceof Element)) return false;

    if (myMapAnnotation != null && !myMapAnnotation.surroundWithTag()) {
      return myMapAnnotation.entryTagName().equals(((Element)node).getName());
    }

    return ((Element)node).getName().equals(Constants.MAP);
  }

  public Class getBoundNodeType() {
    return Element.class;
  }

  public void init() {
  }
}

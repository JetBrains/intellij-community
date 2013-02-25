/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.util.xmlb.annotations.AbstractCollection;
import org.jdom.Content;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

abstract class AbstractCollectionBinding implements Binding {
  private Map<Class, Binding> myElementBindings;

  private final Class myElementType;
  private final String myTagName;
  @Nullable protected final Accessor myAccessor;
  private final AbstractCollection myAnnotation;

  public AbstractCollectionBinding(Class elementType, String tagName, @Nullable Accessor accessor) {
    myElementType = elementType;
    myTagName = tagName;
    myAccessor = accessor;
    myAnnotation = accessor == null ? null : XmlSerializerImpl.findAnnotation(accessor.getAnnotations(), AbstractCollection.class);
  }

  public void init() {
    if (myAnnotation != null) {
      if (!myAnnotation.surroundWithTag()) {
        if (myAnnotation.elementTag() == null) {
          throw new XmlSerializationException("If surround with tag is turned off, element tag must be specified for: " + myAccessor);
        }

        if (myAnnotation.elementTag().equals(Constants.OPTION)) {
          for (Binding binding : getElementBindings().values()) {
            if (binding instanceof TagBindingWrapper) {
              throw new XmlSerializationException("If surround with tag is turned off, element tag must be specified for: " + myAccessor);
            }
          }
        }
      }
    }
  }

  protected Binding getElementBinding(Class<?> elementClass) {
    final Binding binding = getElementBindings().get(elementClass);
    return binding == null ? XmlSerializerImpl.getBinding(elementClass) : binding;
  }

  private synchronized Map<Class, Binding> getElementBindings() {
    if (myElementBindings == null) {
      myElementBindings = new HashMap<Class, Binding>();

      myElementBindings.put(myElementType, getBinding(myElementType));

      if (myAnnotation != null) {
        for (Class aClass : myAnnotation.elementTypes()) {
          myElementBindings.put(aClass, getBinding(aClass));

        }
      }
    }

    return myElementBindings;
  }

  protected Binding getElementBinding(Object node) {
    for (Binding binding : getElementBindings().values()) {
      if (binding.isBoundTo(node)) return binding;
    }
    throw new XmlSerializationException("Node " + node + " is not bound");
  }

  private Binding getBinding(final Class type) {
    Binding binding = XmlSerializerImpl.getBinding(type);
    return binding.getBoundNodeType().isAssignableFrom(Element.class) ? binding : createElementTagWrapper(binding);
  }

  private Binding createElementTagWrapper(final Binding elementBinding) {
    if (myAnnotation == null) return new TagBindingWrapper(elementBinding, Constants.OPTION, Constants.VALUE);

    return new TagBindingWrapper(elementBinding,
                                 myAnnotation.elementTag() != null ? myAnnotation.elementTag() : Constants.OPTION,
                                 myAnnotation.elementValueAttribute() != null ? myAnnotation.elementValueAttribute() : Constants.VALUE);
  }

  abstract Object processResult(Collection result, Object target);
  abstract Iterable getIterable(Object o);

  public Object serialize(Object o, Object context, SerializationFilter filter) {
    Iterable iterable = getIterable(o);
    if (iterable == null) return context;

    final String tagName = getTagName(o);
    if (tagName != null) {
      Element result = new Element(tagName);
      for (Object e : iterable) {
        if (e == null) {
          throw new XmlSerializationException("Collection " + myAccessor + " contains 'null' object");
        }
        final Binding binding = getElementBinding(e.getClass());
        result.addContent((Content)binding.serialize(e, result, filter));
      }

      return result;
    }
    else {
      List<Object> result = new ArrayList<Object>();
      for (Object e : iterable) {
        final Binding binding = getElementBinding(e.getClass());
        result.add(binding.serialize(e, result, filter));
      }

      return result;
    }
  }

  public Object deserialize(Object o, @NotNull Object... nodes) {
    Collection result;

    if (getTagName(o) != null) {
      assert nodes.length == 1;
      Element e = (Element)nodes[0];

      result = createCollection(e.getName());
      final Content[] childElements = JDOMUtil.getContent(e);
      for (final Content n : childElements) {
        if (XmlSerializerImpl.isIgnoredNode(n)) continue;
        final Binding elementBinding = getElementBinding(n);
        Object v = elementBinding.deserialize(o, n);
        //noinspection unchecked
        result.add(v);
      }
    }
    else {
      result = new ArrayList();
      for (Object node : nodes) {
        if (XmlSerializerImpl.isIgnoredNode(node)) continue;
        final Binding elementBinding = getElementBinding(node);
        Object v = elementBinding.deserialize(o, node);
        //noinspection unchecked
        result.add(v);
      }
    }


    return processResult(result, o);
  }

  protected Collection createCollection(final String tagName) {
    return new ArrayList();
  }

  public boolean isBoundTo(Object node) {
    if (!(node instanceof Element)) return false;

    final String tagName = getTagName(node);
    if (tagName == null) {
      for (Binding binding : getElementBindings().values()) {
        if (binding.isBoundTo(node)) return true;
      }
    }

    return ((Element)node).getName().equals(tagName);
  }

  public Class getBoundNodeType() {
    return Element.class;
  }

  public Class getElementType() {
    return myElementType;
  }

  @Nullable
  private String getTagName(final Object target) {
    if (myAnnotation == null || myAnnotation.surroundWithTag()) return getCollectionTagName(target);
    return null;
  }

  protected String getCollectionTagName(final Object target) {
    return myTagName;
  }
}

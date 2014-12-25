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

import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import gnu.trove.THashMap;
import org.jdom.Content;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

abstract class AbstractCollectionBinding extends Binding implements MultiNodeBinding {
  private Map<Class, Binding> myElementBindings;

  private final Class myElementType;
  private final String myTagName;
  private final AbstractCollection myAnnotation;

  public AbstractCollectionBinding(Class elementType, String tagName, @Nullable Accessor accessor) {
    super(accessor);

    myElementType = elementType;
    myTagName = tagName;
    myAnnotation = accessor == null ? null : accessor.getAnnotation(AbstractCollection.class);
  }

  @Override
  public boolean isMulti() {
    return true;
  }

  @Override
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

  protected Binding getElementBinding(@NotNull Class<?> elementClass) {
    final Binding binding = getElementBindings().get(elementClass);
    return binding == null ? XmlSerializerImpl.getBinding(elementClass) : binding;
  }

  private synchronized Map<Class, Binding> getElementBindings() {
    if (myElementBindings == null) {
      myElementBindings = new THashMap<Class, Binding>();
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

  private Binding getBinding(@NotNull Class type) {
    Binding binding = XmlSerializerImpl.getBinding(type);
    //noinspection unchecked
    return binding.getBoundNodeType().isAssignableFrom(Element.class) ? binding : createElementTagWrapper(binding);
  }

  private Binding createElementTagWrapper(final Binding elementBinding) {
    if (myAnnotation == null) return new TagBindingWrapper(elementBinding, Constants.OPTION, Constants.VALUE);

    return new TagBindingWrapper(elementBinding,
                                 myAnnotation.elementTag() != null ? myAnnotation.elementTag() : Constants.OPTION,
                                 myAnnotation.elementValueAttribute() != null ? myAnnotation.elementValueAttribute() : Constants.VALUE);
  }

  abstract Object processResult(Collection result, Object target);

  @NotNull
  abstract Collection<Object> getIterable(@NotNull Object o);

  @Nullable
  @Override
  public Object serialize(Object o, @Nullable Object context, SerializationFilter filter) {
    Collection<Object> collection = o == null ? null : getIterable(o);

    final String tagName = getTagName(o);
    if (tagName != null) {
      Element result = new Element(tagName);
      if (ContainerUtil.isEmpty(collection)) {
        return new Element(tagName);
      }
      for (Object e : collection) {
        if (e == null) {
          throw new XmlSerializationException("Collection " + myAccessor + " contains 'null' object");
        }
        Content child = (Content)getElementBinding(e.getClass()).serialize(e, result, filter);
        if (child != null) {
          result.addContent(child);
        }
      }
      return result;
    }
    else {
      List<Object> result = new SmartList<Object>();
      if (ContainerUtil.isEmpty(collection)) {
        return result;
      }

      for (Object e : collection) {
        ContainerUtil.addIfNotNull(result, getElementBinding(e.getClass()).serialize(e, result, filter));
      }
      return result;
    }
  }

  @Nullable
  @Override
  public Object deserializeList(Object context, @NotNull List<?> nodes) {
    Collection result;
    if (getTagName(context) == null) {
      if (context instanceof Collection) {
        result = (Collection)context;
        result.clear();
      }
      else {
        result = new SmartList();
      }
      for (Object node : nodes) {
        if (!XmlSerializerImpl.isIgnoredNode(node)) {
          //noinspection unchecked
          result.add(getElementBinding(node).deserialize(context, node));
        }
      }

      if (result == context) {
        return result;
      }
    }
    else {
      assert nodes.size() == 1;
      result = processSingle(context, (Element)nodes.get(0));
    }
    return processResult(result, context);
  }

  @Override
  public Object deserialize(Object context, @NotNull Object node) {
    Collection result;
    if (getTagName(context) == null) {
      if (context instanceof Collection) {
        result = (Collection)context;
        result.clear();
      }
      else {
        result = new SmartList();
      }
      if (!XmlSerializerImpl.isIgnoredNode(node)) {
        //noinspection unchecked
        result.add(getElementBinding(node).deserialize(context, node));
      }

      if (result == context) {
        return result;
      }
    }
    else {
      result = processSingle(context, (Element)node);
    }
    return processResult(result, context);
  }

  @NotNull
  private Collection processSingle(Object context, @NotNull Element node) {
    Collection result = createCollection(node.getName());
    for (Content child : node.getContent()) {
      if (!XmlSerializerImpl.isIgnoredNode(child)) {
        //noinspection unchecked
        result.add(getElementBinding(child).deserialize(context, child));
      }
    }
    return result;
  }

  protected Collection createCollection(@NotNull String tagName) {
    return new SmartList();
  }

  @Override
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

  @Override
  public Class getBoundNodeType() {
    return Element.class;
  }

  public Class getElementType() {
    return myElementType;
  }

  @Nullable
  private String getTagName(final Object target) {
    return myAnnotation == null || myAnnotation.surroundWithTag() ? getCollectionTagName(target) : null;
  }

  protected String getCollectionTagName(final Object target) {
    return myTagName;
  }
}

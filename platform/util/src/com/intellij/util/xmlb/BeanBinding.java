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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.xmlb.annotations.*;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.Introspector;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

class BeanBinding implements Binding {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xmlb.BeanBinding");

  private final String myTagName;
  private final Map<Binding, Accessor> myPropertyBindings = new HashMap<Binding, Accessor>();
  private final List<Binding> myPropertyBindingsList = new ArrayList<Binding>();
  private final Class<?> myBeanClass;
  private final SerializationFilter filter;
  private final XmlSerializerImpl serializer;
  @NonNls private static final String CLASS_PROPERTY = "class";
  private final Accessor myAccessor;

  public BeanBinding(Class<?> beanClass, XmlSerializerImpl serializer, final Accessor accessor) {
    myAccessor = accessor;
    assert !beanClass.isArray() : "Bean is an array: " + beanClass;
    assert !beanClass.isPrimitive() : "Bean is primitive type: " + beanClass;
    myBeanClass = beanClass;
    filter = serializer.getFilter();
    this.serializer = serializer;
    myTagName = getTagName(beanClass);
    assert !StringUtil.isEmptyOrSpaces(myTagName) : "Bean name is empty: " + beanClass;
  }

  public void init() {
    initPropertyBindings(myBeanClass);
  }

  private void initPropertyBindings(Class<?> beanClass) {
    for (Accessor accessor : getAccessors(beanClass)) {
      final Binding binding = createBindingByAccessor(serializer, accessor);
      myPropertyBindingsList.add(binding);
      myPropertyBindings.put(binding, accessor);
    }
  }

  public Object serialize(Object o, Object context) {
    Element element = new Element(myTagName);

    serializeInto(o, element);

    return element;
  }

  public void serializeInto(final Object o, final Element element) {
    for (Binding binding : myPropertyBindingsList) {
      Accessor accessor = myPropertyBindings.get(binding);
      if (!filter.accepts(accessor, o)) continue;

      //todo: optimize. Cache it.
      final Property property = XmlSerializerImpl.findAnnotation(accessor.getAnnotations(), Property.class);
      if (property != null) {
        try {
          if (!property.filter().newInstance().accepts(accessor, o)) continue;
        }
        catch (InstantiationException e) {
          throw new XmlSerializationException(e);
        }
        catch (IllegalAccessException e) {
          throw new XmlSerializationException(e);
        }
      }

      Object node = binding.serialize(o, element);
      if (node != element) {
        if (node instanceof org.jdom.Attribute) {
          org.jdom.Attribute attr = (org.jdom.Attribute)node;
          element.setAttribute(attr.getName(), attr.getValue());
        }
        else {
          JDOMUtil.addContent(element, node);
        }
      }
    }
  }

  public void deserializeInto(final Object bean, final Element element) {
    _deserializeInto(bean, element);
  }

  public Object deserialize(Object o, Object... nodes) {
    return _deserializeInto(instantiateBean(), nodes);
  }

  private Object _deserializeInto(final Object result, final Object... aNodes) {
    List<Object> nodes = new ArrayList<Object>();
    for (Object aNode : aNodes) {
      if (XmlSerializerImpl.isIgnoredNode(aNode)) continue;
      nodes.add(aNode);
    }

    if (nodes.size() != 1) {
      throw new XmlSerializationException("Wrong set of nodes: " + nodes + " for bean" + myBeanClass + " in " + myAccessor);
    }
    assert nodes.get(0) instanceof Element : "Wrong node: " + nodes;
    Element e = (Element)nodes.get(0);

    ArrayList<Binding> bindings = new ArrayList<Binding>(myPropertyBindings.keySet());

    MultiMap<Binding, Object> data = new MultiMap<Binding, Object>();

    final Object[] children = JDOMUtil.getChildNodesWithAttrs(e);
    nextNode:
    for (Object child : children) {
      if (XmlSerializerImpl.isIgnoredNode(child)) continue;

      for (Binding binding : bindings) {
        if (binding.isBoundTo(child)) {
          data.putValue(binding, child);
          continue nextNode;
        }
      }

      {
        final String message = "Format error: no binding for " + child + " inside " + this;
        LOG.debug(message);
        Logger.getInstance(myBeanClass.getName()).debug(message);
        Logger.getInstance("#" + myBeanClass.getName()).debug(message);
      }
    }

    for (Object o1 : data.keySet()) {
      Binding binding = (Binding)o1;
      Collection<Object> nn = data.get(binding);
      binding.deserialize(result, ArrayUtil.toObjectArray(nn));
    }

    return result;
  }

  private Object instantiateBean() {
    Object result;

    try {
      result = myBeanClass.newInstance();
    }
    catch (InstantiationException e) {
      throw new XmlSerializationException(e);
    }
    catch (IllegalAccessException e) {
      throw new XmlSerializationException(e);
    }
    return result;
  }

  public boolean isBoundTo(Object node) {
    return node instanceof Element && ((Element)node).getName().equals(myTagName);
  }

  public Class getBoundNodeType() {
    return Element.class;
  }

  private static String getTagName(Class<?> aClass) {
    Tag tag = aClass.getAnnotation(Tag.class);
    if (tag != null && tag.value().length() != 0) return tag.value();
    return aClass.getSimpleName();
  }

  @NotNull
  static List<Accessor> getAccessors(Class<?> aClass) {
    final List<Accessor> accessors = Lists.newArrayList();

    final Map<String, Pair<Method, Method>> candidates = Maps.newTreeMap();  // (name,(getter,setter))
    for (Method method : aClass.getMethods()) {
      if (!Modifier.isPublic(method.getModifiers())) continue;
      final Pair<String, Boolean> propertyData = getPropertyData(method.getName());  // (name,isSetter)
      if (propertyData == null || propertyData.first.equals(CLASS_PROPERTY)) continue;
      if (method.getParameterTypes().length != (propertyData.second ? 1 : 0)) continue;

      Pair<Method, Method> candidate = candidates.get(propertyData.first);
      if (candidate == null) candidate = Pair.empty();
      if ((propertyData.second ? candidate.second : candidate.first) != null) continue;
      candidate = Pair.create(propertyData.second ? candidate.first : method, propertyData.second ? method : candidate.second);
      candidates.put(propertyData.first, candidate);
    }
    for (Map.Entry<String, Pair<Method, Method>> candidate: candidates.entrySet()) {
      final Pair<Method, Method> methods = candidate.getValue();  // (getter,setter)
      if (methods.first != null && methods.second != null &&
          methods.first.getReturnType().equals(methods.second.getParameterTypes()[0]) &&
          XmlSerializerImpl.findAnnotation(methods.first.getAnnotations(), Transient.class) == null &&
          XmlSerializerImpl.findAnnotation(methods.second.getAnnotations(), Transient.class) == null) {
        accessors.add(new PropertyAccessor(candidate.getKey(), methods.first.getReturnType(), methods.first, methods.second));
      }
    }

    for (Field field : aClass.getFields()) {
      final int modifiers = field.getModifiers();
      if (Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers) &&
          !Modifier.isFinal(modifiers) && !Modifier.isTransient(modifiers) &&
          XmlSerializerImpl.findAnnotation(field.getAnnotations(), Transient.class) == null) {
        accessors.add(new FieldAccessor(field));
      }
    }

    return accessors;
  }

  @Nullable
  private static Pair<String, Boolean> getPropertyData(final String methodName) {
    String part = "";
    boolean isSetter = false;
    if (methodName.startsWith("get")) {
      part = methodName.substring(3, methodName.length());
    }
    else if (methodName.startsWith("is")) {
      part = methodName.substring(2, methodName.length());
    }
    else if (methodName.startsWith("set")) {
      part = methodName.substring(3, methodName.length());
      isSetter = true;
    }
    return part.length() > 0 ? Pair.create(Introspector.decapitalize(part), isSetter) : null;
  }

  public String toString() {
    return "BeanBinding[" + myBeanClass.getName() + ", tagName=" + myTagName + "]";
  }

  private static Binding createBindingByAccessor(final XmlSerializerImpl xmlSerializer, final Accessor accessor) {
    final Binding binding = _createBinding(accessor, xmlSerializer);
    binding.init();
    return binding;
  }

  private static Binding _createBinding(final Accessor accessor, final XmlSerializerImpl xmlSerializer) {
    Property property = XmlSerializerImpl.findAnnotation(accessor.getAnnotations(), Property.class);
    Tag tag = XmlSerializerImpl.findAnnotation(accessor.getAnnotations(), Tag.class);
    Attribute attribute = XmlSerializerImpl.findAnnotation(accessor.getAnnotations(), Attribute.class);
    Text text = XmlSerializerImpl.findAnnotation(accessor.getAnnotations(), Text.class);

    final Binding binding = xmlSerializer.getTypeBinding(accessor.getGenericType(), accessor);

    if (binding instanceof JDOMElementBinding) return binding;

    if (text != null) return new TextBinding(accessor, xmlSerializer);

    if (attribute != null) {
      return new AttributeBinding(accessor, attribute, xmlSerializer);
    }

    if (tag != null) {
      if (tag.value().length() > 0) return new TagBinding(accessor, tag, xmlSerializer);
    }

    boolean surroundWithTag = true;

    if (property != null) {
      surroundWithTag = property.surroundWithTag();
    }

    if (!surroundWithTag) {
      if (!Element.class.isAssignableFrom(binding.getBoundNodeType())) {
        throw new XmlSerializationException("Text-serializable properties can't be serialized without surrounding tags: " + accessor);
      }
      return new AccessorBindingWrapper(accessor, binding);
    }

    OptionTag optionTag = XmlSerializerImpl.findAnnotation(accessor.getAnnotations(), OptionTag.class);
    return new OptionTagBinding(accessor, xmlSerializer, optionTag);
  }
}

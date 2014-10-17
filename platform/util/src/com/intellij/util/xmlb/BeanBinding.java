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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ConcurrentSoftValueHashMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.intellij.util.xmlb.annotations.*;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.beans.Introspector;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Set;

class BeanBinding implements Binding {
  private static final Logger LOG = Logger.getInstance(BeanBinding.class);

  private static final Map<Class, List<Accessor>> ourAccessorCache = new ConcurrentSoftValueHashMap<Class, List<Accessor>>();

  private final String myTagName;
  private final LinkedHashMap<Binding, Accessor> myPropertyBindings = new LinkedHashMap<Binding, Accessor>();
  private final Class<?> myBeanClass;

  public BeanBinding(Class<?> beanClass) {
    assert !beanClass.isArray() : "Bean is an array: " + beanClass;
    assert !beanClass.isPrimitive() : "Bean is primitive type: " + beanClass;
    myBeanClass = beanClass;
    myTagName = getTagName(beanClass);
    assert !StringUtil.isEmptyOrSpaces(myTagName) : "Bean name is empty: " + beanClass;
  }

  @Override
  public void init() {
    initPropertyBindings(myBeanClass);
  }

  private synchronized void initPropertyBindings(Class<?> beanClass) {
    for (Accessor accessor : getAccessors(beanClass)) {
      myPropertyBindings.put(createBindingByAccessor(accessor), accessor);
    }
  }

  @Override
  @Nullable
  public Object serialize(@NotNull Object o, @Nullable Object context, SerializationFilter filter) {
    Element element = new Element(myTagName);
    serializeInto(o, element, filter);
    return element;
  }

  public void serializeInto(@NotNull Object o, @NotNull Element element, @NotNull SerializationFilter filter) {
    for (Binding binding : myPropertyBindings.keySet()) {
      Accessor accessor = myPropertyBindings.get(binding);
      if (!filter.accepts(accessor, o)) {
        continue;
      }

      //todo: optimize. Cache it.
      Property property = XmlSerializerImpl.findAnnotation(accessor.getAnnotations(), Property.class);
      if (property != null && property.filter() != SerializationFilter.class) {
        try {
          if (!ReflectionUtil.newInstance(property.filter()).accepts(accessor, o)) {
            continue;
          }
        }
        catch (RuntimeException e) {
          throw new XmlSerializationException(e);
        }
      }

      Object node = binding.serialize(o, element, filter);
      if (node != null) {
        if (node instanceof org.jdom.Attribute) {
          element.setAttribute((org.jdom.Attribute)node);
        }
        else {
          JDOMUtil.addContent(element, node);
        }
      }
    }
  }

  @Override
  public Object deserialize(Object o, @NotNull Object... nodes) {
    Element element = null;
    for (Object aNode : nodes) {
      if (!XmlSerializerImpl.isIgnoredNode(aNode)) {
        element = (Element)aNode;
        break;
      }
    }

    if (element == null) {
      return o;
    }
    return deserializeInto(XmlSerializerImpl.newInstance(myBeanClass), element);
  }

  public Object deserializeInto(@NotNull Object result, @NotNull Element element) {
    Set<Binding> bindings = myPropertyBindings.keySet();
    MultiMap<Binding, Object> data = MultiMap.createSmartList();
    nextNode:
    for (Object child : ContainerUtil.concat(element.getContent(), element.getAttributes())) {
      if (XmlSerializerImpl.isIgnoredNode(child)) {
        continue;
      }

      for (Binding binding : bindings) {
        if (binding.isBoundTo(child)) {
          data.putValue(binding, child);
          continue nextNode;
        }
      }

      final String message = "Format error: no binding for " + child + " inside " + this;
      LOG.debug(message);
      Logger.getInstance(myBeanClass.getName()).debug(message);
      Logger.getInstance("#" + myBeanClass.getName()).debug(message);
    }

    for (Binding binding : data.keySet()) {
      binding.deserialize(result, ArrayUtil.toObjectArray(data.get(binding)));
    }

    return result;
  }

  @Override
  public boolean isBoundTo(Object node) {
    return node instanceof Element && ((Element)node).getName().equals(myTagName);
  }

  @Override
  public Class getBoundNodeType() {
    return Element.class;
  }

  private static String getTagName(Class<?> aClass) {
    for (Class<?> c = aClass; c != null; c = c.getSuperclass()) {
      String name = getTagNameFromAnnotation(c);
      if (name != null) {
        return name;
      }
    }
    return aClass.getSimpleName();
  }

  private static String getTagNameFromAnnotation(Class<?> aClass) {
    Tag tag = aClass.getAnnotation(Tag.class);
    if (tag != null && !tag.value().isEmpty()) return tag.value();
    return null;
  }

  @NotNull
  static List<Accessor> getAccessors(Class<?> aClass) {
    List<Accessor> accessors = ourAccessorCache.get(aClass);
    if (accessors != null) {
      return accessors;
    }

    accessors = ContainerUtil.newArrayList();

    if (aClass != Rectangle.class) {   // special case for Rectangle.class to avoid infinite recursion during serialization due to bounds() method
      collectPropertyAccessors(aClass, accessors);
    }
    collectFieldAccessors(aClass, accessors);

    ourAccessorCache.put(aClass, accessors);

    return accessors;
  }

  private static void collectPropertyAccessors(Class<?> aClass, List<Accessor> accessors) {
    final Map<String, Couple<Method>> candidates = ContainerUtil.newTreeMap();  // (name,(getter,setter))
    for (Method method : aClass.getMethods()) {
      if (!Modifier.isPublic(method.getModifiers())) continue;
      final Pair<String, Boolean> propertyData = getPropertyData(method.getName());  // (name,isSetter)
      if (propertyData == null || propertyData.first.equals("class")) continue;
      if (method.getParameterTypes().length != (propertyData.second ? 1 : 0)) continue;

      Couple<Method> candidate = candidates.get(propertyData.first);
      if (candidate == null) candidate = Couple.getEmpty();
      if ((propertyData.second ? candidate.second : candidate.first) != null) continue;
      candidate = Couple.of(propertyData.second ? candidate.first : method, propertyData.second ? method : candidate.second);
      candidates.put(propertyData.first, candidate);
    }
    for (Map.Entry<String, Couple<Method>> candidate: candidates.entrySet()) {
      final Couple<Method> methods = candidate.getValue();  // (getter,setter)
      if (methods.first != null && methods.second != null &&
          methods.first.getReturnType().equals(methods.second.getParameterTypes()[0]) &&
          XmlSerializerImpl.findAnnotation(methods.first.getAnnotations(), Transient.class) == null &&
          XmlSerializerImpl.findAnnotation(methods.second.getAnnotations(), Transient.class) == null) {
        accessors.add(new PropertyAccessor(candidate.getKey(), methods.first.getReturnType(), methods.first, methods.second));
      }
    }
  }

  private static void collectFieldAccessors(Class<?> aClass, List<Accessor> accessors) {
    for (Field field : aClass.getFields()) {
      final int modifiers = field.getModifiers();
      if (Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers) &&
          !Modifier.isFinal(modifiers) && !Modifier.isTransient(modifiers) &&
          XmlSerializerImpl.findAnnotation(field.getAnnotations(), Transient.class) == null) {
        accessors.add(new FieldAccessor(field));
      }
    }
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
    return !part.isEmpty() ? Pair.create(Introspector.decapitalize(part), isSetter) : null;
  }

  public String toString() {
    return "BeanBinding[" + myBeanClass.getName() + ", tagName=" + myTagName + "]";
  }

  private static Binding createBindingByAccessor(@NotNull Accessor accessor) {
    final Binding binding = _createBinding(accessor);
    binding.init();
    return binding;
  }

  private static Binding _createBinding(@NotNull Accessor accessor) {
    Binding binding = XmlSerializerImpl.getTypeBinding(accessor.getGenericType(), accessor);
    if (binding instanceof JDOMElementBinding) {
      return binding;
    }

    Attribute attribute = XmlSerializerImpl.findAnnotation(accessor.getAnnotations(), Attribute.class);
    if (attribute != null) {
      return new AttributeBinding(accessor, attribute);
    }

    Tag tag = XmlSerializerImpl.findAnnotation(accessor.getAnnotations(), Tag.class);
    if (tag != null && !tag.value().isEmpty()) {
      return new TagBinding(accessor, tag);
    }

    Text text = XmlSerializerImpl.findAnnotation(accessor.getAnnotations(), Text.class);
    if (text != null) {
      return new TextBinding(accessor);
    }

    boolean surroundWithTag = true;
    Property property = XmlSerializerImpl.findAnnotation(accessor.getAnnotations(), Property.class);
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
    return new OptionTagBinding(accessor, optionTag);
  }
}

/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.*;
import gnu.trove.TObjectFloatHashMap;
import org.jdom.Comment;
import org.jdom.Content;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.beans.Introspector;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.*;
import java.util.List;

public class BeanBinding extends NotNullDeserializeBinding {
  private static final Map<Class, List<MutableAccessor>> ourAccessorCache = ContainerUtil.createConcurrentSoftValueMap();

  private final String myTagName;
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private Binding[] myBindings;

  protected final Class<?> myBeanClass;

  ThreeState compareByFields = ThreeState.UNSURE;

  public BeanBinding(@NotNull Class<?> beanClass, @Nullable MutableAccessor accessor) {
    super(accessor);

    assert !beanClass.isArray() : "Bean is an array: " + beanClass;
    assert !beanClass.isPrimitive() : "Bean is primitive type: " + beanClass;
    myBeanClass = beanClass;
    myTagName = getTagName(beanClass);
    assert !StringUtil.isEmptyOrSpaces(myTagName) : "Bean name is empty: " + beanClass;
  }

  @Override
  public synchronized void init(@NotNull Type originalType, @NotNull Serializer serializer) {
    assert myBindings == null;

    List<MutableAccessor> accessors = getAccessors(myBeanClass);
    myBindings = new Binding[accessors.size()];
    for (int i = 0, size = accessors.size(); i < size; i++) {
      Binding binding = createBinding(accessors.get(i), serializer);
      binding.init(originalType, serializer);
      myBindings[i] = binding;
    }
  }

  @Override
  @Nullable
  public Object serialize(@NotNull Object o, @Nullable Object context, @Nullable SerializationFilter filter) {
    return serializeInto(o, context == null ? null : new Element(myTagName), filter);
  }

  public Element serialize(@NotNull Object object, boolean createElementIfEmpty, @Nullable SerializationFilter filter) {
    return serializeInto(object, createElementIfEmpty ? new Element(myTagName) : null, filter);
  }

  @Nullable
  public Element serializeInto(@NotNull Object o, @Nullable Element element, @Nullable SerializationFilter filter) {
    for (Binding binding : myBindings) {
      Accessor accessor = binding.getAccessor();

      if (o instanceof SerializationFilter && !((SerializationFilter)o).accepts(accessor,  o)) {
        continue;
      }

      Property property = accessor.getAnnotation(Property.class);
      if (property == null || !property.alwaysWrite()) {
        if (filter != null) {
          if (filter instanceof SkipDefaultsSerializationFilter) {
            if (((SkipDefaultsSerializationFilter)filter).equal(binding, o)) {
              continue;
            }
          }
          else if (!filter.accepts(accessor, o)) {
            continue;
          }
        }

        //todo: optimize. Cache it.
        if (property != null && property.filter() != SerializationFilter.class &&
            !ReflectionUtil.newInstance(property.filter()).accepts(accessor, o)) {
          continue;
        }
      }

      if (element == null) {
        element = new Element(myTagName);
      }

      Object node = binding.serialize(o, element, filter);
      if (node != null) {
        if (node instanceof org.jdom.Attribute) {
          element.setAttribute((org.jdom.Attribute)node);
        }
        else {
          addContent(element, node);
        }
      }
    }
    return element;
  }

  @Override
  @NotNull
  public Object deserialize(@Nullable Object context, @NotNull Element element) {
    Object instance = ReflectionUtil.newInstance(myBeanClass);
    deserializeInto(instance, element);
    return instance;
  }

  boolean equalByFields(@NotNull Object currentValue, @NotNull Object defaultValue, @NotNull SkipDefaultsSerializationFilter filter) {
    for (Binding binding : myBindings) {
      Accessor accessor = binding.getAccessor();
      if (!filter.equal(binding, accessor.read(currentValue), accessor.read(defaultValue))) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  public TObjectFloatHashMap<String> computeBindingWeights(@NotNull LinkedHashSet<String> accessorNameTracker) {
    TObjectFloatHashMap<String> weights = new TObjectFloatHashMap<String>(accessorNameTracker.size());
    float weight = 0;
    float step = (float)myBindings.length / (float)accessorNameTracker.size();
    for (String name : accessorNameTracker) {
      weights.put(name, weight);
      weight += step;
    }

    weight = 0;
    for (Binding binding : myBindings) {
      String name = binding.getAccessor().getName();
      if (!weights.containsKey(name)) {
        weights.put(name, weight);
      }

      weight++;
    }
    return weights;
  }

  public void sortBindings(@NotNull final TObjectFloatHashMap<String> weights) {
    Arrays.sort(myBindings, new Comparator<Binding>() {
      @Override
      public int compare(@NotNull Binding o1, @NotNull Binding o2) {
        String n1 = o1.getAccessor().getName();
        String n2 = o2.getAccessor().getName();
        float w1 = weights.get(n1);
        float w2 = weights.get(n2);
        return (int)(w1 - w2);
      }
    });
  }

  public void deserializeInto(@NotNull Object result, @NotNull Element element) {
    deserializeInto(result, element, null);
  }

  public void deserializeInto(@NotNull Object result, @NotNull Element element, @Nullable Set<String> accessorNameTracker) {
    nextAttribute:
    for (org.jdom.Attribute attribute : element.getAttributes()) {
      if (StringUtil.isEmpty(attribute.getNamespaceURI())) {
        for (Binding binding : myBindings) {
          if (binding instanceof AttributeBinding && ((AttributeBinding)binding).myName.equals(attribute.getName())) {
            if (accessorNameTracker != null) {
              accessorNameTracker.add(binding.getAccessor().getName());
            }
            ((AttributeBinding)binding).set(result, attribute.getValue());
            continue nextAttribute;
          }
        }
      }
    }

    MultiMap<Binding, Element> data = null;
    nextNode:
    for (Content content : element.getContent()) {
      if (content instanceof Comment) {
        continue;
      }

      for (Binding binding : myBindings) {
        if (content instanceof org.jdom.Text) {
          if (binding instanceof TextBinding) {
            ((TextBinding)binding).set(result, content.getValue());
          }
          continue;
        }

        Element child = (Element)content;
        if (binding.isBoundTo(child)) {
          if (binding instanceof MultiNodeBinding && ((MultiNodeBinding)binding).isMulti()) {
            if (data == null) {
              data = MultiMap.createLinked();
            }
            data.putValue(binding, child);
          }
          else {
            if (accessorNameTracker != null) {
              accessorNameTracker.add(binding.getAccessor().getName());
            }
            binding.deserializeUnsafe(result, child);
          }
          continue nextNode;
        }
      }
    }

    for (Binding binding : myBindings) {
      if (binding instanceof AccessorBindingWrapper && ((AccessorBindingWrapper)binding).isFlat()) {
        ((AccessorBindingWrapper)binding).deserialize(result, element);
      }
    }

    if (data != null) {
      for (Binding binding : data.keySet()) {
        if (accessorNameTracker != null) {
          accessorNameTracker.add(binding.getAccessor().getName());
        }
        ((MultiNodeBinding)binding).deserializeList(result, (List<Element>)data.get(binding));
      }
    }
  }

  @Override
  public boolean isBoundTo(@NotNull Element element) {
    return element.getName().equals(myTagName);
  }

  @NotNull
  private static String getTagName(@NotNull Class<?> aClass) {
    for (Class<?> c = aClass; c != null; c = c.getSuperclass()) {
      String name = getTagNameFromAnnotation(c);
      if (name != null) {
        return name;
      }
    }
    String name = aClass.getSimpleName();
    if (name.isEmpty()) {
      name = aClass.getSuperclass().getSimpleName();
    }

    int lastIndexOf = name.lastIndexOf('$');
    if (lastIndexOf > 0 && name.length() > (lastIndexOf + 1)) {
      return name.substring(lastIndexOf + 1);
    }
    return name;
  }

  private static String getTagNameFromAnnotation(Class<?> aClass) {
    Tag tag = aClass.getAnnotation(Tag.class);
    return tag != null && !tag.value().isEmpty() ? tag.value() : null;
  }

  @NotNull
  static List<MutableAccessor> getAccessors(@NotNull Class<?> aClass) {
    List<MutableAccessor> accessors = ourAccessorCache.get(aClass);
    if (accessors != null) {
      return accessors;
    }

    accessors = ContainerUtil.newArrayList();

    Map<String, Couple<Method>> nameToAccessors;
    if (aClass != Rectangle.class) {   // special case for Rectangle.class to avoid infinite recursion during serialization due to bounds() method
      nameToAccessors = collectPropertyAccessors(aClass, accessors);
    }
    else {
      nameToAccessors = Collections.emptyMap();
    }

    int propertyAccessorCount  = accessors.size();
    collectFieldAccessors(aClass, accessors);

    // if there are field accessor and property accessor, prefer field - Kotlin generates private var and getter/setter, but annotation moved to var, not to getter/setter
    // so, we must remove duplicated accessor
    for (int j = propertyAccessorCount; j < accessors.size(); j++) {
      String name = accessors.get(j).getName();
      if (nameToAccessors.containsKey(name)) {
        for (int i = 0; i < propertyAccessorCount; i++) {
          if (accessors.get(i).getName().equals(name)) {
            accessors.remove(i);
            propertyAccessorCount--;
            //noinspection AssignmentToForLoopParameter
            j--;
            break;
          }
        }
      }
    }

    ourAccessorCache.put(aClass, accessors);

    return accessors;
  }

  @NotNull
  private static Map<String, Couple<Method>> collectPropertyAccessors(@NotNull Class<?> aClass, @NotNull List<MutableAccessor> accessors) {
    final Map<String, Couple<Method>> candidates = ContainerUtilRt.newTreeMap(); // (name,(getter,setter))
    for (Method method : aClass.getMethods()) {
      if (!Modifier.isPublic(method.getModifiers())) {
        continue;
      }

      Pair<String, Boolean> propertyData = getPropertyData(method.getName()); // (name,isSetter)
      if (propertyData == null || propertyData.first.equals("class") ||
          method.getParameterTypes().length != (propertyData.second ? 1 : 0)) {
        continue;
      }

      Couple<Method> candidate = candidates.get(propertyData.first);
      if (candidate == null) {
        candidate = Couple.getEmpty();
      }
      if ((propertyData.second ? candidate.second : candidate.first) != null) {
        continue;
      }
      candidate = Couple.of(propertyData.second ? candidate.first : method, propertyData.second ? method : candidate.second);
      candidates.put(propertyData.first, candidate);
    }
    for (Iterator<Map.Entry<String, Couple<Method>>> iterator = candidates.entrySet().iterator(); iterator.hasNext(); ) {
      Map.Entry<String, Couple<Method>> candidate = iterator.next();
      Couple<Method> methods = candidate.getValue(); // (getter,setter)
      if (methods.first != null && methods.second != null &&
          methods.first.getReturnType().equals(methods.second.getParameterTypes()[0]) &&
          methods.first.getAnnotation(Transient.class) == null &&
          methods.second.getAnnotation(Transient.class) == null) {
        accessors.add(new PropertyAccessor(candidate.getKey(), methods.first.getReturnType(), methods.first, methods.second));
      }
      else {
        iterator.remove();
      }
    }
    return candidates;
  }

  private static void collectFieldAccessors(@NotNull Class<?> aClass, @NotNull List<MutableAccessor> accessors) {
    Class<?> currentClass = aClass;
    do {
      for (Field field : currentClass.getDeclaredFields()) {
        int modifiers = field.getModifiers();
        //noinspection deprecation
        if (!Modifier.isStatic(modifiers) &&
            (field.getAnnotation(OptionTag.class) != null ||
             field.getAnnotation(Tag.class) != null ||
             field.getAnnotation(Attribute.class) != null ||
             field.getAnnotation(Property.class) != null ||
             field.getAnnotation(Text.class) != null ||
             field.getAnnotation(CollectionBean.class) != null ||
             field.getAnnotation(MapAnnotation.class) != null ||
             field.getAnnotation(AbstractCollection.class) != null ||
             (Modifier.isPublic(modifiers) &&
              // we don't want to allow final fields of all types, but only supported
              (!Modifier.isFinal(modifiers) || Collection.class.isAssignableFrom(field.getType())) &&
              !Modifier.isTransient(modifiers) &&
              field.getAnnotation(Transient.class) == null))) {
          accessors.add(new FieldAccessor(field));
        }
      }
    }
    while ((currentClass = currentClass.getSuperclass()) != null && currentClass.getAnnotation(Transient.class) == null);
  }

  @Nullable
  private static Pair<String, Boolean> getPropertyData(@NotNull String methodName) {
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

    if (part.isEmpty()) {
      return null;
    }

    int suffixIndex = part.indexOf('$');
    if (suffixIndex > 0) {
      // see XmlSerializerTest.internalVar
      part = part.substring(0, suffixIndex);
    }
    return Pair.create(Introspector.decapitalize(part), isSetter);
  }

  public String toString() {
    return "BeanBinding[" + myBeanClass.getName() + ", tagName=" + myTagName + "]";
  }

  @NotNull
  private static Binding createBinding(@NotNull MutableAccessor accessor, @NotNull Serializer serializer) {
    Binding binding = serializer.getBinding(accessor);
    if (binding instanceof JDOMElementBinding) {
      return binding;
    }

    Attribute attribute = accessor.getAnnotation(Attribute.class);
    if (attribute != null) {
      return new AttributeBinding(accessor, attribute);
    }

    Tag tag = accessor.getAnnotation(Tag.class);
    if (tag != null) {
      return new TagBinding(accessor, tag);
    }

    Text text = accessor.getAnnotation(Text.class);
    if (text != null) {
      return new TextBinding(accessor);
    }

    if (binding instanceof CompactCollectionBinding) {
      return new AccessorBindingWrapper(accessor, binding, false);
    }

    boolean surroundWithTag = true;
    boolean inline = false;
    Property property = accessor.getAnnotation(Property.class);
    if (property != null) {
      surroundWithTag = property.surroundWithTag();
      inline = property.flat();
    }

    if (!surroundWithTag || inline) {
      if (inline && !(binding instanceof BeanBinding)) {
        throw new XmlSerializationException("inline supported only for BeanBinding: " + accessor);
      }
      if (binding == null || binding instanceof TextBinding) {
        throw new XmlSerializationException("Text-serializable properties can't be serialized without surrounding tags: " + accessor);
      }
      return new AccessorBindingWrapper(accessor, binding, inline);
    }

    return new OptionTagBinding(accessor, accessor.getAnnotation(OptionTag.class));
  }
}

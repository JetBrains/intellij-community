// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb;

import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
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
import java.lang.reflect.*;
import java.util.List;
import java.util.*;

public class BeanBinding extends NotNullDeserializeBinding {
  private static final Map<Class, List<MutableAccessor>> ourAccessorCache = ContainerUtil.createConcurrentSoftValueMap();

  private final String myTagName;
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  protected Binding[] myBindings;

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

  @SuppressWarnings("NonPrivateFieldAccessedInSynchronizedContext")
  @Override
  public final synchronized void init(@NotNull Type originalType, @NotNull Serializer serializer) {
    assert myBindings == null;

    Property classAnnotation = myBeanClass.getAnnotation(Property.class);

    List<MutableAccessor> accessors = getAccessors(myBeanClass);
    myBindings = new Binding[accessors.size()];
    for (int i = 0, size = accessors.size(); i < size; i++) {
      Binding binding = createBinding(accessors.get(i), serializer, classAnnotation == null ? Property.Style.OPTION_TAG : classAnnotation.style());
      binding.init(originalType, serializer);
      myBindings[i] = binding;
    }
  }

  @Override
  @Nullable
  public final Object serialize(@NotNull Object o, @Nullable Object context, @Nullable SerializationFilter filter) {
    return serializeInto(o, context == null ? null : new Element(myTagName), filter);
  }

  public final Element serialize(@NotNull Object object, boolean createElementIfEmpty, @Nullable SerializationFilter filter) {
    return serializeInto(object, createElementIfEmpty ? new Element(myTagName) : null, filter);
  }

  @Nullable
  public Element serializeInto(@NotNull Object o, @Nullable Element element, @Nullable SerializationFilter filter) {
    for (Binding binding : myBindings) {
      Accessor accessor = binding.getAccessor();
      if (o instanceof SerializationFilter && !((SerializationFilter)o).accepts(accessor, o)) {
        continue;
      }

      element = serializePropertyInto(binding, o, element, filter, true);
    }
    return element;
  }

  @Nullable
  protected final Element serializePropertyInto(@NotNull Binding binding,
                                                @NotNull Object o,
                                                @Nullable Element element,
                                                @Nullable SerializationFilter filter,
                                                boolean isFilterPropertyItself) {
    Accessor accessor = binding.getAccessor();
    Property property = accessor.getAnnotation(Property.class);
    if (property == null || !property.alwaysWrite()) {
      if (filter != null && isFilterPropertyItself) {
        if (filter instanceof SkipDefaultsSerializationFilter) {
          if (((SkipDefaultsSerializationFilter)filter).equal(binding, o)) {
            return element;
          }
        }
        else if (!filter.accepts(accessor, o)) {
          return element;
        }
      }

      //todo: optimize. Cache it.
      if (property != null && property.filter() != SerializationFilter.class &&
          !ReflectionUtil.newInstance(property.filter()).accepts(accessor, o)) {
        return element;
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
    return element;
  }

  @Override
  @NotNull
  public Object deserialize(@Nullable Object context, @NotNull Element element) {
    Object instance = ReflectionUtil.newInstance(myBeanClass);
    deserializeInto(instance, element);
    return instance;
  }

  final boolean equalByFields(@NotNull Object currentValue, @NotNull Object defaultValue, @NotNull SkipDefaultsSerializationFilter filter) {
    for (Binding binding : myBindings) {
      Accessor accessor = binding.getAccessor();
      if (!filter.equal(binding, accessor.read(currentValue), accessor.read(defaultValue))) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  public final TObjectFloatHashMap<String> computeBindingWeights(@NotNull LinkedHashSet<String> accessorNameTracker) {
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

  public final void sortBindings(@NotNull final TObjectFloatHashMap<? super String> weights) {
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

  public final void deserializeInto(@NotNull Object result, @NotNull Element element) {
    deserializeInto(result, element, null);
  }

  public final void deserializeInto(@NotNull Object result, @NotNull Element element, @Nullable Set<? super String> accessorNameTracker) {
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
  public final boolean isBoundTo(@NotNull Element element) {
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

  @Nullable
  private static String getTagNameFromAnnotation(@NotNull Class<?> aClass) {
    Tag tag = aClass.getAnnotation(Tag.class);
    return tag != null && !tag.value().isEmpty() ? tag.value() : null;
  }

  @NotNull
  public static List<MutableAccessor> getAccessors(@NotNull Class<?> aClass) {
    List<MutableAccessor> accessors = ourAccessorCache.get(aClass);
    if (accessors != null) {
      return accessors;
    }

    accessors = new ArrayList<MutableAccessor>();

    Map<String, Couple<Method>> nameToAccessors;
    // special case for Rectangle.class to avoid infinite recursion during serialization due to bounds() method
    if (aClass == Rectangle.class) {
      nameToAccessors = Collections.emptyMap();
    }
    else {
      nameToAccessors = collectPropertyAccessors(aClass, accessors);
    }

    int propertyAccessorCount = accessors.size();
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

    if (accessors.isEmpty() && !isAssertBindings(aClass)) {
      LOG.warn("no accessors for " + aClass);
    }

    ourAccessorCache.put(aClass, accessors);

    return accessors;
  }

  private static boolean isAssertBindings(@NotNull Class<?> aClass) {
    do {
      Property property = aClass.getAnnotation(Property.class);
      if (property != null && !property.assertIfNoBindings()) {
        return true;
      }
    }
    while ((aClass = aClass.getSuperclass()) != null);
    return false;
  }

  private static class NameAndIsSetter {
    final String name;
    final boolean isSetter;

    private NameAndIsSetter(String name, boolean isSetter) {
      this.name = name;
      this.isSetter = isSetter;
    }
  }

  @NotNull
  private static Map<String, Couple<Method>> collectPropertyAccessors(@NotNull Class<?> aClass, @NotNull List<? super MutableAccessor> accessors) {
    final Map<String, Couple<Method>> candidates = new TreeMap<String, Couple<Method>>(); // (name,(getter,setter))
    for (Method method : aClass.getMethods()) {
      if (!Modifier.isPublic(method.getModifiers())) {
        continue;
      }

      NameAndIsSetter propertyData = getPropertyData(method.getName());
      if (propertyData == null || propertyData.name.equals("class") ||
          method.getParameterTypes().length != (propertyData.isSetter ? 1 : 0)) {
        continue;
      }

      Couple<Method> candidate = candidates.get(propertyData.name);
      if (candidate == null) {
        candidate = Couple.getEmpty();
      }
      if ((propertyData.isSetter ? candidate.second : candidate.first) != null) {
        continue;
      }
      candidate = Couple.of(propertyData.isSetter ? candidate.first : method, propertyData.isSetter ? method : candidate.second);
      candidates.put(propertyData.name, candidate);
    }

    for (Iterator<Map.Entry<String, Couple<Method>>> iterator = candidates.entrySet().iterator(); iterator.hasNext(); ) {
      Map.Entry<String, Couple<Method>> candidate = iterator.next();
      Couple<Method> methods = candidate.getValue();
      Method getter = methods.first;
      Method setter = methods.second;
      if (isAcceptableProperty(getter, setter)) {
        accessors.add(new PropertyAccessor(candidate.getKey(), getter.getReturnType(), getter, setter));
      }
      else {
        iterator.remove();
      }
    }
    return candidates;
  }

  private static boolean isAcceptableProperty(@Nullable Method getter, @Nullable Method setter) {
    if (getter == null || getter.getAnnotation(Transient.class) != null) {
      return false;
    }

    if (setter == null) {
      // check hasStoreAnnotations to ensure that this addition will not lead to regression (since there is a chance that there is some existing not-annotated list getters without setter)
      return (Collection.class.isAssignableFrom(getter.getReturnType()) || Map.class.isAssignableFrom(getter.getReturnType())) && hasStoreAnnotations(getter);
    }

    if (setter.getAnnotation(Transient.class) != null || !getter.getReturnType().equals(setter.getParameterTypes()[0])) {
      return false;
    }

    return true;
  }

  private static boolean hasStoreAnnotations(@NotNull AccessibleObject object) {
    //noinspection deprecation
    return object.getAnnotation(OptionTag.class) != null ||
           object.getAnnotation(Tag.class) != null ||
           object.getAnnotation(Attribute.class) != null ||
           object.getAnnotation(Property.class) != null ||
           object.getAnnotation(Text.class) != null ||
           object.getAnnotation(CollectionBean.class) != null ||
           object.getAnnotation(MapAnnotation.class) != null ||
           object.getAnnotation(XMap.class) != null ||
           object.getAnnotation(XCollection.class) != null ||
           object.getAnnotation(AbstractCollection.class) != null;
  }

  private static void collectFieldAccessors(@NotNull Class<?> aClass, @NotNull List<? super MutableAccessor> accessors) {
    Class<?> currentClass = aClass;
    do {
      for (Field field : currentClass.getDeclaredFields()) {
        int modifiers = field.getModifiers();
        if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
          continue;
        }

        if (!hasStoreAnnotations(field)) {
          if (!(Modifier.isPublic(modifiers))) {
            continue;
          }

          if (Modifier.isFinal(modifiers)) {
            Class<?> fieldType = field.getType();
            // we don't want to allow final fields of all types, but only supported
            if (!(Collection.class.isAssignableFrom(fieldType) || Map.class.isAssignableFrom(fieldType))) {
              continue;
            }
          }

          if (field.getAnnotation(Transient.class) != null) {
            continue;
          }
        }

        accessors.add(new FieldAccessor(field));
      }
    }
    while ((currentClass = currentClass.getSuperclass()) != null && currentClass.getAnnotation(Transient.class) == null);
  }

  @Nullable
  private static NameAndIsSetter getPropertyData(@NotNull String methodName) {
    String part = "";
    boolean isSetter = false;
    if (methodName.startsWith("get")) {
      part = methodName.substring(3);
    }
    else if (methodName.startsWith("is")) {
      part = methodName.substring(2);
    }
    else if (methodName.startsWith("set")) {
      part = methodName.substring(3);
      isSetter = true;
    }

    if (part.isEmpty()) {
      return null;
    }

    int suffixIndex = part.indexOf('$');
    if (suffixIndex > 0) {
      // ignore special kotlin properties
      if (part.endsWith("$annotations")) {
        return null;
      }
      // see XmlSerializerTest.internalVar
      part = part.substring(0, suffixIndex);
    }
    return new NameAndIsSetter(Introspector.decapitalize(part), isSetter);
  }

  public String toString() {
    return "BeanBinding[" + myBeanClass.getName() + ", tagName=" + myTagName + "]";
  }

  @NotNull
  private static Binding createBinding(@NotNull MutableAccessor accessor, @NotNull Serializer serializer, @NotNull Property.Style propertyStyle) {
    Attribute attribute = accessor.getAnnotation(Attribute.class);
    if (attribute != null) {
      return new AttributeBinding(accessor, attribute);
    }

    Text text = accessor.getAnnotation(Text.class);
    if (text != null) {
      return new TextBinding(accessor);
    }

    OptionTag optionTag = accessor.getAnnotation(OptionTag.class);
    if (optionTag != null && optionTag.converter() != Converter.class) {
      return new OptionTagBinding(accessor, optionTag);
    }

    Binding binding = serializer.getBinding(accessor);
    if (binding instanceof JDOMElementBinding) {
      return binding;
    }

    Tag tag = accessor.getAnnotation(Tag.class);
    if (tag != null) {
      return new TagBinding(accessor, tag);
    }

    if (binding instanceof CompactCollectionBinding) {
      return new AccessorBindingWrapper(accessor, binding, false, Property.Style.OPTION_TAG);
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
      return new AccessorBindingWrapper(accessor, binding, inline, property.style());
    }

    XCollection xCollection = accessor.getAnnotation(XCollection.class);
    if (xCollection != null && (!xCollection.propertyElementName().isEmpty() || xCollection.style() == XCollection.Style.v2)) {
      return new TagBinding(accessor, xCollection.propertyElementName());
    }

    if (optionTag == null) {
      XMap xMap = accessor.getAnnotation(XMap.class);
      if (xMap != null) {
        return new TagBinding(accessor, xMap.propertyElementName());
      }
    }

    if (propertyStyle == Property.Style.ATTRIBUTE) {
      return new AttributeBinding(accessor, null);
    }
    return new OptionTagBinding(accessor, optionTag);
  }
}

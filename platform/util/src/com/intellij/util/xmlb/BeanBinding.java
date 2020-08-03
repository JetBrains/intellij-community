// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb;

import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.serialization.MutableAccessor;
import com.intellij.serialization.PropertyCollector;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.*;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import org.jdom.Comment;
import org.jdom.Content;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ApiStatus.Internal
public class BeanBinding extends NotNullDeserializeBinding {
  private static final XmlSerializerPropertyCollector PROPERTY_COLLECTOR = new XmlSerializerPropertyCollector();

  private final String myTagName;
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  protected NestedBinding[] myBindings;

  protected final Class<?> myBeanClass;

  ThreeState compareByFields = ThreeState.UNSURE;

  public BeanBinding(@NotNull Class<?> beanClass) {
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
    myBindings = new NestedBinding[accessors.size()];
    for (int i = 0, size = accessors.size(); i < size; i++) {
      NestedBinding binding = createBinding(accessors.get(i), serializer, classAnnotation == null ? Property.Style.OPTION_TAG : classAnnotation.style());
      binding.init(originalType, serializer);
      myBindings[i] = binding;
    }
  }

  @Override
  public final @Nullable Object serialize(@NotNull Object o, @Nullable Object context, @Nullable SerializationFilter filter) {
    return serializeInto(o, context == null ? null : new Element(myTagName), filter);
  }

  public final Element serialize(@NotNull Object object, boolean createElementIfEmpty, @Nullable SerializationFilter filter) {
    return serializeInto(object, createElementIfEmpty ? new Element(myTagName) : null, filter);
  }

  public @Nullable Element serializeInto(@NotNull Object o, @Nullable Element element, @Nullable SerializationFilter filter) {
    for (NestedBinding binding : myBindings) {
      if (o instanceof SerializationFilter && !((SerializationFilter)o).accepts(binding.getAccessor(), o)) {
        continue;
      }

      element = serializePropertyInto(binding, o, element, filter, true);
    }
    return element;
  }

  protected final @Nullable Element serializePropertyInto(@NotNull NestedBinding binding,
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
        Binding.addContent(element, node);
      }
    }
    return element;
  }

  @Override
  public final @NotNull Object deserialize(@Nullable Object context, @NotNull Element element) {
    Object instance = newInstance();
    deserializeInto(instance, element);
    return instance;
  }

  protected @NotNull Object newInstance() {
    return ReflectionUtil.newInstance(myBeanClass, false);
  }

  final boolean equalByFields(@NotNull Object currentValue, @NotNull Object defaultValue, @NotNull SkipDefaultsSerializationFilter filter) {
    for (NestedBinding binding : myBindings) {
      Accessor accessor = binding.getAccessor();
      if (!filter.equal(binding, accessor.read(currentValue), accessor.read(defaultValue))) {
        return false;
      }
    }
    return true;
  }

  public final @NotNull Object2FloatMap<String> computeBindingWeights(@NotNull ObjectLinkedOpenHashSet<String> accessorNameTracker) {
    Object2FloatMap<String> weights = new Object2FloatOpenHashMap<>(accessorNameTracker.size());
    float weight = 0;
    float step = (float)myBindings.length / (float)accessorNameTracker.size();
    for (String name : accessorNameTracker) {
      weights.put(name, weight);
      weight += step;
    }

    weight = 0;
    for (NestedBinding binding : myBindings) {
      String name = binding.getAccessor().getName();
      if (!weights.containsKey(name)) {
        weights.put(name, weight);
      }

      weight++;
    }
    return weights;
  }

  public final void sortBindings(@NotNull Object2FloatMap<? super String> weights) {
    Arrays.sort(myBindings, (o1, o2) -> {
      String n1 = o1.getAccessor().getName();
      String n2 = o2.getAccessor().getName();
      float w1 = weights.getFloat(n1);
      float w2 = weights.getFloat(n2);
      return (int)(w1 - w2);
    });
  }

  public final void deserializeInto(@NotNull Object result, @NotNull Element element) {
    deserializeInto(result, element, null);
  }

  public final void deserializeInto(@NotNull Object result, @NotNull Element element, @Nullable Set<? super String> accessorNameTracker) {
    nextAttribute:
    for (org.jdom.Attribute attribute : element.getAttributes()) {
      if (StringUtil.isEmpty(attribute.getNamespaceURI())) {
        for (NestedBinding binding : myBindings) {
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

    LinkedHashMap<NestedBinding, List<Element>> data = null;
    nextNode:
    for (Content content : element.getContent()) {
      if (content instanceof Comment) {
        continue;
      }

      for (NestedBinding binding : myBindings) {
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
              data = new LinkedHashMap<>();
            }
            data.computeIfAbsent(binding, it -> new ArrayList<>()).add(child);
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
      for (NestedBinding binding : data.keySet()) {
        if (accessorNameTracker != null) {
          accessorNameTracker.add(binding.getAccessor().getName());
        }
        ((MultiNodeBinding)binding).deserializeList(result, data.get(binding));
      }
    }
  }

  @Override
  public final boolean isBoundTo(@NotNull Element element) {
    return element.getName().equals(myTagName);
  }

  private static @NotNull String getTagName(@NotNull Class<?> aClass) {
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

  private static @Nullable String getTagNameFromAnnotation(@NotNull Class<?> aClass) {
    Tag tag = aClass.getAnnotation(Tag.class);
    return tag != null && !tag.value().isEmpty() ? tag.value() : null;
  }

  public static @NotNull List<MutableAccessor> getAccessors(@NotNull Class<?> aClass) {
    return PROPERTY_COLLECTOR.collect(aClass);
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

  private static final class XmlSerializerPropertyCollector extends PropertyCollector {
    private final Map<Class<?>, List<MutableAccessor>> accessorCache = new ConcurrentHashMap<>();

    XmlSerializerPropertyCollector() {
      super(PropertyCollector.COLLECT_ACCESSORS);
    }

    @Override
    public @NotNull List<MutableAccessor> collect(@NotNull Class<?> aClass) {
      return accessorCache.computeIfAbsent(aClass, aClass1 -> {
        List<MutableAccessor> result = super.collect(aClass1);
        if (result.isEmpty() && !isAssertBindings(aClass)) {
          //noinspection deprecation
          if (JDOMExternalizable.class.isAssignableFrom(aClass)) {
            LOG.error("Do not compute bindings for JDOMExternalizable: " + aClass.getName());
          }
          else if (aClass.isEnum()) {
            LOG.error("Do not compute bindings for enum: " + aClass.getName());
          }
          else if (aClass == String.class) {
            LOG.error("Do not compute bindings for String");
          }
          LOG.warn("no accessors for " + aClass.getName());
        }
        return result;
      });
    }

    @Override
    protected boolean isAnnotatedAsTransient(@NotNull AnnotatedElement element) {
      return element.isAnnotationPresent(Transient.class);
    }

    @Override
    protected boolean hasStoreAnnotations(@NotNull AccessibleObject element) {
      //noinspection deprecation
      return element.isAnnotationPresent(OptionTag.class) ||
             element.isAnnotationPresent(Tag.class) ||
             element.isAnnotationPresent(Attribute.class) ||
             element.isAnnotationPresent(Property.class) ||
             element.isAnnotationPresent(Text.class) ||
             element.isAnnotationPresent(CollectionBean.class) ||
             element.isAnnotationPresent(MapAnnotation.class) ||
             element.isAnnotationPresent(XMap.class) ||
             element.isAnnotationPresent(XCollection.class) ||
             element.isAnnotationPresent(AbstractCollection.class);
    }

    @Override
    public void clearSerializationCaches() {
      super.clearSerializationCaches();
      accessorCache.clear();
    }
  }

  public String toString() {
    return "BeanBinding[" + myBeanClass.getName() + ", tagName=" + myTagName + "]";
  }

  private static @NotNull NestedBinding createBinding(@NotNull MutableAccessor accessor, @NotNull Serializer serializer, @NotNull Property.Style propertyStyle) {
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
      return ((JDOMElementBinding)binding);
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

  public static void clearSerializationCaches() {
    PROPERTY_COLLECTOR.clearSerializationCaches();
  }
}

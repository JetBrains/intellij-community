// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.serialization.ClassUtil;
import com.intellij.serialization.MutableAccessor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.xml.dom.XmlElement;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jdom.Content;
import org.jdom.Element;
import org.jdom.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

abstract class AbstractCollectionBinding extends NotNullDeserializeBinding implements MultiNodeBinding, NestedBinding {
  private final MutableAccessor myAccessor;
  private List<Binding> itemBindings;

  protected final Class<?> itemType;
  @SuppressWarnings("deprecation") private final @Nullable AbstractCollection annotation;
  protected final @Nullable XCollection newAnnotation;

  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private Serializer serializer;

  AbstractCollectionBinding(@NotNull Class<?> elementType, @Nullable MutableAccessor accessor) {
    myAccessor = accessor;

    itemType = elementType;
    newAnnotation = accessor == null ? null : accessor.getAnnotation(XCollection.class);
    //noinspection deprecation
    annotation = newAnnotation == null ? (accessor == null ? null : accessor.getAnnotation(AbstractCollection.class)) : null;
  }

  @Override
  public final @NotNull MutableAccessor getAccessor() {
    return myAccessor;
  }

  protected final boolean isSortOrderedSet() {
    return annotation == null || annotation.sortOrderedSet();
  }

  @Override
  public final boolean isMulti() {
    return true;
  }

  @Override
  public final void init(@NotNull Type originalType, @NotNull Serializer serializer) {
    this.serializer = serializer;
  }

  private boolean isSurroundWithTag() {
    return newAnnotation == null && (annotation == null || annotation.surroundWithTag());
  }

  private @NotNull Class<?> @NotNull [] getElementTypes() {
    if (newAnnotation != null) {
      return newAnnotation.elementTypes();
    }
    return annotation == null ? ArrayUtil.EMPTY_CLASS_ARRAY : annotation.elementTypes();
  }

  private @Nullable Binding getItemBinding(@NotNull Class<?> aClass) {
    return ClassUtil.isPrimitive(aClass) ? null : serializer.getRootBinding(aClass, aClass);
  }

  private synchronized @NotNull List<Binding> getItemBindings() {
    if (itemBindings == null) {
      Binding binding = getItemBinding(itemType);
      Class<?>[] elementTypes = getElementTypes();
      if (elementTypes.length == 0) {
        itemBindings = binding == null ? Collections.emptyList() : Collections.singletonList(binding);
      }
      else {
        itemBindings = new SmartList<>();
        if (binding != null) {
          itemBindings.add(binding);
        }

        for (Class<?> aClass : elementTypes) {
          Binding b = getItemBinding(aClass);
          if (b != null && !itemBindings.contains(b)) {
            itemBindings.add(b);
          }
        }
        if (itemBindings.isEmpty()) {
          itemBindings = Collections.emptyList();
        }
      }
    }
    return itemBindings;
  }

  private @Nullable Binding getElementBinding(@NotNull Element element) {
    for (Binding binding : getItemBindings()) {
      if (binding.isBoundTo(element)) {
        return binding;
      }
    }
    return null;
  }

  private @Nullable Binding getElementBinding(@NotNull XmlElement element) {
    for (Binding binding : getItemBindings()) {
      if (binding.isBoundTo(element)) {
        return binding;
      }
    }
    return null;
  }

  abstract @NotNull Collection<?> getIterable(@NotNull Object o);

  @Override
  public final @NotNull Object serialize(@NotNull Object object, @Nullable Object context, @Nullable SerializationFilter filter) {
    Collection<?> collection = getIterable(object);

    String tagName = isSurroundWithTag() ? getCollectionTagName(object) : null;
    if (tagName == null) {
      List<Object> result = new SmartList<>();
      if (!collection.isEmpty()) {
        for (Object item : collection) {
          ContainerUtil.addAllNotNull(result, serializeItem(item, result, filter));
        }
      }
      return result;
    }
    else {
      Element result = new Element(tagName);
      if (!collection.isEmpty()) {
        for (Object item : collection) {
          Content child = (Content)serializeItem(item, result, filter);
          if (child != null) {
            result.addContent(child);
          }
        }
      }
      return result;
    }
  }

  @Override
  public final @NotNull Object deserialize(@Nullable Object context, @NotNull Element element) {
    if (isSurroundWithTag()) {
      return doDeserializeList(context, element.getChildren());
    }
    else {
      return doDeserializeList(context, Collections.singletonList(element));
    }
  }

  @Override
  public final @NotNull Object deserialize(@Nullable Object context, @NotNull XmlElement element) {
    if (isSurroundWithTag()) {
      return doDeserializeList2(context, element.children);
    }
    else {
      return doDeserializeList2(context, Collections.singletonList(element));
    }
  }

  @Override
  public final @NotNull Object deserializeList(@Nullable Object context, @NotNull List<Element> elements) {
    if (!isSurroundWithTag()) {
      return doDeserializeList(context, elements);
    }

    assert elements.size() == 1;
    Element element = elements.get(0);
    return doDeserializeList(context == null && element.getName().equals(Constants.SET) ? new HashSet<>() : context, element.getChildren());
  }

  @Override
  public @Nullable Object deserializeList2(@Nullable Object context, @NotNull List<XmlElement> elements) {
    if (!isSurroundWithTag()) {
      return doDeserializeList2(context, elements);
    }

    assert elements.size() == 1;
    XmlElement element = elements.get(0);
    return doDeserializeList2(context == null && element.name.equals(Constants.SET) ? new HashSet<>() : context, element.children);
  }

  protected abstract @NotNull Object doDeserializeList(@Nullable Object context, @NotNull List<Element> elements);

  protected abstract @NotNull Object doDeserializeList2(@Nullable Object context, @NotNull List<XmlElement> elements);

  private @Nullable Object serializeItem(@Nullable Object value, Object context, @Nullable SerializationFilter filter) {
    if (value == null) {
      LOG.warn("Collection " + myAccessor + " contains 'null' object");
      return null;
    }

    Binding binding = getItemBinding(value.getClass());
    if (binding == null) {
      String elementName = getElementName();
      if (StringUtil.isEmpty(elementName)) {
        throw new Error("elementName must be not empty");
      }

      Element serializedItem = new Element(elementName);
      String attributeName = getValueAttributeName();
      String serialized = XmlSerializerImpl.convertToString(value);
      if (attributeName.isEmpty()) {
        if (!serialized.isEmpty()) {
          serializedItem.addContent(new Text(serialized));
        }
      }
      else {
        serializedItem.setAttribute(attributeName, JDOMUtil.removeControlChars(serialized));
      }
      return serializedItem;
    }
    else {
      return binding.serialize(value, context, filter);
    }
  }

  protected final Object deserializeItem(@NotNull Element node, @Nullable Object context) {
    Binding binding = getElementBinding(node);
    if (binding == null) {
      String attributeName = getValueAttributeName();
      String value;
      if (attributeName.isEmpty()) {
        value = XmlSerializerImpl.getTextValue(node, "");
      }
      else {
        value = node.getAttributeValue(attributeName);
      }
      return XmlSerializerImpl.convert(value, itemType);
    }
    else {
      return binding.deserializeUnsafe(context, node);
    }
  }

  protected final Object deserializeItem(@NotNull XmlElement node, @Nullable Object context) {
    Binding binding = getElementBinding(node);
    if (binding == null) {
      String attributeName = getValueAttributeName();
      String value;
      if (attributeName.isEmpty()) {
        value = node.content;
      }
      else {
        value = node.getAttributeValue(attributeName);
      }
      return XmlSerializerImpl.convert(value, itemType);
    }
    else {
      return binding.deserializeUnsafe(context, node);
    }
  }

  private @NotNull String getElementName() {
    if (newAnnotation != null) {
      return newAnnotation.elementName();
    }
    return annotation == null ? Constants.OPTION : annotation.elementTag();
  }

  private @NotNull String getValueAttributeName() {
    if (newAnnotation != null) {
      return newAnnotation.valueAttributeName();
    }
    return annotation == null ? Constants.VALUE : annotation.elementValueAttribute();
  }

  @Override
  public final boolean isBoundTo(@NotNull Element element) {
    if (isSurroundWithTag()) {
      return element.getName().equals(getCollectionTagName(null));
    }
    else if (getItemBindings().isEmpty()) {
      return element.getName().equals(getElementName());
    }
    else {
      return getElementBinding(element) != null;
    }
  }

  @Override
  public final boolean isBoundTo(@NotNull XmlElement element) {
    if (isSurroundWithTag()) {
      return element.name.equals(getCollectionTagName(null));
    }
    else if (getItemBindings().isEmpty()) {
      return element.name.equals(getElementName());
    }
    else {
      for (Binding binding : getItemBindings()) {
        if (binding.isBoundTo(element)) {
          return true;
        }
      }
      return false;
    }
  }

  protected abstract @NotNull String getCollectionTagName(@Nullable Object target);
}

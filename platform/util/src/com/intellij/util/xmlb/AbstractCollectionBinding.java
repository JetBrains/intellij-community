/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.util.xmlb;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
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
import java.util.List;

abstract class AbstractCollectionBinding extends NotNullDeserializeBinding implements MultiNodeBinding {
  private List<Binding> itemBindings;

  protected final Class<?> itemType;
  @Nullable
  private final AbstractCollection annotation;
  @Nullable
  protected final XCollection newAnnotation;

  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private Serializer serializer;

  public AbstractCollectionBinding(@NotNull Class elementType, @Nullable MutableAccessor accessor) {
    super(accessor);

    itemType = elementType;
    newAnnotation = accessor == null ? null : accessor.getAnnotation(XCollection.class);
    annotation = newAnnotation == null ? (accessor == null ? null : accessor.getAnnotation(AbstractCollection.class)) : null;
  }

  protected boolean isSortOrderedSet() {
    return annotation == null || annotation.sortOrderedSet();
  }

  @Override
  public boolean isMulti() {
    return true;
  }

  @Override
  public void init(@NotNull Type originalType, @NotNull Serializer serializer) {
    this.serializer = serializer;
  }

  private boolean isSurroundWithTag() {
    return newAnnotation == null && (annotation == null || annotation.surroundWithTag());
  }

  @NotNull
  private Class<?>[] getElementTypes() {
    if (newAnnotation != null) {
      return newAnnotation.elementTypes();
    }
    return annotation == null ? ArrayUtil.EMPTY_CLASS_ARRAY : annotation.elementTypes();
  }

  @NotNull
  private synchronized List<Binding> getElementBindings() {
    if (itemBindings == null) {
      Binding binding = serializer.getBinding(itemType);
      Class<?>[] elementTypes = getElementTypes();
      if (elementTypes.length == 0) {
        itemBindings = binding == null ? Collections.<Binding>emptyList() : Collections.singletonList(binding);
      }
      else {
        itemBindings = new SmartList<Binding>();
        if (binding != null) {
          itemBindings.add(binding);
        }

        for (Class<?> aClass : elementTypes) {
          Binding b = serializer.getBinding(aClass);
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

  @Nullable
  private Binding getElementBinding(@NotNull Element element) {
    for (Binding binding : getElementBindings()) {
      if (binding.isBoundTo(element)) {
        return binding;
      }
    }
    return null;
  }

  @NotNull
  abstract Object processResult(@NotNull Collection result, @Nullable Object target);

  @NotNull
  abstract Collection<Object> getIterable(@NotNull Object o);

  @Nullable
  @Override
  public Object serialize(@NotNull Object o, @Nullable Object context, @Nullable SerializationFilter filter) {
    Collection<Object> collection = getIterable(o);

    String tagName = getTagName(o);
    if (tagName == null) {
      List<Object> result = new SmartList<Object>();
      if (!ContainerUtil.isEmpty(collection)) {
        for (Object item : collection) {
          ContainerUtil.addAllNotNull(result, serializeItem(item, result, filter));
        }
      }
      return result;
    }
    else {
      Element result = new Element(tagName);
      if (!ContainerUtil.isEmpty(collection)) {
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

  @Nullable
  @Override
  public Object deserializeList(@Nullable Object context, @NotNull List<Element> elements) {
    Collection result;
    if (getTagName(context) == null) {
      if (context instanceof Collection) {
        result = (Collection)context;
        result.clear();
      }
      else {
        result = new SmartList();
      }
      for (Element node : elements) {
        //noinspection unchecked
        result.add(deserializeItem(node, context));
      }

      if (result == context) {
        return result;
      }
    }
    else {
      assert elements.size() == 1;
      result = deserializeSingle(context, elements.get(0));
    }
    return processResult(result, context);
  }

  @Nullable
  private Object serializeItem(@Nullable Object value, Object context, @Nullable SerializationFilter filter) {
    if (value == null) {
      LOG.warn("Collection " + myAccessor + " contains 'null' object");
      return null;
    }

    Binding binding = serializer.getBinding(value.getClass());
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
        serializedItem.setAttribute(attributeName, XmlSerializerImpl.removeControlChars(serialized));
      }
      return serializedItem;
    }
    else {
      return binding.serialize(value, context, filter);
    }
  }

  private Object deserializeItem(@NotNull Element node, @Nullable Object context) {
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

  @NotNull
  private String getElementName() {
    if (newAnnotation != null) {
      return newAnnotation.elementName();
    }
    return annotation == null ? Constants.OPTION : annotation.elementTag();
  }

  @NotNull
  private String getValueAttributeName() {
    if (newAnnotation != null) {
      return newAnnotation.valueAttributeName();
    }
    return annotation == null ? Constants.VALUE : annotation.elementValueAttribute();
  }

  @Override
  @NotNull
  public Object deserialize(@Nullable Object context, @NotNull Element element) {
    Collection result;
    if (getTagName(context) == null) {
      if (context instanceof Collection) {
        result = (Collection)context;
        result.clear();
      }
      else {
        result = new SmartList();
      }

      //noinspection unchecked
      result.add(deserializeItem(element, context));

      if (result == context) {
        return result;
      }
    }
    else {
      result = deserializeSingle(context, element);
    }
    //noinspection unchecked
    return processResult(result, context);
  }

  @NotNull
  private Collection deserializeSingle(Object context, @NotNull Element node) {
    Collection result = createCollection(node.getName());
    for (Element child : node.getChildren()) {
      //noinspection unchecked
      result.add(deserializeItem(child, context));
    }
    return result;
  }

  protected Collection createCollection(@NotNull String tagName) {
    return new SmartList();
  }

  @Override
  public boolean isBoundTo(@NotNull Element element) {
    String tagName = getTagName(element);
    if (tagName != null) {
      return element.getName().equals(tagName);
    }

    if (getElementBindings().isEmpty()) {
      return element.getName().equals(getElementName());
    }
    else {
      return getElementBinding(element) != null;
    }
  }

  @Nullable
  private String getTagName(@Nullable Object target) {
    return isSurroundWithTag() ? getCollectionTagName(target) : null;
  }

  protected abstract String getCollectionTagName(@Nullable Object target);
}

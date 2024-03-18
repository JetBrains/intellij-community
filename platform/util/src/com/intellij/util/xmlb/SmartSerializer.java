// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb;

import com.intellij.util.ThreeState;
import com.intellij.util.containers.CollectionFactory;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public final class SmartSerializer {
  private Set<String> serializedAccessorNameTracker;
  private Object2FloatMap<String> orderedBindings;
  private final SerializationFilter mySerializationFilter;

  private SmartSerializer(boolean trackSerializedNames, boolean useSkipEmptySerializationFilter) {
    serializedAccessorNameTracker = trackSerializedNames ? CollectionFactory.createSmallMemoryFootprintLinkedSet() : null;

    mySerializationFilter = useSkipEmptySerializationFilter ?
                            new SkipEmptySerializationFilter() {
                              @Override
                              protected ThreeState accepts(@NotNull String name, @NotNull Object beanValue) {
                                return serializedAccessorNameTracker != null && serializedAccessorNameTracker.contains(name) ? ThreeState.YES : ThreeState.UNSURE;
                              }
                            } :
                            new SkipDefaultValuesSerializationFilters() {
                              @Override
                              public boolean accepts(@NotNull Accessor accessor, @NotNull Object bean) {
                                if (serializedAccessorNameTracker != null && serializedAccessorNameTracker.contains(accessor.getName())) {
                                  return true;
                                }
                                return super.accepts(accessor, bean);
                              }
                            };
  }

  public SmartSerializer() {
    this(true, false);
  }

  public static @NotNull SmartSerializer skipEmptySerializer() {
    return new SmartSerializer(true, true);
  }

  public void readExternal(@NotNull Object bean, @NotNull Element element) {
    if (serializedAccessorNameTracker != null) {
      serializedAccessorNameTracker.clear();
      orderedBindings = null;
    }

    BeanBinding beanBinding = getBinding(bean);
    assert beanBinding.bindings != null;
    BeanBindingKt.deserializeJdomIntoBean(bean, element, beanBinding.bindings, serializedAccessorNameTracker);

    if (serializedAccessorNameTracker != null) {
      orderedBindings = beanBinding.computeBindingWeights$intellij_platform_util(serializedAccessorNameTracker);
    }
  }

  public void writeExternal(@NotNull Object bean, @NotNull Element element) {
    writeExternal(bean, element, true);
  }

  public void writeExternal(@NotNull Object bean, @NotNull Element element, boolean preserveCompatibility) {
    BeanBinding binding = getBinding(bean);
    if (preserveCompatibility && orderedBindings != null) {
      binding.sortBindings(orderedBindings);
    }

    if (preserveCompatibility || serializedAccessorNameTracker == null) {
      binding.serializeProperties(bean, element, mySerializationFilter);
    }
    else {
      Set<String> oldTracker = serializedAccessorNameTracker;
      try {
        serializedAccessorNameTracker = null;
        binding.serializeProperties(bean, element, mySerializationFilter);
      }
      finally {
        serializedAccessorNameTracker = oldTracker;
      }
    }
  }

  private static @NotNull BeanBinding getBinding(@NotNull Object bean) {
    return (BeanBinding)XmlSerializerImpl.serializer.getRootBinding(bean.getClass());
  }
}
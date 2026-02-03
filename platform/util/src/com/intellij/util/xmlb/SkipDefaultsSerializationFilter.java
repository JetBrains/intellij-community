// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.ThreeState;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.lang.reflect.Method;

/**
 * If class doesn't provide "equals" implementation, will be compared by serializable members.
 */
public class SkipDefaultsSerializationFilter extends SkipDefaultValuesSerializationFilters {
  /**
   * Use {@link com.intellij.configurationStore.XmlSerializer#serialize(Object)} instead of creating own filter.
   */
  @TestOnly
  @ApiStatus.Internal
  public SkipDefaultsSerializationFilter() {
  }

  public SkipDefaultsSerializationFilter(Object... defaultBeans) {
    super(defaultBeans);
  }

  boolean equal(@Nullable Binding binding, @Nullable Object currentValue, @Nullable Object defaultValue) {
    if (defaultValue instanceof Element && currentValue instanceof Element) {
      return JDOMUtil.areElementsEqual((Element)currentValue, (Element)defaultValue);
    }

    if (currentValue == defaultValue) {
      return true;
    }
    if (currentValue == null || defaultValue == null) {
      return false;
    }

    if (binding instanceof TagBinding) {
      Binding referencedBinding = ((TagBinding)binding).binding;
      if (referencedBinding instanceof BeanBinding) {
        BeanBinding classBinding = (BeanBinding)referencedBinding;
        ThreeState compareByFields = classBinding.compareByFields;
        if (compareByFields == ThreeState.UNSURE) {
          Method method = null;
          try {
            method = classBinding.beanClass.getDeclaredMethod("equals", Object.class);
          }
          catch (NoSuchMethodException ignore) {
          }
          compareByFields = method == null ? ThreeState.YES : ThreeState.NO;
          classBinding.compareByFields = compareByFields;
        }

        if (compareByFields == ThreeState.YES) {
          return classBinding.equalByFields(currentValue, defaultValue, this);
        }
      }
    }

    return Comparing.equal(currentValue, defaultValue);
  }
}

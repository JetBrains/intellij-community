/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.util.xmlb;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ThreeState;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * If class doesn't provide "equals" implementation, will be compared by serializable members.
 */
public class SkipDefaultsSerializationFilter extends SkipDefaultValuesSerializationFilters {
  boolean equal(@NotNull Binding binding, @NotNull Object bean) {
    Accessor accessor = binding.getAccessor();
    return equal(binding, accessor.read(bean), accessor.read(getDefaultBean(bean)));
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

    if (binding instanceof BasePrimitiveBinding) {
      Binding referencedBinding = ((BasePrimitiveBinding)binding).myBinding;
      if (referencedBinding instanceof BeanBinding) {
        BeanBinding classBinding = (BeanBinding)referencedBinding;
        ThreeState compareByFields = classBinding.compareByFields;
        if (compareByFields == ThreeState.UNSURE) {
          compareByFields = ReflectionUtil.getDeclaredMethod(classBinding.myBeanClass, "equals", Object.class) == null ? ThreeState.YES : ThreeState.NO;

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

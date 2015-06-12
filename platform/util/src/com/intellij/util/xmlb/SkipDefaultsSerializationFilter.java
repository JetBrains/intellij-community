/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
public final class SkipDefaultsSerializationFilter extends SkipDefaultValuesSerializationFilters {
  boolean equal(@NotNull Binding binding, @NotNull Object bean) {
    Accessor accessor = binding.getAccessor();
    return equal(binding, accessor.read(bean), accessor.read(getDefaultBean(bean)));
  }

  boolean equal(@Nullable Binding binding, @Nullable Object currentValue, @Nullable Object defaultValue) {
    if (defaultValue instanceof Element && currentValue instanceof Element) {
      return JDOMUtil.areElementsEqual((Element)currentValue, (Element)defaultValue);
    }
    else {
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
}

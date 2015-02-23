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
import com.intellij.util.ThreeState;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public final class SkipDefaultsSerializationFilter extends SkipDefaultValuesSerializationFilters {
  private Map<Class<?>, ThreeState> hasEqualMethod;

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
          Class<?> referencedBeanClass = ((BeanBinding)referencedBinding).myBeanClass;
          ThreeState compareByFields;
          if (hasEqualMethod == null) {
            compareByFields = null;
            hasEqualMethod = new THashMap<Class<?>, ThreeState>();
          }
          else {
            compareByFields = hasEqualMethod.get(referencedBeanClass);
          }

          if (compareByFields == null) {
            try {
              referencedBeanClass.getDeclaredMethod("equals", Object.class);
              compareByFields = ThreeState.NO;
              hasEqualMethod.put(referencedBeanClass, compareByFields);
            }
            catch (NoSuchMethodException ignored) {
              compareByFields = ThreeState.YES;
              hasEqualMethod.put(referencedBeanClass, compareByFields);
            }
            catch (Exception e) {
              BeanBinding.LOG.warn(e);
            }
          }

          if (compareByFields == ThreeState.YES) {
            return ((BeanBinding)referencedBinding).equalByFields(currentValue, defaultValue, this);
          }
        }
      }

      return Comparing.equal(currentValue, defaultValue);
    }
  }
}

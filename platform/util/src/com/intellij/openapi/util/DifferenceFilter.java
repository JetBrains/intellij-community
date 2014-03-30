/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;

/**
 * @author peter
*/
public class DifferenceFilter<T> implements DefaultJDOMExternalizer.JDOMFilter {
  private final T myThisSettings;
  private final T myParentSettings;

  public DifferenceFilter(final T object, final T parentObject) {
    myThisSettings = object;
    myParentSettings = parentObject;
  }

  @Override
  public boolean isAccept(@NotNull Field field) {
    try {
      Object thisValue = field.get(myThisSettings);
      Object parentValue = field.get(myParentSettings);
      return !Comparing.equal(thisValue, parentValue);
    }
    catch (Throwable e) {
      return true;
    }
  }
}

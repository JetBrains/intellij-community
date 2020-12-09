// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.function.Predicate;

@SuppressWarnings("deprecation")
public class DifferenceFilter<T> implements Predicate<Field>, DefaultJDOMExternalizer.JDOMFilter {
  private final T myThisSettings;
  private final T myParentSettings;

  public DifferenceFilter(final T object, final T parentObject) {
    myThisSettings = object;
    myParentSettings = parentObject;
  }

  @Override
  public boolean test(@NotNull Field field) {
    try {
      Object thisValue = field.get(myThisSettings);
      Object parentValue = field.get(myParentSettings);
      return !Comparing.equal(thisValue, parentValue);
    }
    catch (Throwable e) {
      return true;
    }
  }

  @Override
  public boolean isAccept(@NotNull Field field) {
    return test(field);
  }
}

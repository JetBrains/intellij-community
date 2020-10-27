/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.model;

import com.android.tools.idea.gradle.dsl.parser.ModificationAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Represents a cached value, this class encapsulates the logic to produce the value (stored as Object) and a
 * ModificationAware object that is used to invalidate the stored object.
 *
 * @param <T> type of ModificationAware object
 */
public class CachedValue<T extends ModificationAware> {
  @NotNull private final T myDependent;
  @NotNull private final Function<T, Object> myProducer;
  private long myCachedModificationCount = -1;
  @Nullable private Object myCachedValue;

  /**
   * @param dependent the object that should be used to invalidate this value.
   * @param producer  the function used to produce a new value if the cached value is invalid. The dependent is passed as an argument to
   *                  this producer.
   */
  public CachedValue(@NotNull T dependent, @NotNull Function<T, Object> producer) {
    myDependent = dependent;
    myProducer = producer;
  }

  /**
   * @return the cached value if present or runs the producing computation, caches the new value and returns it.
   */
  @Nullable
  public Object getValue() {
    long currentCount = myDependent.getModificationCount();
    if (currentCount > myCachedModificationCount) {
      updateValue();
    }

    return myCachedValue;
  }

  /**
   * Forcefully invalidates the cached value.
   */
  public void clear() {
    myCachedModificationCount = -1;
    myCachedValue = null;
  }

  private void updateValue() {
    myCachedValue = myProducer.apply(myDependent);
    myCachedModificationCount = myDependent.getModificationCount();
  }
}

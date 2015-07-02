/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public class Ref<T> {
  private T myValue;

  public Ref() { }

  public Ref(@Nullable T value) {
    myValue = value;
  }

  public boolean isNull() {
    return myValue == null;
  }

  public T get() {
    return myValue;
  }

  public void set(@Nullable T value) {
    myValue = value;
  }

  public boolean setIfNull(@Nullable T value) {
    if (myValue == null) {
      myValue = value;
      return true;
    }
    return false;
  }

  @NotNull
  public static <T> Ref<T> create() {
    return new Ref<T>();
  }

  public static <T> Ref<T> create(@Nullable T value) {
    return new Ref<T>(value);
  }

  @Nullable
  public static <T> T deref(@Nullable Ref<T> ref) {
    return ref == null ? null : ref.get();
  } 

  @Override
  public String toString() {
    return String.valueOf(myValue);
  }
}

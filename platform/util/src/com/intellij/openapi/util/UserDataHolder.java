/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * Allows to store custom user data within a model object. This might be preferred to an explicit Map with model objects as keys and
 * custom data in values because this allows the data to be garbage-collected together with the values.
 */
public interface UserDataHolder {
  /**
   * @return a user data value associated with this object. Doesn't require read action.
   */
  @Nullable
  <T> T getUserData(@NotNull Key<T> key);

  /**
   * Add a new user data value to this object. Doesn't require write action.
   */
  <T> void putUserData(@NotNull Key<T> key, @Nullable T value);
}
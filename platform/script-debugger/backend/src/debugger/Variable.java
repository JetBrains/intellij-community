/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.values.Value;

public interface Variable {
  /**
   * @return whether it is possible to read this variable
   */
  boolean isReadable();

  /**
   * Returns the value of this variable.
   *
   * @return a Value corresponding to this variable. {@code null} if the property has accessor descriptor
   * @see #isReadable()
   */
  @Nullable
  Value getValue();

  void setValue(Value value);

  @NotNull
  String getName();

  /**
   * @return whether it is possible to modify this variable
   */
  boolean isMutable();

  @Nullable
  ValueModifier getValueModifier();
}
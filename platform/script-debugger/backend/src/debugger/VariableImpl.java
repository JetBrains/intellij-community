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

public class VariableImpl implements Variable {
  protected volatile Value value;
  private final String name;

  private final ValueModifier valueModifier;

  public VariableImpl(@NotNull String name, @Nullable Value value, @Nullable ValueModifier valueModifier) {
    this.name = name;
    this.value = value;
    this.valueModifier = valueModifier;
  }

  public VariableImpl(@NotNull String name, @NotNull Value value) {
    this(name, value, null);
  }

  @Nullable
  @Override
  public final ValueModifier getValueModifier() {
    return valueModifier;
  }

  @NotNull
  @Override
  public final String getName() {
    return name;
  }

  @Nullable
  @Override
  public final Value getValue() {
    return value;
  }

  @Override
  public void setValue(Value value) {
    this.value = value;
  }

  @Override
  public boolean isMutable() {
    return valueModifier != null;
  }

  @Override
  public boolean isReadable() {
    return true;
  }

  @Override
  public String toString() {
    return "[Variable: name=" + getName() + ", value=" + getValue() + ']';
  }
}
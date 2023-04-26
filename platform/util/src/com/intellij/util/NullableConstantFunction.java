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
package com.intellij.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 * Deprecated. Use {@link java.util.function.Function} instead.
 */
@ApiStatus.Obsolete
public final class NullableConstantFunction<Param, Result> implements NullableFunction<Param, Result> {
  private final Result value;

  public NullableConstantFunction(@Nullable Result value) {
    this.value = value;
  }

  @Nullable
  @Override
  public Result fun(Param param) {
    return value;
  }
}

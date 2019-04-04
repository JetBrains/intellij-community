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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface MutableAccessor extends Accessor {
  void set(@NotNull Object host, @Nullable Object value);

  void setBoolean(@NotNull Object host, boolean value);

  void setInt(@NotNull Object host, int value);

  void setShort(@NotNull Object host, short value);

  void setLong(@NotNull Object host, long value);

  void setDouble(@NotNull Object host, double value);

  void setFloat(@NotNull Object host, float value);
}

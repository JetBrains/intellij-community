// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization;

import com.intellij.util.xmlb.Accessor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface MutableAccessor extends Accessor {
  void set(@NotNull Object host, @Nullable Object value);

  void setBoolean(@NotNull Object host, boolean value);

  void setInt(@NotNull Object host, int value);

  void setShort(@NotNull Object host, short value);

  void setLong(@NotNull Object host, long value);

  void setDouble(@NotNull Object host, double value);

  void setFloat(@NotNull Object host, float value);
}

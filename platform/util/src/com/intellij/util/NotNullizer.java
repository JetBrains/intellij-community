// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Has its own {@code null}-object instance, which is hidden from outside world, preventing accidental &quot;unwrapping&quot;.
 */
public final class NotNullizer {

  private final Object myNull;

  @Contract(pure = true)
  public NotNullizer(@NotNull String name) {
    myNull = ObjectUtils.sentinel(name);
  }

  @Contract(pure = true)
  @NotNull
  private <T> T fakeNull() {
    //noinspection unchecked
    return (T)myNull;
  }

  /**
   * &quot;Wraps&quot; {@code null} with {@code null}-object.
   * <p/>
   * Useful when some generic data structure A does not allow {@code null}s,
   * but there is a need to implement another structure B on top of A which should support {@code null}s.
   * <br/>
   * Returned value should never be presented to clients of structure B,
   * and it must be &quot;unwrapped&quot; back with {@link #nullize(Object)}.
   * <br/>
   * Casting the value to anything but {@link Object} will result in {@link ClassCastException},
   * this means the value cannot be used anywhere where {@link T} is a specific type different from {@link Object}.
   *
   * @return {@code null} wrapper if value is {@code null}, otherwise original value
   * @see #nullize(Object)
   */
  @Contract(value = "!null -> param1", pure = true)
  @NotNull
  public <T> T notNullize(@Nullable T value) {
    if (value == null) {
      return fakeNull();
    }
    else {
      return value;
    }
  }

  /**
   * &quot;Unwraps&quot; {@code null} from the value returned by {@link #notNullize(Object)}.
   *
   * @return {@code null} if value is the {@code null} wrapper, otherwise original value
   * @see #notNullize(Object)
   */
  @Contract(pure = true)
  @Nullable
  public <T> T nullize(@NotNull T value) {
    return value == myNull ? null : value;
  }
}

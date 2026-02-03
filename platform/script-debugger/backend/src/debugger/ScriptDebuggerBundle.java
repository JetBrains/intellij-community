// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.debugger;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.*;

import java.util.function.Supplier;

@ApiStatus.Internal
public final class ScriptDebuggerBundle {
  private static final @NonNls String BUNDLE = "messages.ScriptDebuggerBundle";
  private static final DynamicBundle INSTANCE = new DynamicBundle(ScriptDebuggerBundle.class, BUNDLE);

  private ScriptDebuggerBundle() {
  }

  public static @NotNull @Nls
  String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

  public static @NotNull Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
                                                              Object @NotNull ... params) {
    return INSTANCE.getLazyMessage(key, params);
  }
}

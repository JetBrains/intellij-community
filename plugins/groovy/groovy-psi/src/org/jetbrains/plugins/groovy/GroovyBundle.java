// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

public final class GroovyBundle extends DynamicBundle {

  public static final @NonNls String BUNDLE = "messages.GroovyBundle";
  private static final GroovyBundle INSTANCE = new GroovyBundle();

  private GroovyBundle() {
    super(BUNDLE);
  }

  public static @Nls @NotNull String message(@PropertyKey(resourceBundle = BUNDLE) @NotNull String key, Object @NotNull ... params) {
    if (INSTANCE.containsKey(key)) {
      return INSTANCE.getMessage(key, params);
    }
    return GroovyDeprecatedMessagesBundle.message(key, params);
  }

  public static @NotNull Supplier<@Nls @NotNull String> messagePointer(@PropertyKey(resourceBundle = BUNDLE) @NotNull String key,
                                                                       Object @NotNull ... params) {
    if (INSTANCE.containsKey(key)) {
      return INSTANCE.getLazyMessage(key, params);
    }
    return GroovyDeprecatedMessagesBundle.messagePointer(key, params);
  }
}

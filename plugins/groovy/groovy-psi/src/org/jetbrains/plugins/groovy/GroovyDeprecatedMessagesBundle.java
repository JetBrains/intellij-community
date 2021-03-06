// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

final class GroovyDeprecatedMessagesBundle extends DynamicBundle {

  private static final @NonNls String BUNDLE = "messages.GroovyDeprecatedMessagesBundle";
  private static final GroovyDeprecatedMessagesBundle INSTANCE = new GroovyDeprecatedMessagesBundle();

  private GroovyDeprecatedMessagesBundle() {
    super(BUNDLE);
  }

  public static @Nls @NotNull String message(@PropertyKey(resourceBundle = BUNDLE) @NotNull String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

  public static @NotNull Supplier<@Nls @NotNull String> messagePointer(@PropertyKey(resourceBundle = BUNDLE) @NotNull String key,
                                                                       Object @NotNull ... params) {
    return INSTANCE.getLazyMessage(key, params);
  }
}

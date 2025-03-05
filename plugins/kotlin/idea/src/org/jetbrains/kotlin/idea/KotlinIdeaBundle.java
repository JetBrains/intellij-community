// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.*;

import java.util.function.Supplier;

public final class KotlinIdeaBundle {

    private static final @NonNls String BUNDLE_FQN = "messages.KotlinIdeaBundle";
    private static final DynamicBundle BUNDLE = new DynamicBundle(KotlinIdeaBundle.class, BUNDLE_FQN);

    private KotlinIdeaBundle() {
    }

    @Nls
    public static @NotNull String message(
            @PropertyKey(resourceBundle = BUNDLE_FQN) @NotNull String key,
            @Nullable Object @NotNull ... params
    ) {
        return BUNDLE.getMessage(key, params);
    }

    public static @NotNull Supplier<@NotNull String> messagePointer(
            @PropertyKey(resourceBundle = BUNDLE_FQN) @NotNull String key,
            @Nullable Object @NotNull ... params
    ) {
        return BUNDLE.getLazyMessage(key, params);
    }
}

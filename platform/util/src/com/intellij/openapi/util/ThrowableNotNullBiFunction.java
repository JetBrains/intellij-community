// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface ThrowableNotNullBiFunction<T, U, R, E extends Throwable> {

  @NotNull R fun(@NotNull T t1, @NotNull U t2) throws E;
}


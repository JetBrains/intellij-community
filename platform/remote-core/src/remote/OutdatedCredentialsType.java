// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote;

import com.intellij.openapi.project.Project;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface OutdatedCredentialsType<V, T> {
  @NotNull Pair<CredentialsType<V>, V> transformToNewerType(@NotNull T credentials, @Nullable Project project);
}

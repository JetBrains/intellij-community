// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import com.intellij.openapi.project.Project;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface OutdatedCredentialsType<V, T> {
  @NotNull
  Pair<CredentialsType<V>, V> transformToNewerType(@NotNull T credentials, @Nullable Project project);
}

// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public interface GitAuthenticationGate {
  <T> T waitAndCompute(@NotNull Supplier<T> operation);
  void cancel();

  @Nullable
  String getSavedInput(@NotNull String key);

  void saveInput(@NotNull String key, @NotNull String value);
}

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.function.Supplier;

@ApiStatus.Internal
public final class PathExecLazyValue {
  private PathExecLazyValue() { }

  public static @NotNull Supplier<Boolean> create(@NlsSafe @NotNull String name) {
    if (Strings.containsAnyChar(name, "/\\")) {
      throw new IllegalArgumentException(name);
    }

    return new SynchronizedClearableLazy<>(() -> {
      String path = EnvironmentUtil.getValue("PATH");
      if (path != null) {
        for (String dir : StringUtil.tokenize(path, File.pathSeparator)) {
          if (new File(dir, name).canExecute()) {
            return true;
          }
        }
      }

      return false;
    });
  }
}

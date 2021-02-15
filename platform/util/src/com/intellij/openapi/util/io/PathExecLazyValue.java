// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.io;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.EnvironmentUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public final class PathExecLazyValue {
  private PathExecLazyValue() {
  }

  public static @NotNull NotNullLazyValue<Boolean> create(@NlsSafe @NotNull String name) {
    if (Strings.containsAnyChar(name, "/\\")) {
      throw new IllegalArgumentException(name);
    }

    return NotNullLazyValue.atomicLazy(() -> {
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
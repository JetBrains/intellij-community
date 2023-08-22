// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitNotInstalledException extends GitVersionIdentificationException {
  public GitNotInstalledException(@NotNull @Nls String message, @Nullable Throwable cause) {
    super(message, cause);
  }
}

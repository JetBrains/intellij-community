// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import org.jetbrains.annotations.NotNull;

public class CannotAddVcsLogWindowException extends RuntimeException {
  public CannotAddVcsLogWindowException(@NotNull String message) {
    super(message);
  }
}

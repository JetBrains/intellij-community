// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison;

import com.intellij.openapi.progress.ProcessCanceledException;

public class DiffTooBigException extends ProcessCanceledException {
  public DiffTooBigException() {
  }
}

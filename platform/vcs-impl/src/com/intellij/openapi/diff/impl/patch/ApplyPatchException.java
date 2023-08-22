// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch;

public final class ApplyPatchException extends Exception {
  public ApplyPatchException(String s) {
    super(s);
  }
}
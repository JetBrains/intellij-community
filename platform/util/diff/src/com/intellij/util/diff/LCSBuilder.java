// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.diff;

interface LCSBuilder {
  void addEqual(int length);
  void addChange(int first, int second);
}

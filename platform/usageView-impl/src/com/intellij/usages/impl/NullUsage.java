// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.usages.impl;

public final class NullUsage extends UsageAdapter {
  public static final NullUsage INSTANCE = new NullUsage();

  private NullUsage() {
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.util.io;

public class IntInlineKeyDescriptor extends InlineKeyDescriptor<Integer> {
  @Override
  public Integer fromInt(int n) {
    return n;
  }

  @Override
  public int toInt(Integer integer) {
    return integer.intValue();
  }
}

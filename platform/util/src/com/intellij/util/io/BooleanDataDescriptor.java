// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

/**
 * @author peter
 */
public final class BooleanDataDescriptor extends InlineKeyDescriptor<Boolean> {
  public static final BooleanDataDescriptor INSTANCE = new BooleanDataDescriptor();

  private BooleanDataDescriptor() {
  }

  @Override
  public Boolean fromInt(int n) {
    return n != 0;
  }

  @Override
  public int toInt(Boolean aBoolean) {
    return aBoolean == Boolean.TRUE ? 1 : 0;
  }

  @Override
  protected boolean isCompactFormat() {
    return true;
  }
}

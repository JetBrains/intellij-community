// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.struct.consts;

public class PooledConstant {
  public final int type;

  public PooledConstant(int type) {
    this.type = type;
  }

  public void resolveConstant(ConstantPool pool) { }
}

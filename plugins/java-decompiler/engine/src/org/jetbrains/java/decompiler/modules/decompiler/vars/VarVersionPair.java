// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler.vars;

import org.jetbrains.java.decompiler.modules.decompiler.exps.VarExprent;

public class VarVersionPair {

  public final int var;
  public final int version;

  private int hashCode = -1;

  public VarVersionPair(int var, int version) {
    this.var = var;
    this.version = version;
  }

  public VarVersionPair(Integer var, Integer version) {
    this.var = var;
    this.version = version;
  }

  public VarVersionPair(VarExprent var) {
    this.var = var.getIndex();
    this.version = var.getVersion();
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof VarVersionPair paar)) return false;

    return var == paar.var && version == paar.version;
  }

  @Override
  public int hashCode() {
    if (hashCode == -1) {
      hashCode = this.var * 3 + this.version;
    }
    return hashCode;
  }

  @Override
  public String toString() {
    return "(" + var + "," + version + ")";
  }
}

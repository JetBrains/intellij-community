// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler.vars;

import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.struct.gen.VarType;

import java.util.ArrayList;
import java.util.List;

public class CheckTypesResult {
  private final List<ExprentTypePair> maxTypeExprents = new ArrayList<>();
  private final List<ExprentTypePair> minTypeExprents = new ArrayList<>();

  public void addMaxTypeExprent(Exprent exprent, VarType type) {
    maxTypeExprents.add(new ExprentTypePair(exprent, type));
  }

  public void addMinTypeExprent(Exprent exprent, VarType type) {
    minTypeExprents.add(new ExprentTypePair(exprent, type));
  }

  public List<ExprentTypePair> getMaxTypeExprents() {
    return maxTypeExprents;
  }

  public List<ExprentTypePair> getMinTypeExprents() {
    return minTypeExprents;
  }

  static class ExprentTypePair {
    public final Exprent exprent;
    public final VarType type;

    ExprentTypePair(Exprent exprent, VarType type) {
      this.exprent = exprent;
      this.type = type;
    }
  }
}
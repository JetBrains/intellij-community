// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.struct;

public class StructTypePath {
  private int typePathKind;

  private int typeArgumentIndex;

  public StructTypePath(int typePathKind, int typeArgumentIndex) {
    this.typePathKind = typePathKind;
    this.typeArgumentIndex = typeArgumentIndex;
  }

  public int getTypePathKind() {
    return typePathKind;
  }

  public void setTypePathKind(int typePathKind) {
    this.typePathKind = typePathKind;
  }

  public int getTypeArgumentIndex() {
    return typeArgumentIndex;
  }

  public void setTypeArgumentIndex(int typeArgumentIndex) {
    this.typeArgumentIndex = typeArgumentIndex;
  }
}

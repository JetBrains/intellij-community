// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.struct;

public class StructTypePath {
  public enum Kind {
    ARRAY(0), NESTED(1), TYPE_WILDCARD(2), TYPE(3);

    private final int opcode;

    Kind(int opcode) {
      this.opcode = opcode;
    }

    public int getOpcode() {
      return opcode;
    }
  }

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

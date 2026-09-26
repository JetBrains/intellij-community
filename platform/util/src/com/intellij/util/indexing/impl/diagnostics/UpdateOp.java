// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.diagnostics;

final class UpdateOp {
  public enum Type {ADD, ADD_DIRECT, REMOVE}

  private final Type type;
  private final int inputId;
  private final Object value;

  UpdateOp(Type type, int id, Object value) {
    this.type = type;
    this.inputId = id;
    this.value = value;
  }

  @Override
  public String toString() {
    return "(" + type + ", " + inputId + ", " + value + ")";
  }
}

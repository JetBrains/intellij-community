// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.struct.gen;

public interface Type {
  int getType();

  int getArrayDim();

  String getValue();
}


// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.struct.gen;

import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;

import java.util.Arrays;
import java.util.List;

public interface Type {
  int getType();

  int getArrayDim();

  String getValue();

  /**
   * Checks whether this Type can be annotated. Nested types can't be annotated when the right side of the currently considered types
   * contains a reference to a static class.
   */
  default boolean isAnnotatable() {
    List<String> nestedTypes = Arrays.asList(DecompilerContext.getImportCollector().getNestedName(getValue()).split("\\."));
    if (nestedTypes.isEmpty()) return true;
    String curPath = getValue().substring(0, getValue().lastIndexOf('/') + 1) + nestedTypes.get(0) + '$';
    return ExprProcessor.canWriteNestedTypeAnnotation(curPath, nestedTypes.subList(1, nestedTypes.size()));
  }
}


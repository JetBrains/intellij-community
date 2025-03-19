// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.struct.gen.generics;

import org.jetbrains.java.decompiler.struct.gen.VarType;

import java.util.Collections;
import java.util.List;

public class GenericMethodDescriptor {
  public final List<String> typeParameters;
  public final List<List<VarType>> typeParameterBounds;
  public final List<VarType> parameterTypes;
  public final VarType returnType;
  public final List<VarType> exceptionTypes;

  public GenericMethodDescriptor(List<String> typeParameters,
                                 List<List<VarType>> typeParameterBounds,
                                 List<VarType> parameterTypes,
                                 VarType returnType,
                                 List<VarType> exceptionTypes) {
    this.typeParameters = substitute(typeParameters);
    this.typeParameterBounds = substitute(typeParameterBounds);
    this.parameterTypes = substitute(parameterTypes);
    this.returnType = returnType;
    this.exceptionTypes = substitute(exceptionTypes);
  }

  private static <T> List<T> substitute(List<T> list) {
    return list.isEmpty() ? Collections.emptyList() : list;
  }
}
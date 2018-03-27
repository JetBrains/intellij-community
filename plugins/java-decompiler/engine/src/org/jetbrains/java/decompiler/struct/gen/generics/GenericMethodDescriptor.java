// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.struct.gen.generics;

import java.util.Collections;
import java.util.List;

public class GenericMethodDescriptor {
  public final List<String> typeParameters;
  public final List<List<GenericType>> typeParameterBounds;
  public final List<GenericType> parameterTypes;
  public final GenericType returnType;
  public final List<GenericType> exceptionTypes;

  public GenericMethodDescriptor(List<String> typeParameters,
                                 List<List<GenericType>> typeParameterBounds,
                                 List<GenericType> parameterTypes,
                                 GenericType returnType,
                                 List<GenericType> exceptionTypes) {
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
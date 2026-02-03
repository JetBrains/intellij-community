// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.struct.gen.generics;

import org.jetbrains.java.decompiler.struct.gen.VarType;

import java.util.ArrayList;
import java.util.List;

public class GenericClassDescriptor {

  public VarType superclass;

  public GenericType genericType;

  public final List<VarType> superinterfaces = new ArrayList<>();

  public final List<String> fparameters = new ArrayList<>();

  public final List<List<VarType>> fbounds = new ArrayList<>();
}

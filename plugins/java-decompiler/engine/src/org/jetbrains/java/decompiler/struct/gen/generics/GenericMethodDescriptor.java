// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.struct.gen.generics;

import java.util.ArrayList;
import java.util.List;

public class GenericMethodDescriptor {

  public final List<String> fparameters = new ArrayList<>();

  public final List<List<GenericType>> fbounds = new ArrayList<>();

  public final List<GenericType> params = new ArrayList<>();

  public GenericType ret;

  public final List<GenericType> exceptions = new ArrayList<>();
}

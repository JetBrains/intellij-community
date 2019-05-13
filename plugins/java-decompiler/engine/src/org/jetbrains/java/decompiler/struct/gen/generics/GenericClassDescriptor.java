// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.struct.gen.generics;

import java.util.ArrayList;
import java.util.List;

public class GenericClassDescriptor {

  public GenericType superclass;

  public final List<GenericType> superinterfaces = new ArrayList<>();

  public final List<String> fparameters = new ArrayList<>();

  public final List<List<GenericType>> fbounds = new ArrayList<>();
}

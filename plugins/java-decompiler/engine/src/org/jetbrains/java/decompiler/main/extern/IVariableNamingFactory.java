// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.main.extern;

import org.jetbrains.java.decompiler.struct.StructMethod;

public interface IVariableNamingFactory {
  IVariableNameProvider createFactory(StructMethod structMethod);
}

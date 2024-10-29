// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.main;

import org.jetbrains.java.decompiler.main.extern.IVariableNameProvider;
import org.jetbrains.java.decompiler.main.extern.IVariableNamingFactory;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersion;
import org.jetbrains.java.decompiler.struct.StructMethod;

import java.util.Map;

public class IdentityRenamerFactory implements IVariableNamingFactory, IVariableNameProvider {
  @Override
  public IVariableNameProvider createFactory(StructMethod method) {
    return this;
  }

  @Override
  public Map<VarVersion, String> rename(Map<VarVersion, String> variables) {
    return null;
  }

  @Override
  public void addParentContext(IVariableNameProvider renamer) {
  }
}

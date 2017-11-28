// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.renamer;

import org.jetbrains.java.decompiler.main.extern.IIdentifierRenamer;

import java.util.HashMap;

public class PoolInterceptor {

  private final IIdentifierRenamer helper;

  private final HashMap<String, String> mapOldToNewNames = new HashMap<>();

  private final HashMap<String, String> mapNewToOldNames = new HashMap<>();

  public PoolInterceptor(IIdentifierRenamer helper) {
    this.helper = helper;
  }

  public void addName(String oldName, String newName) {
    mapOldToNewNames.put(oldName, newName);
    mapNewToOldNames.put(newName, oldName);
  }

  public String getName(String oldName) {
    return mapOldToNewNames.get(oldName);
  }

  public String getOldName(String newName) {
    return mapNewToOldNames.get(newName);
  }

  public IIdentifierRenamer getHelper() {
    return helper;
  }
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps;

public interface UnloadedModulesNameHolder {

  UnloadedModulesNameHolder DUMMY = new UnloadedModulesNameHolder() {

    @Override
    public boolean isUnloaded(String name) {
      return false;
    }

    @Override
    public boolean hasUnloaded() {
      return false;
    }
  };

  boolean isUnloaded(String name);

  boolean hasUnloaded();
}

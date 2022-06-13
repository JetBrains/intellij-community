// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

public interface PathCustomizer {
  CustomPaths customizePaths();

  class CustomPaths {
    public String configPath;
    public String systemPath;
    public String pluginsPath;
  }
}

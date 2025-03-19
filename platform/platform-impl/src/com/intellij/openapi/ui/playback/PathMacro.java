// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.playback;

import org.jetbrains.annotations.ApiStatus;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@ApiStatus.Internal
public final class PathMacro {
  
  private final Map<String, File> myMap = new HashMap<>();

  public PathMacro setScriptDir(File dir) {
    myMap.put("\\{script\\.dir\\}", dir);
    return this;
  }

  public PathMacro setBaseDir(File dir) {
    myMap.put("\\{base\\.dir\\}", dir);
    return this;
  }
  
  public File resolveFile(String path, File defaultDir) {
    Set<String> macros = myMap.keySet();
    String actualtPath = path;
    for (String each : macros) {
      actualtPath = actualtPath.replaceAll(each, myMap.get(each).getAbsolutePath());
    }


    File file = new File(actualtPath);
    if (!file.isAbsolute()) {
      file = new File(defaultDir, actualtPath);
    }

    return file;
  }

}

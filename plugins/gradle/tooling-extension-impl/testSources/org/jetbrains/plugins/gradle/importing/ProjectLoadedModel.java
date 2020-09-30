// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing;

import java.io.Serializable;
import java.util.Map;

public class ProjectLoadedModel implements Serializable {

  private final Map myMap;

  public ProjectLoadedModel(Map map) {
    myMap = map;
  }

  public Map getMap() {
    return myMap;
  }
}
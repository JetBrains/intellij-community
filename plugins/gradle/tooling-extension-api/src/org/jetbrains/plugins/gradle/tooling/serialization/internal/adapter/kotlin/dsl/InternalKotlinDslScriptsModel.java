// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.kotlin.dsl;

import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel;
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel;

import java.io.File;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public class InternalKotlinDslScriptsModel implements KotlinDslScriptsModel, Serializable {
  private final Map<File, KotlinDslScriptModel> myScriptModelsMap;

  public InternalKotlinDslScriptsModel(Map<File, KotlinDslScriptModel> scriptModelsMap) {
    myScriptModelsMap = new LinkedHashMap<File, KotlinDslScriptModel>(scriptModelsMap.size());
    for (Map.Entry<File, KotlinDslScriptModel> modelEntry : scriptModelsMap.entrySet()) {
      myScriptModelsMap.put(modelEntry.getKey(), new InternalKotlinDslScriptModel(modelEntry.getValue()));
    }
  }

  @Override
  public Map<File, KotlinDslScriptModel> getScriptModels() {
    return myScriptModelsMap;
  }
}

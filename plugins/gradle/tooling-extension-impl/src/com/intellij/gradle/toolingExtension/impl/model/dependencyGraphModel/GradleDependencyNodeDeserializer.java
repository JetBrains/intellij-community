// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.dependencyGraphModel;

import com.google.gson.*;
import com.intellij.openapi.externalSystem.model.project.dependencies.*;

import java.lang.reflect.Type;

public class GradleDependencyNodeDeserializer implements JsonDeserializer<DependencyNode> {

  @Override
  public DependencyNode deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
    JsonObject jsonObject = json.getAsJsonObject();
    if (jsonObject.get("scope") != null) {
      return context.deserialize(json, DependencyScopeNode.class);
    }
    else if (jsonObject.get("projectName") != null) {
      return context.deserialize(json, ProjectDependencyNodeImpl.class);
    }
    else if (jsonObject.get("module") != null) {
      return context.deserialize(json, ArtifactDependencyNodeImpl.class);
    }
    else if (jsonObject.get("path") != null) {
      return context.deserialize(json, FileCollectionDependencyNodeImpl.class);
    }
    else if (jsonObject.size() == 1 && jsonObject.get("id") != null) {
      return context.deserialize(json, ReferenceNode.class);
    }
    else {
      return context.deserialize(json, UnknownDependencyNode.class);
    }
  }
}
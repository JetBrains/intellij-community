// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter;

import org.gradle.tooling.model.idea.IdeaModuleDependency;

public final class InternalIdeaModuleDependency extends InternalIdeaDependency implements IdeaModuleDependency {
  private String targetModuleName;

  @Override
  public String getTargetModuleName() {
    return targetModuleName;
  }

  public void setTargetModuleName(String targetModuleName) {
    this.targetModuleName = targetModuleName;
  }

  @Override
  public String toString() {
    return "IdeaModuleDependency{" +
           "targetModuleName='" + targetModuleName + '\'' +
           "} " + super.toString();
  }
}

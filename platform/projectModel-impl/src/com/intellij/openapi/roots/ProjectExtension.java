// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import com.intellij.openapi.projectRoots.Sdk;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ProjectExtension {
  public void projectSdkChanged(@Nullable Sdk sdk) {
  }

  public abstract void readExternal(@NotNull Element element);

  public abstract void writeExternal(@NotNull Element element);
}
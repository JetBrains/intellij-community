// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

public interface IdeFrameTitleContributor {
  ExtensionPointName<IdeFrameTitleContributor> EP_NAME = ExtensionPointName.create("com.intellij.ideFrameTitleContributor");

  double getPriority();
  void contribute(StringBuilder builder);
}

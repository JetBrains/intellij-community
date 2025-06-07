// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.validation;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.dom.AntDomProject;
import com.intellij.util.xml.highlighting.BasicDomElementsInspection;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public abstract class AntInspection extends BasicDomElementsInspection<AntDomProject> {

  protected AntInspection() {
    super(AntDomProject.class);
  }

  @Override
  public @Nls @NotNull String getGroupDisplayName() {
    return AntBundle.message("ant.inspections.display.name");
  }
}

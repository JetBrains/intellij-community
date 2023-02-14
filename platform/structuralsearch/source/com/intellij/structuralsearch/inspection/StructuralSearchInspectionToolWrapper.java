// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.inspection;

import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class StructuralSearchInspectionToolWrapper extends LocalInspectionToolWrapper {

  public StructuralSearchInspectionToolWrapper(@NotNull Collection<@NotNull Configuration> configurations) {
    super(new StructuralSearchFakeInspection(configurations));
  }

  public int hashCode() {
    return myTool.getShortName().hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj.getClass() == StructuralSearchInspectionToolWrapper.class &&
           ((StructuralSearchInspectionToolWrapper)obj).myTool.getShortName().equals(myTool.getShortName());
  }

  @NotNull
  @Override
  public LocalInspectionToolWrapper createCopy() {
    final StructuralSearchFakeInspection inspection = (StructuralSearchFakeInspection)getTool();
    final List<Configuration> copies = new SmartList<>();
    for (Configuration configuration : inspection.getConfigurations()) {
      copies.add(configuration.copy());
    }
    return new StructuralSearchInspectionToolWrapper(copies);
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return getTool().getDisplayName();
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public @NotNull String getID() {
    return getTool().getID();
  }

  @NotNull
  @Override
  public String getGroupDisplayName() {
    return getTool().getGroupDisplayName();
  }
}

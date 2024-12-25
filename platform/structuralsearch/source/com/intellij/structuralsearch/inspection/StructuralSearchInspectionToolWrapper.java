// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  @Override
  public int hashCode() {
    return myTool.getShortName().hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj.getClass() == StructuralSearchInspectionToolWrapper.class &&
           ((StructuralSearchInspectionToolWrapper)obj).myTool.getShortName().equals(myTool.getShortName());
  }

  @Override
  public @NotNull LocalInspectionToolWrapper createCopy() {
    final StructuralSearchFakeInspection inspection = (StructuralSearchFakeInspection)getTool();
    final List<Configuration> copies = new SmartList<>();
    for (Configuration configuration : inspection.getConfigurations()) {
      copies.add(configuration.copy());
    }
    return new StructuralSearchInspectionToolWrapper(copies);
  }

  @Override
  public @NotNull String getDisplayName() {
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

  @Override
  public @NotNull String getGroupDisplayName() {
    return getTool().getGroupDisplayName();
  }

  @Override
  public boolean isCleanupTool() {
    return ((StructuralSearchFakeInspection)myTool).isCleanup();
  }
}

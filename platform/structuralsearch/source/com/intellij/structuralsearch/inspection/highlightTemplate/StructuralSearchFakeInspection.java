// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.inspection.highlightTemplate;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class StructuralSearchFakeInspection extends LocalInspectionTool {

  @NotNull private final Configuration myConfiguration;
  private InspectionProfileImpl myProfile = null;

  public StructuralSearchFakeInspection(@NotNull Configuration configuration) {
    myConfiguration = configuration;
  }

  public StructuralSearchFakeInspection(StructuralSearchFakeInspection copy) {
    myConfiguration = copy.myConfiguration;
    myProfile =  copy.myProfile;
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getDisplayName() {
    return myConfiguration.getName();
  }

  @NotNull
  @Override
  public String getShortName() {
    return myConfiguration.getUuid().toString();
  }

  @NotNull
  @Override
  public String getID() {
    // todo make configurable
    return getShortName();
  }

  @Nullable
  @Override
  public String getMainToolId() {
    return SSBasedInspection.SHORT_NAME;
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return "Structural Search";
  }

  @Nullable
  @Override
  public String getStaticDescription() {
    return "no description provided";
  }

  public void setProfile(InspectionProfileImpl profile) {
    myProfile = profile;
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.inspection.highlightTemplate;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * @author Bas Leijdekkers
 */
public class StructuralSearchFakeInspection extends LocalInspectionTool {

  private final String myShortName;
  private String myName;
  private InspectionProfileImpl myProfile = null;

  public StructuralSearchFakeInspection(String name, UUID uuid) {
    this.myName = name;
    myShortName = uuid.toString();
  }

  public StructuralSearchFakeInspection(StructuralSearchFakeInspection copy) {
    myShortName = copy.myShortName;
    myName = copy.myName;
    myProfile =  copy.myProfile;
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getDisplayName() {
    return myName;
  }

  @NotNull
  @Override
  public String getShortName() {
    return myShortName;
  }

  @NotNull
  @Override
  public String getID() {
    return myShortName;
  }

  @Nullable
  @Override
  public String getMainToolId() {
    return SSBasedInspection.SHORT_NAME;
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String[] getGroupPath() {
    return new String[]{"Structural Search"};
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

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.inspection.highlightTemplate;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.structuralsearch.inspection.StructuralSearchProfileActionProvider;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

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

  @SuppressWarnings("PatternValidation")
  @Pattern(VALID_ID_PATTERN)
  @NotNull
  @Override
  public String getID() {
    final String suppressId = myConfiguration.getSuppressId();
    if (!StringUtil.isEmpty(suppressId)) {
      return suppressId;
    }
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
    final String description = myConfiguration.getDescription();
    if (StringUtil.isEmpty(description)) {
      return "No description provided";
    }
    return description;
  }

  public void setProfile(InspectionProfileImpl profile) {
    myProfile = profile;
  }

  @Override
  public @Nullable JComponent createOptionsPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    final JButton button = new JButton("Edit Metadata...");
    button.addActionListener(e -> {
      Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(button));
      if (project == null) project = ProjectManager.getInstance().getDefaultProject();
      final InspectionToolWrapper<?,?> wrapper = myProfile.getInspectionTool(SSBasedInspection.SHORT_NAME, (Project)null);
      assert wrapper != null;
      if (StructuralSearchProfileActionProvider.saveInspection(project, (SSBasedInspection)wrapper.getTool(), myConfiguration)) {
        myProfile.getProfileManager().fireProfileChanged(myProfile);
      }
    });
    panel.add(button, BorderLayout.NORTH);
    return panel;
  }
}

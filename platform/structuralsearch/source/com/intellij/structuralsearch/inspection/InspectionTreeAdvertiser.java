// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.inspection;

import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.ui.EmptyInspectionTreeLinkProvider;
import com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.inspection.StructuralSearchProfileActionProvider.AddInspectionAction;
import com.intellij.structuralsearch.plugin.ui.SearchContext;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class InspectionTreeAdvertiser extends EmptyInspectionTreeLinkProvider {

  @Override
  @NotNull
  public @Nls String getText() {
    return SSRBundle.message("create.an.inspection");
  }

  @Override
  @NotNull
  public ActionListener getActionListener(SingleInspectionProfilePanel panel) {
    return new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final Project project = panel.getProject();
        AddInspectionAction.createAndFocusInspection(panel, false, new SearchContext(project), project);
      }
    };
  }
}

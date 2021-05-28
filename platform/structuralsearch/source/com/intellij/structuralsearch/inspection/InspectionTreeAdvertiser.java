// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.inspection;

import com.intellij.codeInspection.ex.InspectionProfileModifiableModel;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.ui.EmptyInspectionTreeLinkProvider;
import com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.SearchContext;
import com.intellij.structuralsearch.plugin.ui.StructuralSearchDialog;
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
        final InspectionProfileModifiableModel profile = panel.getProfile();

        final StructuralSearchDialog dialog = new StructuralSearchDialog(new SearchContext(project), false, true);
        if (!dialog.showAndGet()) return;
        final Configuration configuration = dialog.getConfiguration();
        if (!StructuralSearchProfileActionProvider.createNewInspection(configuration, project, profile)) return;
        panel.selectInspectionTool(configuration.getUuid().toString());
      }
    };
  }
}

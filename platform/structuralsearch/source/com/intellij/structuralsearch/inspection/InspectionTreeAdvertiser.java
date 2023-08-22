// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.inspection;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.profile.codeInspection.ui.EmptyInspectionTreeActionProvider;
import com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.inspection.StructuralSearchProfileActionProvider.AddInspectionAction;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class InspectionTreeAdvertiser extends EmptyInspectionTreeActionProvider {

  @Override
  @NotNull
  public List<AnAction> getActions(SingleInspectionProfilePanel panel) {
    return List.of(
      new AddInspectionAction(panel, SSRBundle.message("inspection.tree.create.inspection.search.template"), false),
      new AddInspectionAction(panel, SSRBundle.message("inspection.tree.create.inspection.replace.template"), true)
    );
  }
}

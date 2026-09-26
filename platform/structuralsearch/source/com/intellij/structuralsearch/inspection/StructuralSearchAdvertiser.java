// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.inspection;

import com.intellij.profile.codeInspection.ui.InspectionTreeAdvertiser;
import com.intellij.structuralsearch.SSRBundle;

import java.util.List;

public class StructuralSearchAdvertiser extends InspectionTreeAdvertiser {

  @Override
  public List<CustomGroup> getCustomGroups() {
    return List.of(
      new CustomGroup(InspectionProfileUtil.getGroup(), SSRBundle.message("inspection.tree.group.description"))
    );
  }
}

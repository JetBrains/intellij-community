/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.fixes;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

/**
* @author Bas Leijdekkers
*/
public class SuppressForTestsScopeFix extends InspectionGadgetsFix {

  private final BaseInspection myInspection;

  public SuppressForTestsScopeFix(BaseInspection inspection) {
    myInspection = inspection;
  }

  @NotNull
  @Override
  public String getName() {
    return InspectionGadgetsBundle.message("suppress.for.tests.scope.quickfix");
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getName();
  }

  @Override
  protected boolean prepareForWriting() {
    return false;
  }

  @Override
  protected void doFix(Project project, ProblemDescriptor descriptor) {
    final InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(project);
    final InspectionProfileImpl profile = (InspectionProfileImpl)profileManager.getInspectionProfile();
    final String shortName = myInspection.getShortName();
    final InspectionToolWrapper tool = profile.getInspectionTool(shortName, project);
    if (tool == null) {
      return;
    }
    final NamedScope namedScope = NamedScopesHolder.getScope(project, "Tests");
    final HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
    final HighlightDisplayLevel level = profile.getErrorLevel(key, namedScope, project);
    profile.addScope(tool, namedScope, level, false, project);
  }
}

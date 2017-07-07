/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.UnfairLocalInspectionTool;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovySuppressableInspectionTool;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;

import javax.swing.*;

/**
 * @author Maxim.Medvedev
 */
public class GrUnresolvedAccessInspection extends GroovySuppressableInspectionTool implements UnfairLocalInspectionTool {
  private static final String SHORT_NAME = "GrUnresolvedAccess";

  public boolean myHighlightIfGroovyObjectOverridden = true;
  public boolean myHighlightIfMissingMethodsDeclared = true;
  public boolean myHighlightInnerClasses = true;

  public static boolean isSuppressed(@NotNull PsiElement ref) {
    return isElementToolSuppressedIn(ref, SHORT_NAME);
  }

  public static HighlightDisplayKey findDisplayKey() {
    return HighlightDisplayKey.find(SHORT_NAME);
  }

  public static GrUnresolvedAccessInspection getInstance(PsiFile file, Project project) {
    return (GrUnresolvedAccessInspection)getInspectionProfile(project).getUnwrappedTool(SHORT_NAME, file);
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(GroovyInspectionBundle.message("highlight.if.groovy.object.methods.overridden"), "myHighlightIfGroovyObjectOverridden");
    optionsPanel.addCheckbox(GroovyInspectionBundle.message("highlight.if.missing.methods.declared"), "myHighlightIfMissingMethodsDeclared");
    optionsPanel.addCheckbox(GroovyBundle.message("highlight.constructor.calls.of.a.non.static.inner.classes.without.enclosing.instance.passed"), "myHighlightInnerClasses");
    return optionsPanel;
  }

  public static boolean isInspectionEnabled(PsiFile file, Project project) {
    return getInspectionProfile(project).isToolEnabled(findDisplayKey(), file);
  }

  public static HighlightDisplayLevel getHighlightDisplayLevel(Project project, GrReferenceElement ref) {
    return getInspectionProfile(project).getErrorLevel(findDisplayKey(), ref);
  }

  @NotNull
  private static InspectionProfile getInspectionProfile(@NotNull Project project) {
    return InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
  }

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return getDisplayText();
  }

  public static String getDisplayText() {
    return "Access to unresolved expression";
  }
}

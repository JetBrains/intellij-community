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
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.UnfairLocalInspectionTool;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovySuppressableInspectionTool;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;

/**
 * @author Maxim.Medvedev
 */
public class GroovyAccessibilityInspection extends GroovySuppressableInspectionTool implements UnfairLocalInspectionTool {
  private static final String SHORT_NAME = "GroovyAccessibility";

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return GroovyInspectionBundle.message("access.to.inaccessible.element");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  public static boolean isInspectionEnabled(GroovyFileBase file, Project project) {
    return getInspectionProfile(project).isToolEnabled(findDisplayKey(), file);
  }

  public static GroovyAccessibilityInspection getInstance(GroovyFileBase file, Project project) {
    return (GroovyAccessibilityInspection)getInspectionProfile(project).getUnwrappedTool(SHORT_NAME, file);
  }

  public static HighlightDisplayKey findDisplayKey() {
    return HighlightDisplayKey.find(SHORT_NAME);
  }

  public static HighlightDisplayLevel getHighlightDisplayLevel(Project project, GrReferenceElement ref) {
    return getInspectionProfile(project).getErrorLevel(findDisplayKey(), ref);
  }

  @NotNull
  private static InspectionProfile getInspectionProfile(@NotNull Project project) {
    return InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
  }

  public static boolean isSuppressed(PsiElement ref) {
    return isElementToolSuppressedIn(ref, SHORT_NAME);
  }

}

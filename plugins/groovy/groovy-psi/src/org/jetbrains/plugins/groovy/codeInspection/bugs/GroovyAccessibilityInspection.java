// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.UnfairLocalInspectionTool;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.GroovySuppressableInspectionTool;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;

/**
 * @author Maxim.Medvedev
 */
public final class GroovyAccessibilityInspection extends LocalInspectionTool implements UnfairLocalInspectionTool {

  private static final String SHORT_NAME = "GroovyAccessibility";

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

  private static @NotNull InspectionProfile getInspectionProfile(@NotNull Project project) {
    return InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
  }

  public static boolean isSuppressed(PsiElement ref) {
    return GroovySuppressableInspectionTool.isElementToolSuppressedIn(ref, SHORT_NAME);
  }
}

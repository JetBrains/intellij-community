// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.util.PsiUtilCore;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author Bas Leijdekkers
 */
public class IgnoreClassFix extends InspectionGadgetsFix implements LowPriorityAction {

  final Collection<String> myIgnoredClasses;
  final String myQualifiedName;
  private final String myFixName;

  public IgnoreClassFix(String qualifiedName, Collection<String> ignoredClasses, String fixName) {
    myIgnoredClasses = ignoredClasses;
    myQualifiedName = qualifiedName;
    myFixName = fixName;
  }

  @Nls
  @NotNull
  @Override
  public String getName() {
    return myFixName;
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return "Ignore for these types";
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  protected void doFix(Project project, ProblemDescriptor descriptor) {
    if (!myIgnoredClasses.add(myQualifiedName)) {
      return;
    }
    ProjectInspectionProfileManager.getInstance(project).fireProfileChanged();
    final VirtualFile vFile = PsiUtilCore.getVirtualFile(descriptor.getPsiElement());
    UndoManager.getInstance(project).undoableActionPerformed(new BasicUndoableAction(vFile) {
      @Override
      public void undo() {
        myIgnoredClasses.remove(myQualifiedName);
        ProjectInspectionProfileManager.getInstance(project).fireProfileChanged();
      }

      @Override
      public void redo() {
        myIgnoredClasses.add(myQualifiedName);
        ProjectInspectionProfileManager.getInstance(project).fireProfileChanged();
      }

      @Override
      public boolean isGlobal() {
        return true;
      }
    });
  }
}

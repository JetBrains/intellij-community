// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.util.PsiUtilCore;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class IgnoreClassFix extends InspectionGadgetsFix implements LowPriorityAction {

  final Collection<String> myIgnoredClasses;
  final String myQualifiedName;
  private final @IntentionName String myFixName;

  public IgnoreClassFix(String qualifiedName, Collection<String> ignoredClasses, @IntentionName String fixName) {
    myIgnoredClasses = ignoredClasses;
    myQualifiedName = qualifiedName;
    myFixName = fixName;
  }

  @NotNull
  @Override
  public String getName() {
    return myFixName;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("ignore.class.fix.family.name");
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
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

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    List<@NlsSafe String> updatedList = StreamEx.of(myIgnoredClasses).append(myQualifiedName).sorted().toList();
    return IntentionPreviewInfo.addListOption(updatedList, myQualifiedName,
                                              InspectionGadgetsBundle.message("options.label.ignored.classes"));
  }
}

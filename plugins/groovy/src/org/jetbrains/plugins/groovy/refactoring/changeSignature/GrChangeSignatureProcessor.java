// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.changeSignature;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.ConflictsDialogBase;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessorBase;
import com.intellij.refactoring.changeSignature.ChangeSignatureViewDescriptor;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class GrChangeSignatureProcessor extends ChangeSignatureProcessorBase {
  public static final Logger LOG =
    Logger.getInstance(GrChangeSignatureProcessor.class);

  public GrChangeSignatureProcessor(Project project, GrChangeInfoImpl changeInfo) {
    super(project, changeInfo);
  }

  @Override
  public GrChangeInfoImpl getChangeInfo() {
    return (GrChangeInfoImpl)super.getChangeInfo();
  }

  @Override
  protected @NotNull UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    return new ChangeSignatureViewDescriptor(getChangeInfo().getMethod());
  }

  @Override
  protected void refreshElements(PsiElement @NotNull [] elements) {
    boolean condition = elements.length == 1 && elements[0] instanceof PsiMethod;
    LOG.assertTrue(condition);
    getChangeInfo().updateMethod((PsiMethod)elements[0]);
  }

  @Override
  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    MultiMap<PsiElement, String> conflictDescriptions = new MultiMap<>();
    collectConflictsFromExtensions(refUsages, conflictDescriptions, myChangeInfo);

    final UsageInfo[] usagesIn = refUsages.get();
    RenameUtil.addConflictDescriptions(usagesIn, conflictDescriptions);
    Set<UsageInfo> usagesSet = ContainerUtil.newHashSet(usagesIn);
    RenameUtil.removeConflictUsages(usagesSet);
    if (!conflictDescriptions.isEmpty()) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        throw new ConflictsInTestsException(conflictDescriptions.values());
      }

      ConflictsDialogBase dialog = prepareConflictsDialog(conflictDescriptions, usagesIn);
      if (!dialog.showAndGet()) {
        if (dialog.isShowConflicts()) prepareSuccessful();
        return false;
      }
    }
    refUsages.set(usagesSet.toArray(UsageInfo.EMPTY_ARRAY));
    prepareSuccessful();
    return true;
  }
}
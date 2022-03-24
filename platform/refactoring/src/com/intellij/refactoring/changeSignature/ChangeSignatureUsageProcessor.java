// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeSignature;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.ResolveSnapshotProvider;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public interface ChangeSignatureUsageProcessor {
  ExtensionPointName<ChangeSignatureUsageProcessor> EP_NAME =
    new ExtensionPointName<>("com.intellij.refactoring.changeSignatureUsageProcessor");

  UsageInfo[] findUsages(ChangeInfo info);

  MultiMap<PsiElement, String> findConflicts(ChangeInfo info, Ref<UsageInfo[]> refUsages);

  boolean processUsage(ChangeInfo changeInfo, UsageInfo usageInfo, boolean beforeMethodChange, UsageInfo[] usages);

  boolean processPrimaryMethod(ChangeInfo changeInfo);

  boolean shouldPreviewUsages(ChangeInfo changeInfo, UsageInfo[] usages);

  boolean setupDefaultValues(ChangeInfo changeInfo, Ref<UsageInfo[]> refUsages, Project project);

  void registerConflictResolvers(List<? super ResolveSnapshotProvider.ResolveSnapshot> snapshots,
                                 @NotNull ResolveSnapshotProvider resolveSnapshotProvider,
                                 UsageInfo[] usages,
                                 ChangeInfo changeInfo);
}

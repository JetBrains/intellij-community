// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.replace.ui;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;
import com.intellij.structuralsearch.plugin.replace.impl.Replacer;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.SearchContext;
import com.intellij.structuralsearch.plugin.ui.UsageViewContext;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.rules.UsageInFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

class ReplaceUsageViewContext extends UsageViewContext {
  private final Map<Usage,ReplacementInfo> usage2ReplacementInfo = new HashMap<>();
  private final Replacer replacer = new Replacer(mySearchContext.getProject(), myConfiguration.getReplaceOptions());

  ReplaceUsageViewContext(@NotNull SearchContext context, @NotNull Configuration configuration, @NotNull Runnable searchStarter) {
    super(configuration, context, searchStarter);
  }

  public void addReplaceUsage(@NotNull Usage usage, @NotNull MatchResult result) {
    usage2ReplacementInfo.put(usage, replacer.buildReplacement(result));
  }

  private static boolean isValid(@NotNull UsageInfo2UsageAdapter info) {
    final PsiElement element = info.getUsageInfo().getElement();
    return element != null && element.isValid();
  }

  @Override
  protected void configureActions() {
    super.configureActions();
    myUsageView.addButtonToLowerPane(() -> replace(myUsageView.getSortedUsages()), SSRBundle.message("do.replace.all.button"));
    myUsageView.addButtonToLowerPane(() -> replace(myUsageView.getSelectedUsages()), SSRBundle.message("replace.selected.button"));

    final Runnable previewReplacement = () -> {
      final Set<Usage> selection = myUsageView.getSelectedUsages();
      if (selection.isEmpty()) {
        return;
      }
      for (Usage usage : selection) {
        final UsageInfo2UsageAdapter info = (UsageInfo2UsageAdapter)usage;
        if (!isValid(info) || myUsageView.getExcludedUsages().contains(usage)) {
          continue;
        }
        final ReplacementInfo replacementInfo = usage2ReplacementInfo.get(usage);
        final ReplacementPreviewDialog previewDialog =
          new ReplacementPreviewDialog(mySearchContext.getProject(), info.getUsageInfo(), replacementInfo.getReplacement());
        if (!previewDialog.showAndGet()) {
          return;
        }
        replace(Collections.singleton(info));
      }
    };
    myUsageView.addButtonToLowerPane(previewReplacement, SSRBundle.message("preview.replacement.button"));
  }

  private void replace(@NotNull Collection<? extends Usage> usages) {
    final Set<Usage> excluded = myUsageView.getExcludedUsages();
    usages = usages.stream().filter(u -> !excluded.contains(u)).filter(u -> isValid((UsageInfo2UsageAdapter)u)).collect(Collectors.toList());

    final List<VirtualFile> files = ContainerUtil.map(usages, i -> ((UsageInFile)i).getFile());
    if (ReadonlyStatusHandler.getInstance(mySearchContext.getProject()).ensureFilesWritable(files).hasReadonlyFiles()) {
      return;
    }
    removeUsagesAndSelectNext(usages, excluded);
    final List<ReplacementInfo> replacementInfos = ContainerUtil.map(usages, usage2ReplacementInfo::get);
    final LocalHistoryAction action = LocalHistory.getInstance().startAction(SSRBundle.message("structural.replace.title"));
    try {
      CommandProcessor.getInstance().executeCommand(
        mySearchContext.getProject(), () -> replacer.replaceAll(replacementInfos), SSRBundle.message("structural.replace.title"), null);
    } finally {
      action.finish();
    }
  }

  private void removeUsagesAndSelectNext(@NotNull Collection<? extends Usage> usages, @NotNull Collection<? extends Usage> excluded) {
    final List<Usage> sortedUsages = myUsageView.getSortedUsages();
    if (sortedUsages.size() == usages.size()) {
      myUsageView.close();
    }
    else {
      Usage firstValid = null;
      Usage select = null;
      for (Usage usage : sortedUsages) {
        if (usages.contains(usage)) {
          select = null;
          continue;
        }
        if (excluded.contains(usage) || !isValid((UsageInfo2UsageAdapter)usage)) {
          continue;
        }
        if (select == null) {
          select = usage;
        }
        if (firstValid == null) {
          firstValid = usage;
        }
      }
      myUsageView.removeUsagesBulk(usages);
      myUsageView.selectUsages(new Usage[]{
        ObjectUtils.coalesce(select, firstValid, myUsageView.getSortedUsages().get(0))
      });
    }
  }
}

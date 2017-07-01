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
import com.intellij.usages.UsageView;
import com.intellij.usages.rules.UsageInFile;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

class ReplaceUsageViewContext extends UsageViewContext {
  private final HashMap<Usage,ReplacementInfo> usage2ReplacementInfo = new HashMap<>();
  private final Replacer replacer = new Replacer(mySearchContext.getProject(), ((ReplaceConfiguration)myConfiguration).getReplaceOptions());
  private UsageView myUsageView;

  ReplaceUsageViewContext(SearchContext context, Configuration configuration, Runnable searchStarter) {
    super(configuration, context, searchStarter);
  }

  @Override
  public void setUsageView(UsageView usageView) {
    myUsageView = usageView;
  }

  public void addReplaceUsage(Usage usage, MatchResult result) {
    usage2ReplacementInfo.put(usage, replacer.buildReplacement(result));
  }

  private static boolean isValid(UsageInfo2UsageAdapter info) {
    final PsiElement element = info.getUsageInfo().getElement();
    return element != null && element.isValid();
  }

  @Override
  protected void configureActions() {
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

  private void replace(@NotNull Collection<Usage> usages) {
    final Set<Usage> excluded = myUsageView.getExcludedUsages();
    usages = usages.stream().filter(u -> !excluded.contains(u)).filter(u -> isValid((UsageInfo2UsageAdapter)u)).collect(Collectors.toList());

    final List<VirtualFile> files = usages.stream().map(i -> ((UsageInFile)i).getFile()).collect(Collectors.toList());
    if (ReadonlyStatusHandler.getInstance(mySearchContext.getProject()).ensureFilesWritable(files).hasReadonlyFiles()) {
      return;
    }
    removeUsagesAndSelectNext(usages, excluded);
    final List<ReplacementInfo> replacementInfos = usages.stream().map(usage2ReplacementInfo::get).collect(Collectors.toList());
    final LocalHistoryAction action = LocalHistory.getInstance().startAction(SSRBundle.message("structural.replace.title"));
    try {
      CommandProcessor.getInstance().executeCommand(
        mySearchContext.getProject(), () -> replacer.replaceAll(replacementInfos), SSRBundle.message("structural.replace.title"), null);
    } finally {
      action.finish();
    }
  }

  private void removeUsagesAndSelectNext(Collection<Usage> usages, Collection<Usage> excluded) {
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

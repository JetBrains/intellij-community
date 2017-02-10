package com.intellij.structuralsearch.plugin.replace.ui;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;
import com.intellij.structuralsearch.plugin.replace.impl.Replacer;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.SearchContext;
import com.intellij.structuralsearch.plugin.ui.UsageViewContext;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.UsageView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Mar 9, 2005
 * Time: 4:37:08 PM
 * To change this template use File | Settings | File Templates.
 */
class ReplaceUsageViewContext extends UsageViewContext {
  private final HashMap<Usage,ReplacementInfo> usage2ReplacementInfo = new HashMap<>();
  private final Replacer replacer = new Replacer(mySearchContext.getProject(), ((ReplaceConfiguration)myConfiguration).getOptions());
  private UsageView myUsageView;
  private Set<Usage> myExcludedSet;

  ReplaceUsageViewContext(SearchContext context, Configuration configuration, Runnable searchStarter) {
    super(configuration, context, searchStarter);
  }

  @Override
  public void setUsageView(UsageView usageView) {
    myUsageView = usageView;
  }

  public Replacer getReplacer() {
    return replacer;
  }

  public void addReplaceUsage(Usage usage, MatchResult result) {
    usage2ReplacementInfo.put(usage, getReplacer().buildReplacement(result));
  }

  private boolean isValid(UsageInfo2UsageAdapter info) {
    final UsageInfo usageInfo = info.getUsageInfo();
    return !isExcluded(info) && usageInfo.getElement() != null && usageInfo.getElement().isValid();
  }

  @Override
  protected void configureActions() {
    final Runnable replaceRunnable = () -> {
      LocalHistoryAction labelAction = LocalHistory.getInstance().startAction(SSRBundle.message("structural.replace.title"));

      doReplace();
      myUsageView.close();

      labelAction.finish();
    };

    //noinspection HardCodedStringLiteral
    myUsageView.addPerformOperationAction(replaceRunnable, "Replace All", null, SSRBundle.message("do.replace.all.button"));

    final Runnable replaceSelected = () -> {
      final Set<Usage> infos = myUsageView.getSelectedUsages();
      if (infos == null || infos.isEmpty()) return;

      LocalHistoryAction labelAction = LocalHistory.getInstance().startAction(SSRBundle.message("structural.replace.title"));

      for (final Usage info : infos) {
        final UsageInfo2UsageAdapter usage = (UsageInfo2UsageAdapter)info;

        if (isValid(usage)) {
          replaceOne(usage, false);
        }
      }

      labelAction.finish();

      if (myUsageView.getUsagesCount() > 0) {
        for (Usage usage : myUsageView.getSortedUsages()) {
          if (!isExcluded(usage)) {
            myUsageView.selectUsages(new Usage[]{usage});
            return;
          }
        }
      }
    };

    myUsageView.addButtonToLowerPane(replaceSelected, SSRBundle.message("replace.selected.button"));

    final Runnable previewReplacement = () -> {
      Set<Usage> selection = myUsageView.getSelectedUsages();

      if (selection != null && !selection.isEmpty()) {
        UsageInfo2UsageAdapter usage = (UsageInfo2UsageAdapter)selection.iterator().next();

        if (isValid(usage)) {
          replaceOne(usage, true);
        }
      }
    };

    myUsageView.addButtonToLowerPane(previewReplacement, SSRBundle.message("preview.replacement.button"));

    super.configureActions();
  }

  private static void ensureFileWritable(final UsageInfo2UsageAdapter usage) {
    final VirtualFile file = usage.getFile();

    if (file != null && !file.isWritable()) {
      ReadonlyStatusHandler.getInstance(usage.getElement().getProject()).ensureFilesWritable(file);
    }
  }

  private void replaceOne(UsageInfo2UsageAdapter info, boolean doConfirm) {
    ReplacementInfo replacementInfo = usage2ReplacementInfo.get(info);
    boolean approved;

    if (doConfirm) {
      ReplacementPreviewDialog wrapper =
        new ReplacementPreviewDialog(mySearchContext.getProject(), info.getUsageInfo(), replacementInfo.getReplacement());

      approved = wrapper.showAndGet();
    }
    else {
      approved = true;
    }

    if (approved) {
      ensureFileWritable(info);
      myUsageView.removeUsage(info);
      getReplacer().replace(replacementInfo);

      if (myUsageView.getUsagesCount() == 0) {
        myUsageView.close();
      }
    }
  }

  private void doReplace() {
    List<Usage> infos = myUsageView.getSortedUsages();
    List<ReplacementInfo> results = new ArrayList<>(infos.size());

    for (final Usage info : infos) {
      UsageInfo2UsageAdapter usage = (UsageInfo2UsageAdapter)info;

      if (isValid(usage)) {
        results.add(usage2ReplacementInfo.get(usage));
      }
    }

    getReplacer().replaceAll(results);
  }

  private boolean isExcluded(Usage usage) {
    if (myExcludedSet == null) myExcludedSet = myUsageView.getExcludedUsages();
    return myExcludedSet.contains(usage);
  }
}

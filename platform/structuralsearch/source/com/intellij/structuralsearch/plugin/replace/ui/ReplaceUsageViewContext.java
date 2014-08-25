package com.intellij.structuralsearch.plugin.replace.ui;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;
import com.intellij.structuralsearch.plugin.replace.impl.Replacer;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.SearchCommand;
import com.intellij.structuralsearch.plugin.ui.SearchContext;
import com.intellij.structuralsearch.plugin.ui.UsageViewContext;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;

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
  private HashMap<Usage,ReplacementInfo> usage2ReplacementInfo;
  private Replacer replacer;

  ReplaceUsageViewContext(final SearchContext context, final Configuration configuration) {
    super(context,configuration);
  }

  protected SearchCommand createCommand() {
    ReplaceCommand command = new ReplaceCommand(mySearchContext.getProject(), this);

    usage2ReplacementInfo = new HashMap<Usage, ReplacementInfo>();
    replacer = new Replacer(mySearchContext.getProject(), ((ReplaceConfiguration)myConfiguration).getOptions());

    return command;
  }

  public Replacer getReplacer() {
    return replacer;
  }

  public void addReplaceUsage(final Usage usage, final ReplacementInfo replacementInfo) {
    usage2ReplacementInfo.put(usage,replacementInfo);
  }

  private boolean isValid(UsageInfo2UsageAdapter info) {
    final UsageInfo usageInfo = info.getUsageInfo();
    return !isExcluded(info) && usageInfo.getElement() != null && usageInfo.getElement().isValid();
  }

  @Override
  protected void configureActions() {
    final Runnable replaceRunnable = new Runnable() {
      public void run() {
        LocalHistoryAction labelAction = LocalHistory.getInstance().startAction(SSRBundle.message("structural.replace.title"));

        doReplace();
        getUsageView().close();

        labelAction.finish();
      }
    };

    //noinspection HardCodedStringLiteral
    getUsageView().addPerformOperationAction(replaceRunnable, "Replace All", null, SSRBundle.message("do.replace.all.button"));

    final Runnable replaceSelected = new Runnable() {
      public void run() {
        final Set<Usage> infos = getUsageView().getSelectedUsages();
        if (infos == null || infos.isEmpty()) return;

        LocalHistoryAction labelAction = LocalHistory.getInstance().startAction(SSRBundle.message("structural.replace.title"));

        for (final Usage info : infos) {
          final UsageInfo2UsageAdapter usage = (UsageInfo2UsageAdapter)info;

          if (isValid(usage)) {
            replaceOne(usage, false);
          }
        }

        labelAction.finish();

        if (getUsageView().getUsagesCount() > 0) {
          for (Usage usage : getUsageView().getSortedUsages()) {
            if (!isExcluded(usage)) {
              getUsageView().selectUsages(new Usage[]{usage});
              return;
            }
          }
        }
      }
    };

    getUsageView().addButtonToLowerPane(replaceSelected, SSRBundle.message("replace.selected.button"));

    final Runnable previewReplacement = new Runnable() {
      public void run() {
        Set<Usage> selection = getUsageView().getSelectedUsages();

        if (selection != null && !selection.isEmpty()) {
          UsageInfo2UsageAdapter usage = (UsageInfo2UsageAdapter)selection.iterator().next();

          if (isValid(usage)) {
            replaceOne(usage, true);
          }
        }
      }
    };

    getUsageView().addButtonToLowerPane(previewReplacement, SSRBundle.message("preview.replacement.button"));

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

      wrapper.show();
      approved = wrapper.isOK();
    }
    else {
      approved = true;
    }

    if (approved) {
      ensureFileWritable(info);
      getUsageView().removeUsage(info);
      getReplacer().replace(replacementInfo);

      if (getUsageView().getUsagesCount() == 0) {
        getUsageView().close();
      }
    }
  }

  private void doReplace() {
    List<Usage> infos = getUsageView().getSortedUsages();
    List<ReplacementInfo> results = new ArrayList<ReplacementInfo>(infos.size());

    for (final Usage info : infos) {
      UsageInfo2UsageAdapter usage = (UsageInfo2UsageAdapter)info;

      if (isValid(usage)) {
        results.add(usage2ReplacementInfo.get(usage));
      }
    }

    getReplacer().replaceAll(results);
  }
}

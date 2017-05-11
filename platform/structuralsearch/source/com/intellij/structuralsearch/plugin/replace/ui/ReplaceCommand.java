package com.intellij.structuralsearch.plugin.replace.ui;

import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.plugin.StructuralSearchPlugin;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.SearchCommand;
import com.intellij.structuralsearch.plugin.ui.SearchContext;
import com.intellij.structuralsearch.plugin.ui.UsageViewContext;
import com.intellij.usages.Usage;

public class ReplaceCommand extends SearchCommand {

  private ReplaceUsageViewContext myReplaceUsageViewContext;

  public ReplaceCommand(Configuration configuration, SearchContext searchContext) {
    super(configuration, searchContext);
  }

  protected UsageViewContext createUsageViewContext() {
    final Runnable searchStarter = () -> new ReplaceCommand(myConfiguration, mySearchContext).startSearching();
    myReplaceUsageViewContext = new ReplaceUsageViewContext(mySearchContext, myConfiguration, searchStarter);
    return myReplaceUsageViewContext;
  }

  protected void findStarted() {
    super.findStarted();

    StructuralSearchPlugin.getInstance(mySearchContext.getProject()).setReplaceInProgress(true);
  }

  protected void findEnded() {
    StructuralSearchPlugin.getInstance(mySearchContext.getProject()).setReplaceInProgress( false );

    super.findEnded();
  }

  protected void foundUsage(MatchResult result, Usage usage) {
    super.foundUsage(result, usage);

    myReplaceUsageViewContext.addReplaceUsage(usage, result);
  }
}

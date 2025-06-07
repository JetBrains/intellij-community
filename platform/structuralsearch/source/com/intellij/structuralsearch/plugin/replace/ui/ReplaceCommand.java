// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.plugin.replace.ui;

import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.SearchCommand;
import com.intellij.structuralsearch.plugin.ui.SearchContext;
import com.intellij.structuralsearch.plugin.ui.UsageViewContext;
import com.intellij.usages.Usage;
import org.jetbrains.annotations.NotNull;

public final class ReplaceCommand extends SearchCommand {
  private ReplaceUsageViewContext myReplaceUsageViewContext;

  public ReplaceCommand(@NotNull Configuration configuration, @NotNull SearchContext searchContext) {
    super(configuration, searchContext);
  }

  @Override
  protected @NotNull UsageViewContext createUsageViewContext() {
    final Runnable searchStarter = () -> new ReplaceCommand(myConfiguration, mySearchContext).startSearching();
    myReplaceUsageViewContext = new ReplaceUsageViewContext(mySearchContext, myConfiguration, searchStarter);
    return myReplaceUsageViewContext;
  }

  @Override
  protected void foundUsage(MatchResult result, Usage usage) {
    super.foundUsage(result, usage);

    myReplaceUsageViewContext.addReplaceUsage(usage, result);
  }
}

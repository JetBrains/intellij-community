package com.intellij.structuralsearch.plugin.ui;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.usages.ConfigurableUsageTarget;
import com.intellij.usages.UsageView;
import com.intellij.usages.UsageViewPresentation;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Mar 9, 2005
 * Time: 2:47:49 PM
 * To change this template use File | Settings | File Templates.
 */
public class UsageViewContext {

  protected final SearchContext mySearchContext;
  protected final Configuration myConfiguration;
  private final ConfigurableUsageTarget myTarget;

  protected UsageViewContext(Configuration configuration, SearchContext searchContext, Runnable searchStarter) {
    myConfiguration = configuration;
    mySearchContext = searchContext;
    myTarget = new StructuralSearchUsageTarget(configuration, searchContext, searchStarter);
  }

  public void setUsageView(final UsageView usageView) {}

  public ConfigurableUsageTarget getTarget() {
    return myTarget;
  }

  public void configure(@NotNull UsageViewPresentation presentation) {
    final String pattern = myConfiguration.getMatchOptions().getSearchPattern();
    final String scopeText = myConfiguration.getMatchOptions().getScope().getDisplayName();
    presentation.setScopeText(scopeText);
    final String usagesString = SSRBundle.message("occurrences.of", pattern);
    presentation.setUsagesString(usagesString);
    presentation.setTabText(StringUtil.shortenTextWithEllipsis(usagesString, 60, 0, false));
    presentation.setUsagesWord(SSRBundle.message("occurrence"));
    presentation.setCodeUsagesString(SSRBundle.message("found.occurrences", scopeText));
    presentation.setTargetsNodeText(SSRBundle.message("targets.node.text"));
    presentation.setCodeUsages(false);
  }

  protected void configureActions() {}
}

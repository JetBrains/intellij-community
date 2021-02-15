// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.inspection.StructuralSearchProfileActionProvider;
import com.intellij.usages.ConfigurableUsageTarget;
import com.intellij.usages.UsageView;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class UsageViewContext {

  @NotNull
  protected final SearchContext mySearchContext;
  @NotNull
  protected final Configuration myConfiguration;
  @NotNull
  private final ConfigurableUsageTarget myTarget;
  protected UsageView myUsageView;

  protected UsageViewContext(@NotNull Configuration configuration, @NotNull SearchContext searchContext, @NotNull Runnable searchStarter) {
    myConfiguration = configuration;
    mySearchContext = searchContext;
    myTarget = new StructuralSearchUsageTarget(configuration, searchContext, searchStarter);
  }

  public void setUsageView(@NotNull UsageView usageView) {
    myUsageView = usageView;
    final MessageBusConnection connection = mySearchContext.getProject().getMessageBus().connect(usageView);
    connection.subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        myUsageView.close();
      }
    });
  }

  public @NotNull ConfigurableUsageTarget getTarget() {
    return myTarget;
  }

  public void configure(@NotNull UsageViewPresentation presentation) {
    final String pattern = myConfiguration.getMatchOptions().getSearchPattern();
    final SearchScope scope = myConfiguration.getMatchOptions().getScope();
    assert scope != null;
    final String scopeText = scope.getDisplayName();
    presentation.setScopeText(scopeText);
    final String usagesString = SSRBundle.message("occurrences.of", pattern);
    presentation.setUsagesString(usagesString);
    presentation.setTabText(StringUtil.shortenTextWithEllipsis(usagesString, 60, 0, false));
    presentation.setCodeUsagesString(SSRBundle.message("found.occurrences", scopeText));
    presentation.setTargetsNodeText(SSRBundle.message("targets.node.text"));
    presentation.setCodeUsages(false);
    presentation.setUsageTypeFilteringAvailable(true);
  }

  protected void configureActions() {
    myUsageView.addButtonToLowerPane(new AbstractAction(SSRBundle.message("create.inspection.from.template.action.text")) {

      @Override
      public void actionPerformed(ActionEvent e) {
        StructuralSearchProfileActionProvider.createNewInspection(myConfiguration.copy(), mySearchContext.getProject());
      }
    });
  }
}

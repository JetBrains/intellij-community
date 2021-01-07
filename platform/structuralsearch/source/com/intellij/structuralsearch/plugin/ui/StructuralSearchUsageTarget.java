// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration;
import com.intellij.usages.ConfigurableUsageTarget;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Bas Leijdekkers
 */
class StructuralSearchUsageTarget implements ConfigurableUsageTarget, ItemPresentation {

  private final Configuration myConfiguration;
  private final Runnable mySearchStarter;
  private final SearchContext mySearchContext;

  StructuralSearchUsageTarget(@NotNull Configuration configuration, @NotNull SearchContext searchContext, @NotNull Runnable searchStarter) {
    myConfiguration = configuration;
    mySearchStarter = searchStarter;
    mySearchContext = searchContext;
  }

  @NotNull
  @Override
  public String getPresentableText() {
    return myConfiguration.getMatchOptions().getSearchPattern();
  }

  @Override
  public Icon getIcon(boolean unused) {
    return null;
  }

  @Override
  public void findUsages() {
    mySearchStarter.run();
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public String getName() {
    return "my name";
  }

  @Override
  public ItemPresentation getPresentation() {
    return this;
  }

  @Override
  public void navigate(boolean requestFocus) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean canNavigate() {
    return false;
  }

  @Override
  public boolean canNavigateToSource() {
    return false;
  }

  @Override
  public void showSettings() {
    UIUtil.invokeAction(myConfiguration, mySearchContext);
  }

  @Override
  public KeyboardShortcut getShortcut() {
    return ActionManager.getInstance().getKeyboardShortcut(myConfiguration instanceof ReplaceConfiguration
                                                           ? "StructuralSearchPlugin.StructuralReplaceAction"
                                                           : "StructuralSearchPlugin.StructuralSearchAction");
  }

  @Override
  public @Nls @NotNull String getLongDescriptiveName() {
    final MatchOptions matchOptions = myConfiguration.getMatchOptions();
    final String pattern = matchOptions.getSearchPattern();
    final SearchScope scope = matchOptions.getScope();
    // a search without scope is not possible, if null here the configuration was modified after the search, which should not happen
    assert scope != null;
    final String scopeString = scope.getDisplayName();
    final String result;
    if (myConfiguration instanceof ReplaceConfiguration) {
      final ReplaceConfiguration replaceConfiguration = (ReplaceConfiguration)myConfiguration;
      final String replacement = replaceConfiguration.getReplaceOptions().getReplacement();
      result = SSRBundle.message("replace.occurrences.of.0.with.1.in.2", pattern, replacement, scopeString);
    }
    else {
      result = SSRBundle.message("occurrences.of.0.in.1", pattern, scopeString);
    }
    return StringUtil.shortenTextWithEllipsis(result, 150, 0, true);
  }
}

// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.NamedScriptableDefinition;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Configuration of the search
 */
public class SearchConfiguration extends Configuration {

  private final MatchOptions matchOptions;

  public SearchConfiguration() {
    matchOptions = new MatchOptions();
  }

  SearchConfiguration(@NotNull Configuration configuration) {
    super(configuration);
    matchOptions = configuration.getMatchOptions().copy();
  }

  public SearchConfiguration(@NotNull String name, @NotNull String category) {
    super(name, category);
    matchOptions = new MatchOptions();
  }

  @Override
  public @NotNull ReplaceOptions getReplaceOptions() {
    throw new IllegalStateException();
  }

  @Override
  public @NotNull SearchConfiguration copy() {
    return new SearchConfiguration(this);
  }

  @Override
  public @NonNls String getTailText() {
    final String fileType = StringUtil.toLowerCase(matchOptions.getFileType().getName());
    return isPredefined() ? SSRBundle.message("predefined.configuration.search.tail.text", fileType)
                          : SSRBundle.message("predefined.configuration.search.tail.text.user.defined", fileType);
  }

  @Override
  public @NotNull MatchOptions getMatchOptions() {
    return matchOptions;
  }

  @Override
  public NamedScriptableDefinition findVariable(@NotNull String name) {
    return matchOptions.getVariableConstraint(name);
  }

  @Override
  public void removeUnusedVariables() {
    matchOptions.removeUnusedVariables();
  }

  @Override
  public void readExternal(Element element) {
    super.readExternal(element);

    matchOptions.readExternal(element);
  }

  @Override
  public void writeExternal(Element element) {
    super.writeExternal(element);

    matchOptions.writeExternal(element);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SearchConfiguration)) return false;
    if (!super.equals(o)) return false;
    return matchOptions.equals(((SearchConfiguration)o).matchOptions);
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() + matchOptions.hashCode();
  }
}

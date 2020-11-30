// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.replace.ui;

import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.NamedScriptableDefinition;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.util.ObjectUtils;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Mossienko
 */
public class ReplaceConfiguration extends Configuration {
  @NotNull
  private final ReplaceOptions myReplaceOptions;
  public static final String REPLACEMENT_VARIABLE_SUFFIX = "$replacement";

  public ReplaceConfiguration() {
    myReplaceOptions = new ReplaceOptions();
  }

  public ReplaceConfiguration(@NotNull Configuration configuration) {
    super(configuration);
    myReplaceOptions = configuration instanceof ReplaceConfiguration
                       ? ((ReplaceConfiguration)configuration).myReplaceOptions.copy()
                       : new ReplaceOptions(configuration.getMatchOptions().copy());
  }

  public ReplaceConfiguration(@NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String name, @NotNull String category) {
    super(name, category);
    myReplaceOptions = new ReplaceOptions();
  }

  @Override
  public @NotNull ReplaceConfiguration copy() {
    return new ReplaceConfiguration(this);
  }

  @Override
  public @NotNull ReplaceOptions getReplaceOptions() {
    return myReplaceOptions;
  }

  @Override
  public @NotNull MatchOptions getMatchOptions() {
    return myReplaceOptions.getMatchOptions();
  }

  @Override
  public NamedScriptableDefinition findVariable(@NotNull String name) {
    return ObjectUtils.chooseNotNull(myReplaceOptions.getVariableDefinition(name), getMatchOptions().getVariableConstraint(name));
  }

  @Override
  public void removeUnusedVariables() {
    myReplaceOptions.removeUnusedVariables();
    myReplaceOptions.getMatchOptions().removeUnusedVariables();
  }

  @Override
  public void readExternal(Element element) {
    super.readExternal(element);
    myReplaceOptions.readExternal(element);
  }

  @Override
  public void writeExternal(Element element) {
    super.writeExternal(element);
    myReplaceOptions.writeExternal(element);
  }

  public boolean equals(Object configuration) {
    if (this == configuration) return true;
    if (!(configuration instanceof ReplaceConfiguration)) return false;
    if (!super.equals(configuration)) return false;
    return myReplaceOptions.equals(((ReplaceConfiguration)configuration).myReplaceOptions);
  }

  public int hashCode() {
    return 31 * super.hashCode() + myReplaceOptions.hashCode();
  }
}

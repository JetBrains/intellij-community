// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.replace.ui;

import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.NamedScriptableDefinition;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.util.ObjectUtils;
import org.jdom.Element;

/**
 * @author Maxim.Mossienko
 */
public class ReplaceConfiguration extends Configuration {

  private final ReplaceOptions myReplaceOptions;
  public static final String REPLACEMENT_VARIABLE_SUFFIX = "$replacement";

  public ReplaceConfiguration() {
    myReplaceOptions = new ReplaceOptions();
  }

  ReplaceConfiguration(Configuration configuration) {
    super(configuration);
    if (configuration instanceof ReplaceConfiguration) {
      myReplaceOptions = ((ReplaceConfiguration)configuration).myReplaceOptions.copy();
    }
    else {
      final MatchOptions matchOptions = configuration.getMatchOptions();
      myReplaceOptions = new ReplaceOptions(matchOptions);
      myReplaceOptions.setReplacement(matchOptions.getSearchPattern());
    }
  }

  public ReplaceConfiguration(String name, String category) {
    super(name, category);
    myReplaceOptions = new ReplaceOptions();
  }

  @Override
  public ReplaceConfiguration copy() {
    return new ReplaceConfiguration(this);
  }

  public ReplaceOptions getReplaceOptions() {
    return myReplaceOptions;
  }

  @Override
  public MatchOptions getMatchOptions() {
    return myReplaceOptions.getMatchOptions();
  }

  @Override
  public NamedScriptableDefinition findVariable(String name) {
    return ObjectUtils.chooseNotNull(myReplaceOptions.getVariableDefinition(name), getMatchOptions().getVariableConstraint(name));
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

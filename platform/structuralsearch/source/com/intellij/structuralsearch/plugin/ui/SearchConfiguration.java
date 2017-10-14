// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.NamedScriptableDefinition;
import org.jdom.Element;

/**
 * Configuration of the search
 */
public class SearchConfiguration extends Configuration {

  private final MatchOptions matchOptions;

  public SearchConfiguration() {
    matchOptions = new MatchOptions();
  }

  SearchConfiguration(Configuration configuration) {
    super(configuration);
    matchOptions = configuration.getMatchOptions().copy();
  }

  public SearchConfiguration(String name, String category) {
    super(name, category);
    matchOptions = new MatchOptions();
  }

  @Override
  public SearchConfiguration copy() {
    return new SearchConfiguration(this);
  }

  @Override
  public MatchOptions getMatchOptions() {
    return matchOptions;
  }

  @Override
  public NamedScriptableDefinition findVariable(String name) {
    return matchOptions.getVariableConstraint(name);
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

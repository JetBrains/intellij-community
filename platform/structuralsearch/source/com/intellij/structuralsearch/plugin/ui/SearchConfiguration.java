/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.NamedScriptableDefinition;
import org.jdom.Element;

/**
 * Configuration of the search
 */
public class SearchConfiguration extends Configuration {
  private MatchOptions matchOptions;

  public SearchConfiguration() {
    matchOptions = new MatchOptions();
  }

  @Override
  public MatchOptions getMatchOptions() {
    return matchOptions;
  }

  @Override
  public NamedScriptableDefinition findVariable(String name) {
    return matchOptions.getVariableConstraint(name);
  }

  public void setMatchOptions(MatchOptions matchOptions) {
    this.matchOptions = matchOptions;
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

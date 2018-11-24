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
package com.intellij.structuralsearch.plugin.replace.ui;

import org.jdom.Element;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.MatchOptions;

/**
 * @author Maxim.Mossienko
 * Date: Apr 14, 2004
 * Time: 4:41:37 PM
 */
public class ReplaceConfiguration extends Configuration {
  private final ReplaceOptions options = new ReplaceOptions();
  public static final String REPLACEMENT_VARIABLE_SUFFIX = "$replacement";

  public ReplaceOptions getReplaceOptions() {
    return options;
  }

  @Override
  public MatchOptions getMatchOptions() {
    return options.getMatchOptions();
  }

  @Override
  public void readExternal(Element element) {
    super.readExternal(element);
    options.readExternal(element);
  }

  @Override
  public void writeExternal(Element element) {
    super.writeExternal(element);
    options.writeExternal(element);
  }

  public boolean equals(Object configuration) {
    if (this == configuration) return true;
    if (!(configuration instanceof ReplaceConfiguration)) return false;
    if (!super.equals(configuration)) return false;
    return options.equals(((ReplaceConfiguration)configuration).options);
  }

  public int hashCode() {
    return 31 * super.hashCode() + options.hashCode();
  }
}

/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.VcsConfiguration;
import org.jetbrains.annotations.Nls;

import javax.swing.*;

public class VcsLimitHistoryConfigurable extends VcsCheckBoxWithSpinnerConfigurable {
  private final VcsConfiguration myConfiguration;

  public VcsLimitHistoryConfigurable(Project project) {
    super(project, "Limit history to: ", "rows");
    myConfiguration = VcsConfiguration.getInstance(myProject);
  }

  @Override
  protected SpinnerNumberModel createSpinnerModel() {
    final int rows = myConfiguration.MAXIMUM_HISTORY_ROWS;
    return new SpinnerNumberModel(rows, 10, 1000000, 10);
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Limit History";
  }

  @Override
  public boolean isModified() {
    if (myHighlightRecentlyChanged.isSelected() != myConfiguration.LIMIT_HISTORY) return true;
    if (! Comparing.equal(myHighlightInterval.getValue(), myConfiguration.MAXIMUM_HISTORY_ROWS)) return true;
    return false;
  }

  @Override
  public void apply() {
    myConfiguration.LIMIT_HISTORY = myHighlightRecentlyChanged.isSelected();
    myConfiguration.MAXIMUM_HISTORY_ROWS = ((Number) myHighlightInterval.getValue()).intValue();
  }

  @Override
  public void reset() {
    myHighlightRecentlyChanged.setSelected(myConfiguration.LIMIT_HISTORY);
    myHighlightInterval.setValue(myConfiguration.MAXIMUM_HISTORY_ROWS);
    myHighlightInterval.setEnabled(myHighlightRecentlyChanged.isSelected());
  }
}

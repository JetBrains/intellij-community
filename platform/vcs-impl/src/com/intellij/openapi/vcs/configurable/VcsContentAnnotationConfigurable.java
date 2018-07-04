/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.vcs.contentAnnotation.VcsContentAnnotationSettings;
import org.jetbrains.annotations.Nls;

import javax.swing.*;

/**
 * @author Irina.Chernushina
 * @since 4.08.2011
 */
public class VcsContentAnnotationConfigurable extends VcsCheckBoxWithSpinnerConfigurable {
  public VcsContentAnnotationConfigurable(Project project) {
    super(project, "Show changed in last", "days");
  }

  protected SpinnerNumberModel createSpinnerModel() {
    return new SpinnerNumberModel(1, 1, VcsContentAnnotationSettings.ourMaxDays, 1);
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Show recently changed";
  }

  @Override
  public boolean isModified() {
    VcsContentAnnotationSettings settings = VcsContentAnnotationSettings.getInstance(myProject);
    if (myHighlightRecentlyChanged.isSelected() != settings.isShow()) return true;
    if (! Comparing.equal(myHighlightInterval.getValue(), settings.getLimitDays())) return true;
    return false;
  }

  @Override
  public void apply() {
    VcsContentAnnotationSettings settings = VcsContentAnnotationSettings.getInstance(myProject);
    settings.setShow(myHighlightRecentlyChanged.isSelected());
    settings.setLimit(((Number)myHighlightInterval.getValue()).intValue());
  }

  @Override
  public void reset() {
    VcsContentAnnotationSettings settings = VcsContentAnnotationSettings.getInstance(myProject);
    myHighlightRecentlyChanged.setSelected(settings.isShow());
    myHighlightInterval.setValue(settings.getLimitDays());
    myHighlightInterval.setEnabled(myHighlightRecentlyChanged.isSelected());
  }
}

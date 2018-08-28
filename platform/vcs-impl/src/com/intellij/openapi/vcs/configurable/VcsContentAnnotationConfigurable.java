// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.configurable;

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

  @Override
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

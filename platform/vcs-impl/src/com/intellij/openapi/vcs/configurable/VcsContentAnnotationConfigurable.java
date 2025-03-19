// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.contentAnnotation.VcsContentAnnotationSettings;
import org.jetbrains.annotations.Nls;

import javax.swing.*;

public final class VcsContentAnnotationConfigurable extends VcsCheckBoxWithSpinnerConfigurable {
  public VcsContentAnnotationConfigurable(Project project) {
    super(project, VcsBundle.message("settings.checkbox.show.changed.in.last"), VcsBundle.message("settings.checkbox.measure.days"));
  }

  @Override
  protected SpinnerNumberModel createSpinnerModel() {
    return new SpinnerNumberModel(1, 1, VcsContentAnnotationSettings.ourMaxDays, 1);
  }

  @Override
  public @Nls String getDisplayName() {
    return VcsBundle.message("configurable.VcsContentAnnotationConfigurable.display.name");
  }

  @Override
  public boolean isModified() {
    VcsContentAnnotationSettings settings = VcsContentAnnotationSettings.getInstance(myProject);
    if (myHighlightRecentlyChanged.isSelected() != settings.isShow()) return true;
    if (!Comparing.equal(myHighlightInterval.getValue(), settings.getLimitDays())) return true;
    return false;
  }

  @Override
  public void apply() {
    VcsContentAnnotationSettings settings = VcsContentAnnotationSettings.getInstance(myProject);
    settings.setShow(myHighlightRecentlyChanged.isSelected());
    settings.setLimitDays(((Number)myHighlightInterval.getValue()).intValue());
  }

  @Override
  public void reset() {
    VcsContentAnnotationSettings settings = VcsContentAnnotationSettings.getInstance(myProject);
    myHighlightRecentlyChanged.setSelected(settings.isShow());
    myHighlightInterval.setValue(settings.getLimitDays());
    myHighlightInterval.setEnabled(myHighlightRecentlyChanged.isSelected());
  }
}

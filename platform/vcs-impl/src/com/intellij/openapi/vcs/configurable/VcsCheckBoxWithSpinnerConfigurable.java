// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public abstract class VcsCheckBoxWithSpinnerConfigurable implements Configurable {
  protected final Project myProject;
  private final String myCheckboxText;
  private final String myMeasure;
  protected JCheckBox myHighlightRecentlyChanged;
  protected JSpinner myHighlightInterval;

  public VcsCheckBoxWithSpinnerConfigurable(Project project, final String checkboxText, final String measure) {
    myProject = project;
    myCheckboxText = checkboxText;
    myMeasure = measure;
  }

  @Override
  @NotNull
  public JComponent createComponent() {
    JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    myHighlightRecentlyChanged = new JCheckBox(myCheckboxText);
    myHighlightInterval = new JSpinner(createSpinnerModel());
    wrapper.add(myHighlightRecentlyChanged);
    wrapper.add(myHighlightInterval);
    final JLabel days = new JLabel(myMeasure);
    days.setBorder(JBUI.Borders.empty(0, 1));
    wrapper.add(days);

    myHighlightRecentlyChanged.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myHighlightInterval.setEnabled(myHighlightRecentlyChanged.isSelected());
      }
    });
    return wrapper;
  }

  protected abstract SpinnerNumberModel createSpinnerModel();
}

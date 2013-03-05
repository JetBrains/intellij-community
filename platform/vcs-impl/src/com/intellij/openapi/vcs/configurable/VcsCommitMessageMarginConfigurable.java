/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class VcsCommitMessageMarginConfigurable implements UnnamedConfigurable {

  @NotNull private final VcsConfiguration myConfiguration;

  @NotNull private final MySpinnerConfigurable mySpinnerConfigurable;
  @NotNull private final JBCheckBox myWrapCheckbox;

  public VcsCommitMessageMarginConfigurable(@NotNull Project project, @NotNull VcsConfiguration vcsConfiguration) {
    myConfiguration = vcsConfiguration;
    mySpinnerConfigurable = new MySpinnerConfigurable(project);
    myWrapCheckbox = new JBCheckBox(ApplicationBundle.message("checkbox.wrap.typing.on.right.margin"), false);
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    JComponent spinnerComponent = mySpinnerConfigurable.createComponent();
    mySpinnerConfigurable.myHighlightRecentlyChanged.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myWrapCheckbox.setEnabled(mySpinnerConfigurable.myHighlightRecentlyChanged.isSelected());
      }
    });

    JPanel rootPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    rootPanel.add(spinnerComponent);
    rootPanel.add(myWrapCheckbox);
    return rootPanel;
  }

  @Override
  public boolean isModified() {
    return mySpinnerConfigurable.isModified() || myWrapCheckbox.isSelected() != myConfiguration.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN;
  }

  @Override
  public void apply() throws ConfigurationException {
    mySpinnerConfigurable.apply();
    myConfiguration.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = myWrapCheckbox.isSelected();
  }

  @Override
  public void reset() {
    mySpinnerConfigurable.reset();
    myWrapCheckbox.setSelected(myConfiguration.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN);
    myWrapCheckbox.setEnabled(mySpinnerConfigurable.myHighlightRecentlyChanged.isSelected());
  }

  @Override
  public void disposeUIResources() {
    mySpinnerConfigurable.disposeUIResources();
  }

  private class MySpinnerConfigurable extends VcsCheckBoxWithSpinnerConfigurable {

    public MySpinnerConfigurable(Project project) {
      super(project, VcsBundle.message("configuration.commit.message.margin.prompt"), "");
    }

    @Override
    protected SpinnerNumberModel createSpinnerModel() {
      final int columns = myConfiguration.COMMIT_MESSAGE_MARGIN_SIZE;
      return new SpinnerNumberModel(columns, 0, 10000, 1);
    }

    @Nls
    @Override
    public String getDisplayName() {
      return VcsBundle.message("configuration.commit.message.margin.title");
    }

    @Override
    public boolean isModified() {
      if (myHighlightRecentlyChanged.isSelected() != myConfiguration.USE_COMMIT_MESSAGE_MARGIN) {
        return true;
      }

      if (!Comparing.equal(myHighlightInterval.getValue(), myConfiguration.COMMIT_MESSAGE_MARGIN_SIZE)) {
        return true;
      }

      return false;
    }

    @Override
    public void apply() throws ConfigurationException {
      myConfiguration.USE_COMMIT_MESSAGE_MARGIN = myHighlightRecentlyChanged.isSelected();
      myConfiguration.COMMIT_MESSAGE_MARGIN_SIZE = ((Number) myHighlightInterval.getValue()).intValue();
    }

    @Override
    public void reset() {
      myHighlightRecentlyChanged.setSelected(myConfiguration.USE_COMMIT_MESSAGE_MARGIN);
      myHighlightInterval.setValue(myConfiguration.COMMIT_MESSAGE_MARGIN_SIZE);
      myHighlightInterval.setEnabled(myHighlightRecentlyChanged.isSelected());
    }

  }
}

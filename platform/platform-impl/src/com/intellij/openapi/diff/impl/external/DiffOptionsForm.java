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
package com.intellij.openapi.diff.impl.external;

import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.util.config.AbstractProperty;
import com.intellij.util.config.BooleanProperty;
import com.intellij.util.config.StringProperty;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class DiffOptionsForm implements SearchableConfigurable, Configurable.NoScroll {
  private JComponent myPanel;
  // Garbage
  private JCheckBox myEnableFolders;
  private JCheckBox myEnableFiles;
  private TextFieldWithBrowseButton myFoldersTool;
  private TextFieldWithBrowseButton myFilesTool;
  private TextFieldWithBrowseButton myMergeTool;
  private JCheckBox myEnableMerge;
  private JTextField myMergeParameters;

  private final ToolPath[] myTools = new ToolPath[3];

  public DiffOptionsForm() {
    myEnableMerge.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myMergeParameters.setEditable(myEnableMerge.isEnabled());
        myMergeParameters.setEnabled(myEnableMerge.isEnabled());
      }
    });
    myTools[0] = new ToolPath(myEnableFolders, myFoldersTool, null, DiffManagerImpl.FOLDERS_TOOL, DiffManagerImpl.ENABLE_FOLDERS, null);
    myTools[1] = new ToolPath(myEnableFiles, myFilesTool, null, DiffManagerImpl.FILES_TOOL, DiffManagerImpl.ENABLE_FILES, null);
    myTools[2] = new ToolPath(myEnableMerge, myMergeTool, myMergeParameters, DiffManagerImpl.MERGE_TOOL, DiffManagerImpl.ENABLE_MERGE, DiffManagerImpl.MERGE_TOOL_PARAMETERS);
    myMergeParameters.setEditable(myEnableMerge.isEnabled());
    myMergeParameters.setEnabled(myEnableMerge.isEnabled());
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    for (ToolPath tool : myTools) {
      if (tool.isModifier()) return true;
    }
    return false;
  }

  public void apply() {
    for (ToolPath tool : myTools) {
      tool.apply();
    }
  }

  public void reset() {
    for (ToolPath tool : myTools) {
      tool.reset();
    }
  }

  public void disposeUIResources() {
  }

  @Nls
  public String getDisplayName() {
    return "External Diff Tools";
  }

  public String getHelpTopic() {
    return "diff";
  }

  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  public Runnable enableSearch(final String option) {
    return null;
  }

  private static class ToolPath {
    private final JCheckBox myCheckBox;
    private final TextFieldWithBrowseButton myTextField;
    private final StringProperty myPathProperty;
    private final BooleanProperty myEnabledProperty;
    @Nullable private final JTextField myParameters;
    @Nullable private final StringProperty myParametersProperty;

    public ToolPath(JCheckBox checkBox, TextFieldWithBrowseButton textField, @Nullable JTextField parameters,
                    StringProperty pathProperty, BooleanProperty enabledProperty, @Nullable StringProperty parametersProperty) {
      myCheckBox = checkBox;
      myTextField = textField;
      myPathProperty = pathProperty;
      myEnabledProperty = enabledProperty;
      myParameters = parameters;
      myParametersProperty = parametersProperty;
      final ButtonModel model = myCheckBox.getModel();
      model.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          updateEnabledEffect();
        }
      });
      myTextField.addBrowseFolderListener(DiffBundle.message("select.external.diff.program.dialog.title"), null, null,
                                          FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(),
                                          TextComponentAccessor.TEXT_FIELD_SELECTED_TEXT);
    }

    private void updateEnabledEffect() {
      UIUtil.setEnabled(myTextField, isEnabled(), true);
    }

    public boolean isModifier() {
      AbstractProperty.AbstractPropertyContainer properties = getProperties();
      return !myTextField.getText().equals(myPathProperty.get(properties))
             || isEnabled() != myEnabledProperty.value(properties)
             || (myParametersProperty != null && myParameters != null && !myParameters.getText().equals(myParametersProperty.get(properties)));
    }

    private boolean isEnabled() {
      return myCheckBox.getModel().isSelected();
    }

    private static AbstractProperty.AbstractPropertyContainer getProperties() {
      return DiffManagerImpl.getInstanceEx().getProperties();
    }

    public void apply() {
      myPathProperty.set(getProperties(), myTextField.getText());
      myEnabledProperty.primSet(getProperties(), isEnabled());
      if (myParameters != null && myParametersProperty != null) {
        myParametersProperty.set(getProperties(), myParameters.getText());
      }
    }

    public void reset() {
      myTextField.setText(myPathProperty.get(getProperties()));
      myCheckBox.getModel().setSelected(myEnabledProperty.value(getProperties()));
      if (myParameters != null && myParametersProperty != null) {
        myParameters.setText(myParametersProperty.get(getProperties()));
      }
      updateEnabledEffect();
    }
  }
}

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
package org.jetbrains.plugins.groovy.griffon;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author peter
 */
public class GriffonCreateProjectDialog extends DialogWrapper {
  private JTextField myOptionField;
  private JPanel myComponent;
  private JRadioButton myCreateApp;
  private JRadioButton myCreatePlugin;
  private JRadioButton myCreateAddon;
  private JRadioButton myCreateArchetype;
  private JLabel myCreateLabel;

  public GriffonCreateProjectDialog(@NotNull Module module) {
    super(module.getProject());
    setTitle("Create Griffon Structure");
    myCreateLabel.setText("Create Griffon structure in module '" + module.getName() + "':");
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myComponent;
  }

  String getCommand() {
    if (myCreateAddon.isSelected()) return "create-addon";
    if (myCreateApp.isSelected()) return "create-app";
    if (myCreateArchetype.isSelected()) return "create-archetype";
    if (myCreatePlugin.isSelected()) return "create-plugin";
    throw new AssertionError("No selection");
  }

  String[] getArguments() {
    String text = myOptionField.getText();
    if (StringUtil.isEmptyOrSpaces(text)) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
    return text.split(" ");
  }

}

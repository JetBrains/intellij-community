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
package org.jetbrains.plugins.github.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.*;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.util.ui.UI.PanelFactory.panel;

public class GithubCreateGistDialog extends DialogWrapper {
  @Nullable private final JBTextField myFileNameField;
  @NotNull private final JTextArea myDescriptionField;
  @NotNull private final JBCheckBox mySecretCheckBox;
  @NotNull private final JBCheckBox myOpenInBrowserCheckBox;

  public GithubCreateGistDialog(@NotNull Project project, @Nullable String fileName, boolean secret, boolean openInBrowser) {
    super(project, true);

    myFileNameField = fileName != null ? new JBTextField(fileName) : null;
    myDescriptionField = new JTextArea();
    mySecretCheckBox = new JBCheckBox("Secret", secret);
    myOpenInBrowserCheckBox = new JBCheckBox("Open in browser", openInBrowser);

    setTitle("Create Gist");
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    JBBox checkBoxes = JBBox.createHorizontalBox();
    checkBoxes.add(mySecretCheckBox);
    checkBoxes.add(Box.createRigidArea(JBUI.size(UIUtil.DEFAULT_HGAP, 0)));
    checkBoxes.add(myOpenInBrowserCheckBox);

    JBScrollPane descriptionPane = new JBScrollPane(myDescriptionField);
    descriptionPane.setMinimumSize(new JBDimension(150, 50));
    descriptionPane.setPreferredSize(new JBDimension(150, 50));
    descriptionPane.setBorder(BorderFactory.createEtchedBorder());

    BorderLayoutPanel panel = UI.Panels.simplePanel(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP)
                                       .addToCenter(UI.Panels.simplePanel(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP)
                                                             .addToCenter(descriptionPane)
                                                             .addToTop(new JBLabel("Description:")))
                                       .addToBottom(checkBoxes);
    if (myFileNameField != null) panel.addToTop(panel(myFileNameField).withLabel("Filename:").createPanel());
    return panel;
  }

  @Override
  protected String getHelpId() {
    return "github.create.gist.dialog";
  }

  @Override
  protected String getDimensionServiceKey() {
    return "Github.CreateGistDialog";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myDescriptionField;
  }

  @Nullable
  public String getFileName() {
    return myFileNameField != null ? myFileNameField.getText() : null;
  }

  @NotNull
  public String getDescription() {
    return myDescriptionField.getText();
  }

  public boolean isSecret() {
    return mySecretCheckBox.isSelected();
  }

  public boolean isOpenInBrowser() {
    return myOpenInBrowserCheckBox.isSelected();
  }
}

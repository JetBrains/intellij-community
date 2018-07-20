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
import com.intellij.openapi.ui.panel.PanelGridBuilder;
import com.intellij.ui.components.JBBox;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount;
import org.jetbrains.plugins.github.authentication.ui.GithubAccountCombobox;

import javax.swing.*;
import java.util.Set;

import static com.intellij.util.ui.UI.PanelFactory.grid;
import static com.intellij.util.ui.UI.PanelFactory.panel;

public class GithubCreateGistDialog extends DialogWrapper {
  @Nullable private final JBTextField myFileNameField;
  @NotNull private final JTextArea myDescriptionField;
  @NotNull private final JBCheckBox mySecretCheckBox;
  @NotNull private final JBCheckBox myOpenInBrowserCheckBox;
  @NotNull private final GithubAccountCombobox myAccountSelector;

  public GithubCreateGistDialog(@NotNull Project project,
                                @NotNull Set<GithubAccount> accounts,
                                @Nullable GithubAccount defaultAccount,
                                @Nullable String fileName,
                                boolean secret,
                                boolean openInBrowser) {
    super(project, true);

    myFileNameField = fileName != null ? new JBTextField(fileName) : null;
    myDescriptionField = new JTextArea();
    mySecretCheckBox = new JBCheckBox("Secret", secret);
    myOpenInBrowserCheckBox = new JBCheckBox("Open in browser", openInBrowser);
    myAccountSelector = new GithubAccountCombobox(accounts, defaultAccount, null);

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
    descriptionPane.setPreferredSize(new JBDimension(270, 55));
    descriptionPane.setMinimumSize(new JBDimension(270, 55));

    PanelGridBuilder grid = grid().resize();
    if (myFileNameField != null) grid.add(panel(myFileNameField).withLabel("Filename:"));
    grid.add(panel(descriptionPane).withLabel("Description:").anchorLabelOn(UI.Anchor.Top).resizeY(true))
        .add(panel(checkBoxes));
    if (myAccountSelector.isEnabled()) grid.add(panel(myAccountSelector).withLabel("Create for:").resizeX(false));
    return grid.createPanel();
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

  @SuppressWarnings("ConstantConditions")
  @NotNull
  public GithubAccount getAccount() {
    return (GithubAccount)myAccountSelector.getSelectedItem();
  }
}

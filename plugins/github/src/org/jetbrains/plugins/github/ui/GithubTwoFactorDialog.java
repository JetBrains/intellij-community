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
package org.jetbrains.plugins.github.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.util.GithubSettings;
import org.jetbrains.plugins.github.util.GithubUtil;

import javax.swing.*;

/**
 * @author oleg
 * @date 10/20/10
 */
public class GithubTwoFactorDialog extends DialogWrapper {

  protected static final Logger LOG = GithubUtil.LOG;

  protected final GithubTwoFactorPanel myGithubTwoFactorPanel;
  protected final GithubSettings mySettings;

  public GithubTwoFactorDialog(@NotNull final Project project) {
    super(project, false);

    myGithubTwoFactorPanel = new GithubTwoFactorPanel();

    mySettings = GithubSettings.getInstance();

    setTitle("Github Two-Factor Authentication");
    setOKButtonText("Verify");
    init();
  }

  @NotNull
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  @Override
  protected JComponent createCenterPanel() {
    return myGithubTwoFactorPanel.getPanel();
  }

  @Override
  protected String getHelpId() {
    return "github.two.factor.dialog";
  }

  @Override
  protected String getDimensionServiceKey() {
    return "Github.TwoFactorDialog";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myGithubTwoFactorPanel.getPreferableFocusComponent();
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
  }

  @NotNull
  public String getCode() {
    return myGithubTwoFactorPanel.getCode();
  }
}
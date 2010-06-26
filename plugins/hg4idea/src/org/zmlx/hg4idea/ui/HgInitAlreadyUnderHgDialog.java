/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.zmlx.hg4idea.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TitlePanel;
import org.zmlx.hg4idea.HgVcsMessages;

import javax.swing.*;

/**
 * The dialog which appears, when user wants to create a Mercurial repository in the folder,
 * which is already under Mercurial.
 * Provides several options to choose.
 */
public class HgInitAlreadyUnderHgDialog extends DialogWrapper {
  private JPanel contentPane;
  private JRadioButton myCreateNewRepoButton;
  private JRadioButton myUseParentRepoButton;
  private TitlePanel myTitlePanel;
  private String myParentRoot;
  private String mySelectedRoot;

  public enum Answer {
    USE_PARENT_REPO,
    CREATE_REPO_HERE
  }

  public HgInitAlreadyUnderHgDialog(Project project, String selectedRoot, String parentRoot) {
    super(project, false);
    mySelectedRoot = selectedRoot;
    myParentRoot = parentRoot;
    setTitle(HgVcsMessages.message("hg4idea.init.already.under.hg.dialog.title"));
    init();
  }

  public Answer getAnswer() {
    if (myCreateNewRepoButton.isSelected()) {
      return Answer.CREATE_REPO_HERE;
    } else {
      return Answer.USE_PARENT_REPO;
    }
  }

  @Override
  protected void init() {
    super.init();
    final ButtonGroup buttonGroup = new ButtonGroup();
    buttonGroup.add(myCreateNewRepoButton);
    buttonGroup.add(myUseParentRepoButton);
  }

  @Override
  protected JComponent createCenterPanel() {
    return contentPane;
  }

  private void createUIComponents() {
    myTitlePanel = new TitlePanel(HgVcsMessages.message("hg4idea.init.already.under.hg.title"),
                                  HgVcsMessages.message("hg4idea.init.already.under.hg.description", mySelectedRoot, myParentRoot));
  }
  
}
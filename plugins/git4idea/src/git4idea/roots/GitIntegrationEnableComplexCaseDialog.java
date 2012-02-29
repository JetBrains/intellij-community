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
package git4idea.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Collection;

/**
 * @author Kirill Likhodedov
 */
class GitIntegrationEnableComplexCaseDialog extends DialogWrapper {

  enum Choice {
    GIT_INIT,
    JUST_ADD_ROOTS
  }

  @NotNull private final Collection<VirtualFile> myRoots;

  private final JRadioButton myGitInit;
  private final JRadioButton myJustAddRoots;

  protected GitIntegrationEnableComplexCaseDialog(@NotNull Project project, @NotNull Collection<VirtualFile> roots) {
    super(project);
    myRoots = roots;

    myGitInit = new JRadioButton("Create Git repository for the whole project, and register all VCS roots");
    myJustAddRoots = new JRadioButton("Just register these Git repositories as VCS roots");

    setTitle("Enable Git Integration");
    init();
  }

  @Override
  protected JComponent createNorthPanel() {
    String intro;
    String repos;
    String problemText = ", but the whole project is not under Git.";
    if (myRoots.size() == 1) {
      String root = myRoots.iterator().next().getPresentableUrl();
      intro = "We've detected that there is a Git repository " + root + " inside the project";
      repos = "";
    }
    else {
      intro = "We've detected that there are Git repositories inside the project";
      repos = "\nRepositories: \n" + StringUtil.join(myRoots, new Function<VirtualFile, String>() {
        @Override
        public String fun(VirtualFile virtualFile) {
          return virtualFile.getPresentableUrl();
        }
      }, "\n");
    }
    return new JBLabel(intro + problemText + repos);
  }

  @Override
  protected JComponent createCenterPanel() {
    myJustAddRoots.setMnemonic(KeyEvent.VK_R);

    myGitInit.setSelected(true);
    myGitInit.setMnemonic(KeyEvent.VK_I);

    ButtonGroup group = new ButtonGroup();
    group.add(myJustAddRoots);
    group.add(myGitInit);

    JPanel rootPanel = new JPanel(new BorderLayout());
    rootPanel.add(myGitInit);
    rootPanel.add(myJustAddRoots);
    return rootPanel;
  }

  @NotNull
  Choice getChoice() {
    return myGitInit.isSelected() ? Choice.GIT_INIT : Choice.JUST_ADD_ROOTS;
  }

}

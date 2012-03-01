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
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Function;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Collection;

import static com.intellij.openapi.util.text.StringUtil.pluralize;

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

    int rootsNum = myRoots.size();
    myGitInit = new JRadioButton("Create Git repository for the whole project, and register " +
                                 (rootsNum == 1 ? "both" : "all") + " VCS roots");
    myJustAddRoots = new JRadioButton("Just register " + pluralize("this", rootsNum) + " Git " + pluralize("repository", rootsNum) +
                                      " as VCS " + pluralize("root", rootsNum));

    setTitle("Enable Git Integration");
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel mainPanel = new JPanel(new BorderLayout(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP));
    mainPanel.add(new JBLabel(Messages.getQuestionIcon()), BorderLayout.WEST);
    mainPanel.add(createRootPanel());
    return mainPanel;
  }

  @NotNull
  private JComponent createRootPanel() {
    myJustAddRoots.setMnemonic(KeyEvent.VK_R);

    myGitInit.setSelected(true);
    myGitInit.setMnemonic(KeyEvent.VK_I);

    ButtonGroup group = new ButtonGroup();
    group.add(myJustAddRoots);
    group.add(myGitInit);

    JPanel rootPanel = new JPanel(new BorderLayout(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP));
    rootPanel.add(createQuestionPanel(), BorderLayout.NORTH);
    rootPanel.add(myGitInit, BorderLayout.CENTER);
    rootPanel.add(myJustAddRoots, BorderLayout.SOUTH);
    return rootPanel;
  }

  @NotNull
  private JComponent createQuestionPanel() {
    String problemText = "but the whole project is not under Git.";
    String text;
    if (myRoots.size() == 1) {
      String root = myRoots.iterator().next().getPresentableUrl();
      String intro = "We've detected that there is a Git repository <br/>" + root + " inside the project";
      text = intro + ",<br/>" + problemText;
    }
    else {
      String intro = "We've detected that there are Git repositories inside the project, ";
      String repos = "<br/>Repositories: <br/>" + StringUtil.join(myRoots, new Function<VirtualFile, String>() {
        @Override
        public String fun(VirtualFile virtualFile) {
          return virtualFile.getPresentableUrl();
        }
      }, "<br/>");
      text = intro + problemText + repos;
    }
    return new JBLabel("<html>" + text + "</html>");
  }

  @NotNull
  Choice getChoice() {
    return myGitInit.isSelected() ? Choice.GIT_INIT : Choice.JUST_ADD_ROOTS;
  }

}

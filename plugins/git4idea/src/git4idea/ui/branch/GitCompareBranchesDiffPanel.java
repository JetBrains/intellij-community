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
package git4idea.ui.branch;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import git4idea.util.GitCommitCompareInfo;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author Kirill Likhodedov
 */
class GitCompareBranchesDiffPanel extends JPanel {

  private final Project myProject;
  private final String myBranchName;
  private final String myCurrentBranchName;
  private final GitCommitCompareInfo myCompareInfo;

  public GitCompareBranchesDiffPanel(Project project, String branchName, String currentBranchName, GitCommitCompareInfo compareInfo) {
    super();

    myProject = project;
    myCurrentBranchName = currentBranchName;
    myCompareInfo = compareInfo;
    myBranchName = branchName;

    setLayout(new BorderLayout(UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP));
    add(createNorthPanel(),  BorderLayout.NORTH);
    add(createCenterPanel());
  }

  private JComponent createNorthPanel() {
    return new JBLabel(String.format("<html>Difference between current working tree on <b><code>%s</code></b> " +
                                     "and files in <b><code>%s</code></b>:</html>", myCurrentBranchName, myBranchName),
                       UIUtil.ComponentStyle.REGULAR);
  }

  private JComponent createCenterPanel() {
    List<Change> diff = myCompareInfo.getTotalDiff();
    final ChangesBrowser changesBrowser = new ChangesBrowser(myProject, null, diff, null, false, true,
                                                             null, ChangesBrowser.MyUseCase.COMMITTED_CHANGES, null);
    changesBrowser.setChangesToDisplay(diff);
    return changesBrowser;
  }

}

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
package org.jetbrains.plugins.github.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitBranch;
import git4idea.rebase.GitRebaseDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 *
 * @author oleg
 * @date 12/10/10
 */
public class GithubRebaseDialog extends GitRebaseDialog {
  public GithubRebaseDialog(final Project project, final List<VirtualFile> roots, final VirtualFile defaultRoot) {
    super(project, roots, defaultRoot);
  }

  public void configure(final String parent) {
    setTitle("Rebase GitHub");

    myShowRemoteBranchesCheckBox.setSelected(true);
    myShowRemoteBranchesCheckBox.getParent().remove(myShowRemoteBranchesCheckBox);
    myGitRootComboBox.setEnabled(false);
    myLocalBranches.clear();
    final String originName = parent.substring(0, parent.lastIndexOf('/'));
    final ArrayList<GitBranch> remoteCopy = new ArrayList<GitBranch>();
    remoteCopy.addAll(myRemoteBranches);
    myRemoteBranches.clear();
    final String filter = "/" + originName + "/";
    for (GitBranch branch : remoteCopy) {
      if (branch.getFullName().contains(filter)){
        myRemoteBranches.add(branch);
      }
    }
    updateOntoFrom();

    // Preselect remote master
    final String preselected = "/" + originName + "/master";
    for (GitBranch remoteBranch : myRemoteBranches) {
      if (remoteBranch.getFullName().endsWith(preselected)){
        myOntoComboBox.setSelectedItem(remoteBranch);
        break;
      }
    }
  }
}

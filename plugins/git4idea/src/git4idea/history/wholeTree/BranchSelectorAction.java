/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.history.wholeTree;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import git4idea.GitBranch;
import git4idea.history.browser.SymbolicRefs;

import java.util.TreeSet;

/**
 * @author irengrig
 */
public class BranchSelectorAction extends BasePopupAction {
  private SymbolicRefs mySymbolicRefs;
  private final Consumer<String> myConsumer;

  public BranchSelectorAction(final Project project, Consumer<String> consumer) {
    super(project, "Branch:", "Branch");
    myConsumer = consumer;
    myLabel.setText(getText("All"));
  }

  private String getText(final String branch) {
    return minusRefs(branch);
    //return "Show branch: " + (branch.startsWith("refs/") ? branch.substring(5) : branch);
  }

  private String minusRefs(final String branch) {
    if (branch.startsWith("refs/heads/")) {
      return branch.substring("refs/heads/".length());
    }
    else {
      return (branch.startsWith("refs/") ? branch.substring("refs/".length()) : branch);
    }
  }

  public void setSymbolicRefs(SymbolicRefs symbolicRefs) {
    mySymbolicRefs = symbolicRefs;
  }

  @Override
  protected void createActions(Consumer<AnAction> actionConsumer) {
    actionConsumer.consume(new SelectBranchAction("All", null));
    if (mySymbolicRefs == null) return;
    final GitBranch current = mySymbolicRefs.getCurrent();
    if (current != null) {
      actionConsumer.consume(new SelectBranchAction("*" + minusRefs(current.getFullName()), current.getFullName()));
    }
    final TreeSet<String> locals = mySymbolicRefs.getLocalBranches();
    final String currentName = current == null ? null : current.getName();
    if (locals != null && (! locals.isEmpty())) {
      final DefaultActionGroup local = new DefaultActionGroup("Local", true);
      actionConsumer.consume(local);
      for (String s : locals) {
        final String presentation = s.equals(currentName) ? ("*" + s) : s;
        local.add(new SelectBranchAction(presentation, s));
      }
    }
    final TreeSet<String> remotes = mySymbolicRefs.getRemoteBranches();
    if (remotes != null && (! remotes.isEmpty())) {
      final DefaultActionGroup remote = new DefaultActionGroup("Remote", true);
      actionConsumer.consume(remote);
      for (String s : remotes) {
        final String presentation = s.equals(currentName) ? ("*" + s) : s;
        remote.add(new SelectBranchAction(presentation, GitBranch.REFS_REMOTES_PREFIX + s));
      }
    }
  }

  public void setPreset(final String selectedBranch) {
    if (selectedBranch == null) {
      myLabel.setText(getText("All"));
    } else {
      if (selectedBranch.startsWith("refs/")) {
        myLabel.setText(selectedBranch.substring("refs/".length()));
      } else {
        myLabel.setText(selectedBranch);
      }
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    doAction(null);
  }

  private class SelectBranchAction extends DumbAwareAction {
    private final String myValue;

    private SelectBranchAction(String text, String value) {
      super(text);
      myValue = value;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myConsumer.consume(myValue);
      myLabel.setText(myValue == null ? getTemplatePresentation().getText() : getText(myValue));
    }
  }
}

/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package git4idea.push;

import com.intellij.dvcs.push.PushTargetPanel;
import com.intellij.dvcs.push.ui.ExtraEditControl;
import com.intellij.dvcs.push.ui.PushTargetTextField;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitRemoteBranch;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.text.ParseException;
import java.util.Comparator;
import java.util.List;

class GitPushTargetPanel extends PushTargetPanel<GitPushTarget> {

  private static final Logger LOG = Logger.getInstance(GitPushTargetPanel.class);

  private static final Comparator<GitRemoteBranch> REMOTE_BRANCH_COMPARATOR = new MyRemoteBranchComparator();
  private static final String SEPARATOR = " : ";
  private static final String NO_REMOTES = "No remotes";

  private final GitRepository myRepository;
  private final PushTargetTextField myTargetTextField;
  private final JLabel myRemoteLabel;

  @Nullable private GitPushTarget myCurrentTarget;
  @Nullable private Runnable myFireOnChangeAction;
  @NotNull private ExtraEditControl myEditRemoteControl;

  public GitPushTargetPanel(@NotNull GitRepository repository, @Nullable GitPushTarget defaultTarget) {
    myRepository = repository;

    myCurrentTarget = defaultTarget;

    String initialBranch;
    String initialRemote;
    if (defaultTarget == null) {
      initialBranch = "";
      initialRemote = NO_REMOTES;
    }
    else {
      initialBranch = getTextFieldText(defaultTarget);
      initialRemote = getRemoteLabelText(defaultTarget.getBranch().getRemote().getName());
    }

    myEditRemoteControl = new ExtraEditControl() {
      @Override
      public void click(@NotNull MouseEvent event) {
        showRemoteSelector(event);
      }
    };

    myTargetTextField = new PushTargetTextField(repository.getProject(), getTargetNames(myRepository), initialBranch);
    myRemoteLabel = new JBLabel(initialRemote);

    setLayout(new BorderLayout());
    setOpaque(false);
    add(myRemoteLabel, BorderLayout.WEST);
    add(myTargetTextField, BorderLayout.CENTER);
    updateTextField();
  }

  private void updateTextField() {
    myTargetTextField.setVisible(!myRepository.getRemotes().isEmpty());
  }

  private void showRemoteSelector(@NotNull MouseEvent event) {
    final List<String> remotes = getRemotes();
    if (remotes.size() <= 1) {
      return;
    }

    ListPopup popup = JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<String>(null, remotes) {
      @Override
      public PopupStep onChosen(String selectedValue, boolean finalChoice) {
        myRemoteLabel.setText(getRemoteLabelText(selectedValue));
        if (myFireOnChangeAction != null) {
          myFireOnChangeAction.run();
        }
        return super.onChosen(selectedValue, finalChoice);
      }
    });
    popup.show(new RelativePoint(event));
  }

  @NotNull
  private List<String> getRemotes() {
    return ContainerUtil.map(myRepository.getRemotes(), new Function<GitRemote, String>() {
      @Override
      public String fun(GitRemote remote) {
        return remote.getName();
      }
    });
  }

  @Override
  public void render(@NotNull final ColoredTreeCellRenderer renderer) {
    String targetName = myTargetTextField.getText();
    if (StringUtil.isEmptyOrSpaces(targetName)) {
      renderer.append(NO_REMOTES, SimpleTextAttributes.ERROR_ATTRIBUTES, this);
    }
    else {
      String currentRemote = myRemoteLabel.getText();
      if (getRemotes().size() > 1) {
        renderer.append(currentRemote, SimpleTextAttributes.SYNTHETIC_ATTRIBUTES, myEditRemoteControl);
      }
      else {
        renderer.append(currentRemote, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }

      GitPushTarget target = getValue();
      if (target.isNewBranchCreated()) {
        renderer.append("+", SimpleTextAttributes.SYNTHETIC_ATTRIBUTES, this);
      }
      renderer.append(target.getBranch().getNameForRemoteOperations(), SimpleTextAttributes.SYNTHETIC_ATTRIBUTES, this);
    }
  }

  @NotNull
  @Override
  public GitPushTarget getValue() {
    return ObjectUtils.assertNotNull(myCurrentTarget);
  }

  @NotNull
  private static String getTextFieldText(@Nullable GitPushTarget target) {
    return (target != null ? target.getBranch().getNameForRemoteOperations() : "");
  }

  private static String getRemoteLabelText(@NotNull String selectedValue) {
    return selectedValue + SEPARATOR;
  }

  @Override
  public void fireOnCancel() {
    myTargetTextField.setText(getTextFieldText(myCurrentTarget));
  }

  @Override
  public void fireOnChange() {
    String remoteName = getEnteredRemote();
    String branchName = myTargetTextField.getText();
    try {
      myCurrentTarget = GitPushTarget.parse(myRepository, remoteName, branchName);
    }
    catch (ParseException e) {
      LOG.error("Invalid remote name shouldn't be allowed. [" + remoteName + ", " + branchName + "]", e);
    }
  }

  @Nullable
  @Override
  public ValidationInfo verify() {
    try {
      String remoteLabel = getEnteredRemote();
      GitPushTarget.parse(myRepository, remoteLabel, myTargetTextField.getText());
      return null;
    }
    catch (ParseException e) {
      return new ValidationInfo(e.getMessage(), myTargetTextField);
    }
  }

  @Override
  public void setFireOnChangeAction(@NotNull Runnable action) {
    myFireOnChangeAction = action;
  }

  @Nullable
  private String getEnteredRemote() {
    String text = myRemoteLabel.getText();
    return text.equals(NO_REMOTES) ? null : text.replace(SEPARATOR, "");
  }

  @NotNull
  public static List<String> getTargetNames(@NotNull GitRepository repository) {
    List<GitRemoteBranch> remoteBranches = ContainerUtil.sorted(repository.getBranches().getRemoteBranches(), REMOTE_BRANCH_COMPARATOR);
    return ContainerUtil.map(remoteBranches, new Function<GitRemoteBranch, String>() {
      @Override
      public String fun(GitRemoteBranch branch) {
        return branch.getNameForRemoteOperations();
      }
    });
  }

  private static class MyRemoteBranchComparator implements Comparator<GitRemoteBranch> {
    @Override
    public int compare(GitRemoteBranch o1, GitRemoteBranch o2) {
      String remoteName1 = o1.getRemote().getName();
      String remoteName2 = o2.getRemote().getName();
      int remoteComparison = remoteName1.compareTo(remoteName2);
      if (remoteComparison != 0) {
        if (remoteName1.equals(GitRemote.ORIGIN_NAME)) {
          return -1;
        }
        if (remoteName2.equals(GitRemote.ORIGIN_NAME)) {
          return 1;
        }
        return remoteComparison;
      }
      return o1.getNameForLocalOperations().compareTo(o2.getNameForLocalOperations());
    }
  }
}

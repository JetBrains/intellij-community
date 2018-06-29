/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.dvcs.push.ui.*;
import com.intellij.openapi.command.undo.UndoConstants;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.table.ComponentsListFocusTraversalPolicy;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.remote.GitDefineRemoteDialog;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.event.*;
import java.text.ParseException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import static com.intellij.util.containers.ContainerUtil.newArrayList;
import static git4idea.push.GitPushTarget.findRemote;
import static java.util.stream.Collectors.toList;

public class GitPushTargetPanel extends PushTargetPanel<GitPushTarget> {

  private static final Logger LOG = Logger.getInstance(GitPushTargetPanel.class);

  private static final Comparator<GitRemoteBranch> REMOTE_BRANCH_COMPARATOR = new MyRemoteBranchComparator();
  private static final String SEPARATOR = " : ";
  private static final Color NEW_BRANCH_LABEL_FG = new JBColor(0x00b53d, 0x6ba65d);
  private static final Color NEW_BRANCH_LABEL_SELECTION_FG = UIUtil.getTreeSelectionForeground();
  private static final Color NEW_BRANCH_LABEL_BG = new JBColor(0xebfcf1, 0x313b32);
  private static final Color NEW_BRANCH_LABEL_SELECTION_BG =
    new JBColor(ColorUtil.toAlpha(NEW_BRANCH_LABEL_SELECTION_FG, 20), ColorUtil.toAlpha(NEW_BRANCH_LABEL_SELECTION_FG, 30));
  private static final RelativeFont NEW_BRANCH_LABEL_FONT = RelativeFont.TINY.small();
  private static final TextIcon NEW_BRANCH_LABEL = new TextIcon("New", NEW_BRANCH_LABEL_FG, NEW_BRANCH_LABEL_BG, 0);

  @NotNull private final GitPushSupport myPushSupport;
  @NotNull private final GitRepository myRepository;
  @NotNull private final Git myGit;

  @NotNull private final VcsEditableTextComponent myTargetRenderer;
  @NotNull private final PushTargetTextField myTargetEditor;
  @NotNull private final VcsLinkedTextComponent myRemoteRenderer;
  @NotNull private final Project myProject;

  @Nullable private GitPushTarget myCurrentTarget;
  @Nullable private String myError;
  @Nullable private Runnable myFireOnChangeAction;
  private boolean myBranchWasUpdatedManually;
  private boolean myEventFromRemoteChooser;

  public GitPushTargetPanel(@NotNull GitPushSupport support, @NotNull GitRepository repository, @Nullable GitPushTarget defaultTarget) {
    myPushSupport = support;
    myRepository = repository;
    myGit = Git.getInstance();
    myProject = myRepository.getProject();

    myTargetRenderer = new VcsEditableTextComponent("", null);
    myTargetEditor = new PushTargetTextField(repository.getProject(), getTargetNames(myRepository), "");
    myRemoteRenderer = new VcsLinkedTextComponent("", new VcsLinkListener() {
      @Override
      public void hyperlinkActivated(@NotNull DefaultMutableTreeNode sourceNode, @NotNull MouseEvent event) {
        if (myRepository.getRemotes().isEmpty()) {
          showDefineRemoteDialog();
        }
        else {
          Component eventComponent = event.getComponent();
          if (eventComponent != null) {
            showRemoteSelector(eventComponent, event.getPoint());
          }
        }
      }
    });

    setLayout(new BorderLayout());
    setOpaque(false);
    JPanel remoteAndSeparator = new JPanel(new BorderLayout());
    remoteAndSeparator.setOpaque(false);
    remoteAndSeparator.add(myRemoteRenderer, BorderLayout.CENTER);
    remoteAndSeparator.add(new JBLabel(SEPARATOR), BorderLayout.EAST);

    add(remoteAndSeparator, BorderLayout.WEST);
    add(myTargetEditor, BorderLayout.CENTER);

    updateComponents(defaultTarget);

    setFocusCycleRoot(true);
    myRemoteRenderer.setFocusable(true);
    myTargetEditor.setFocusable(true);
    setFocusTraversalPolicy(new MyGitTargetFocusTraversalPolicy());
    myRemoteRenderer.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        // show in edit mode only
        if (myTargetEditor.isShowing()) {
          showRemoteSelector(myRemoteRenderer, new Point(myRemoteRenderer.getLocation()));
        }
      }
    });
    //record undo only in active edit mode and set to ignore by default
    myTargetEditor.getDocument().putUserData(UndoConstants.DONT_RECORD_UNDO, Boolean.TRUE);
  }

  private void updateComponents(@Nullable GitPushTarget target) {
    myCurrentTarget = target;

    String initialBranch = "";
    String initialRemote = "";
    boolean noRemotes = myRepository.getRemotes().isEmpty();
    if (target == null) {
      if (myRepository.getCurrentBranch() == null) {
        myError = "Detached HEAD";
      }
      else if (myRepository.isFresh()) {
        myError = "Empty repository";
      }
      else if (!noRemotes) {
        myError = "Can't push";
      }
    }
    else {
      initialBranch = getTextFieldText(target);
      initialRemote = target.getBranch().getRemote().getName();
    }

    myTargetRenderer.updateLinkText(initialBranch);
    myTargetEditor.setText(initialBranch);
    myRemoteRenderer.updateLinkText(noRemotes ? "Define remote" : initialRemote);

    myTargetEditor.setVisible(!noRemotes);
  }

  private void showDefineRemoteDialog() {
    GitDefineRemoteDialog dialog = new GitDefineRemoteDialog(myRepository, myGit);
    if (dialog.showAndGet()) {
      addRemoteUnderModal(dialog.getRemoteName(), dialog.getRemoteUrl());
    }
  }

  private void addRemoteUnderModal(@NotNull final String remoteName, @NotNull final String remoteUrl) {
    ProgressManager.getInstance().run(new Task.Modal(myRepository.getProject(), "Adding Remote...", true) {
      private GitCommandResult myResult;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        myResult = myGit.addRemote(myRepository, remoteName, remoteUrl);
        myRepository.update();
      }

      @Override
      public void onSuccess() {
        if (myResult.success()) {
          updateComponents(myPushSupport.getDefaultTarget(myRepository));
          if (myFireOnChangeAction != null) {
            myFireOnChangeAction.run();
          }
        }
        else {
          String message = "Couldn't add remote: " + myResult.getErrorOutputAsHtmlString();
          LOG.warn(message);
          Messages.showErrorDialog(myProject, message, "Add Remote");
        }
      }
    });
  }

  private void showRemoteSelector(@NotNull Component component, @NotNull Point point) {
    List<PopupItem> remotes = getPopupItems();
    if (remotes.size() <= 1) {
      return;
    }
    ListPopup popup = new ListPopupImpl(new BaseListPopupStep<PopupItem>(null, remotes) {
      @Override
      public PopupStep onChosen(@NotNull PopupItem selectedValue, boolean finalChoice) {
        return doFinalStep(() -> {
          if (selectedValue.isDefineRemote()) {
            showDefineRemoteDialog();
          }
          else {
            myRemoteRenderer.updateLinkText(selectedValue.getPresentable());
            myEventFromRemoteChooser = true;
            if (!myTargetEditor.isShowing()) {
              if (!myBranchWasUpdatedManually) {
                String defaultPushTargetBranch = getDefaultPushTargetBranch();
                if (defaultPushTargetBranch != null) {
                  myTargetEditor.setText(defaultPushTargetBranch);
                }
              }
              if (myFireOnChangeAction != null) {
                //fireOnChange only when editing completed
                myFireOnChangeAction.run();
              }
            }
            myEventFromRemoteChooser = false;
          }
        });
      }

      @Nullable
      @Override
      public ListSeparator getSeparatorAbove(PopupItem value) {
        return value.isDefineRemote() ? new ListSeparator() : null;
      }
    }) {
      @Override
      public void cancel(InputEvent e) {
        super.cancel(e);
        if (myTargetEditor.isShowing()) {
          //repaint and force move focus to target editor component
          GitPushTargetPanel.this.repaint();
          IdeFocusManager.getInstance(myProject).requestFocus(myTargetEditor, true);
        }
      }
    };
    popup.show(new RelativePoint(component, point));
  }

  @Nullable
  private String getDefaultPushTargetBranch() {
    GitLocalBranch sourceBranch = myRepository.getCurrentBranch();
    GitRemote remote = findRemote(myRepository.getRemotes(), myRemoteRenderer.getText());
    if (remote != null && sourceBranch != null) {
      GitPushTarget fromPushSpec = GitPushTarget.getFromPushSpec(myRepository, remote, sourceBranch);
      if (fromPushSpec != null) {
        return fromPushSpec.getBranch().getNameForRemoteOperations();
      }
    }
    return null;
  }

  @NotNull
  private List<PopupItem> getPopupItems() {
    List<PopupItem> items = newArrayList(ContainerUtil.map(myRepository.getRemotes(), PopupItem::forRemote));
    items.add(PopupItem.DEFINE_REMOTE);
    return items;
  }

  @Override
  public void render(@NotNull ColoredTreeCellRenderer renderer, boolean isSelected, boolean isActive, @Nullable String forceRenderedText) {

    SimpleTextAttributes targetTextAttributes = PushLogTreeUtil.addTransparencyIfNeeded(renderer, SimpleTextAttributes.REGULAR_ATTRIBUTES, isActive);
    if (myError != null) {
      renderer.append(myError, PushLogTreeUtil.addTransparencyIfNeeded(renderer, SimpleTextAttributes.ERROR_ATTRIBUTES, isActive));
    }
    else {
      Collection<GitRemote> remotes = myRepository.getRemotes();
      myRemoteRenderer.setSelected(isSelected);
      myRemoteRenderer.setTransparent(!remotes.isEmpty() && !isActive);
      myRemoteRenderer.render(renderer);

      if (!remotes.isEmpty()) {
        renderer.append(SEPARATOR, targetTextAttributes);
        if (forceRenderedText != null) {
          // update only appearance; do not update model in rendering!!!!
          renderer.append(forceRenderedText);
          return;
        }
        GitPushTarget target = getValue();
        boolean newRemoteBranch = target != null && target.isNewBranchCreated();
        myTargetRenderer.setSelected(isSelected);
        myTargetRenderer.setTransparent(!isActive);
        myTargetRenderer.render(renderer);
        if (newRemoteBranch) {
          renderer.setIconOnTheRight(true);
          NEW_BRANCH_LABEL.setInsets(JBUI.insets(2));
          NEW_BRANCH_LABEL.setRound(JBUI.scale(4));
          NEW_BRANCH_LABEL.setFont(NEW_BRANCH_LABEL_FONT.derive(renderer.getFont()));
          NEW_BRANCH_LABEL.setForeground(isSelected ? NEW_BRANCH_LABEL_SELECTION_FG : NEW_BRANCH_LABEL_FG);
          NEW_BRANCH_LABEL.setBackground(isSelected ? NEW_BRANCH_LABEL_SELECTION_BG : NEW_BRANCH_LABEL_BG);
          renderer.setIcon(NEW_BRANCH_LABEL);
        }
      }
    }
  }

  @Nullable
  @Override
  public GitPushTarget getValue() {
    return myCurrentTarget;
  }

  @NotNull
  private static String getTextFieldText(@Nullable GitPushTarget target) {
    return (target != null ? target.getBranch().getNameForRemoteOperations() : "");
  }

  @Override
  public void fireOnCancel() {
    myTargetEditor.setText(getTextFieldText(myCurrentTarget));
  }

  @Override
  public void fireOnChange() {
    //any changes are senselessly if no remotes
    if (myError != null || myRepository.getRemotes().isEmpty()) return;
    String remoteName = myRemoteRenderer.getText();
    String branchName = myTargetEditor.getText();
    try {
      GitPushTarget target = GitPushTarget.parse(myRepository, remoteName, branchName);
      if (!target.equals(myCurrentTarget)) {
        myCurrentTarget = target;
        myTargetRenderer.updateLinkText(branchName);
        if (!myEventFromRemoteChooser) {
          myBranchWasUpdatedManually = true;
        }
      }
    }
    catch (ParseException e) {
      LOG.error("Invalid remote name shouldn't be allowed. [" + remoteName + ", " + branchName + "]", e);
    }
  }

  @Nullable
  @Override
  public ValidationInfo verify() {
    if (myError != null) {
      return new ValidationInfo(myError, myTargetEditor);
    }
    try {
      GitPushTarget.parse(myRepository, myRemoteRenderer.getText(), myTargetEditor.getText());
      return null;
    }
    catch (ParseException e) {
      return new ValidationInfo(e.getMessage(), myTargetEditor);
    }
  }

  @SuppressWarnings("NullableProblems")
  @Override
  public void setFireOnChangeAction(@NotNull Runnable action) {
    myFireOnChangeAction = action;
  }

  @NotNull
  private static List<String> getTargetNames(@NotNull GitRepository repository) {
    return repository.getBranches().getRemoteBranches().stream().
      sorted(REMOTE_BRANCH_COMPARATOR).
      map(GitRemoteBranch::getNameForRemoteOperations).collect(toList());
  }

  private static class MyRemoteBranchComparator implements Comparator<GitRemoteBranch> {
    @Override
    public int compare(@NotNull GitRemoteBranch o1, @NotNull GitRemoteBranch o2) {
      String remoteName1 = o1.getRemote().getName();
      String remoteName2 = o2.getRemote().getName();
      int remoteComparison = remoteName1.compareTo(remoteName2);
      if (remoteComparison != 0) {
        if (remoteName1.equals(GitRemote.ORIGIN)) {
          return -1;
        }
        if (remoteName2.equals(GitRemote.ORIGIN)) {
          return 1;
        }
        return remoteComparison;
      }
      return o1.getNameForLocalOperations().compareTo(o2.getNameForLocalOperations());
    }
  }

  @Override
  public void addTargetEditorListener(@NotNull final PushTargetEditorListener listener) {
    myTargetEditor.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(DocumentEvent e) {
        processActiveUserChanges(listener);
      }
    });
    myTargetEditor.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        processActiveUserChanges(listener);
      }
    });
    myTargetEditor.addHierarchyListener(new HierarchyListener() {
      @Override
      public void hierarchyChanged(HierarchyEvent e) {
        if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
          myTargetEditor.getDocument().putUserData(UndoConstants.DONT_RECORD_UNDO, !myTargetEditor.isShowing());
        }
      }
    });
  }

  private void processActiveUserChanges(@NotNull PushTargetEditorListener listener) {
    //fire only about user's changes
    if (myTargetEditor.isShowing()) {
      listener.onTargetInEditModeChanged(myTargetEditor.getText());
    }
  }

  @Override
  public void forceUpdateEditableUiModel(@NotNull String forcedText) {
    //if targetEditor is now editing by user, it shouldn't be force updated
    if (!myTargetEditor.isShowing()) {
      myTargetEditor.setText(forcedText);
    }
  }

  private static class PopupItem {
    static final PopupItem DEFINE_REMOTE = new PopupItem(null);

    @Nullable GitRemote remote;

    @NotNull
    static PopupItem forRemote(@NotNull GitRemote remote) {
      return new PopupItem(remote);
    }

    private PopupItem(@Nullable GitRemote remote) {
      this.remote = remote;
    }

    @NotNull
    String getPresentable() {
      return remote == null ? "Define Remote" : remote.getName();
    }

    boolean isDefineRemote() {
      return remote == null;
    }

    @Override
    public String toString() {
      return getPresentable();
    }
  }

  private class MyGitTargetFocusTraversalPolicy extends ComponentsListFocusTraversalPolicy {
    @NotNull
    @Override
    protected List<Component> getOrderedComponents() {
      return newArrayList(myTargetEditor.getFocusTarget(), myRemoteRenderer);
    }

    @Override
    public Component getComponentAfter(Container aContainer, Component aComponent) {
      if (getPopupItems().size() > 1) {
        return super.getComponentAfter(aContainer, aComponent);
      }
      return aComponent;
    }

    @Override
    public Component getComponentBefore(Container aContainer, Component aComponent) {
      if (getPopupItems().size() > 1) {
        return super.getComponentBefore(aContainer, aComponent);
      }
      return aComponent;
    }
  }
}

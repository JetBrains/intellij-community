// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.push;

import com.intellij.dvcs.push.PushTargetPanel;
import com.intellij.dvcs.push.ui.*;
import com.intellij.openapi.command.undo.UndoUtil;
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
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.table.ComponentsListFocusTraversalPolicy;
import com.intellij.xml.util.XmlStringUtil;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.i18n.GitBundle;
import git4idea.remote.GitDefineRemoteDialog;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.validators.GitRefNameValidator;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.event.*;
import java.text.ParseException;
import java.util.List;
import java.util.*;

import static git4idea.push.GitPushTarget.findRemote;
import static java.util.stream.Collectors.toList;

public class GitPushTargetPanel extends PushTargetPanel<GitPushTarget> {

  private static final Logger LOG = Logger.getInstance(GitPushTargetPanel.class);

  private static final Comparator<GitRemoteBranch> REMOTE_BRANCH_COMPARATOR = new MyRemoteBranchComparator();
  private static final String SEPARATOR = " : ";

  private final @NotNull GitPushSupport myPushSupport;
  private final @NotNull GitRepository myRepository;
  private final @NotNull GitPushSource mySource;
  private final @NotNull Git myGit;

  private final @NotNull VcsEditableTextComponent myTargetRenderer;
  private final @NotNull PushTargetTextField myTargetEditor;
  private final @NotNull VcsLinkedTextComponent myRemoteRenderer;
  private final @NotNull Project myProject;
  private final @Nullable SetUpstreamCheckbox myUpstreamCheckbox;

  private @Nullable GitPushTarget myCurrentTarget;
  private @Nullable @Nls String myError;
  private @Nullable Runnable myFireOnChangeAction;
  private boolean myBranchWasUpdatedManually;
  private boolean myEventFromRemoteChooser;

  public GitPushTargetPanel(@NotNull GitPushSupport support, @NotNull GitRepository repository, @Nullable GitPushTarget defaultTarget) {
    this(support, repository, Objects.requireNonNull(support.getSource(repository)), defaultTarget);
  }

  public GitPushTargetPanel(@NotNull GitPushSupport support,
                            @NotNull GitRepository repository,
                            @NotNull GitPushSource source,
                            @Nullable GitPushTarget defaultTarget) {
    myPushSupport = support;
    myRepository = repository;
    mySource = source;
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

    setOpaque(false);

    setLayout(new BorderLayout());
    add(myTargetEditor, BorderLayout.CENTER);

    if (source instanceof GitPushSource.OnBranch && defaultTarget != null &&
        // "Set upstream" checkbox isn't shown if there is no existing tracking branch
        defaultTarget.getTargetType() == GitPushTargetType.TRACKING_BRANCH && !defaultTarget.isNewBranchCreated()
    ) {
      myUpstreamCheckbox = new SetUpstreamCheckbox(defaultTarget.getBranch().getNameForRemoteOperations());
      myTargetEditor.addDocumentListener(new DocumentListener() {
        @Override
        public void documentChanged(@NotNull DocumentEvent event) {
          myUpstreamCheckbox.setVisible(myTargetEditor.getText());
        }
      });
      add(myUpstreamCheckbox, BorderLayout.EAST);
    }
    else {
      myUpstreamCheckbox = null;
    }

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
    UndoUtil.disableUndoFor(myTargetEditor.getDocument());
  }

  private void updateComponents(@Nullable GitPushTarget target) {
    myCurrentTarget = target;

    boolean noRemotes = myRepository.getRemotes().isEmpty();
    if (myRepository.isFresh()) {
      myError = GitBundle.message("push.dialog.target.panel.empty.repository");
    }
    else if (target == null && !noRemotes) {
      myError = GitBundle.message("push.dialog.target.panel.can.t.push");
    }

    String initialBranch = "";
    String initialRemote = "";
    if (target != null) {
      initialBranch = getTextFieldText(target);
      initialRemote = target.getBranch().getRemote().getName();
    }

    myTargetRenderer.updateLinkText(initialBranch);
    myTargetEditor.setText(initialBranch);
    myRemoteRenderer.updateLinkText(noRemotes ? GitBundle.message("push.dialog.target.panel.define.remote") : initialRemote);

    if (myUpstreamCheckbox != null) {
      myUpstreamCheckbox.setVisible(initialBranch);
    }

    myTargetEditor.setVisible(!noRemotes);
  }

  private void showDefineRemoteDialog() {
    GitDefineRemoteDialog dialog = new GitDefineRemoteDialog(myRepository, myGit, GitRemote.ORIGIN, "");
    if (dialog.showAndGet()) {
      addRemoteUnderModal(dialog.getRemoteName(), dialog.getRemoteUrl());
    }
  }

  private void addRemoteUnderModal(final @NotNull String remoteName, final @NotNull String remoteUrl) {
    ProgressManager.getInstance()
      .run(new Task.Modal(myRepository.getProject(), GitBundle.message("push.dialog.target.panel.adding.remote"), true) {
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
            updateComponents(myPushSupport.getDefaultTarget(myRepository, mySource));
            if (myFireOnChangeAction != null) {
              myFireOnChangeAction.run();
            }
          }
          else {
            String message = GitBundle.message("push.dialog.target.panel.couldnt.add.remote", myResult.getErrorOutputAsHtmlString());
            LOG.warn(message);
            Messages.showErrorDialog(myProject, XmlStringUtil.wrapInHtml(message),
                                     GitBundle.message("push.dialog.target.panel.add.remote"));
          }
        }
      });
  }

  private void showRemoteSelector(@NotNull Component component, @NotNull Point point) {
    List<PopupItem> remotes = getPopupItems();
    if (remotes.size() <= 1) {
      return;
    }
    ListPopup popup = new ListPopupImpl(myProject, new BaseListPopupStep<>(null, remotes) {
      @Override
      public PopupStep<?> onChosen(@NotNull PopupItem selectedValue, boolean finalChoice) {
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

      @Override
      public @Nullable ListSeparator getSeparatorAbove(PopupItem value) {
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

  private @Nullable String getDefaultPushTargetBranch() {
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

  private @NotNull List<PopupItem> getPopupItems() {
    List<PopupItem> items = new ArrayList<>(ContainerUtil.map(myRepository.getRemotes(), PopupItem::forRemote));
    items.add(PopupItem.DEFINE_REMOTE);
    return items;
  }

  @Override
  public void render(@NotNull ColoredTreeCellRenderer renderer, boolean isSelected, boolean isActive, @Nullable String forceRenderedText) {

    SimpleTextAttributes targetTextAttributes =
      PushLogTreeUtil.addTransparencyIfNeeded(renderer, SimpleTextAttributes.REGULAR_ATTRIBUTES, isActive);
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
        boolean newUpstream = myUpstreamCheckbox != null &&
                              target != null &&
                              myUpstreamCheckbox.isSelected() &&
                              !myUpstreamCheckbox.isDefaultUpstream(target.getBranch().getNameForRemoteOperations());
        if (newRemoteBranch || newUpstream) {
          renderer.setIconOnTheRight(true);
        }
        if (newRemoteBranch && newUpstream) {
          renderer.setIcon(BranchLabels.getNewAndUpstreamBranchLabel(renderer.getFont(), isSelected));
        } else if (newRemoteBranch) {
          renderer.setIcon(BranchLabels.getNewBranchLabel(renderer.getFont(), isSelected));
        } else if (newUpstream) {
          renderer.setIcon(BranchLabels.getUpstreamBranchLabel(renderer.getFont(), isSelected));
        }
      }
    }
  }

  @Override
  public @Nullable GitPushTarget getValue() {
    return myCurrentTarget;
  }

  private static @NlsSafe @NotNull String getTextFieldText(@Nullable GitPushTarget target) {
    return (target != null ? target.getBranch().getNameForRemoteOperations() : "");
  }

  @Override
  public void editingStarted() {
    if (myUpstreamCheckbox != null) {
      // Checkbox should be explicitly enabled and disabled when toggling editing, as click
      // to start editing can change checkbox state
      // See BasicTreeUi#startEditing for details
      myUpstreamCheckbox.setVisible(myTargetEditor.getText());
      myUpstreamCheckbox.setEnabled(true);
    }
  }

  @Override
  public void fireOnCancel() {
    if (myUpstreamCheckbox != null) {
      myUpstreamCheckbox.setEnabled(false);
    }

    myTargetEditor.setText(getTextFieldText(myCurrentTarget));
  }

  @Override
  public void fireOnChange() {
    if (myUpstreamCheckbox != null) {
      myUpstreamCheckbox.setEnabled(false);
    }

    //any changes are senselessly if no remotes
    if (myError != null || myRepository.getRemotes().isEmpty()) return;
    String remoteName = myRemoteRenderer.getText();
    String branchName = myTargetEditor.getText();
    try {
      GitPushTarget target = GitPushTarget.parse(myRepository, remoteName, branchName);
      if (myUpstreamCheckbox != null) {
        target.shouldSetNewUpstream(myUpstreamCheckbox.isVisible() && myUpstreamCheckbox.isSelected());
      }
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

  @Override
  public @Nullable ValidationInfo verify() {
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

  @Override
  public void setFireOnChangeAction(@NotNull Runnable action) {
    myFireOnChangeAction = action;
  }

  private static @NotNull List<String> getTargetNames(@NotNull GitRepository repository) {
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
  public void addTargetEditorListener(final @NotNull PushTargetEditorListener listener) {
    myTargetEditor.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent e) {
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
          if (myTargetEditor.isShowing()) {
            UndoUtil.enableUndoFor(myTargetEditor.getDocument());
          }
          else {
            UndoUtil.disableUndoFor(myTargetEditor.getDocument());
          }
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

  @Override
  public boolean showSourceWhenEditing() {
    return false;
  }

  private static final class PopupItem {
    static final PopupItem DEFINE_REMOTE = new PopupItem(null);

    @Nullable GitRemote remote;

    static @NotNull PopupItem forRemote(@NotNull GitRemote remote) {
      return new PopupItem(remote);
    }

    private PopupItem(@Nullable GitRemote remote) {
      this.remote = remote;
    }

    @Nls
    @NotNull
    String getPresentable() {
      return remote == null ? GitBundle.message("push.dialog.target.panel.define.remote") : remote.getName();
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
    @Override
    protected @NotNull List<Component> getOrderedComponents() {
      return List.of(myTargetEditor.getFocusTarget(), myRemoteRenderer);
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

  private static class SetUpstreamCheckbox extends JBCheckBox {
    private final String upstreamBranchName;

    SetUpstreamCheckbox(String upstreamBranchName) {
      super(GitBundle.message("push.dialog.target.panel.upstream.checkbox"), false);

      this.upstreamBranchName = upstreamBranchName;
      setBorder(JBUI.Borders.empty(0, 5, 0, 10));
      setOpaque(false);
      setFocusable(false);
    }

    public void setVisible(String targetName) {
      boolean valid = GitRefNameValidator.getInstance().checkInput(targetName);
      setVisible(valid && !isDefaultUpstream(targetName));
    }

    public boolean isDefaultUpstream(String targetName) {
      return upstreamBranchName.equals(targetName);
    }
  }

  private static final class BranchLabels {
    private static final Color LABEL_FG = new JBColor(0x00b53d, 0x6ba65d);
    private static final Color LABEL_SELECTION_FG = UIUtil.getTreeSelectionForeground();
    private static final Color LABEL_BG = new JBColor(0xebfcf1, 0x313b32);
    private static final Color LABEL_SELECTION_BG =
      new JBColor(ColorUtil.toAlpha(LABEL_SELECTION_FG, 20), ColorUtil.toAlpha(LABEL_SELECTION_FG, 30));
    private static final RelativeFont LABEL_FONT = RelativeFont.TINY.small();

    public static TextIcon getNewBranchLabel(Font font, boolean selected) {
      return getLabel(GitBundle.message("push.dialog.target.panel.new"), font, selected);
    }

    public static TextIcon getUpstreamBranchLabel(Font font, boolean selected) {
      return getLabel(GitBundle.message("push.dialog.target.panel.upstream.label"), font, selected);
    }

    public static TextIcon getNewAndUpstreamBranchLabel(Font font, boolean selected) {
      return getLabel(GitBundle.message("push.dialog.target.panel.new.and.upstream"), font, selected);
    }

    private static TextIcon getLabel(String text, Font font, boolean selected) {
      TextIcon label = new TextIcon(text, LABEL_FG, LABEL_BG, 0);
      label.setInsets(JBUI.insets(2));
      label.setRound(JBUIScale.scale(4));
      label.setFont(LABEL_FONT.derive(font));
      label.setForeground(selected ? LABEL_SELECTION_FG : LABEL_FG);
      label.setBackground(selected ? LABEL_SELECTION_BG : LABEL_BG);
      return label;
    }
  }
}

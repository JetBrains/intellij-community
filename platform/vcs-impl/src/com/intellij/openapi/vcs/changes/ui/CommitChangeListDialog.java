/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.actions.ShowDiffAction;
import com.intellij.openapi.vcs.checkin.*;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SeparatorFactory;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * @author max
 */
public class CommitChangeListDialog extends DialogWrapper implements CheckinProjectPanel, TypeSafeDataProvider {
  private final CommitMessage myCommitMessageArea;
  private Splitter mySplitter;
  private final JPanel myAdditionalOptionsPanel;

  private final ChangesBrowser myBrowser;
  private final ChangesBrowserExtender myBrowserExtender;

  private CommitLegendPanel myLegend;

  private final List<RefreshableOnComponent> myAdditionalComponents = new ArrayList<RefreshableOnComponent>();
  private final List<CheckinHandler> myHandlers = new ArrayList<CheckinHandler>();
  private final String myActionName;
  private final Project myProject;
  private final List<CommitExecutor> myExecutors;
  private final Alarm myOKButtonUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private String myLastKnownComment = "";
  private final boolean myAllOfDefaultChangeListChangesIncluded;
  @NonNls private static final String SPLITTER_PROPORTION_OPTION = "CommitChangeListDialog.SPLITTER_PROPORTION";
  private final Action[] myExecutorActions;
  private final boolean myShowVcsCommit;
  private final Map<AbstractVcs, JPanel> myPerVcsOptionsPanels = new HashMap<AbstractVcs, JPanel>();

  @Nullable
  private final AbstractVcs myVcs;
  private final boolean myIsAlien;
  private boolean myDisposed = false;
  private final JLabel myWarningLabel;

  private final Map<String, CheckinChangeListSpecificComponent> myCheckinChangeListSpecificComponents;

  private final Map<String, String> myListComments;
  private String myLastSelectedListName;
  private CommitLegendPanel.ChangeInfoCalculator myChangesInfoCalculator;

  private final PseudoMap<Object, Object> myAdditionalData;

  private static class MyUpdateButtonsRunnable implements Runnable {
    private CommitChangeListDialog myDialog;

    private MyUpdateButtonsRunnable(final CommitChangeListDialog dialog) {
      myDialog = dialog;
    }

    public void cancel() {
      myDialog = null;
    }

    public void run() {
      if (myDialog != null) {
        myDialog.updateButtons();
        myDialog.updateLegend();
      }
    }

    public void restart(final CommitChangeListDialog dialog) {
      myDialog = dialog;
      run();
    }
  }

  private final MyUpdateButtonsRunnable myUpdateButtonsRunnable = new MyUpdateButtonsRunnable(this);

  private static boolean commit(final Project project, final List<Change> changes, final LocalChangeList initialSelection,
                             final List<CommitExecutor> executors, final boolean showVcsCommit, final String comment) {
    final ChangeListManager manager = ChangeListManager.getInstance(project);
    final LocalChangeList defaultList = manager.getDefaultChangeList();
    final ArrayList<LocalChangeList> changeLists = new ArrayList<LocalChangeList>(manager.getChangeListsCopy());
    CommitChangeListDialog dialog =
      new CommitChangeListDialog(project, changes, initialSelection, executors, showVcsCommit, defaultList, changeLists, null, false,
                                 comment);
    dialog.show();
    return dialog.isOK();
  }

  public static void commitPaths(final Project project, Collection<FilePath> paths, final LocalChangeList initialSelection,
                                 @Nullable final CommitExecutor executor, final String comment) {
    final ChangeListManager manager = ChangeListManager.getInstance(project);
    final Collection<Change> changes = new HashSet<Change>();
    for (FilePath path : paths) {
      changes.addAll(manager.getChangesIn(path));
    }

    commitChanges(project, changes, initialSelection, executor, comment);
  }

  public static boolean commitChanges(final Project project, final Collection<Change> changes, final LocalChangeList initialSelection,
                                   final CommitExecutor executor, final String comment) {
    if (executor == null) {
      return commitChanges(project, changes, initialSelection, collectExecutors(project, changes), true, comment);
    }
    else {
      return commitChanges(project, changes, initialSelection, Collections.singletonList(executor), false, comment);
    }
  }

  public static List<CommitExecutor> collectExecutors(Project project, Collection<Change> changes) {
    List<CommitExecutor> result = new ArrayList<CommitExecutor>();
    final ChangeListManager manager = ChangeListManager.getInstance(project);
    final List<AbstractVcs> vcses = getAffectedVcses(project, changes);
    for (AbstractVcs vcs : vcses) {
      result.addAll(vcs.getCommitExecutors());
    }
    result.addAll(manager.getRegisteredExecutors());
    return result;
  }

  public static boolean commitChanges(final Project project, final Collection<Change> changes, final LocalChangeList initialSelection,
                                   final List<CommitExecutor> executors, final boolean showVcsCommit, final String comment) {
    if (changes.isEmpty()) {
      Messages.showWarningDialog(project, VcsBundle.message("commit.dialog.no.changes.detected.text") ,
                                 VcsBundle.message("commit.dialog.no.changes.detected.title"));
      return false;
    }

    return commit(project, new ArrayList<Change>(changes), initialSelection, executors, showVcsCommit, comment);
  }

  public static void commitAlienChanges(final Project project, final List<Change> changes, final AbstractVcs vcs,
                                        final String changelistName, final String comment) {
    final LocalChangeList lcl = new AlienLocalChangeList(changes, changelistName);
    new CommitChangeListDialog(project, changes, null, null, true, AlienLocalChangeList.DEFAULT_ALIEN, Collections.singletonList(lcl), vcs,
                               true, comment).show();
  }

  private CommitChangeListDialog(final Project project,
                                 final List<Change> changes,
                                 final LocalChangeList initialSelection,
                                 final List<CommitExecutor> executors,
                                 final boolean showVcsCommit, final LocalChangeList defaultChangeList,
                                 final List<LocalChangeList> changeLists, final AbstractVcs singleVcs, final boolean isAlien,
                                 final String comment) {
    super(project, true);
    myProject = project;
    myExecutors = executors;
    myShowVcsCommit = showVcsCommit;
    myVcs = singleVcs;
    myListComments = new HashMap<String, String>();
    myAdditionalData = new PseudoMap<Object, Object>();

    if (!myShowVcsCommit && ((myExecutors == null) || myExecutors.size() == 0)) {
      throw new IllegalArgumentException("nothing found to execute commit with");
    }

    myAllOfDefaultChangeListChangesIncluded = changes.containsAll(defaultChangeList.getChanges());

    myIsAlien = isAlien;
    if (isAlien) {
      AlienChangeListBrowser browser = new AlienChangeListBrowser(project, changeLists, changes, initialSelection, true, true, singleVcs);
      myBrowser = browser;
      myBrowserExtender = browser;
    } else {
      MultipleChangeListBrowser browser = new MultipleChangeListBrowser(project, changeLists, changes, initialSelection, true, true,
                                                                        new Runnable() {
                                                                          public void run() {
                                                                            updateWarning();
                                                                          }
                                                                        },
        new Runnable() {
          public void run() {
            for (CheckinHandler handler : myHandlers) {
              handler.includedChangesChanged();
            }
          }
        });
      myBrowser = browser;
      myBrowserExtender = browser.getExtender();
    }

    myBrowserExtender.addToolbarActions(this);

    myBrowserExtender.addSelectedListChangeListener(new SelectedListChangeListener() {
      public void selectedListChanged() {
        updateOnListSelection();
      }
    });
    myBrowser.setDiffExtendUIFactory(new ShowDiffAction.DiffExtendUIFactory() {
      public List<? extends AnAction> createActions(final Change change) {
        return myBrowser.createDiffActions(change);
      }

      @Nullable
      public JComponent createBottomComponent() {
        return new DiffCommitMessageEditor(CommitChangeListDialog.this);
      }
    });

    myCommitMessageArea = new CommitMessage(project);

    if (comment != null) {
      setCommitMessage(comment);
      myLastKnownComment = comment;
      myLastSelectedListName = initialSelection == null ? myBrowser.getSelectedChangeList().getName() : initialSelection.getName();
    } else {
      setCommitMessage(VcsConfiguration.getInstance(project).LAST_COMMIT_MESSAGE);
      updateComment();

      String messageFromVcs = getInitialMessageFromVcs();
      if (messageFromVcs != null) {
        myCommitMessageArea.setText(messageFromVcs);
      }
    }

    myActionName = VcsBundle.message("commit.dialog.title");

    myAdditionalOptionsPanel = new JPanel();

    myAdditionalOptionsPanel.setLayout(new BorderLayout());
    Box optionsBox = Box.createVerticalBox();

    boolean hasVcsOptions = false;
    Box vcsCommitOptions = Box.createVerticalBox();
    final List<AbstractVcs> vcses = getAffectedVcses();
    myCheckinChangeListSpecificComponents = new HashMap<String, CheckinChangeListSpecificComponent>();
    for (AbstractVcs vcs : vcses) {
      final CheckinEnvironment checkinEnvironment = vcs.getCheckinEnvironment();
      if (checkinEnvironment != null) {
        final RefreshableOnComponent options = checkinEnvironment.createAdditionalOptionsPanel(this, myAdditionalData);
        if (options != null) {
          JPanel vcsOptions = new JPanel(new BorderLayout());
          vcsOptions.add(options.getComponent(), BorderLayout.CENTER);
          vcsOptions.add(SeparatorFactory.createSeparator(vcs.getDisplayName(), null), BorderLayout.NORTH);
          vcsCommitOptions.add(vcsOptions);
          myPerVcsOptionsPanels.put(vcs, vcsOptions);
          myAdditionalComponents.add(options);
          if (options instanceof CheckinChangeListSpecificComponent) {
            myCheckinChangeListSpecificComponents.put(vcs.getName(), (CheckinChangeListSpecificComponent) options);
          }
          hasVcsOptions = true;
        }
      }
    }

    if (hasVcsOptions) {
      vcsCommitOptions.add(Box.createVerticalGlue());
      optionsBox.add(vcsCommitOptions);
    }

    boolean beforeVisible = false;
    boolean afterVisible = false;
    Box beforeBox = Box.createVerticalBox();
    Box afterBox = Box.createVerticalBox();
    final List<CheckinHandlerFactory> handlerFactories = ProjectLevelVcsManager.getInstance(project).getRegisteredCheckinHandlerFactories();
    for (CheckinHandlerFactory factory : handlerFactories) {
      final CheckinHandler handler = factory.createHandler(this);
      myHandlers.add(handler);
      final RefreshableOnComponent beforePanel = handler.getBeforeCheckinConfigurationPanel();
      if (beforePanel != null) {
        beforeBox.add(beforePanel.getComponent());
        beforeVisible = true;
        myAdditionalComponents.add(beforePanel);
      }

      final RefreshableOnComponent afterPanel = handler.getAfterCheckinConfigurationPanel(getDisposable());
      if (afterPanel != null) {
        afterBox.add(afterPanel.getComponent());
        afterVisible = true;
        myAdditionalComponents.add(afterPanel);
      }
    }

    final String actionName = getCommitActionName();
    final String borderTitleName = actionName.replace("_", "");
    if (beforeVisible) {
      beforeBox.add(Box.createVerticalGlue());
      beforeBox.add(SeparatorFactory.createSeparator(VcsBundle.message("border.standard.checkin.options.group", borderTitleName), null), 0);
      optionsBox.add(beforeBox);
    }

    if (afterVisible) {
      afterBox.add(Box.createVerticalGlue());
      afterBox.add(SeparatorFactory.createSeparator(VcsBundle.message("border.standard.after.checkin.options.group", borderTitleName), null), 0);
      optionsBox.add(afterBox);
    }

    if (hasVcsOptions || beforeVisible || afterVisible) {
      optionsBox.add(Box.createVerticalGlue());
      myAdditionalOptionsPanel.add(optionsBox, BorderLayout.NORTH);
    }

    setOKButtonText(actionName);

    if (myShowVcsCommit) {
      setTitle(myActionName);
    }
    else {
      setTitle(trimEllipsis(myExecutors.get(0).getActionText()));
    }

    restoreState();

    if (myExecutors != null) {
      myExecutorActions = new Action[myExecutors.size()];

      for (int i = 0; i < myExecutors.size(); i++) {
        final CommitExecutor commitExecutor = myExecutors.get(i);
        myExecutorActions[i] = new CommitExecutorAction(commitExecutor, i == 0 && !myShowVcsCommit);
      }
    } else {
      myExecutorActions = null;
    }

    myWarningLabel = new JLabel();
    myWarningLabel.setUI(new MultiLineLabelUI());
    myWarningLabel.setForeground(Color.red);

    updateWarning();

    init();
    updateButtons();
    updateVcsOptionsVisibility();
    
    updateOnListSelection();
    myCommitMessageArea.requestFocusInMessage();
    
    for (EditChangelistSupport support : Extensions.getExtensions(EditChangelistSupport.EP_NAME, project)) {
      support.installSearch(myCommitMessageArea.getEditorField(), myCommitMessageArea.getEditorField());
    }

  }

  private void updateOnListSelection() {
    updateComment();
    updateVcsOptionsVisibility();
    for (CheckinChangeListSpecificComponent component : myCheckinChangeListSpecificComponents.values()) {
      component.onChangeListSelected((LocalChangeList) myBrowser.getSelectedChangeList());
    }
  }

  private void updateWarning() {
    // check for null since can be called from constructor before field initialization
    if (myWarningLabel != null) {
      myWarningLabel.setVisible(false);
      final VcsException updateException = ((ChangeListManagerImpl)ChangeListManager.getInstance(myProject)).getUpdateException();
      if (updateException != null) {
        final String[] messages = updateException.getMessages();
        if (messages != null || messages.length > 0) {
          final String message = messages[0];
          myWarningLabel.setText("Warning: not all local changes may be shown due to an error: " + message);
          myWarningLabel.setVisible(true);
        }
      }
    }
  }

  private void updateVcsOptionsVisibility() {
    final List<AbstractVcs> affectedVcses = getAffectedVcses(myProject, myBrowser.getSelectedChangeList().getChanges());
    for(Map.Entry<AbstractVcs, JPanel> entry: myPerVcsOptionsPanels.entrySet()) {
      entry.getValue().setVisible(affectedVcses.contains(entry.getKey()));
    }
  }

  protected Action[] createActions() {
    Action[] result;
    final int executorsSize = (myExecutors == null) ? 0 : myExecutors.size();

    if (myShowVcsCommit) {
      result = new Action[2 + executorsSize];
      result[0] = getOKAction();
      if (myExecutors != null) {
        System.arraycopy(myExecutorActions, 0, result, 1, myExecutorActions.length);
      }
    }
    else {
      result = new Action[1 + executorsSize];
      if (myExecutors != null) {
        System.arraycopy(myExecutorActions, 0, result, 0, myExecutorActions.length);
      }
    }
    result[result.length - 1] = getCancelAction();
    return result;
  }

  private void execute(final CommitExecutor commitExecutor) {
    if (!saveDialogState()) return;
    saveComments(true);
    final CommitSession session = commitExecutor.createCommitSession();
    if (session == CommitSession.VCS_COMMIT) {
      doOKAction();
      return;
    }
    boolean isOK = true;
    if (SessionDialog.createConfigurationUI(session, getIncludedChanges(), getCommitMessage())!= null) {
      DialogWrapper sessionDialog = new SessionDialog(commitExecutor.getActionText(),
                                                      getProject(),
                                                      session,
                                                      getIncludedChanges(),
                                                      getCommitMessage());
      sessionDialog.show();
      isOK = sessionDialog.isOK();
    }
    if (isOK) {
      final DefaultListCleaner defaultListCleaner = new DefaultListCleaner();
      runBeforeCommitHandlers(new Runnable() {
        public void run() {
          try {
            final boolean completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
              public void run() {
                session.execute(getIncludedChanges(), getCommitMessage());
              }
            }, commitExecutor.getActionText(), true, getProject());

            if (completed) {
              for (CheckinHandler handler : myHandlers) {
                handler.checkinSuccessful();
              }

              defaultListCleaner.clean();
              close(OK_EXIT_CODE);
            }
            else {
              session.executionCanceled();
            }
          }
          catch (Throwable e) {
            Messages.showErrorDialog(VcsBundle.message("error.executing.commit", commitExecutor.getActionText(), e.getLocalizedMessage()),
                                     commitExecutor.getActionText());

            for (CheckinHandler handler : myHandlers) {
              handler.checkinFailed(Arrays.asList(new VcsException(e)));
            }
          }
        }
      }, commitExecutor);


    }
    else {
      session.executionCanceled();
    }
  }

  @Nullable
  private String getInitialMessageFromVcs() {
    final List<Change> list = getIncludedChanges();
    final Ref<String> result = new Ref<String>();
    ChangesUtil.processChangesByVcs(myProject, list, new ChangesUtil.PerVcsProcessor<Change>() {
      public void process(final AbstractVcs vcs, final List<Change> items) {
        if (result.isNull()) {
          CheckinEnvironment checkinEnvironment = vcs.getCheckinEnvironment();
          if (checkinEnvironment != null) {
            final Collection<FilePath> paths = ChangesUtil.getPaths(items);
            String defaultMessage = checkinEnvironment.getDefaultMessageFor(paths.toArray(new FilePath[paths.size()]));
            if (defaultMessage != null) {
              result.set(defaultMessage);
            }
          }
        }
      }
    });
    return result.get();
  }

  private void saveCommentIntoChangeList() {
    if (myLastSelectedListName != null) {
      final String actualCommentText = myCommitMessageArea.getComment();
      final String saved = myListComments.get(myLastSelectedListName);
      if (! Comparing.equal(saved, actualCommentText)) {
        myListComments.put(myLastSelectedListName, actualCommentText);
      }
    }
  }

  private boolean isDefaultList(final LocalChangeList list) {
    return VcsBundle.message("changes.default.changlist.name").equals(list.getName());
  }

  private void updateComment() {
    final LocalChangeList list = (LocalChangeList) myBrowser.getSelectedChangeList();
    if (list == null || (list.getName().equals(myLastSelectedListName))) {
      return;
    } else if (myLastSelectedListName != null) {
      saveCommentIntoChangeList();
    }
    myLastSelectedListName = list.getName();

    String listComment = list.getComment();
    if (StringUtil.isEmptyOrSpaces(listComment)) {
      final String listTitle = list.getName();
      if (! isDefaultList(list)) {
        listComment = listTitle;
      }
      else {
        // use last know comment; it is already stored in list
        listComment = myLastKnownComment;
      }
    }

    myCommitMessageArea.setText(listComment);
  }


  @Override
  public void dispose() {
    myDisposed = true;
    myBrowser.dispose();
    Disposer.dispose(myCommitMessageArea);
    Disposer.dispose(myOKButtonUpdateAlarm);
    myUpdateButtonsRunnable.cancel();
    super.dispose();
    PropertiesComponent.getInstance().setValue(SPLITTER_PROPORTION_OPTION, String.valueOf(mySplitter.getProportion()));
  }

  public String getCommitActionName() {
    String name = null;
    for (AbstractVcs vcs : getAffectedVcses()) {
      final CheckinEnvironment checkinEnvironment = vcs.getCheckinEnvironment();
      if (name == null && checkinEnvironment != null) {
        name = checkinEnvironment.getCheckinOperationName();
      }
      else {
        name = VcsBundle.message("commit.dialog.default.commit.operation.name");
      }
    }
    return name != null ? name : VcsBundle.message("commit.dialog.default.commit.operation.name");
  }

  private boolean checkComment() {
    if (VcsConfiguration.getInstance(myProject).FORCE_NON_EMPTY_COMMENT && (getCommitMessage().length() == 0)) {
      int requestForCheckin = Messages.showYesNoDialog(VcsBundle.message("confirmation.text.check.in.with.empty.comment"),
                                                       VcsBundle.message("confirmation.title.check.in.with.empty.comment"),
                                                       Messages.getWarningIcon());
      return requestForCheckin == OK_EXIT_CODE;
    }
    else {
      return true;
    }
  }

  private void stopUpdate() {
    myDisposed = true;
    myUpdateButtonsRunnable.cancel();
  }

  private void restartUpdate() {
    myDisposed = false;
    myUpdateButtonsRunnable.restart(this);
  }
  
  private void runBeforeCommitHandlers(final Runnable okAction, final CommitExecutor executor) {
    Runnable proceedRunnable = new Runnable() {
      public void run() {
        FileDocumentManager.getInstance().saveAllDocuments();

        for (CheckinHandler handler : myHandlers) {
          final CheckinHandler.ReturnResult result = handler.beforeCheckin(executor, myAdditionalData);
          if (result == CheckinHandler.ReturnResult.COMMIT) continue;
          if (result == CheckinHandler.ReturnResult.CANCEL) {
            restartUpdate();
            return;
          }

          if (result == CheckinHandler.ReturnResult.CLOSE_WINDOW) {
            final ChangeList changeList = myBrowser.getSelectedChangeList();
            CommitHelper.moveToFailedList(changeList,
                             getCommitMessage(),
                             getIncludedChanges(),
                             VcsBundle.message("commit.dialog.rejected.commit.template", changeList.getName()),
                             myProject);
            doCancelAction();
            return;
          }
        }

        okAction.run();
      }
    };

    stopUpdate();
    for(CheckinHandler handler: myHandlers) {
      if (handler instanceof CheckinMetaHandler) {
        ((CheckinMetaHandler) handler).runCheckinHandlers(proceedRunnable);
        return;
      }
    }
    proceedRunnable.run();
  }

  protected void doOKAction() {
    if (!saveDialogState()) return;
    saveComments(true);
    final DefaultListCleaner defaultListCleaner = new DefaultListCleaner();

    ensureDataIsActual(new Runnable() {
      public void run() {
        try {
          runBeforeCommitHandlers(new Runnable() {
            public void run() {
              CommitChangeListDialog.super.doOKAction();
              doCommit();
            }
          }, null);

          defaultListCleaner.clean();
        }
        catch (InputException ex) {
          ex.show();
        }
      }
    });
  }

  private boolean saveDialogState() {
    if (!checkComment()) {
      return false;
    }

    saveCommentIntoChangeList();
    VcsConfiguration.getInstance(myProject).saveCommitMessage(getCommitMessage());
    try {
      saveState();
    }
    catch(InputException ex) {
      ex.show();
      return false;
    }
    return true;
  }

  private class DefaultListCleaner {
    private final boolean myToClean;

    private DefaultListCleaner() {
      final int selectedSize = getIncludedChanges().size();
      final ChangeList selectedList = myBrowser.getSelectedChangeList();
      final int totalSize = selectedList.getChanges().size();
      myToClean = (totalSize == selectedSize) && (isDefaultList((LocalChangeList) selectedList));
    }

    void clean() {
      if (myToClean) {
        final ChangeListManager clManager = ChangeListManager.getInstance(myProject);
        clManager.editComment(myLastSelectedListName, "");
      }
    }
  }

  private void saveComments(final boolean isOk) {
    final ChangeListManager clManager = ChangeListManager.getInstance(myProject);
    if (isOk) {
      final int selectedSize = getIncludedChanges().size();
      final ChangeList selectedList = myBrowser.getSelectedChangeList();
      final int totalSize = selectedList.getChanges().size();
      if (totalSize > selectedSize) {
        myListComments.remove(myLastSelectedListName);
      }
    }
    for (Map.Entry<String, String> entry : myListComments.entrySet()) {
      final String name = entry.getKey();
      final String value = entry.getValue();
      clManager.editComment(name, value);
    }
  }

  @Override
  public void doCancelAction() {
    for (CheckinChangeListSpecificComponent component : myCheckinChangeListSpecificComponents.values()) {
      component.saveState();
    }
    saveCommentIntoChangeList();
    saveComments(false);
    //VcsConfiguration.getInstance(myProject).saveCommitMessage(getCommitMessage());
    super.doCancelAction();
  }

  private void doCommit() {
    final CommitHelper helper = new CommitHelper(
      myProject,
      myBrowser.getSelectedChangeList(),
      getIncludedChanges(),
      myActionName,
      getCommitMessage(),
      myHandlers,
      myAllOfDefaultChangeListChangesIncluded, false, myAdditionalData);

    if (myIsAlien) {
      helper.doAlienCommit(myVcs);
    } else {
      helper.doCommit();
    }
  }

  @Nullable
  protected JComponent createCenterPanel() {
    JPanel rootPane = new JPanel(new BorderLayout());

    mySplitter = new Splitter(true);
    mySplitter.setHonorComponentsMinimumSize(true);
    mySplitter.setFirstComponent(myBrowser);
    mySplitter.setSecondComponent(myCommitMessageArea);
    mySplitter.setProportion(calcSplitterProportion());
    mySplitter.setDividerWidth(3);
    rootPane.add(mySplitter, BorderLayout.CENTER);

    JComponent browserHeader = myBrowser.getHeaderPanel();
    myBrowser.remove(browserHeader);
    rootPane.add(browserHeader, BorderLayout.NORTH);

    JPanel infoPanel = new JPanel(new BorderLayout());
    myChangesInfoCalculator = new CommitLegendPanel.ChangeInfoCalculator();
    myLegend = new CommitLegendPanel(myChangesInfoCalculator);
    infoPanel.add(myLegend.getComponent(), BorderLayout.NORTH);
    infoPanel.add(myAdditionalOptionsPanel, BorderLayout.CENTER);
    rootPane.add(infoPanel, BorderLayout.EAST);
    infoPanel.setBorder(IdeBorderFactory.createEmptyBorder(0, 10, 0, 0));

    rootPane.add(myWarningLabel, BorderLayout.SOUTH);

    return rootPane;
  }

  private static float calcSplitterProportion() {
    try {
      final String s = PropertiesComponent.getInstance().getValue(SPLITTER_PROPORTION_OPTION);
      return s != null ? Float.valueOf(s).floatValue() : 0.5f;
    }
    catch (NumberFormatException e) {
      return 0.5f;
    }
  }

  public List<AbstractVcs> getAffectedVcses() {
    if (! myShowVcsCommit) {
      return Collections.emptyList();
    }
    return myBrowserExtender.getAffectedVcses();
  }

  private static List<AbstractVcs> getAffectedVcses(Project project, final Collection<Change> changes) {
    Set<AbstractVcs> result = new HashSet<AbstractVcs>();
    for (Change change : changes) {
      final AbstractVcs vcs = ChangesUtil.getVcsForChange(change, project);
      if (vcs != null) {
        result.add(vcs);
      }
    }
    return new ArrayList<AbstractVcs>(result);
  }

  public Collection<VirtualFile> getRoots() {
    Set<VirtualFile> result = new HashSet<VirtualFile>();
    for (Change change : myBrowser.getCurrentDisplayedChanges()) {
      final FilePath filePath = ChangesUtil.getFilePath(change);
      VirtualFile root = ProjectLevelVcsManager.getInstance(myProject).getVcsRootFor(filePath);
      if (root != null) {
        result.add(root);
      }
    }
    return result;
  }

  public JComponent getComponent() {
    return mySplitter;
  }

  public boolean hasDiffs() {
    return !getIncludedChanges().isEmpty();
  }

  public Collection<VirtualFile> getVirtualFiles() {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (Change change: getIncludedChanges()) {
      final FilePath path = ChangesUtil.getFilePath(change);
      final VirtualFile vFile = path.getVirtualFile();
      if (vFile != null) {
        result.add(vFile);
      }
    }

    return result;
  }

  public Collection<Change> getSelectedChanges() {
    return new ArrayList<Change>(getIncludedChanges());
  }

  public Collection<File> getFiles() {
    List<File> result = new ArrayList<File>();
    for (Change change: getIncludedChanges()) {
      final FilePath path = ChangesUtil.getFilePath(change);
      final File file = path.getIOFile();
      result.add(file);
    }

    return result;
  }

  public Project getProject() {
    return myProject;
  }

  @Override
  public boolean vcsIsAffected(String name) {
    // tod +- performance?
    if (! ProjectLevelVcsManager.getInstance(myProject).checkVcsIsActive(name)) return false;
    final List<AbstractVcs> affected = myBrowserExtender.getAffectedVcses();
    for (AbstractVcs vcs : affected) {
      if (Comparing.equal(vcs.getName(), name)) return true;
    }
    return false;
  }

  public void setCommitMessage(final String currentDescription) {
    setCommitMessageText(currentDescription);
    myCommitMessageArea.requestFocusInMessage();
  }

  public Object getContextInfo(Object object) {
    // todo
    return null;
  }

  public void setWarning(String s) {
    // todo
  }

  private void setCommitMessageText(final String currentDescription) {
    myLastKnownComment = currentDescription;
    myCommitMessageArea.setText(currentDescription);
  }

  public String getCommitMessage() {
    return myCommitMessageArea.getComment();
  }

  public void refresh() {
    ChangeListManager.getInstance(myProject).invokeAfterUpdate(new Runnable() {
      public void run() {
        myBrowser.rebuildList();
        for (RefreshableOnComponent component : myAdditionalComponents) {
          component.refresh();
        }
      }
    }, InvokeAfterUpdateMode.SILENT, "commit dialog", ModalityState.current());   // title not shown for silently
  }

  public void saveState() {
    for (RefreshableOnComponent component : myAdditionalComponents) {
      component.saveState();
    }
  }

  public void restoreState() {
    for (RefreshableOnComponent component : myAdditionalComponents) {
      component.restoreState();
    }
  }

  private void updateButtons() {
    if (myDisposed) return;
    setOKActionEnabled(hasDiffs());
    if (myExecutorActions != null) {
      for (Action executorAction : myExecutorActions) {
        executorAction.setEnabled(hasDiffs());
      }
    }
    myOKButtonUpdateAlarm.cancelAllRequests();
    myOKButtonUpdateAlarm.addRequest(myUpdateButtonsRunnable, 300, ModalityState.stateForComponent(myBrowser));
  }

  private void updateLegend() {
    if (myDisposed) return;
    myChangesInfoCalculator.update(myBrowser.getCurrentDisplayedChanges(), myBrowserExtender.getCurrentIncludedChanges());
    myLegend.update();
  }

  @NotNull
  private List<Change> getIncludedChanges() {
    return myBrowserExtender.getCurrentIncludedChanges();
  }

  @NonNls
  protected String getDimensionServiceKey() {
    return "CommitChangelistDialog";
  }
  
  public JComponent getPreferredFocusedComponent() {
    return myCommitMessageArea.getEditorField();
  }

  public void calcData(DataKey key, DataSink sink) {
    if (key == CheckinProjectPanel.PANEL_KEY) {
      sink.put(CheckinProjectPanel.PANEL_KEY, this);
    }
    else {
      myBrowser.calcData(key, sink);
    }
  }

  static String trimEllipsis(final String title) {
    if (title.endsWith("...")) {
      return title.substring(0, title.length() - 3);
    }
    else {
      return title;
    }
  }

  private void ensureDataIsActual(final Runnable runnable) {
    ChangeListManager.getInstance(myProject).invokeAfterUpdate(runnable, InvokeAfterUpdateMode.SYNCHRONOUS_CANCELLABLE,
                                                               "Refreshing changelists...", ModalityState.current());
  }

  private class CommitExecutorAction extends AbstractAction {
    private final CommitExecutor myCommitExecutor;

    public CommitExecutorAction(final CommitExecutor commitExecutor, final boolean isDefault) {
      super(commitExecutor.getActionText());
      myCommitExecutor = commitExecutor;
      if (isDefault) {
        putValue(DEFAULT_ACTION, Boolean.TRUE);
      }
    }

    public void actionPerformed(ActionEvent e) {
      ensureDataIsActual(new Runnable() {
        public void run() {
          execute(myCommitExecutor);
        }
      });
    }
  }

  private static class DiffCommitMessageEditor extends JPanel implements Disposable {
    private CommitChangeListDialog myCommitDialog;
    private final JTextArea myArea = new JTextArea();

    public DiffCommitMessageEditor(final CommitChangeListDialog dialog) {
      super(new BorderLayout());
      myArea.setText(dialog.getCommitMessage());
      myArea.setLineWrap(true);      
      myArea.setWrapStyleWord(true);
      JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myArea);
      setBorder(IdeBorderFactory.createTitledBorder(VcsBundle.message("diff.commit.message.title")));
      add(scrollPane, BorderLayout.CENTER);
      myCommitDialog = dialog;
    }

    public void dispose() {
      if (myCommitDialog != null) {
        myCommitDialog.setCommitMessageText(myArea.getText());
        myCommitDialog = null;
      }
    }

    public Dimension getPreferredSize() {
      // we don't want to be squeezed to one line
      return new Dimension(400, 120);
    }
  }
}

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.diff.util.DiffPlaces;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.ide.HelpIdProvider;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalChangeListDiffTool;
import com.intellij.openapi.vcs.checkin.*;
import com.intellij.openapi.vcs.impl.CheckinHandlersManager;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.SplitterWithSecondHideable;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.vcsUtil.VcsUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.List;
import java.util.*;

import static com.intellij.openapi.diagnostic.Logger.getInstance;
import static com.intellij.openapi.util.text.StringUtil.escapeXmlEntities;
import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;
import static com.intellij.openapi.vcs.VcsBundle.message;
import static com.intellij.openapi.vcs.changes.ui.SingleChangeListCommitter.moveToFailedList;
import static com.intellij.ui.components.JBBox.createHorizontalBox;
import static com.intellij.util.ArrayUtil.isEmpty;
import static com.intellij.util.ArrayUtil.toObjectArray;
import static com.intellij.util.ObjectUtils.chooseNotNull;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.*;
import static com.intellij.util.ui.SwingHelper.buildHtml;
import static com.intellij.util.ui.UIUtil.*;
import static java.util.Collections.emptyList;
import static java.util.Collections.*;

public class CommitChangeListDialog extends DialogWrapper implements CheckinProjectPanel, DataProvider {
  private static final Logger LOG = getInstance(CommitChangeListDialog.class);

  public static final String DIALOG_TITLE = message("commit.dialog.title");

  private static final String HELP_ID = "reference.dialogs.vcs.commit";

  private static final int LAYOUT_VERSION = 2;
  private static final String SPLITTER_PROPORTION_OPTION = "CommitChangeListDialog.SPLITTER_PROPORTION_" + LAYOUT_VERSION;
  private static final String DETAILS_SPLITTER_PROPORTION_OPTION = "CommitChangeListDialog.DETAILS_SPLITTER_PROPORTION_" + LAYOUT_VERSION;
  private static final String DETAILS_SHOW_OPTION = "CommitChangeListDialog.DETAILS_SHOW_OPTION_";

  private static final float SPLITTER_PROPORTION_OPTION_DEFAULT = 0.5f;
  private static final float DETAILS_SPLITTER_PROPORTION_OPTION_DEFAULT = 0.6f;
  private static final boolean DETAILS_SHOW_OPTION_DEFAULT = true;

  @NotNull private final Project myProject;
  @NotNull private final VcsConfiguration myVcsConfiguration;
  private final boolean myShowVcsCommit;
  @NotNull private final DialogCommitWorkflow myWorkflow;
  @Nullable private final CommitResultHandler myResultHandler;

  @NotNull private final Set<? extends AbstractVcs<?>> myAffectedVcses;
  @NotNull private final List<? extends CommitExecutor> myExecutors;
  @NotNull private final List<CheckinHandler> myHandlers = newArrayList();
  @NotNull private final String myCommitActionName;

  @NotNull private final Map<String, String> myListComments = newHashMap();
  @NotNull private final List<CommitExecutorAction> myExecutorActions;

  @NotNull private final CommitOptionsPanel myCommitOptions;
  @NotNull private final CommitContext myCommitContext;
  @NotNull private final ChangeInfoCalculator myChangesInfoCalculator;
  @NotNull private final CommitDialogChangesBrowser myBrowser;
  @NotNull private final JComponent myBrowserBottomPanel = createHorizontalBox();
  @NotNull private final MyChangeProcessor myDiffDetails;
  @NotNull private final CommitMessage myCommitMessageArea;
  @NotNull private final CommitLegendPanel myLegend;

  @NotNull private final Splitter mySplitter;
  @NotNull private final SplitterWithSecondHideable myDetailsSplitter;
  @NotNull private final JBLabel myWarningLabel;

  @Nullable private final String myHelpId;
  @Nullable private final CommitAction myCommitAction;

  @NotNull private final Alarm myOKButtonUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  @NotNull private final Runnable myUpdateButtonsRunnable = () -> {
    updateButtons();
    updateLegend();
  };

  private boolean myDisposed = false;
  private boolean myUpdateDisabled = false;

  private String myLastKnownComment = "";
  private String myLastSelectedListName;

  public static boolean commitChanges(@NotNull Project project,
                                      @NotNull Collection<? extends Change> changes,
                                      @Nullable LocalChangeList initialSelection,
                                      @Nullable CommitExecutor executor,
                                      @Nullable String comment) {
    return commitChanges(project, changes, changes, initialSelection, executor, comment);
  }

  public static boolean commitChanges(@NotNull Project project,
                                      @NotNull Collection<? extends Change> changes,
                                      @NotNull Collection<?> included,
                                      @Nullable LocalChangeList initialSelection,
                                      @Nullable CommitExecutor executor,
                                      @Nullable String comment) {
    if (executor == null) {
      return commitChanges(project, newArrayList(changes), included, initialSelection, collectExecutors(project, changes), true, null,
                           comment, null, true);
    }
    else {
      return commitChanges(project, newArrayList(changes), included, initialSelection, singletonList(executor), false, null, comment, null,
                           true);
    }
  }

  /**
   * Shows the commit dialog, and performs the selected action: commit, commit & push, create patch, etc.
   *
   * @param customResultHandler If this is not null, after commit is completed, custom result handler is called instead of
   *                            showing the default notification in case of commit or failure.
   * @return true if user agreed to commit, false if he pressed "Cancel".
   */
  public static boolean commitChanges(@NotNull Project project,
                                      @NotNull Collection<? extends Change> changes,
                                      @Nullable LocalChangeList initialSelection,
                                      @NotNull List<? extends CommitExecutor> executors,
                                      boolean showVcsCommit,
                                      @Nullable String comment,
                                      @Nullable CommitResultHandler customResultHandler) {
    return commitChanges(project, newArrayList(changes), initialSelection, executors, showVcsCommit, comment, customResultHandler, true);
  }

  public static boolean commitChanges(@NotNull Project project,
                                      @NotNull List<Change> changes,
                                      @Nullable LocalChangeList initialSelection,
                                      @NotNull List<? extends CommitExecutor> executors,
                                      boolean showVcsCommit,
                                      @Nullable String comment,
                                      @Nullable CommitResultHandler customResultHandler,
                                      boolean cancelIfNoChanges) {
    return commitChanges(project, changes, changes, initialSelection, executors, showVcsCommit, null, comment, customResultHandler,
                         cancelIfNoChanges);
  }

  public static boolean commitChanges(@NotNull Project project,
                                      @NotNull List<Change> changes,
                                      @NotNull Collection<?> included,
                                      @Nullable LocalChangeList initialSelection,
                                      @NotNull List<? extends CommitExecutor> executors,
                                      boolean showVcsCommit,
                                      @Nullable AbstractVcs forceCommitInVcs,
                                      @Nullable String comment,
                                      @Nullable CommitResultHandler customResultHandler,
                                      boolean cancelIfNoChanges) {
    ChangeListManager manager = ChangeListManager.getInstance(project);
    LocalChangeList defaultList = manager.getDefaultChangeList();
    List<LocalChangeList> changeLists = manager.getChangeListsCopy();

    Set<AbstractVcs<?>> affectedVcses = new HashSet<>();
    if (forceCommitInVcs != null) affectedVcses.add(forceCommitInVcs);
    for (LocalChangeList list : changeLists) {
      //noinspection unchecked
      affectedVcses.addAll((Set)ChangesUtil.getAffectedVcses(list.getChanges(), project));
    }
    if (showVcsCommit) {
      List<VirtualFile> unversionedFiles = ChangeListManagerImpl.getInstanceImpl(project).getUnversionedFiles();
      //noinspection unchecked
      affectedVcses.addAll((Set)ChangesUtil.getAffectedVcsesForFiles(unversionedFiles, project));
    }


    if (cancelIfNoChanges && affectedVcses.isEmpty()) {
      Messages.showInfoMessage(project, message("commit.dialog.no.changes.detected.text"),
                               message("commit.dialog.no.changes.detected.title"));
      return false;
    }

    for (BaseCheckinHandlerFactory factory : getCheckInFactories(project)) {
      BeforeCheckinDialogHandler handler = factory.createSystemReadyHandler(project);
      if (handler != null && !handler.beforeCommitDialogShown(project, changes, (Iterable<CommitExecutor>)executors, showVcsCommit)) {
        return false;
      }
    }

    boolean isDefaultChangeListFullyIncluded = newHashSet(changes).containsAll(defaultList.getChanges());
    DialogCommitWorkflow workflow =
      new DialogCommitWorkflow(project, included, initialSelection, executors, showVcsCommit, forceCommitInVcs, affectedVcses,
                               isDefaultChangeListFullyIncluded, comment, customResultHandler);
    return workflow.showDialog();
  }

  @NotNull
  private static List<BaseCheckinHandlerFactory> getCheckInFactories(@NotNull Project project) {
    return CheckinHandlersManager.getInstance().getRegisteredCheckinHandlerFactories(
      ProjectLevelVcsManager.getInstance(project).getAllActiveVcss());
  }

  @NotNull
  public static List<CommitExecutor> collectExecutors(@NotNull Project project, @NotNull Collection<? extends Change> changes) {
    List<CommitExecutor> result = newArrayList();
    for (AbstractVcs<?> vcs : ChangesUtil.getAffectedVcses(changes, project)) {
      result.addAll(vcs.getCommitExecutors());
    }
    result.addAll(ChangeListManager.getInstance(project).getRegisteredExecutors());
    return result;
  }

  CommitChangeListDialog(@NotNull DialogCommitWorkflow workflow) {
    super(workflow.getProject(), true, (Registry.is("ide.perProjectModality")) ? IdeModalityType.PROJECT : IdeModalityType.IDE);
    myCommitContext = new CommitContext();
    myWorkflow = workflow;
    myProject = myWorkflow.getProject();
    myVcsConfiguration = notNull(VcsConfiguration.getInstance(myProject));
    myShowVcsCommit = myWorkflow.isDefaultCommitEnabled();
    myAffectedVcses = myWorkflow.getAffectedVcses();
    myExecutors = myWorkflow.getExecutors();
    myResultHandler = myWorkflow.getResultHandler();

    if (!myShowVcsCommit && ContainerUtil.isEmpty(myExecutors)) {
      throw new IllegalArgumentException("nothing found to execute commit with");
    }

    myHandlers.addAll(createCheckinHandlers(myProject, this, myCommitContext));

    setTitle(myShowVcsCommit ? DIALOG_TITLE : getExecutorPresentableText(myExecutors.get(0)));
    myCommitActionName = getCommitActionName(myAffectedVcses);
    myExecutorActions = createExecutorActions(myExecutors);
    if (myShowVcsCommit) {
      myCommitAction = new CommitAction(myCommitActionName);
      myCommitAction.setOptions(myExecutorActions);
    }
    else {
      myCommitAction = null;
      myExecutorActions.get(0).putValue(DEFAULT_ACTION, Boolean.TRUE);
    }
    myHelpId = myShowVcsCommit ? HELP_ID : getHelpId(myExecutors);

    myDiffDetails = new MyChangeProcessor(myProject, myWorkflow.isPartialCommitEnabled());
    myCommitMessageArea = new CommitMessage(myProject, true, true, myShowVcsCommit);
    myBrowser = myWorkflow.createBrowser();
    myChangesInfoCalculator = new ChangeInfoCalculator();
    myLegend = new CommitLegendPanel(myChangesInfoCalculator);
    mySplitter = new Splitter(true);
    myCommitOptions = new CommitOptionsPanel(this, myHandlers, myShowVcsCommit ? myAffectedVcses : emptySet());
    myWarningLabel = new JBLabel();

    JPanel mainPanel;
    if (!myCommitOptions.isEmpty()) {
      mainPanel = new JPanel(new MyOptionsLayout(mySplitter, myCommitOptions, JBUI.scale(150), JBUI.scale(400)));
      mainPanel.add(mySplitter);
      mainPanel.add(myCommitOptions);
    }
    else {
      mainPanel = mySplitter;
    }

    JPanel rootPane = JBUI.Panels.simplePanel(mainPanel).addToBottom(myWarningLabel);
    myDetailsSplitter = createDetailsSplitter(rootPane);
  }

  @Override
  protected void init() {
    beforeInit();
    super.init();
    afterInit();
  }

  private void beforeInit() {
    myBrowserBottomPanel.add(myLegend.getComponent());
    BorderLayoutPanel topPanel = JBUI.Panels.simplePanel().addToCenter(myBrowser).addToBottom(myBrowserBottomPanel);

    mySplitter.setHonorComponentsMinimumSize(true);
    mySplitter.setFirstComponent(topPanel);
    mySplitter.setSecondComponent(myCommitMessageArea);
    mySplitter.setProportion(PropertiesComponent.getInstance().getFloat(SPLITTER_PROPORTION_OPTION, SPLITTER_PROPORTION_OPTION_DEFAULT));

    if (!myVcsConfiguration.CLEAR_INITIAL_COMMIT_MESSAGE) {
      initComment(myWorkflow.getInitialCommitMessage());
    }

    restoreState();

    myWarningLabel.setForeground(JBColor.RED);
    myWarningLabel.setBorder(JBUI.Borders.empty(5, 5, 0, 5));
    updateWarning();
  }

  private void afterInit() {
    updateButtons();
    updateLegend();
    updateOnListSelection();
    myCommitMessageArea.requestFocusInMessage();

    for (EditChangelistSupport support : EditChangelistSupport.EP_NAME.getExtensions(myProject)) {
      support.installSearch(myCommitMessageArea.getEditorField(), myCommitMessageArea.getEditorField());
    }

    showDetailsIfSaved();
  }

  @NotNull
  private SplitterWithSecondHideable createDetailsSplitter(@NotNull JPanel rootPane) {
    SplitterWithSecondHideable.OnOffListener<Integer> listener = new SplitterWithSecondHideable.OnOffListener<Integer>() {
      @Override
      public void on(Integer integer) {
        if (integer == 0) return;
        myDiffDetails.refresh(false);
        mySplitter.skipNextLayout();
        myDetailsSplitter.getComponent().skipNextLayout();
        Dimension dialogSize = getSize();
        setSize(dialogSize.width, dialogSize.height + integer);
        repaint();
      }

      @Override
      public void off(Integer integer) {
        if (integer == 0) return;
        myDiffDetails.clear();
        mySplitter.skipNextLayout();
        myDetailsSplitter.getComponent().skipNextLayout();
        Dimension dialogSize = getSize();
        setSize(dialogSize.width, dialogSize.height - integer);
        repaint();
      }
    };
    // TODO: there are no reason to use such heavy interface for a simple task.
    return new SplitterWithSecondHideable(true, "Diff", rootPane, listener) {
      @Override
      protected RefreshablePanel createDetails() {
        JPanel panel = JBUI.Panels.simplePanel(myDiffDetails.getComponent());
        return new RefreshablePanel() {
          @Override
          public void refresh() {
          }

          @Override
          public JPanel getPanel() {
            return panel;
          }
        };
      }

      @Override
      protected float getSplitterInitialProportion() {
        float value = PropertiesComponent.getInstance().getFloat(DETAILS_SPLITTER_PROPORTION_OPTION, DETAILS_SPLITTER_PROPORTION_OPTION_DEFAULT);
        return value <= 0.05 || value >= 0.95 ? DETAILS_SPLITTER_PROPORTION_OPTION_DEFAULT : value;
      }
    };
  }

  @NotNull
  public static List<CheckinHandler> createCheckinHandlers(@NotNull Project project,
                                                           @NotNull CheckinProjectPanel checkinPanel,
                                                           @NotNull CommitContext commitContext) {
    List<CheckinHandler> handlers = new ArrayList<>();
    for (BaseCheckinHandlerFactory factory : getCheckInFactories(project)) {
      CheckinHandler handler = factory.createHandler(checkinPanel, commitContext);
      if (!CheckinHandler.DUMMY.equals(handler)) {
        handlers.add(handler);
      }
    }
    return handlers;
  }

  @NotNull
  private List<CommitExecutorAction> createExecutorActions(@NotNull List<? extends CommitExecutor> executors) {
    if(executors.isEmpty()) return emptyList();
    List<CommitExecutorAction> result = newArrayList();

    if (myShowVcsCommit && UISettings.getShadowInstance().getAllowMergeButtons()) {
      ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction("Vcs.CommitExecutor.Actions");

      result.addAll(map(group.getChildren(null), CommitExecutorAction::new));
      result.addAll(map(filter(executors, CommitExecutor::useDefaultAction), CommitExecutorAction::new));
    }
    else {
      result.addAll(map(executors, CommitExecutorAction::new));
    }

    return result;
  }

  @Nullable
  private static String getHelpId(@NotNull List<? extends CommitExecutor> executors) {
    return StreamEx.of(executors).select(HelpIdProvider.class).map(HelpIdProvider::getHelpId).nonNull().findFirst().orElse(null);
  }

  private void initComment(@Nullable String comment) {
    LocalChangeList list = myBrowser.getSelectedChangeList();
    myLastSelectedListName = list.getName();

    if (comment == null) {
      comment = getCommentFromChangelist(list);

      if (isEmptyOrSpaces(comment)) {
        myLastKnownComment = myVcsConfiguration.LAST_COMMIT_MESSAGE;
        comment = chooseNotNull(getInitialMessageFromVcs(), myVcsConfiguration.LAST_COMMIT_MESSAGE);
      }
    }
    else {
      myLastKnownComment = comment;
    }

    myCommitMessageArea.setText(comment);
  }

  private void showDetailsIfSaved() {
    boolean showDetails = PropertiesComponent.getInstance().getBoolean(DETAILS_SHOW_OPTION, DETAILS_SHOW_OPTION_DEFAULT);
    if (showDetails) {
      myDetailsSplitter.initOn();
    }
    changeDetails(false);
  }

  private void updateOnListSelection() {
    if (!myVcsConfiguration.CLEAR_INITIAL_COMMIT_MESSAGE) {
      updateComment();
    }
    myCommitMessageArea.setChangeList(myBrowser.getSelectedChangeList());
    myCommitOptions.onChangeListSelected(myBrowser.getSelectedChangeList(),
                                         ChangeListManagerImpl.getInstanceImpl(myProject).getUnversionedFiles());
  }

  private void updateWarning() {
    // check for null since can be called from constructor before field initialization
    //noinspection ConstantConditions
    if (myWarningLabel != null) {
      myWarningLabel.setVisible(false);
      VcsException updateException = ChangeListManagerImpl.getInstanceImpl(myProject).getUpdateException();
      if (updateException != null) {
        String[] messages = updateException.getMessages();
        if (!isEmpty(messages)) {
          String message = "Warning: not all local changes may be shown due to an error: " + messages[0];
          String htmlMessage = buildHtml(getCssFontDeclaration(getLabelFont()), getHtmlBody(escapeXmlEntities(message)));

          myWarningLabel.setText(htmlMessage);
          myWarningLabel.setVisible(true);
        }
      }
    }
  }

  @Nullable
  @Override
  protected String getHelpId() {
    return myHelpId;
  }

  private class CommitAction extends AbstractAction implements OptionAction {
    @NotNull private Action[] myOptions = new Action[0];

    private CommitAction(String okActionText) {
      super(okActionText);
      putValue(DEFAULT_ACTION, Boolean.TRUE);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      executeDefaultCommitSession(null);
    }

    @NotNull
    @Override
    public Action[] getOptions() {
      return myOptions;
    }

    public void setOptions(@NotNull List<? extends Action> actions) {
      myOptions = toObjectArray(actions, Action.class);
    }
  }

  @NotNull
  @Override
  protected Action getOKAction() {
    return myCommitAction != null ? myCommitAction : myExecutorActions.get(0);
  }

  @Override
  @NotNull
  protected Action[] createActions() {
    List<Action> result = newArrayList();

    if (myCommitAction != null) {
      result.add(myCommitAction);
    }
    else {
      result.addAll(myExecutorActions);
    }
    result.add(getCancelAction());
    if (myHelpId != null) {
      result.add(getHelpAction());
    }

    return toObjectArray(result, Action.class);
  }

  private void executeDefaultCommitSession(@Nullable CommitExecutor executor) {
    if (!myWorkflow.prepareCommit(myBrowser.getIncludedUnversionedFiles(), myBrowser)) return;
    if (!saveDialogState()) return;
    saveComments(true);

    ensureDataIsActual(() -> {
      try {
        DefaultListCleaner defaultListCleaner = new DefaultListCleaner();
        CheckinHandler.ReturnResult result = performBeforeCommitChecks(executor);
        if (result == CheckinHandler.ReturnResult.COMMIT) {
          close(OK_EXIT_CODE);
          myWorkflow.doCommit(myBrowser.getSelectedChangeList(), getIncludedChanges(), getCommitMessage(), myHandlers,
                              myCommitOptions.getAdditionalData());

          defaultListCleaner.clean();
        }
      }
      catch (InputException ex) {
        ex.show();
      }
    });
  }

  public void execute(@NotNull CommitExecutor commitExecutor) {
    CommitSession session = commitExecutor.createCommitSession();
    if (session == CommitSession.VCS_COMMIT) {
      executeDefaultCommitSession(commitExecutor);
      return;
    }

    if (!myWorkflow.canExecute(commitExecutor, getIncludedChanges())) return;
    if (!saveDialogState()) return;
    saveComments(true);

    if (session instanceof CommitSessionContextAware) {
      ((CommitSessionContextAware)session).setContext(myCommitContext);
    }

    ensureDataIsActual(() -> {
      JComponent configurationUI = SessionDialog.createConfigurationUI(session, getIncludedChanges(), getCommitMessage());
      if (configurationUI != null) {
        DialogWrapper sessionDialog =
          new SessionDialog(getExecutorPresentableText(commitExecutor), getProject(), session, getIncludedChanges(), getCommitMessage(),
                            configurationUI);
        if (!sessionDialog.showAndGet()) {
          session.executionCanceled();
          return;
        }
      }

      DefaultListCleaner defaultListCleaner = new DefaultListCleaner();
      CheckinHandler.ReturnResult result = performBeforeCommitChecks(commitExecutor);
      if (result == CheckinHandler.ReturnResult.COMMIT) {
        boolean success = false;
        try {
          boolean completed = ProgressManager.getInstance()
            .runProcessWithProgressSynchronously(() -> session.execute(getIncludedChanges(), getCommitMessage()),
                                                 commitExecutor.getActionText(), true, getProject());

          if (completed) {
            LOG.debug("Commit successful");
            myHandlers.forEach(CheckinHandler::checkinSuccessful);
            success = true;
            defaultListCleaner.clean();
            close(OK_EXIT_CODE);
          }
          else {
            LOG.debug("Commit canceled");
            session.executionCanceled();
          }
        }
        catch (Throwable e) {
          Messages.showErrorDialog(message("error.executing.commit", commitExecutor.getActionText(), e.getLocalizedMessage()),
                                   commitExecutor.getActionText());

          myHandlers.forEach(handler -> handler.checkinFailed(singletonList(new VcsException(e))));
        }
        finally {
          if (myResultHandler != null) {
            if (success) {
              myResultHandler.onSuccess(getCommitMessage());
            }
            else {
              myResultHandler.onFailure();
            }
          }
        }
      }
    });
  }

  @Nullable
  private String getInitialMessageFromVcs() {
    Ref<String> result = new Ref<>();
    ChangesUtil.processChangesByVcs(myProject, getIncludedChanges(), (vcs, changes) -> {
      if (result.isNull()) {
        CheckinEnvironment checkinEnvironment = vcs.getCheckinEnvironment();
        if (checkinEnvironment != null) {
          FilePath[] paths = toObjectArray(ChangesUtil.getPaths(changes), FilePath.class);
          result.set(checkinEnvironment.getDefaultMessageFor(paths));
        }
      }
    });
    return result.get();
  }

  private void saveCommentIntoChangeList() {
    if (myLastSelectedListName != null) {
      myListComments.put(myLastSelectedListName, myCommitMessageArea.getComment());
    }
  }

  private void updateComment() {
    LocalChangeList list = myBrowser.getSelectedChangeList();
    if (!list.getName().equals(myLastSelectedListName)) {
      saveCommentIntoChangeList();

      myLastSelectedListName = list.getName();
      myCommitMessageArea.setText(chooseNotNull(getCommentFromChangelist(list), myLastKnownComment));
    }
  }

  @Nullable
  private String getCommentFromChangelist(@NotNull LocalChangeList list) {
    for (CommitMessageProvider provider : CommitMessageProvider.EXTENSION_POINT_NAME.getExtensionList()) {
      String message = provider.getCommitMessage(list, getProject());
      if (message != null) return message;
    }

    String changeListComment = list.getComment();
    if (!isEmptyOrSpaces(changeListComment)) return changeListComment;

    if (!list.hasDefaultName()) return list.getName();
    return null;
  }

  @Override
  public void dispose() {
    myDisposed = true;
    Disposer.dispose(myCommitOptions);
    Disposer.dispose(myBrowser);
    Disposer.dispose(myCommitMessageArea);
    Disposer.dispose(myOKButtonUpdateAlarm);
    super.dispose();
    Disposer.dispose(myDiffDetails);
    PropertiesComponent.getInstance().setValue(SPLITTER_PROPORTION_OPTION, mySplitter.getProportion(), SPLITTER_PROPORTION_OPTION_DEFAULT);
    float usedProportion = myDetailsSplitter.getUsedProportion();
    if (usedProportion > 0) {
      PropertiesComponent.getInstance().setValue(DETAILS_SPLITTER_PROPORTION_OPTION, usedProportion, DETAILS_SPLITTER_PROPORTION_OPTION_DEFAULT);
    }
    PropertiesComponent.getInstance().setValue(DETAILS_SHOW_OPTION, myDetailsSplitter.isOn(), DETAILS_SHOW_OPTION_DEFAULT);
  }

  @NotNull
  @Override
  public String getCommitActionName() {
    return myCommitActionName;
  }

  @NotNull
  private static String getCommitActionName(@NotNull Collection<? extends AbstractVcs<?>> affectedVcses) {
    Set<String> names = map2SetNotNull(affectedVcses, vcs -> {
      CheckinEnvironment checkinEnvironment = vcs.getCheckinEnvironment();
      return checkinEnvironment != null ? checkinEnvironment.getCheckinOperationName() : null;
    });
    if (names.size() == 1) {
      return notNull(getFirstItem(names));
    }
    return VcsBundle.getString("commit.dialog.default.commit.operation.name");
  }

  private boolean checkComment() {
    if (myVcsConfiguration.FORCE_NON_EMPTY_COMMENT && getCommitMessage().isEmpty()) {
      int requestForCheckin = Messages.showYesNoDialog(message("confirmation.text.check.in.with.empty.comment"),
                                                       message("confirmation.title.check.in.with.empty.comment"),
                                                       Messages.getWarningIcon());
      return requestForCheckin == Messages.YES;
    }
    else {
      return true;
    }
  }

  private void stopUpdate() {
    myUpdateDisabled = true;
  }

  private void restartUpdate() {
    myUpdateDisabled = false;
    myUpdateButtonsRunnable.run();
  }

  @NotNull
  private CheckinHandler.ReturnResult performBeforeCommitChecks(@Nullable CommitExecutor executor) {
    stopUpdate();

    Ref<CheckinHandler.ReturnResult> compoundResultRef = Ref.create();
    Runnable proceedRunnable = () -> {
      FileDocumentManager.getInstance().saveAllDocuments();
      compoundResultRef.set(runBeforeCheckinHandlers(executor));
    };

    Runnable runnable = wrapIntoCheckinMetaHandlers(proceedRunnable);
    myWorkflow.doRunBeforeCommitChecks(myBrowser.getSelectedChangeList(), runnable);
    return notNull(compoundResultRef.get(), CheckinHandler.ReturnResult.CANCEL);
  }

  @NotNull
  private CheckinHandler.ReturnResult runBeforeCheckinHandlers(@Nullable CommitExecutor executor) {
    for (CheckinHandler handler : myHandlers) {
      if (!handler.acceptExecutor(executor)) continue;
      LOG.debug("CheckinHandler.beforeCheckin: " + handler);
      CheckinHandler.ReturnResult result = handler.beforeCheckin(executor, myCommitOptions.getAdditionalData());
      if (result == CheckinHandler.ReturnResult.COMMIT) continue;
      if (result == CheckinHandler.ReturnResult.CANCEL) {
        restartUpdate();
        return CheckinHandler.ReturnResult.CANCEL;
      }

      if (result == CheckinHandler.ReturnResult.CLOSE_WINDOW) {
        ChangeList changeList = myBrowser.getSelectedChangeList();
        moveToFailedList(myProject, changeList, getCommitMessage(), getIncludedChanges(),
                         message("commit.dialog.rejected.commit.template", changeList.getName()));
        doCancelAction();
        return CheckinHandler.ReturnResult.CLOSE_WINDOW;
      }
    }

    return CheckinHandler.ReturnResult.COMMIT;
  }

  private Runnable wrapIntoCheckinMetaHandlers(Runnable runnable) {
    for (CheckinHandler handler : myHandlers) {
      if (handler instanceof CheckinMetaHandler) {
        CheckinMetaHandler metaHandler = (CheckinMetaHandler)handler;
        Runnable previousRunnable = runnable;
        runnable = () -> {
          LOG.debug("CheckinMetaHandler.runCheckinHandlers: " + handler);
          metaHandler.runCheckinHandlers(previousRunnable);
        };
      }
    }
    return runnable;
  }

  private boolean saveDialogState() {
    if (!checkComment()) {
      return false;
    }

    saveCommentIntoChangeList();
    myVcsConfiguration.saveCommitMessage(getCommitMessage());
    try {
      saveState();
      return true;
    }
    catch(InputException ex) {
      ex.show();
      return false;
    }
  }

  private class DefaultListCleaner {
    private final boolean myToClean;

    private DefaultListCleaner() {
      int selectedSize = getIncludedChanges().size();
      LocalChangeList selectedList = myBrowser.getSelectedChangeList();
      int totalSize = selectedList.getChanges().size();
      myToClean = totalSize == selectedSize && selectedList.hasDefaultName();
    }

    void clean() {
      if (myToClean) {
        ChangeListManager.getInstance(myProject).editComment(LocalChangeList.DEFAULT_NAME, "");
      }
    }
  }

  private void saveComments(boolean isOk) {
    if (isOk) {
      int selectedSize = getIncludedChanges().size();
      int totalSize = myBrowser.getSelectedChangeList().getChanges().size();
      if (totalSize > selectedSize) {
        myListComments.remove(myLastSelectedListName);
      }
    }
    ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    myListComments.forEach((changeListName, comment) -> changeListManager.editComment(changeListName, comment));
  }

  @Override
  public void doCancelAction() {
    myCommitOptions.saveChangeListComponentsState();
    saveCommentIntoChangeList();
    saveComments(false);
    LineStatusTrackerManager.getInstanceImpl(myProject).resetExcludedFromCommitMarkers();
    super.doCancelAction();
  }

  @NotNull
  @Override
  protected JComponent createCenterPanel() {
    return myDetailsSplitter.getComponent();
  }

  @Deprecated
  @NotNull
  public Set<? extends AbstractVcs> getAffectedVcses() {
    return myShowVcsCommit ? myAffectedVcses : emptySet();
  }

  @NotNull
  public List<? extends CommitExecutor> getExecutors() {
    return myExecutors;
  }

  @NotNull
  @Override
  public Collection<VirtualFile> getRoots() {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);

    return map2SetNotNull(getDisplayedPaths(), filePath -> vcsManager.getVcsRootFor(filePath));
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return mySplitter;
  }

  @Override
  public boolean hasDiffs() {
    return !getIncludedChanges().isEmpty() || !myBrowser.getIncludedUnversionedFiles().isEmpty();
  }

  @NotNull
  @Override
  public Collection<VirtualFile> getVirtualFiles() {
    return mapNotNull(getIncludedPaths(), filePath -> filePath.getVirtualFile());
  }

  @NotNull
  @Override
  public Collection<Change> getSelectedChanges() {
    return newArrayList(getIncludedChanges());
  }

  @NotNull
  @Override
  public Collection<File> getFiles() {
    return map(getIncludedPaths(), filePath -> filePath.getIOFile());
  }

  @NotNull
  @Override
  public Project getProject() {
    return myProject;
  }

  @Override
  public boolean vcsIsAffected(String name) {
    return ProjectLevelVcsManager.getInstance(myProject).checkVcsIsActive(name) &&
           exists(myAffectedVcses, vcs -> Comparing.equal(vcs.getName(), name));
  }

  @Override
  public void setCommitMessage(@Nullable String commitMessage) {
    myLastKnownComment = commitMessage;
    myCommitMessageArea.setText(commitMessage);
    myCommitMessageArea.requestFocusInMessage();
  }

  @NotNull
  @Override
  public String getCommitMessage() {
    return myCommitMessageArea.getComment();
  }

  @Override
  public void refresh() {
    ChangeListManager.getInstance(myProject).invokeAfterUpdate(() -> {
      myBrowser.updateDisplayedChangeLists();
      myCommitOptions.refresh();
    }, InvokeAfterUpdateMode.SILENT, "commit dialog", ModalityState.current());
  }

  @Override
  public void saveState() {
    myCommitOptions.saveState();
  }

  @Override
  public void restoreState() {
    myCommitOptions.restoreState();
  }

  // Used in plugins
  @SuppressWarnings("unused")
  @NotNull
  public List<RefreshableOnComponent> getAdditionalComponents() {
    return myCommitOptions.getAdditionalComponents();
  }

  private void updateButtons() {
    if (myDisposed || myUpdateDisabled) return;
    boolean enabled = hasDiffs();
    if (myCommitAction != null) {
      myCommitAction.setEnabled(enabled);
    }
    myExecutorActions.forEach(action -> action.updateEnabled(enabled));
    myOKButtonUpdateAlarm.cancelAllRequests();
    myOKButtonUpdateAlarm.addRequest(myUpdateButtonsRunnable, 300, ModalityState.stateForComponent(myBrowser));
  }

  private void updateLegend() {
    if (myDisposed || myUpdateDisabled) return;
    myChangesInfoCalculator.update(myBrowser.getDisplayedChanges(), getIncludedChanges(),
                                   myBrowser.getDisplayedUnversionedFiles().size(),
                                   myBrowser.getIncludedUnversionedFiles().size());
    myLegend.update();
  }

  @NotNull
  private List<Change> getIncludedChanges() {
    return myBrowser.getIncludedChanges();
  }

  @NotNull
  private List<FilePath> getIncludedPaths() {
    List<FilePath> paths = new ArrayList<>();
    for (Change change : myBrowser.getIncludedChanges()) {
      paths.add(ChangesUtil.getFilePath(change));
    }
    for (VirtualFile file : myBrowser.getIncludedUnversionedFiles()) {
      paths.add(VcsUtil.getFilePath(file));
    }
    return paths;
  }

  @NotNull
  private List<FilePath> getDisplayedPaths() {
    List<FilePath> paths = new ArrayList<>();
    for (Change change : myBrowser.getDisplayedChanges()) {
      paths.add(ChangesUtil.getFilePath(change));
    }
    for (VirtualFile file : myBrowser.getDisplayedUnversionedFiles()) {
      paths.add(VcsUtil.getFilePath(file));
    }
    return paths;
  }

  @Override
  @NonNls
  protected String getDimensionServiceKey() {
    return "CommitChangelistDialog" + LAYOUT_VERSION;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myCommitMessageArea.getEditorField();
  }

  @Nullable
  @Override
  public Object getData(@NotNull String dataId) {
    if (Refreshable.PANEL_KEY.is(dataId)) {
      return this;
    }
    return myBrowser.getData(dataId);
  }

  @NotNull
  public CommitDialogChangesBrowser getBrowser() {
    return myBrowser;
  }

  @NotNull
  CommitMessage getCommitMessageComponent() {
    return myCommitMessageArea;
  }

  @NotNull
  JComponent getBrowserBottomPanel() {
    return myBrowserBottomPanel;
  }

  void inclusionChanged() {
    myHandlers.forEach(CheckinHandler::includedChangesChanged);
    updateButtons();
  }

  void selectedChangeListChanged() {
    updateOnListSelection();
    updateWarning();
  }

  @NotNull
  static String getExecutorPresentableText(@NotNull CommitExecutor executor) {
    return trimEllipsis(removeMnemonic(executor.getActionText()));
  }

  @NotNull
  static String trimEllipsis(@NotNull String title) {
    return StringUtil.trimEnd(StringUtil.trimEnd(title, "..."), "\u2026");
  }

  private void ensureDataIsActual(@NotNull Runnable runnable) {
    ChangeListManager.getInstance(myProject).invokeAfterUpdate(
      () -> {
        myBrowser.updateDisplayedChangeLists();
        runnable.run();
      },
      InvokeAfterUpdateMode.SYNCHRONOUS_CANCELLABLE, "Refreshing changelists...", ModalityState.current());
  }

  private class CommitExecutorAction extends AbstractAction {
    @Nullable private final CommitExecutor myCommitExecutor;

    CommitExecutorAction(@NotNull AnAction anAction) {
      putValue(OptionAction.AN_ACTION, anAction);
      myCommitExecutor = null;
    }

    CommitExecutorAction(@NotNull CommitExecutor commitExecutor) {
      super(commitExecutor.getActionText());
      myCommitExecutor = commitExecutor;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      if (myCommitExecutor != null) {
        execute(myCommitExecutor);
      }
    }

    public void updateEnabled(boolean hasDiffs) {
      if (myCommitExecutor != null) {
        setEnabled(
          hasDiffs || myCommitExecutor instanceof CommitExecutorBase && !((CommitExecutorBase)myCommitExecutor).areChangesRequired());
      }
    }
  }

  void changeDetails(boolean fromModelRefresh) {
    SwingUtilities.invokeLater(() -> {
      if (myDetailsSplitter.isOn()) {
        myDiffDetails.refresh(fromModelRefresh);
      }
    });
  }

  private class MyChangeProcessor extends ChangeViewDiffRequestProcessor {
    MyChangeProcessor(@NotNull Project project, boolean enablePartialCommit) {
      super(project, DiffPlaces.COMMIT_DIALOG);

      putContextUserData(DiffUserDataKeysEx.SHOW_READ_ONLY_LOCK, true);
      putContextUserData(LocalChangeListDiffTool.ALLOW_EXCLUDE_FROM_COMMIT, enablePartialCommit);
      putContextUserData(DiffUserDataKeysEx.LAST_REVISION_WITH_LOCAL, true);
    }

    @NotNull
    @Override
    protected List<Wrapper> getSelectedChanges() {
      return wrap(myBrowser.getSelectedChanges(), myBrowser.getSelectedUnversionedFiles());
    }

    @NotNull
    @Override
    protected List<Wrapper> getAllChanges() {
      return wrap(myBrowser.getDisplayedChanges(), myBrowser.getDisplayedUnversionedFiles());
    }

    @Override
    protected void selectChange(@NotNull Wrapper change) {
      myBrowser.selectEntries(singletonList(change.getUserObject()));
    }

    @NotNull
    private List<Wrapper> wrap(@NotNull Collection<? extends Change> changes, @NotNull Collection<? extends VirtualFile> unversioned) {
      return concat(map(changes, ChangeWrapper::new), map(unversioned, UnversionedFileWrapper::new));
    }

    @Override
    protected void onAfterNavigate() {
      doCancelAction();
    }
  }

  private static class MyOptionsLayout extends AbstractLayoutManager {
    @NotNull private final JComponent myPanel;
    @NotNull private final JComponent myOptions;
    private final int myMinOptionsWidth;
    private final int myMaxOptionsWidth;

    MyOptionsLayout(@NotNull JComponent panel, @NotNull JComponent options, int minOptionsWidth, int maxOptionsWidth) {
      myPanel = panel;
      myOptions = options;
      myMinOptionsWidth = minOptionsWidth;
      myMaxOptionsWidth = maxOptionsWidth;
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      Dimension size1 = myPanel.getPreferredSize();
      Dimension size2 = myOptions.getPreferredSize();
      return new Dimension(size1.width + size2.width, Math.max(size1.height, size2.height));
    }

    @Override
    public void layoutContainer(@NotNull Container parent) {
      Rectangle bounds = parent.getBounds();
      int preferredWidth = myOptions.getPreferredSize().width;
      int optionsWidth = Math.max(Math.min(myMaxOptionsWidth, preferredWidth), myMinOptionsWidth);
      myPanel.setBounds(new Rectangle(0, 0, bounds.width - optionsWidth, bounds.height));
      myOptions.setBounds(new Rectangle(bounds.width - optionsWidth, 0, optionsWidth, bounds.height));
    }
  }
}

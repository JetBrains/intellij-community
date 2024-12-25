// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.diff.util.DiffPlaces;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.ide.HelpIdProvider;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsActions;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalChangeListDiffTool;
import com.intellij.openapi.vcs.checkin.BaseCheckinHandlerFactory;
import com.intellij.openapi.vcs.checkin.BeforeCheckinDialogHandler;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.ui.JBColor;
import com.intellij.ui.SplitterWithSecondHideable;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Alarm;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.vcs.commit.*;
import kotlin.sequences.SequencesKt;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.*;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.escapeXmlEntities;
import static com.intellij.openapi.vcs.VcsBundle.message;
import static com.intellij.ui.components.JBBox.createHorizontalBox;
import static com.intellij.util.ArrayUtil.isEmpty;
import static com.intellij.util.MathUtil.clamp;
import static com.intellij.util.containers.ContainerUtil.filter;
import static com.intellij.util.containers.ContainerUtil.map;
import static com.intellij.util.ui.JBUI.Borders.emptyLeft;
import static com.intellij.util.ui.SwingHelper.buildHtml;
import static com.intellij.util.ui.UIUtil.*;
import static com.intellij.vcs.commit.AbstractCommitWorkflow.getCommitExecutors;
import static com.intellij.vcs.commit.AbstractCommitWorkflow.getCommitHandlerFactories;
import static com.intellij.vcs.commit.AbstractCommitWorkflowKt.cleanActionText;
import static java.lang.Math.max;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public abstract class CommitChangeListDialog extends DialogWrapper implements SingleChangeListCommitWorkflowUi, ComponentContainer {
  public static final @NlsContexts.DialogTitle String DIALOG_TITLE = message("commit.dialog.title");

  private static final String HELP_ID = "reference.dialogs.vcs.commit";

  private static final int LAYOUT_VERSION = 2;
  @ApiStatus.Internal
  public static final String DIMENSION_SERVICE_KEY = "CommitChangelistDialog" + LAYOUT_VERSION;

  private static final String SPLITTER_PROPORTION_OPTION = "CommitChangeListDialog.SPLITTER_PROPORTION_" + LAYOUT_VERSION;
  private static final String DETAILS_SPLITTER_PROPORTION_OPTION = "CommitChangeListDialog.DETAILS_SPLITTER_PROPORTION_" + LAYOUT_VERSION;
  private static final String DETAILS_SHOW_OPTION = "CommitChangeListDialog.DETAILS_SHOW_OPTION_";

  private static final float SPLITTER_PROPORTION_OPTION_DEFAULT = 0.5f;
  private static final float DETAILS_SPLITTER_PROPORTION_OPTION_DEFAULT = 0.6f;
  private static final boolean DETAILS_SHOW_OPTION_DEFAULT = true;

  private final @NotNull Project myProject;
  private final @NotNull CommitChangeListDialogWorkflow myWorkflow;
  private final @NotNull EventDispatcher<CommitWorkflowUiStateListener> myStateEventDispatcher =
    EventDispatcher.create(CommitWorkflowUiStateListener.class);
  private final @NotNull EventDispatcher<CommitExecutorListener> myExecutorEventDispatcher =
    EventDispatcher.create(CommitExecutorListener.class);
  private final @NotNull List<DataProvider> myDataProviders = new ArrayList<>();
  private final @NotNull EventDispatcher<InclusionListener> myInclusionEventDispatcher = EventDispatcher.create(InclusionListener.class);

  private @NotNull @NlsContexts.Button String myDefaultCommitActionName = "";
  private @Nullable CommitAction myCommitAction;
  private final @NotNull List<CommitExecutorAction> myExecutorActions = new ArrayList<>();

  private final @NotNull CommitOptionsPanel myCommitOptions;
  private final @NotNull JComponent myCommitOptionsPanel;
  private final @NotNull ChangeInfoCalculator myChangesInfoCalculator;
  private final @NotNull JComponent myBrowserBottomPanel = createHorizontalBox();
  private final @NotNull MyChangeProcessor myDiffDetails;
  private final @NotNull CommitMessage myCommitMessageArea;
  private final @NotNull CommitLegendPanel myLegend;

  private final @NotNull Splitter mySplitter;
  private final @NotNull SplitterWithSecondHideable myDetailsSplitter;
  private final @NotNull JBLabel myWarningLabel;

  private final @Nullable String myHelpId;

  private final @NotNull Alarm okButtonUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
  private final @NotNull Runnable myUpdateButtonsRunnable = () -> {
    updateButtons();
    updateLegend();
  };

  private boolean myDisposed = false;
  private boolean myUpdateDisabled = false;

  /**
   * @deprecated Prefer using {@link #commitWithExecutor} or {@link #commitVcsChanges}.
   */
  @Deprecated
  public static boolean commitChanges(@NotNull Project project,
                                      @NotNull Collection<? extends Change> included,
                                      @Nullable LocalChangeList initialChangeList,
                                      @Nullable CommitExecutor executor,
                                      @Nullable String comment) {
    if (executor != null) {
      return commitWithExecutor(project, included, initialChangeList, executor, comment, null);
    }
    else {
      return commitVcsChanges(project, included, initialChangeList, comment, null);
    }
  }

  private static @NotNull Set<AbstractVcs> getVcsesForLocalChanges(@NotNull Project project, boolean showVcsCommit) {
    Set<AbstractVcs> affectedVcses = new HashSet<>();
    ChangeListManager manager = ChangeListManager.getInstance(project);

    Collection<Change> localChanges = manager.getAllChanges();
    affectedVcses.addAll(ChangesUtil.getAffectedVcses(localChanges, project));

    if (showVcsCommit) {
      List<FilePath> unversionedFiles = manager.getUnversionedFilesPaths();
      affectedVcses.addAll(ChangesUtil.getAffectedVcsesForFilePaths(unversionedFiles, project));
    }

    return affectedVcses;
  }

  public static boolean commitWithExecutor(@NotNull Project project,
                                           @NotNull Collection<?> included,
                                           @Nullable LocalChangeList initialChangeList,
                                           @NotNull CommitExecutor executor,
                                           @Nullable String comment,
                                           @Nullable CommitResultHandler customResultHandler) {
    boolean showVcsCommit = false;
    Set<AbstractVcs> affectedVcses = getVcsesForLocalChanges(project, showVcsCommit);
    if (affectedVcses.isEmpty()) {
      showNothingToCommitMessage(project);
      return false;
    }

    List<CommitExecutor> executors = singletonList(executor);
    return showCommitDialog(project, affectedVcses, included, initialChangeList, executors, showVcsCommit,
                            comment, customResultHandler);
  }

  public static boolean commitVcsChanges(@NotNull Project project,
                                         @NotNull Collection<?> included,
                                         @Nullable LocalChangeList initialChangeList,
                                         @Nullable String comment,
                                         @Nullable CommitResultHandler customResultHandler) {
    boolean showVcsCommit = true;
    Set<AbstractVcs> affectedVcses = getVcsesForLocalChanges(project, showVcsCommit);
    if (affectedVcses.isEmpty()) {
      showNothingToCommitMessage(project);
      return false;
    }

    List<CommitExecutor> executors = getCommitExecutors(project, affectedVcses);
    return showCommitDialog(project, affectedVcses, included, initialChangeList, executors, showVcsCommit,
                            comment, customResultHandler);
  }

  /**
   * Shows the commit dialog for local changes and performs the selected action: commit, commit & push, create patch, etc.
   *
   * @param affectedVcses       Used for vcs-specific commit {@link com.intellij.openapi.vcs.checkin.CheckinHandler} and {@link RefreshableOnComponent}.
   * @param included            Files selected for commit by default.
   *                            Pass {@link Change} for modified and {@link com.intellij.openapi.vfs.VirtualFile} for unversioned files.
   * @param initialChangeList   Changelist to be selected by default. If not set, {@link ChangeListManager#getDefaultChangeList()} will be used.
   * @param executors           Additional commit executors, available in the dialog. See also {@code showVcsCommit}.
   * @param showVcsCommit       Whether default "Commit into VCS" action is available in the dialog.
   *                            This does not affect "Commit & Push" and similar actions, use {@link AbstractVcs#getCommitExecutors()} or
   *                            {@link AbstractCommitWorkflow#getCommitExecutors(Project, Collection)}.
   * @param comment             Pre-entered commit message.
   * @param customResultHandler If this is not null, after commit is completed, passed handler is called instead of default
   *                            {@link ShowNotificationCommitResultHandler}.
   * @return true if user agreed to commit, false if he pressed "Cancel".
   */
  public static boolean showCommitDialog(@NotNull Project project,
                                         @NotNull Set<AbstractVcs> affectedVcses,
                                         @NotNull Collection<?> included,
                                         @Nullable LocalChangeList initialChangeList,
                                         @NotNull List<? extends CommitExecutor> executors,
                                         boolean showVcsCommit,
                                         @Nullable String comment,
                                         @Nullable CommitResultHandler customResultHandler) {
    List<Change> changes = ContainerUtil.filterIsInstance(included, Change.class);
    for (BaseCheckinHandlerFactory factory : getCommitHandlerFactories(affectedVcses)) {
      BeforeCheckinDialogHandler handler = factory.createSystemReadyHandler(project);
      if (handler != null && !handler.beforeCommitDialogShown(project, changes, executors, showVcsCommit)) {
        return false;
      }
    }

    SingleChangeListCommitWorkflow workflow = new SingleChangeListCommitWorkflow(project, affectedVcses, initialChangeList,
                                                                                 executors, showVcsCommit, customResultHandler);
    CommitChangeListDialog dialog = new DefaultCommitChangeListDialog(workflow);

    return new SingleChangeListCommitWorkflowHandler(workflow, dialog, comment, included).activate();
  }

  public static void showNothingToCommitMessage(@NotNull Project project) {
    Messages.showInfoMessage(project, message("commit.dialog.no.changes.detected.text"),
                             message("commit.dialog.no.changes.detected.title"));
  }

  protected CommitChangeListDialog(@NotNull CommitChangeListDialogWorkflow workflow) {
    super(workflow.getProject(), true, IdeModalityType.IDE);
    myWorkflow = workflow;
    myProject = myWorkflow.getProject();
    Disposer.register(getDisposable(), this);

    List<? extends CommitExecutor> executors = myWorkflow.getCommitExecutors();
    if (!isDefaultCommitEnabled() && ContainerUtil.isEmpty(executors)) {
      throw new IllegalArgumentException("nothing found to execute commit with");
    }

    setTitle(isDefaultCommitEnabled() ? DIALOG_TITLE : cleanActionText(executors.get(0).getActionText(), true));
    myHelpId = isDefaultCommitEnabled() ? HELP_ID : getHelpId(executors);

    myDiffDetails = new MyChangeProcessor(myProject, myWorkflow.isPartialCommitEnabled());
    myCommitMessageArea = new CommitMessage(myProject, true, true, isDefaultCommitEnabled());
    myChangesInfoCalculator = new ChangeInfoCalculator();
    myLegend = new CommitLegendPanel(myChangesInfoCalculator);
    mySplitter = new Splitter(true);
    boolean nonFocusable = !UISettings.getInstance().getDisableMnemonicsInControls(); // Or that won't be keyboard accessible at all
    myCommitOptions = new CommitOptionsPanel(myProject, () -> getDefaultCommitActionName(), nonFocusable, false);
    myCommitOptionsPanel = myCommitOptions.component;
    myWarningLabel = new JBLabel();

    JPanel mainPanel = new JPanel(new MyOptionsLayout(mySplitter, myCommitOptions, JBUIScale.scale(150), JBUIScale.scale(400)));
    mainPanel.add(mySplitter);
    mainPanel.add(myCommitOptionsPanel);

    JPanel rootPane = JBUI.Panels.simplePanel(mainPanel).addToBottom(myWarningLabel);
    myDetailsSplitter = createDetailsSplitter(rootPane);
  }

  public abstract @NotNull CommitDialogChangesBrowser getBrowser();

  @Override
  public boolean activate() {
    beforeInit();
    init();
    afterInit();

    return showAndGet();
  }

  @Override
  public void deactivate() {
    close(OK_EXIT_CODE);
  }

  @Override
  public void addStateListener(@NotNull CommitWorkflowUiStateListener listener, @NotNull Disposable parent) {
    myStateEventDispatcher.addListener(listener, parent);
  }

  private void beforeInit() {
    //readaction is not enough
    getBrowser().setInclusionChangedListener(() -> WriteIntentReadAction.run(
      (Runnable)() -> myInclusionEventDispatcher.getMulticaster().inclusionChanged()
    ));

    addInclusionListener(() -> updateButtons(), this);
    getBrowser().getViewer().addSelectionListener(() -> {
      refreshDetails(getBrowser().getViewer().isModelUpdateInProgress(), true);
    });

    initCommitActions(myWorkflow.getCommitExecutors());

    myCommitOptionsPanel.setBorder(emptyLeft(10));

    myBrowserBottomPanel.add(myLegend.getComponent());
    BorderLayoutPanel topPanel = JBUI.Panels.simplePanel().addToCenter(getBrowser()).addToBottom(myBrowserBottomPanel);

    mySplitter.setHonorComponentsMinimumSize(true);
    mySplitter.setFirstComponent(topPanel);
    mySplitter.setSecondComponent(myCommitMessageArea);
    mySplitter.setProportion(PropertiesComponent.getInstance().getFloat(SPLITTER_PROPORTION_OPTION, SPLITTER_PROPORTION_OPTION_DEFAULT));

    myWarningLabel.setForeground(JBColor.RED);
    myWarningLabel.setBorder(JBUI.Borders.empty(5, 5, 0, 5));
    updateWarning();
  }

  protected void afterInit() {
    updateButtons();
    updateLegend();
    myCommitMessageArea.setChangesSupplier(new ChangeListChangesSupplier(getChangeList()));
    myCommitMessageArea.requestFocusInMessage();

    for (EditChangelistSupport support : EditChangelistSupport.EP_NAME.getExtensionList(myProject)) {
      support.installSearch(myCommitMessageArea.getEditorField(), myCommitMessageArea.getEditorField());
    }

    showDetailsIfSaved();

    LaterInvocator.markTransparent(ModalityState.stateForComponent(getComponent()));
  }

  private @NotNull SplitterWithSecondHideable createDetailsSplitter(@NotNull JPanel rootPane) {
    SplitterWithSecondHideable.OnOffListener listener = new SplitterWithSecondHideable.OnOffListener() {
      @Override
      public void on(int hideableHeight) {
        if (hideableHeight == 0) return;
        getWindow().addComponentListener(new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent e) {
            e.getComponent().removeComponentListener(this);
            refreshDetails(false, true);
          }
        });
        mySplitter.skipNextLayout();
        myDetailsSplitter.getComponent().skipNextLayout();
        Dimension dialogSize = getSize();
        setSize(dialogSize.width, dialogSize.height + hideableHeight);
        repaint();
      }

      @Override
      public void off(int hideableHeight) {
        if (hideableHeight == 0) return;
        myDiffDetails.clear();
        mySplitter.skipNextLayout();
        myDetailsSplitter.getComponent().skipNextLayout();
        Dimension dialogSize = getSize();
        setSize(dialogSize.width, dialogSize.height - hideableHeight);
        repaint();
      }
    };
    // TODO: there are no reason to use such heavy interface for a simple task.
    return new SplitterWithSecondHideable(true, message("changes.diff.separator"), rootPane, listener) {
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
        float value = PropertiesComponent.getInstance().getFloat(DETAILS_SPLITTER_PROPORTION_OPTION,
                                                                 DETAILS_SPLITTER_PROPORTION_OPTION_DEFAULT);
        return value <= 0.05 || value >= 0.95 ? DETAILS_SPLITTER_PROPORTION_OPTION_DEFAULT : value;
      }
    };
  }

  private void initCommitActions(@NotNull List<? extends CommitExecutor> executors) {
    myExecutorActions.addAll(createExecutorActions(executors));
    if (isDefaultCommitEnabled()) {
      myCommitAction = new CommitAction(getDefaultCommitActionName());
      myCommitAction.setOptions(myExecutorActions);
    }
    else {
      myCommitAction = null;
      myExecutorActions.get(0).putValue(DEFAULT_ACTION, Boolean.TRUE);
    }
  }

  private @NotNull List<CommitExecutorAction> createExecutorActions(@NotNull List<? extends CommitExecutor> executors) {
    if (executors.isEmpty()) return emptyList();
    List<CommitExecutorAction> result = new ArrayList<>();

    if (isDefaultCommitEnabled() && UISettings.getInstance().getAllowMergeButtons()) {
      ActionManager actionManager = ActionManager.getInstance();
      DefaultActionGroup primaryActions = (DefaultActionGroup)actionManager.getAction(VcsActions.PRIMARY_COMMIT_EXECUTORS_GROUP);
      DefaultActionGroup executorActions = (DefaultActionGroup)actionManager.getAction(VcsActions.COMMIT_EXECUTORS_GROUP);

      result.addAll(map(primaryActions.getChildren(actionManager), CommitExecutorAction::new));
      result.addAll(map(executorActions.getChildren(actionManager), CommitExecutorAction::new));
      result.addAll(map(filter(executors, CommitExecutor::useDefaultAction), CommitExecutorAction::new));
    }
    else {
      result.addAll(map(executors, CommitExecutorAction::new));
    }

    return result;
  }

  private static @Nullable String getHelpId(@NotNull List<? extends CommitExecutor> executors) {
    return StreamEx.of(executors).select(HelpIdProvider.class).map(HelpIdProvider::getHelpId).nonNull().findFirst().orElse(null);
  }

  private void showDetailsIfSaved() {
    boolean showDetails = PropertiesComponent.getInstance().getBoolean(DETAILS_SHOW_OPTION, DETAILS_SHOW_OPTION_DEFAULT);
    if (showDetails) {
      myDetailsSplitter.initOn();
      myDetailsSplitter.setInitialProportion();
      runWhenWindowOpened(getWindow(), () -> {
        myDetailsSplitter.setInitialProportion();
        refreshDetails(false, false);
      });
    }
  }

  protected void updateWarning() {
    // check for null since can be called from constructor before field initialization
    //noinspection ConstantConditions
    if (myWarningLabel != null) {
      myWarningLabel.setVisible(false);
      VcsException updateException = ChangeListManagerImpl.getInstanceImpl(myProject).getUpdateException();
      if (updateException != null) {
        String[] messages = updateException.getMessages();
        if (!isEmpty(messages)) {
          String message = message("changes.warning.not.all.local.changes.may.be.shown.due.to.an.error", messages[0]);
          String htmlMessage = buildHtml(getCssFontDeclaration(StartupUiUtil.getLabelFont()), getHtmlBody(escapeXmlEntities(message)));

          myWarningLabel.setText(htmlMessage);
          myWarningLabel.setVisible(true);
        }
      }
    }
  }

  @Override
  protected @Nullable String getHelpId() {
    return myHelpId;
  }

  private final class CommitAction extends AbstractAction implements OptionAction {
    private Action @NotNull [] myOptions = new Action[0];

    private CommitAction(@NlsContexts.Button String okActionText) {
      super(okActionText);
      putValue(DEFAULT_ACTION, Boolean.TRUE);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      myExecutorEventDispatcher.getMulticaster().executorCalled(null);
    }

    @Override
    public Action @NotNull [] getOptions() {
      return myOptions;
    }

    public void setOptions(@NotNull List<? extends Action> actions) {
      myOptions = actions.toArray(new Action[0]);
    }
  }

  @Override
  protected @NotNull Action getOKAction() {
    return myCommitAction != null ? myCommitAction : myExecutorActions.get(0);
  }

  @Override
  protected Action @NotNull [] createActions() {
    List<Action> result = new ArrayList<>();

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

    return result.toArray(new Action[0]);
  }

  @Override
  public void dispose() {
    myDisposed = true;
    Disposer.dispose(getBrowser());
    Disposer.dispose(myCommitMessageArea);
    Disposer.dispose(okButtonUpdateAlarm);
    super.dispose();
    Disposer.dispose(myDiffDetails);
    PropertiesComponent.getInstance().setValue(SPLITTER_PROPORTION_OPTION, mySplitter.getProportion(), SPLITTER_PROPORTION_OPTION_DEFAULT);
    float usedProportion = myDetailsSplitter.getUsedProportion();
    if (usedProportion > 0) {
      PropertiesComponent.getInstance().setValue(DETAILS_SPLITTER_PROPORTION_OPTION, usedProportion,
                                                 DETAILS_SPLITTER_PROPORTION_OPTION_DEFAULT);
    }
    PropertiesComponent.getInstance().setValue(DETAILS_SHOW_OPTION, myDetailsSplitter.isOn(), DETAILS_SHOW_OPTION_DEFAULT);
  }

  private void stopUpdate() {
    myUpdateDisabled = true;
  }

  private void restartUpdate() {
    myUpdateDisabled = false;
    myUpdateButtonsRunnable.run();
  }

  @Override
  public void doCancelAction() {
    myStateEventDispatcher.getMulticaster().cancelled();
    super.doCancelAction();
  }

  @Override
  protected @NotNull JComponent createCenterPanel() {
    return myDetailsSplitter.getComponent();
  }

  public boolean hasDiffs() {
    return !getIncludedChanges().isEmpty() || !getIncludedUnversionedFiles().isEmpty();
  }

  public @NotNull Project getProject() {
    return myProject;
  }

  @Deprecated(forRemoval = true)
  public @NotNull String getCommitMessage() {
    return myCommitMessageArea.getText();
  }

  // Used in plugins
  @SuppressWarnings("unused")
  public @NotNull List<RefreshableOnComponent> getAdditionalComponents() {
    return SequencesKt.toList(CommitOptionsKt.getAllOptions(getCommitOptions()));
  }

  private void updateButtons() {
    if (myDisposed || myUpdateDisabled) return;
    boolean enabled = hasDiffs();
    if (myCommitAction != null) {
      myCommitAction.setEnabled(enabled);
    }
    myExecutorActions.forEach(action -> action.updateEnabled(enabled));
    okButtonUpdateAlarm.cancelAllRequests();
    okButtonUpdateAlarm.addRequest(myUpdateButtonsRunnable, 300, ModalityState.stateForComponent(getBrowser()));
  }

  private void updateLegend() {
    if (myDisposed || myUpdateDisabled) return;
    myChangesInfoCalculator
      .update(getDisplayedChanges(), getIncludedChanges(), getDisplayedUnversionedFiles().size(), getIncludedUnversionedFiles().size());
    myLegend.update();
  }

  @Override
  protected @NonNls String getDimensionServiceKey() {
    return DIMENSION_SERVICE_KEY;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myCommitMessageArea.getEditorField();
  }

  @Override
  public @NotNull JComponent getComponent() {
    return mySplitter;
  }

  @Override
  public JComponent getPreferredFocusableComponent() {
    return getPreferredFocusedComponent();
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    DataSink.uiDataSnapshot(sink, getBrowser());
    for (DataProvider provider : myDataProviders) {
      DataSink.uiDataSnapshot(sink, provider);
    }
  }

  @Override
  public @NotNull CommitMessageUi getCommitMessageUi() {
    return myCommitMessageArea;
  }

  @Override
  public @NotNull CommitOptionsUi getCommitOptionsUi() {
    return myCommitOptions;
  }

  @Override
  public @NotNull @NlsContexts.Button String getDefaultCommitActionName() {
    return myDefaultCommitActionName;
  }

  @Override
  public void setDefaultCommitActionName(@NotNull @NlsContexts.Button String defaultCommitActionName) {
    myDefaultCommitActionName = defaultCommitActionName;
  }

  @Override
  public void addDataProvider(@NotNull DataProvider provider) {
    myDataProviders.add(provider);
  }

  @Override
  public void addExecutorListener(@NotNull CommitExecutorListener listener, @NotNull Disposable parent) {
    myExecutorEventDispatcher.addListener(listener, parent);
  }

  @Override
  public void refreshDataBeforeCommit() {
    getBrowser().updateDisplayedChangeLists();
  }

  @Override
  public @NotNull LocalChangeList getChangeList() {
    return getBrowser().getSelectedChangeList();
  }

  @Override
  public @NotNull List<Change> getDisplayedChanges() {
    return getBrowser().getDisplayedChanges();
  }

  @Override
  public @NotNull List<Change> getIncludedChanges() {
    return getBrowser().getIncludedChanges();
  }

  @Override
  public @NotNull List<FilePath> getDisplayedUnversionedFiles() {
    return getBrowser().getDisplayedUnversionedFiles();
  }

  @Override
  public @NotNull List<FilePath> getIncludedUnversionedFiles() {
    return getBrowser().getIncludedUnversionedFiles();
  }

  @Override
  public @NotNull InclusionModel getInclusionModel() {
    return getBrowser().getViewer().getInclusionModel();
  }

  @Override
  public void addInclusionListener(@NotNull InclusionListener listener, @NotNull Disposable parent) {
    myInclusionEventDispatcher.addListener(listener, parent);
  }

  @Override
  public boolean confirmCommitWithEmptyMessage() {
    Messages.showErrorDialog(myProject,
                             message("error.text.check.in.with.empty.comment"),
                             message("error.title.check.in.with.empty.comment"));
    return false;
  }

  @Override
  public void startBeforeCommitChecks() {
    stopUpdate();
  }

  @Override
  public void endBeforeCommitChecks(@NotNull CommitChecksResult result) {
    if (result.getShouldCommit()) return;
    if (result.getShouldCloseWindow()) {
      doCancelAction();
    }
    else {
      restartUpdate();
    }
  }

  private @NotNull CommitOptions getCommitOptions() {
    return myWorkflow.getCommitOptions();
  }

  public boolean isDefaultCommitEnabled() {
    return myWorkflow.isDefaultCommitEnabled();
  }

  @NotNull
  CommitMessage getCommitMessageComponent() {
    return myCommitMessageArea;
  }

  @NotNull
  JComponent getBrowserBottomPanel() {
    return myBrowserBottomPanel;
  }

  private class CommitExecutorAction extends AbstractAction {
    private final @Nullable CommitExecutor myCommitExecutor;

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
        myExecutorEventDispatcher.getMulticaster().executorCalled(myCommitExecutor);
      }
    }

    public void updateEnabled(boolean hasDiffs) {
      if (myCommitExecutor != null) {
        setEnabled(hasDiffs || !myCommitExecutor.areChangesRequired());
      }
    }
  }

  private void refreshDetails(boolean fromModelRefresh, boolean async) {
    Runnable task = () -> {
      if (myDetailsSplitter.isOn()) {
        myDiffDetails.refresh(fromModelRefresh);
      }
    };
    if (async) {
      ApplicationManager.getApplication().invokeLater(task, ModalityState.stateForComponent(getRootPane()));
    }
    else {
      task.run();
    }
  }

  private class MyChangeProcessor extends ChangeViewDiffRequestProcessor {
    MyChangeProcessor(@NotNull Project project, boolean enablePartialCommit) {
      super(project, DiffPlaces.COMMIT_DIALOG);

      putContextUserData(DiffUserDataKeysEx.SHOW_READ_ONLY_LOCK, true);
      putContextUserData(LocalChangeListDiffTool.ALLOW_EXCLUDE_FROM_COMMIT, enablePartialCommit);
      putContextUserData(DiffUserDataKeysEx.LAST_REVISION_WITH_LOCAL, true);
    }

    @Override
    public @NotNull Iterable<Wrapper> iterateSelectedChanges() {
      return wrap(getBrowser().getSelectedChanges(), getBrowser().getSelectedUnversionedFiles());
    }

    @Override
    public @NotNull Iterable<Wrapper> iterateAllChanges() {
      return wrap(getDisplayedChanges(), getDisplayedUnversionedFiles());
    }

    @Override
    protected void selectChange(@NotNull Wrapper change) {
      getBrowser().selectEntries(singletonList(change.getUserObject()));
    }

    private static @NotNull Iterable<Wrapper> wrap(@NotNull Collection<? extends Change> changes,
                                                   @NotNull Collection<? extends FilePath> unversioned) {
      return JBIterable.<Wrapper>empty()
        .append(JBIterable.from(changes).map(ChangeWrapper::new))
        .append(JBIterable.from(unversioned).map(UnversionedFileWrapper::new));
    }

    @Override
    protected @NotNull Runnable createAfterNavigateCallback() {
      return () -> doCancelAction();
    }

  }

  private static class MyOptionsLayout extends AbstractLayoutManager {
    private final @NotNull JComponent myPanel;
    private final @NotNull CommitOptionsPanel myOptions;
    private final @NotNull JComponent myOptionsPanel;
    private final int myMinOptionsWidth;
    private final int myMaxOptionsWidth;

    MyOptionsLayout(@NotNull JComponent panel, @NotNull CommitOptionsPanel options, int minOptionsWidth, int maxOptionsWidth) {
      myPanel = panel;
      myOptions = options;
      myOptionsPanel = options.component;
      myMinOptionsWidth = minOptionsWidth;
      myMaxOptionsWidth = maxOptionsWidth;
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      Dimension size1 = myPanel.getPreferredSize();
      Dimension size2 = myOptionsPanel.getPreferredSize();
      return new Dimension(size1.width + size2.width, max(size1.height, size2.height));
    }

    @Override
    public void layoutContainer(@NotNull Container parent) {
      Rectangle bounds = parent.getBounds();
      int preferredWidth = myOptionsPanel.getPreferredSize().width;
      int optionsWidth = myOptions.isEmpty() ? 0 : clamp(preferredWidth, myMinOptionsWidth, myMaxOptionsWidth);
      myPanel.setBounds(new Rectangle(0, 0, bounds.width - optionsWidth, bounds.height));
      myOptionsPanel.setBounds(new Rectangle(bounds.width - optionsWidth, 0, optionsWidth, bounds.height));
    }
  }
}

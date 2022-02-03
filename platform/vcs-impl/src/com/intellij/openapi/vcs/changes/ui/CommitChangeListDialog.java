// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.diff.util.DiffPlaces;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.ide.HelpIdProvider;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalChangeListDiffTool;
import com.intellij.openapi.vcs.checkin.BaseCheckinHandlerFactory;
import com.intellij.openapi.vcs.checkin.BeforeCheckinDialogHandler;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.ui.JBColor;
import com.intellij.ui.SplitterWithSecondHideable;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Alarm;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
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
import java.util.List;
import java.util.*;
import java.util.stream.Stream;

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
import static com.intellij.vcs.commit.SingleChangeListCommitWorkflowKt.getPresentableText;
import static java.lang.Math.max;
import static java.util.Arrays.asList;
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

  @NotNull private final Project myProject;
  @NotNull private final SingleChangeListCommitWorkflow myWorkflow;
  @NotNull private final EventDispatcher<CommitWorkflowUiStateListener> myStateEventDispatcher =
    EventDispatcher.create(CommitWorkflowUiStateListener.class);
  @NotNull private final EventDispatcher<CommitExecutorListener> myExecutorEventDispatcher =
    EventDispatcher.create(CommitExecutorListener.class);
  @NotNull private final List<DataProvider> myDataProviders = new ArrayList<>();
  @NotNull private final EventDispatcher<InclusionListener> myInclusionEventDispatcher = EventDispatcher.create(InclusionListener.class);

  @NotNull @NlsContexts.Button private String myDefaultCommitActionName = "";
  @Nullable private CommitAction myCommitAction;
  @NotNull private final List<CommitExecutorAction> myExecutorActions = new ArrayList<>();

  @NotNull private final CommitOptionsPanel myCommitOptions;
  @NotNull private final ChangeInfoCalculator myChangesInfoCalculator;
  @NotNull private final JComponent myBrowserBottomPanel = createHorizontalBox();
  @NotNull private final MyChangeProcessor myDiffDetails;
  @NotNull private final CommitMessage myCommitMessageArea;
  @NotNull private final CommitLegendPanel myLegend;

  @NotNull private final Splitter mySplitter;
  @NotNull private final SplitterWithSecondHideable myDetailsSplitter;
  @NotNull private final JBLabel myWarningLabel;

  @Nullable private final String myHelpId;

  @NotNull private final Alarm myOKButtonUpdateAlarm = new Alarm();
  @NotNull private final Runnable myUpdateButtonsRunnable = () -> {
    updateButtons();
    updateLegend();
  };

  private boolean myDisposed = false;
  private boolean myUpdateDisabled = false;

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
      return commitChanges(project, new ArrayList<>(changes), included, initialSelection, getCommitExecutors(project, changes), true, null,
                           comment, null, true);
    }
    else {
      return commitChanges(project, new ArrayList<>(changes), included, initialSelection, singletonList(executor), false, null, comment, null,
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
    return commitChanges(project, new ArrayList<>(changes), initialSelection, executors, showVcsCommit, comment, customResultHandler, true);
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
    List<LocalChangeList> changeLists = manager.getChangeLists();

    Set<AbstractVcs> affectedVcses = new HashSet<>();
    if (forceCommitInVcs != null) affectedVcses.add(forceCommitInVcs);
    for (LocalChangeList list : changeLists) {
      affectedVcses.addAll(ChangesUtil.getAffectedVcses(list.getChanges(), project));
    }
    if (showVcsCommit) {
      List<FilePath> unversionedFiles = ChangeListManager.getInstance(project).getUnversionedFilesPaths();
      affectedVcses.addAll(ChangesUtil.getAffectedVcsesForFilePaths(unversionedFiles, project));
    }


    if (cancelIfNoChanges && affectedVcses.isEmpty()) {
      Messages.showInfoMessage(project, message("commit.dialog.no.changes.detected.text"),
                               message("commit.dialog.no.changes.detected.title"));
      return false;
    }

    AbstractVcs[] vcses = ProjectLevelVcsManager.getInstance(project).getAllActiveVcss();
    for (BaseCheckinHandlerFactory factory : getCommitHandlerFactories(asList(vcses))) {
      BeforeCheckinDialogHandler handler = factory.createSystemReadyHandler(project);
      if (handler != null && !handler.beforeCommitDialogShown(project, changes, (Iterable<CommitExecutor>)executors, showVcsCommit)) {
        return false;
      }
    }

    boolean isDefaultChangeListFullyIncluded = new HashSet<>(changes).containsAll(defaultList.getChanges());
    SingleChangeListCommitWorkflow workflow =
      new SingleChangeListCommitWorkflow(project, affectedVcses, included, initialSelection, executors, showVcsCommit,
                                         isDefaultChangeListFullyIncluded, comment, customResultHandler);
    CommitChangeListDialog dialog = new DefaultCommitChangeListDialog(workflow);

    return new SingleChangeListCommitWorkflowHandler(workflow, dialog).activate();
  }

  protected CommitChangeListDialog(@NotNull SingleChangeListCommitWorkflow workflow) {
    super(workflow.getProject(), true, (Registry.is("ide.perProjectModality")) ? IdeModalityType.PROJECT : IdeModalityType.IDE);
    myWorkflow = workflow;
    myProject = myWorkflow.getProject();
    Disposer.register(getDisposable(), this);

    List<? extends CommitExecutor> executors = myWorkflow.getCommitExecutors();
    if (!isDefaultCommitEnabled() && ContainerUtil.isEmpty(executors)) {
      throw new IllegalArgumentException("nothing found to execute commit with");
    }

    setTitle(isDefaultCommitEnabled() ? DIALOG_TITLE : getPresentableText(executors.get(0)));
    myHelpId = isDefaultCommitEnabled() ? HELP_ID : getHelpId(executors);

    myDiffDetails = new MyChangeProcessor(myProject, myWorkflow.isPartialCommitEnabled());
    myCommitMessageArea = new CommitMessage(myProject, true, true, isDefaultCommitEnabled());
    myChangesInfoCalculator = new ChangeInfoCalculator();
    myLegend = new CommitLegendPanel(myChangesInfoCalculator);
    mySplitter = new Splitter(true);
    myCommitOptions = new CommitOptionsPanel(() -> getDefaultCommitActionName());
    myWarningLabel = new JBLabel();

    JPanel mainPanel = new JPanel(new MyOptionsLayout(mySplitter, myCommitOptions, JBUIScale.scale(150), JBUIScale.scale(400)));
    mainPanel.add(mySplitter);
    mainPanel.add(myCommitOptions);

    JPanel rootPane = JBUI.Panels.simplePanel(mainPanel).addToBottom(myWarningLabel);
    myDetailsSplitter = createDetailsSplitter(rootPane);
  }

  @NotNull
  public abstract CommitDialogChangesBrowser getBrowser();

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
    getBrowser().setInclusionChangedListener(() -> myInclusionEventDispatcher.getMulticaster().inclusionChanged());

    addInclusionListener(() -> updateButtons(), this);
    getBrowser().getViewer().addSelectionListener(() -> {
      refreshDetails(getBrowser().getViewer().isModelUpdateInProgress(), true);
    });

    initCommitActions(myWorkflow.getCommitExecutors());

    myCommitOptions.setBorder(emptyLeft(10));

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

  private void afterInit() {
    updateButtons();
    updateLegend();
    myCommitMessageArea.setChangesSupplier(new ChangeListChangesSupplier(getChangeList()));
    myCommitMessageArea.requestFocusInMessage();

    for (EditChangelistSupport support : EditChangelistSupport.EP_NAME.getExtensions(myProject)) {
      support.installSearch(myCommitMessageArea.getEditorField(), myCommitMessageArea.getEditorField());
    }

    showDetailsIfSaved();

    LaterInvocator.markTransparent(ModalityState.stateForComponent(getComponent()));
  }

  @NotNull
  private SplitterWithSecondHideable createDetailsSplitter(@NotNull JPanel rootPane) {
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
        float value = PropertiesComponent.getInstance().getFloat(DETAILS_SPLITTER_PROPORTION_OPTION, DETAILS_SPLITTER_PROPORTION_OPTION_DEFAULT);
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

  @NotNull
  private List<CommitExecutorAction> createExecutorActions(@NotNull List<? extends CommitExecutor> executors) {
    if(executors.isEmpty()) return emptyList();
    List<CommitExecutorAction> result = new ArrayList<>();

    if (isDefaultCommitEnabled() && UISettings.getShadowInstance().getAllowMergeButtons()) {
      ActionGroup primaryActions = (ActionGroup)ActionManager.getInstance().getAction("Vcs.Commit.PrimaryCommitActions");
      ActionGroup executorActions = (ActionGroup)ActionManager.getInstance().getAction("Vcs.CommitExecutor.Actions");

      result.addAll(map(primaryActions.getChildren(null), CommitExecutorAction::new));
      result.addAll(map(executorActions.getChildren(null), CommitExecutorAction::new));
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

  @Nullable
  @Override
  protected String getHelpId() {
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

  @NotNull
  @Override
  protected Action getOKAction() {
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

  @NotNull
  @Override
  protected JComponent createCenterPanel() {
    return myDetailsSplitter.getComponent();
  }

  public boolean hasDiffs() {
    return !getIncludedChanges().isEmpty() || !getIncludedUnversionedFiles().isEmpty();
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @SuppressWarnings("unused")
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public void setCommitMessage(@Nullable String commitMessage) {}

  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  @NotNull
  public String getCommitMessage() {
    return myCommitMessageArea.getText();
  }

  // Used in plugins
  @SuppressWarnings("unused")
  @NotNull
  public List<RefreshableOnComponent> getAdditionalComponents() {
    return SequencesKt.toList(CommitOptionsKt.getAllOptions(getCommitOptions()));
  }

  private void updateButtons() {
    if (myDisposed || myUpdateDisabled) return;
    boolean enabled = hasDiffs();
    if (myCommitAction != null) {
      myCommitAction.setEnabled(enabled);
    }
    myExecutorActions.forEach(action -> action.updateEnabled(enabled));
    myOKButtonUpdateAlarm.cancelAllRequests();
    myOKButtonUpdateAlarm.addRequest(myUpdateButtonsRunnable, 300, ModalityState.stateForComponent(getBrowser()));
  }

  private void updateLegend() {
    if (myDisposed || myUpdateDisabled) return;
    myChangesInfoCalculator
      .update(getDisplayedChanges(), getIncludedChanges(), getDisplayedUnversionedFiles().size(), getIncludedUnversionedFiles().size());
    myLegend.update();
  }

  @Override
  @NonNls
  protected String getDimensionServiceKey() {
    return DIMENSION_SERVICE_KEY;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myCommitMessageArea.getEditorField();
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return mySplitter;
  }

  @Override
  public JComponent getPreferredFocusableComponent() {
    return getPreferredFocusedComponent();
  }

  @Nullable
  @Override
  public Object getData(@NotNull String dataId) {
    return StreamEx.of(myDataProviders)
      .map(provider -> provider.getData(dataId))
      .nonNull()
      .findFirst()
      .orElseGet(() -> getBrowser().getData(dataId));
  }

  @NotNull
  @Override
  public CommitMessageUi getCommitMessageUi() {
    return myCommitMessageArea;
  }

  @NotNull
  @Override
  public CommitOptionsUi getCommitOptionsUi() {
    return myCommitOptions;
  }

  @NotNull
  @Override
  @NlsContexts.Button
  public String getDefaultCommitActionName() {
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
  public void refreshData() {
    getBrowser().updateDisplayedChangeLists();
  }

  @NotNull
  @Override
  public LocalChangeList getChangeList() {
    return getBrowser().getSelectedChangeList();
  }

  @NotNull
  @Override
  public List<Change> getDisplayedChanges() {
    return getBrowser().getDisplayedChanges();
  }

  @NotNull
  @Override
  public List<Change> getIncludedChanges() {
    return getBrowser().getIncludedChanges();
  }

  @NotNull
  @Override
  public List<FilePath> getDisplayedUnversionedFiles() {
    return getBrowser().getDisplayedUnversionedFiles();
  }

  @NotNull
  @Override
  public List<FilePath> getIncludedUnversionedFiles() {
    return getBrowser().getIncludedUnversionedFiles();
  }

  @Override
  public void includeIntoCommit(@NotNull Collection<?> items) {
    getBrowser().getViewer().includeChanges(items);
  }

  @Override
  public void addInclusionListener(@NotNull InclusionListener listener, @NotNull Disposable parent) {
    myInclusionEventDispatcher.addListener(listener, parent);
  }

  @Override
  public boolean confirmCommitWithEmptyMessage() {
    return showEmptyCommitMessageConfirmation(myProject);
  }

  public static boolean showEmptyCommitMessageConfirmation(@NotNull Project project) {
    return MessageDialogBuilder
      .yesNo(message("confirmation.title.check.in.with.empty.comment"), message("confirmation.text.check.in.with.empty.comment"))
      .icon(Messages.getWarningIcon())
      .ask(project);
  }

  @Override
  public void startBeforeCommitChecks() {
    stopUpdate();
  }

  @Override
  public void endBeforeCommitChecks(@NotNull CheckinHandler.ReturnResult result) {
    if (result == CheckinHandler.ReturnResult.CANCEL) {
      restartUpdate();
    }
    else if (result == CheckinHandler.ReturnResult.CLOSE_WINDOW) {
      doCancelAction();
    }
  }

  @NotNull
  private CommitOptions getCommitOptions() {
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

    @NotNull
    @Override
    public Stream<Wrapper> getSelectedChanges() {
      return wrap(getBrowser().getSelectedChanges(), getBrowser().getSelectedUnversionedFiles());
    }

    @NotNull
    @Override
    public Stream<Wrapper> getAllChanges() {
      return wrap(getDisplayedChanges(), getDisplayedUnversionedFiles());
    }

    @Override
    protected void selectChange(@NotNull Wrapper change) {
      getBrowser().selectEntries(singletonList(change.getUserObject()));
    }

    @NotNull
    private Stream<Wrapper> wrap(@NotNull Collection<? extends Change> changes, @NotNull Collection<? extends FilePath> unversioned) {
      return Stream.concat(
        changes.stream().map(ChangeWrapper::new),
        unversioned.stream().map(UnversionedFileWrapper::new)
      );
    }

    @Override
    protected void onAfterNavigate() {
      doCancelAction();
    }
  }

  private static class MyOptionsLayout extends AbstractLayoutManager {
    @NotNull private final JComponent myPanel;
    @NotNull private final CommitOptionsPanel myOptions;
    private final int myMinOptionsWidth;
    private final int myMaxOptionsWidth;

    MyOptionsLayout(@NotNull JComponent panel, @NotNull CommitOptionsPanel options, int minOptionsWidth, int maxOptionsWidth) {
      myPanel = panel;
      myOptions = options;
      myMinOptionsWidth = minOptionsWidth;
      myMaxOptionsWidth = maxOptionsWidth;
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      Dimension size1 = myPanel.getPreferredSize();
      Dimension size2 = myOptions.getPreferredSize();
      return new Dimension(size1.width + size2.width, max(size1.height, size2.height));
    }

    @Override
    public void layoutContainer(@NotNull Container parent) {
      Rectangle bounds = parent.getBounds();
      int preferredWidth = myOptions.getPreferredSize().width;
      int optionsWidth = myOptions.isEmpty() ? 0 : clamp(preferredWidth, myMinOptionsWidth, myMaxOptionsWidth);
      myPanel.setBounds(new Rectangle(0, 0, bounds.width - optionsWidth, bounds.height));
      myOptions.setBounds(new Rectangle(bounds.width - optionsWidth, 0, optionsWidth, bounds.height));
    }
  }
}

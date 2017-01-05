/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.diff.util.DiffPlaces;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.actions.ScheduleForAdditionAction;
import com.intellij.openapi.vcs.checkin.*;
import com.intellij.openapi.vcs.impl.CheckinHandlersManager;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SplitterWithSecondHideable;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.*;
import java.util.List;

import static com.intellij.util.containers.ContainerUtil.createMaybeSingletonList;

public class CommitChangeListDialog extends DialogWrapper implements CheckinProjectPanel, TypeSafeDataProvider {
  private static final String HELP_ID = "reference.dialogs.vcs.commit";
  private static final String TITLE = VcsBundle.message("commit.dialog.title");

  private static final int LAYOUT_VERSION = 2;
  private static final String SPLITTER_PROPORTION_OPTION = "CommitChangeListDialog.SPLITTER_PROPORTION_" + LAYOUT_VERSION;
  private static final String DETAILS_SPLITTER_PROPORTION_OPTION = "CommitChangeListDialog.DETAILS_SPLITTER_PROPORTION_" + LAYOUT_VERSION;
  private static final String DETAILS_SHOW_OPTION = "CommitChangeListDialog.DETAILS_SHOW_OPTION_";

  private static final float SPLITTER_PROPORTION_OPTION_DEFAULT = 0.5f;
  private static final float DETAILS_SPLITTER_PROPORTION_OPTION_DEFAULT = 0.6f;
  private static final boolean DETAILS_SHOW_OPTION_DEFAULT = true;

  private static final Comparator<AbstractVcs> VCS_COMPARATOR = Comparator.comparing(it -> it.getKeyInstanceMethod().getName(),
                                                                                     String::compareToIgnoreCase);

  @NotNull private final Project myProject;
  @NotNull private final VcsConfiguration myVcsConfiguration;
  @NotNull private final List<CommitExecutor> myExecutors;
  private final boolean myShowVcsCommit;
  @Nullable private final AbstractVcs mySingleVcs;
  private final boolean myIsAlien;
  @Nullable private final CommitResultHandler myResultHandler;

  @NotNull private final List<CheckinHandler> myHandlers = ContainerUtil.newArrayList();
  @NotNull private final Map<AbstractVcs, JPanel> myPerVcsOptionsPanels = ContainerUtil.newHashMap();
  @NotNull private final List<RefreshableOnComponent> myAdditionalComponents = ContainerUtil.newArrayList();
  @NotNull private final Map<String, CheckinChangeListSpecificComponent> myCheckinChangeListSpecificComponents = ContainerUtil.newHashMap();
  private final boolean myAllOfDefaultChangeListChangesIncluded;

  @NotNull private final Map<String, String> myListComments;
  @NotNull private final PseudoMap<Object, Object> myAdditionalData;
  @NotNull private final List<CommitExecutorAction> myExecutorActions;

  @NotNull private final CommitContext myCommitContext;
  @NotNull private final ChangeInfoCalculator myChangesInfoCalculator;
  @NotNull private final ChangesBrowserBase<?> myBrowser;
  @NotNull private final MyChangeProcessor myDiffDetails;
  @NotNull private final CommitMessage myCommitMessageArea;
  @NotNull private final CommitLegendPanel myLegend;

  @NotNull private final Splitter mySplitter;
  @NotNull private final SplitterWithSecondHideable myDetailsSplitter;
  @NotNull private final JLabel myWarningLabel;

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


  public static void commitPaths(@NotNull Project project,
                                 @NotNull Collection<FilePath> paths,
                                 @Nullable LocalChangeList initialSelection,
                                 @Nullable CommitExecutor executor,
                                 @Nullable String comment) {
    final ChangeListManager manager = ChangeListManager.getInstance(project);
    final Collection<Change> changes = new HashSet<>();
    for (FilePath path : paths) {
      changes.addAll(manager.getChangesIn(path));
    }

    commitChanges(project, changes, initialSelection, executor, comment);
  }

  public static boolean commitChanges(@NotNull Project project,
                                      @NotNull Collection<Change> changes,
                                      @Nullable LocalChangeList initialSelection,
                                      @Nullable final CommitExecutor executor,
                                      @Nullable String comment) {
    if (executor == null) {
      return commitChanges(project, changes, initialSelection, collectExecutors(project, changes), true, comment, null);
    }
    else {
      return commitChanges(project, changes, initialSelection, Collections.singletonList(executor), false, comment, null);
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
                                      @NotNull Collection<Change> changes,
                                      @Nullable LocalChangeList initialSelection,
                                      @NotNull List<CommitExecutor> executors,
                                      boolean showVcsCommit,
                                      @Nullable String comment,
                                      @Nullable CommitResultHandler customResultHandler) {
    return commitChanges(project, new ArrayList<>(changes), initialSelection, executors, showVcsCommit, comment, customResultHandler, true);
  }

  public static boolean commitChanges(@NotNull Project project,
                                      @NotNull List<Change> changes,
                                      @Nullable LocalChangeList initialSelection,
                                      @NotNull List<CommitExecutor> executors,
                                      boolean showVcsCommit,
                                      @Nullable String comment,
                                      @Nullable CommitResultHandler customResultHandler,
                                      boolean cancelIfNoChanges) {
    return commitChanges(project, changes, initialSelection, executors, showVcsCommit, null, comment, customResultHandler,
                         cancelIfNoChanges);
  }

  public static boolean commitChanges(@NotNull Project project,
                                      @NotNull List<Change> changes,
                                      @Nullable LocalChangeList initialSelection,
                                      @NotNull List<CommitExecutor> executors,
                                      boolean showVcsCommit,
                                      @Nullable final AbstractVcs singleVcs,
                                      @Nullable String comment,
                                      @Nullable CommitResultHandler customResultHandler,
                                      boolean cancelIfNoChanges) {
    if (cancelIfNoChanges && changes.isEmpty() && !ApplicationManager.getApplication().isUnitTestMode()) {
      Messages.showInfoMessage(project, VcsBundle.message("commit.dialog.no.changes.detected.text"),
                               VcsBundle.message("commit.dialog.no.changes.detected.title"));
      return false;
    }

    for (BaseCheckinHandlerFactory factory : getCheckInFactories(project)) {
      BeforeCheckinDialogHandler handler = factory.createSystemReadyHandler(project);
      if (handler != null && !handler.beforeCommitDialogShown(project, changes, executors, showVcsCommit)) {
        return false;
      }
    }

    final ChangeListManager manager = ChangeListManager.getInstance(project);
    CommitChangeListDialog dialog =
      new CommitChangeListDialog(project, changes, initialSelection, executors, showVcsCommit, manager.getDefaultChangeList(),
                                 manager.getChangeListsCopy(), singleVcs,
                                 false, comment, customResultHandler);
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      dialog.show();
    }
    else {
      Action okAction = dialog.getOKAction();
      if (okAction.isEnabled()) {
        okAction.actionPerformed(null);
      }
      else {
        dialog.doCancelAction();
      }
    }
    return dialog.isOK();
  }

  @NotNull
  private static List<BaseCheckinHandlerFactory> getCheckInFactories(@NotNull Project project) {
    return CheckinHandlersManager.getInstance().getRegisteredCheckinHandlerFactories(
      ProjectLevelVcsManager.getInstance(project).getAllActiveVcss());
  }

  @NotNull
  public static List<CommitExecutor> collectExecutors(@NotNull Project project, @NotNull Collection<Change> changes) {
    List<CommitExecutor> result = new ArrayList<>();
    for (AbstractVcs<?> vcs : ChangesUtil.getAffectedVcses(changes, project)) {
      result.addAll(vcs.getCommitExecutors());
    }
    result.addAll(ChangeListManager.getInstance(project).getRegisteredExecutors());
    return result;
  }

  public static void commitAlienChanges(@NotNull Project project,
                                        @NotNull List<Change> changes,
                                        @NotNull AbstractVcs vcs,
                                        @NotNull String changelistName,
                                        @Nullable String comment) {
    final LocalChangeList lcl = new AlienLocalChangeList(changes, changelistName);
    new CommitChangeListDialog(project, changes, null, Collections.emptyList(), true, AlienLocalChangeList.DEFAULT_ALIEN,
                               Collections.singletonList(lcl), vcs, true, comment, null).show();
  }

  private CommitChangeListDialog(@NotNull Project project,
                                 @NotNull List<Change> changes,
                                 @Nullable LocalChangeList initialSelection,
                                 @NotNull List<CommitExecutor> executors,
                                 boolean showVcsCommit,
                                 @NotNull LocalChangeList defaultChangeList,
                                 @NotNull List<LocalChangeList> changeLists,
                                 @Nullable final AbstractVcs singleVcs,
                                 boolean isAlien,
                                 @Nullable String comment,
                                 @Nullable CommitResultHandler customResultHandler) {
    super(project, true, (Registry.is("ide.perProjectModality")) ? IdeModalityType.PROJECT : IdeModalityType.IDE);
    myCommitContext = new CommitContext();
    myProject = project;
    myVcsConfiguration = ObjectUtils.assertNotNull(VcsConfiguration.getInstance(myProject));
    myExecutors = executors;
    myShowVcsCommit = showVcsCommit;
    mySingleVcs = singleVcs;
    myResultHandler = customResultHandler;
    myListComments = new HashMap<>();
    myAdditionalData = new PseudoMap<>();
    myDiffDetails = new MyChangeProcessor(myProject);

    if (!myShowVcsCommit && ContainerUtil.isEmpty(myExecutors)) {
      throw new IllegalArgumentException("nothing found to execute commit with");
    }

    myAllOfDefaultChangeListChangesIncluded =
      ContainerUtil.newHashSet(changes).containsAll(ContainerUtil.newHashSet(defaultChangeList.getChanges()));

    myCommitMessageArea = new CommitMessage(project);

    myIsAlien = isAlien;
    if (isAlien) {
      myCommitMessageArea.setChangeLists(ContainerUtil.newArrayList(changeLists));
      myBrowser = new AlienChangeListBrowser(project, changeLists, changes, initialSelection, true, true, singleVcs);
    } else {
      myCommitMessageArea.setChangeLists(createMaybeSingletonList(initialSelection));
      boolean unversionedFilesEnabled = myShowVcsCommit && Registry.is("vcs.unversioned.files.in.commit");
      //noinspection unchecked
      MultipleChangeListBrowser browser = new MultipleChangeListBrowser(project, changeLists, (List)changes, initialSelection, true, true,
                                                                        new Runnable() {
                                                                          @Override
                                                                          public void run() {
                                                                            updateWarning();
                                                                          }
                                                                        },
                                                                        new Runnable() {
                                                                          @Override
                                                                          public void run() {
                                                                            for (CheckinHandler handler : myHandlers) {
                                                                              handler.includedChangesChanged();
                                                                            }
                                                                          }
                                                                        }, unversionedFilesEnabled);
      browser.addSelectedListChangeListener(new SelectedListChangeListener() {
        @Override
        public void selectedListChanged() {
          myCommitMessageArea.setChangeLists(createMaybeSingletonList(browser.getSelectedChangeList()));
          updateOnListSelection();
        }
      });
      myBrowser = browser;
      myBrowser.setAlwayExpandList(false);
    }
    myBrowser.getViewer().addSelectionListener(new Runnable() {
      @Override
      public void run() {
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            changeDetails();
          }
        });
      }
    });
    myBrowser.setDiffBottomComponent(new DiffCommitMessageEditor(myProject, myCommitMessageArea));

    mySplitter = new Splitter(true);
    mySplitter.setHonorComponentsMinimumSize(true);
    mySplitter.setFirstComponent(myBrowser);
    mySplitter.setSecondComponent(myCommitMessageArea);
    mySplitter.setProportion(PropertiesComponent.getInstance().getFloat(SPLITTER_PROPORTION_OPTION, SPLITTER_PROPORTION_OPTION_DEFAULT));

    myChangesInfoCalculator = new ChangeInfoCalculator();
    myLegend = new CommitLegendPanel(myChangesInfoCalculator);
    myBrowser.getBottomPanel().add(JBUI.Panels.simplePanel().addToRight(myLegend.getComponent()), BorderLayout.SOUTH);

    if (!myVcsConfiguration.CLEAR_INITIAL_COMMIT_MESSAGE) {
      setComment(initialSelection, comment);
    }

    final String actionName = getCommitActionName();
    final String borderTitleName = actionName.replace("_", "").replace("&", "");

    JPanel optionsPanel = createOptionsPanel(project, borderTitleName);
    restoreState();

    if (myShowVcsCommit) {
      setTitle(TITLE);
    }
    else {
      setTitle(trimEllipsis(myExecutors.get(0).getActionText()));
    }

    if (myShowVcsCommit) {
      myCommitAction = new CommitAction(actionName);
    }
    else {
      myCommitAction = null;
    }

    myExecutorActions = ContainerUtil.map(myExecutors, CommitExecutorAction::new);
    if (myCommitAction != null) {
      myCommitAction.setOptions(myExecutorActions);
      myHelpId = HELP_ID;
    }
    else {
      myExecutorActions.get(0).putValue(DEFAULT_ACTION, Boolean.TRUE);
      myHelpId = getHelpId(myExecutors);
    }

    myWarningLabel = new JLabel();
    myWarningLabel.setUI(new MultiLineLabelUI());
    myWarningLabel.setForeground(JBColor.RED);
    myWarningLabel.setBorder(JBUI.Borders.empty(5, 5, 0, 5));
    updateWarning();

    JPanel mainPanel;
    if (optionsPanel != null) {
      mainPanel = new JPanel(new MyOptionsLayout(mySplitter, optionsPanel, JBUI.scale(150), JBUI.scale(400)));
      mainPanel.add(mySplitter);
      mainPanel.add(optionsPanel);
    }
    else {
      mainPanel = mySplitter;
    }

    final JPanel panel = new JPanel(new GridBagLayout());
    panel.add(myWarningLabel, new GridBag().anchor(GridBagConstraints.NORTHWEST).weightx(1));

    JPanel rootPane = JBUI.Panels.simplePanel(mainPanel).addToBottom(panel);

    // TODO: there are no reason to use such heavy interface for a simple task.
    myDetailsSplitter = new SplitterWithSecondHideable(true, "Diff", rootPane,
                                                       new SplitterWithSecondHideable.OnOffListener<Integer>() {
                                                         @Override
                                                         public void on(Integer integer) {
                                                           if (integer == 0) return;
                                                           myDiffDetails.refresh();
                                                           mySplitter.skipNextLayouting();
                                                           myDetailsSplitter.getComponent().skipNextLayouting();
                                                           final Dimension dialogSize = getSize();
                                                           setSize(dialogSize.width, dialogSize.height + integer);
                                                           repaint();
                                                         }

                                                         @Override
                                                         public void off(Integer integer) {
                                                           if (integer == 0) return;
                                                           myDiffDetails.clear(); // TODO: we may want to keep it in memory
                                                           mySplitter.skipNextLayouting();
                                                           myDetailsSplitter.getComponent().skipNextLayouting();
                                                           final Dimension dialogSize = getSize();
                                                           setSize(dialogSize.width, dialogSize.height - integer);
                                                           repaint();
                                                         }
                                                       }) {
      @Override
      protected RefreshablePanel createDetails() {
        final JPanel panel = JBUI.Panels.simplePanel(myDiffDetails.getComponent());
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

    init();
    updateButtons();
    updateVcsOptionsVisibility();

    updateOnListSelection();
    myCommitMessageArea.requestFocusInMessage();

    for (EditChangelistSupport support : Extensions.getExtensions(EditChangelistSupport.EP_NAME, project)) {
      support.installSearch(myCommitMessageArea.getEditorField(), myCommitMessageArea.getEditorField());
    }

    showDetailsIfSaved();
  }

  @Nullable
  private JPanel createOptionsPanel(@NotNull Project project, @NotNull String borderTitleName) {
    boolean hasVcsOptions = false;
    Box vcsCommitOptions = Box.createVerticalBox();
    for (AbstractVcs vcs : ContainerUtil.sorted(getAffectedVcses(), VCS_COMPARATOR)) {
      final CheckinEnvironment checkinEnvironment = vcs.getCheckinEnvironment();
      if (checkinEnvironment != null) {
        final RefreshableOnComponent options = checkinEnvironment.createAdditionalOptionsPanel(this, myAdditionalData);
        if (options != null) {
          JPanel vcsOptions = new JPanel(new BorderLayout());
          vcsOptions.add(options.getComponent(), BorderLayout.CENTER);
          vcsOptions.setBorder(IdeBorderFactory.createTitledBorder(vcs.getDisplayName(), true));
          vcsCommitOptions.add(vcsOptions);
          myPerVcsOptionsPanels.put(vcs, vcsOptions);
          myAdditionalComponents.add(options);
          if (options instanceof CheckinChangeListSpecificComponent) {
            myCheckinChangeListSpecificComponents.put(vcs.getName(), (CheckinChangeListSpecificComponent)options);
          }
          hasVcsOptions = true;
        }
      }
    }

    boolean beforeVisible = false;
    boolean afterVisible = false;
    Box beforeBox = Box.createVerticalBox();
    Box afterBox = Box.createVerticalBox();
    for (BaseCheckinHandlerFactory factory : getCheckInFactories(project)) {
      final CheckinHandler handler = factory.createHandler(this, myCommitContext);
      if (CheckinHandler.DUMMY.equals(handler)) continue;

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

    if (!hasVcsOptions && !beforeVisible && !afterVisible) return null;

    Box optionsBox = Box.createVerticalBox();
    if (hasVcsOptions) {
      vcsCommitOptions.add(Box.createVerticalGlue());
      optionsBox.add(vcsCommitOptions);
    }

    if (beforeVisible) {
      beforeBox.add(Box.createVerticalGlue());
      JPanel beforePanel = new JPanel(new BorderLayout());
      beforePanel.add(beforeBox);
      beforePanel.setBorder(IdeBorderFactory.createTitledBorder(
        VcsBundle.message("border.standard.checkin.options.group", borderTitleName), true));
      optionsBox.add(beforePanel);
    }

    if (afterVisible) {
      afterBox.add(Box.createVerticalGlue());
      JPanel afterPanel = new JPanel(new BorderLayout());
      afterPanel.add(afterBox);
      afterPanel.setBorder(IdeBorderFactory.createTitledBorder(
        VcsBundle.message("border.standard.after.checkin.options.group", borderTitleName), true));
      optionsBox.add(afterPanel);
    }

    optionsBox.add(Box.createVerticalGlue());
    JPanel additionalOptionsPanel = new JPanel(new BorderLayout());
    additionalOptionsPanel.add(optionsBox, BorderLayout.NORTH);

    JScrollPane optionsPane = ScrollPaneFactory.createScrollPane(additionalOptionsPanel, true);
    return JBUI.Panels.simplePanel(optionsPane).withBorder(JBUI.Borders.emptyLeft(10));
  }

  @Nullable
  private static String getHelpId(@NotNull List<CommitExecutor> executors) {
    for (CommitExecutor executor : executors) {
      if (executor instanceof CommitExecutorWithHelp) {
        String helpId = ((CommitExecutorWithHelp)executor).getHelpId();
        if (helpId != null) return helpId;
      }
    }
    return null;
  }

  private void setComment(@Nullable LocalChangeList initialSelection, @Nullable String comment) {
    if (comment != null) {
      setCommitMessage(comment);
      myLastKnownComment = comment;
      myLastSelectedListName = initialSelection == null ? myBrowser.getSelectedChangeList().getName() : initialSelection.getName();
    } else {
      updateComment();

      if (StringUtil.isEmptyOrSpaces(myCommitMessageArea.getComment())) {
        setCommitMessage(myVcsConfiguration.LAST_COMMIT_MESSAGE);
        final String messageFromVcs = getInitialMessageFromVcs();
        if (messageFromVcs != null) {
          myCommitMessageArea.setText(messageFromVcs);
        }
      }
    }
  }

  private void showDetailsIfSaved() {
    boolean showDetails = PropertiesComponent.getInstance().getBoolean(DETAILS_SHOW_OPTION, DETAILS_SHOW_OPTION_DEFAULT);
    if (showDetails) {
      myDetailsSplitter.initOn();
    }
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        changeDetails();
      }
    });
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
    //noinspection ConstantConditions
    if (myWarningLabel != null) {
      myWarningLabel.setVisible(false);
      @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
      final VcsException updateException = ((ChangeListManagerImpl)ChangeListManager.getInstance(myProject)).getUpdateException();
      if (updateException != null) {
        final String[] messages = updateException.getMessages();
        if (messages != null && messages.length > 0) {
          final String message = messages[0];
          myWarningLabel.setText("Warning: not all local changes may be shown due to an error: " + message);
          myWarningLabel.setVisible(true);
        }
      }
    }
  }

  private void updateVcsOptionsVisibility() {
    Collection<AbstractVcs> affectedVcses = ChangesUtil.getAffectedVcses(myBrowser.getSelectedChangeList().getChanges(), myProject);
    for (Map.Entry<AbstractVcs, JPanel> entry : myPerVcsOptionsPanels.entrySet()) {
      entry.getValue().setVisible(affectedVcses.contains(entry.getKey()));
    }
  }

  @Override
  protected String getHelpId() {
    return myHelpId;
  }

  private class CommitAction extends AbstractAction implements OptionAction {

    private Action[] myOptions = new Action[0];

    private CommitAction(String okActionText) {
      super(okActionText);
      putValue(DEFAULT_ACTION, Boolean.TRUE);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      executeDefaultCommitSession();
    }

    @NotNull
    @Override
    public Action[] getOptions() {
      return myOptions;
    }

    public void setOptions(List<? extends Action> actions) {
      myOptions = ArrayUtil.toObjectArray(actions, Action.class);
    }
  }

  private boolean addUnversionedFiles() {
    return ScheduleForAdditionAction
      .addUnversioned(myProject, myBrowser.getIncludedUnversionedFiles(), ChangeListManagerImpl.getDefaultUnversionedFileCondition(),
                      myBrowser);
  }

  @NotNull
  @Override
  protected Action getOKAction() {
    return myCommitAction != null ? myCommitAction : myExecutorActions.get(0);
  }

  @Override
  @NotNull
  protected Action[] createActions() {
    final List<Action> actions = new ArrayList<>();

    if (myCommitAction != null) {
      actions.add(myCommitAction);
    }
    else {
      actions.addAll(myExecutorActions);
    }
    actions.add(getCancelAction());
    if (myHelpId != null) {
      actions.add(getHelpAction());
    }

    return actions.toArray(new Action[actions.size()]);
  }

  private void executeDefaultCommitSession() {
    if (!myIsAlien && !addUnversionedFiles()) return;
    if (!saveDialogState()) return;
    saveComments(true);

    ensureDataIsActual(() -> {
      try {
        final DefaultListCleaner defaultListCleaner = new DefaultListCleaner();
        CheckinHandler.ReturnResult result = runBeforeCommitHandlers(null);
        if (result == CheckinHandler.ReturnResult.COMMIT) {
          close(OK_EXIT_CODE);
          doCommit(myResultHandler);

          defaultListCleaner.clean();
        }
      }
      catch (InputException ex) {
        ex.show();
      }
    });
  }

  private void execute(final CommitExecutor commitExecutor) {
    final CommitSession session = commitExecutor.createCommitSession();
    if (session == CommitSession.VCS_COMMIT) {
      executeDefaultCommitSession();
      return;
    }

    if (!saveDialogState()) return;
    saveComments(true);

    if (session instanceof CommitSessionContextAware) {
      ((CommitSessionContextAware)session).setContext(myCommitContext);
    }

    ensureDataIsActual(() -> {
      final JComponent configurationUI = SessionDialog.createConfigurationUI(session, getIncludedChanges(), getCommitMessage());
      if (configurationUI != null) {
        DialogWrapper sessionDialog = new SessionDialog(commitExecutor.getActionText(),
                                                        getProject(),
                                                        session,
                                                        getIncludedChanges(),
                                                        getCommitMessage(),
                                                        configurationUI);
        if (!sessionDialog.showAndGet()) {
          session.executionCanceled();
          return;
        }
      }

      final DefaultListCleaner defaultListCleaner = new DefaultListCleaner();
      CheckinHandler.ReturnResult result = runBeforeCommitHandlers(commitExecutor);
      if (result == CheckinHandler.ReturnResult.COMMIT) {
        boolean success = false;
        try {
          final boolean completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
            session.execute(getIncludedChanges(), getCommitMessage());
          }, commitExecutor.getActionText(), true, getProject());

          if (completed) {
            for (CheckinHandler handler : myHandlers) {
              handler.checkinSuccessful();
            }

            success = true;
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
            handler.checkinFailed(Collections.singletonList(new VcsException(e)));
          }
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
    final List<Change> list = getIncludedChanges();
    final Ref<String> result = new Ref<>();
    ChangesUtil.processChangesByVcs(myProject, list, (vcs, items) -> {
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

  private void updateComment() {
    if (myVcsConfiguration.CLEAR_INITIAL_COMMIT_MESSAGE) return;
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
      if (!list.hasDefaultName()) {
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

  @Override
  public String getCommitActionName() {
    String name = null;
    for (AbstractVcs vcs : getAffectedVcses()) {
      final CheckinEnvironment checkinEnvironment = vcs.getCheckinEnvironment();
      if (name == null && checkinEnvironment != null) {
        name = checkinEnvironment.getCheckinOperationName();
      }
      else {
        name = VcsBundle.getString("commit.dialog.default.commit.operation.name");
      }
    }
    return name != null ? name : VcsBundle.getString("commit.dialog.default.commit.operation.name");
  }

  private boolean checkComment() {
    if (myVcsConfiguration.FORCE_NON_EMPTY_COMMENT && getCommitMessage().isEmpty()) {
      int requestForCheckin = Messages.showYesNoDialog(VcsBundle.message("confirmation.text.check.in.with.empty.comment"),
                                                       VcsBundle.message("confirmation.title.check.in.with.empty.comment"),
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

  private CheckinHandler.ReturnResult runBeforeCommitHandlers(@Nullable CommitExecutor executor) {
    final Computable<CheckinHandler.ReturnResult> proceedRunnable = new Computable<CheckinHandler.ReturnResult>() {
      @Override
      public CheckinHandler.ReturnResult compute() {
        FileDocumentManager.getInstance().saveAllDocuments();

        for (CheckinHandler handler : myHandlers) {
          if (!(handler.acceptExecutor(executor))) continue;
          final CheckinHandler.ReturnResult result = handler.beforeCheckin(executor, myAdditionalData);
          if (result == CheckinHandler.ReturnResult.COMMIT) continue;
          if (result == CheckinHandler.ReturnResult.CANCEL) {
            restartUpdate();
            return CheckinHandler.ReturnResult.CANCEL;
          }

          if (result == CheckinHandler.ReturnResult.CLOSE_WINDOW) {
            final ChangeList changeList = myBrowser.getSelectedChangeList();
            CommitHelper.moveToFailedList(changeList,
                                          getCommitMessage(),
                                          getIncludedChanges(),
                                          VcsBundle.message("commit.dialog.rejected.commit.template", changeList.getName()),
                                          myProject);
            doCancelAction();
            return CheckinHandler.ReturnResult.CLOSE_WINDOW;
          }
        }

        return CheckinHandler.ReturnResult.COMMIT;
      }
    };

    stopUpdate();
    final Ref<CheckinHandler.ReturnResult> compoundResultRef = Ref.create();
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        compoundResultRef.set(proceedRunnable.compute());
      }
    };
    for (final CheckinHandler handler : myHandlers) {
      if (handler instanceof CheckinMetaHandler) {
        final Runnable previousRunnable = runnable;
        runnable = new Runnable() {
          @Override
          public void run() {
            ((CheckinMetaHandler)handler).runCheckinHandlers(previousRunnable);
          }
        };
      }
    }
    runnable.run();
    return compoundResultRef.get();
  }

  private boolean saveDialogState() {
    if (!checkComment()) {
      return false;
    }

    saveCommentIntoChangeList();
    myVcsConfiguration.saveCommitMessage(getCommitMessage());
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
      myToClean = (totalSize == selectedSize) && (((LocalChangeList)selectedList).hasDefaultName());
    }

    void clean() {
      if (myToClean) {
        final ChangeListManager clManager = ChangeListManager.getInstance(myProject);
        clManager.editComment(LocalChangeList.DEFAULT_NAME, "");
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

  private void doCommit(@Nullable CommitResultHandler customResultHandler) {
    final CommitHelper helper = new CommitHelper(
      myProject,
      myBrowser.getSelectedChangeList(),
      getIncludedChanges(),
      TITLE,
      getCommitMessage(),
      myHandlers,
      myAllOfDefaultChangeListChangesIncluded,
      false,
      myAdditionalData,
      customResultHandler);

    if (myIsAlien) {
      helper.doAlienCommit(mySingleVcs);
    } else {
      helper.doCommit(mySingleVcs);
    }
  }

  @Override
  @Nullable
  protected JComponent createCenterPanel() {
    return myDetailsSplitter.getComponent();
  }

  @NotNull
  public Set<AbstractVcs> getAffectedVcses() {
    return myShowVcsCommit ? myBrowser.getAffectedVcses() : Collections.emptySet();
  }

  @NotNull
  @Override
  public Collection<VirtualFile> getRoots() {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);

    return ContainerUtil
      .map2SetNotNull(myBrowser.getCurrentDisplayedChanges(), (change) -> vcsManager.getVcsRootFor(ChangesUtil.getFilePath(change)));
  }

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
    return ContainerUtil.mapNotNull(getIncludedChanges(), (change) -> ChangesUtil.getFilePath(change).getVirtualFile());
  }

  @NotNull
  @Override
  public Collection<Change> getSelectedChanges() {
    return ContainerUtil.newArrayList(getIncludedChanges());
  }

  @NotNull
  @Override
  public Collection<File> getFiles() {
    return ContainerUtil.map(getIncludedChanges(), (change) -> ChangesUtil.getFilePath(change).getIOFile());
  }

  @NotNull
  @Override
  public Project getProject() {
    return myProject;
  }

  @Override
  public boolean vcsIsAffected(String name) {
    // tod +- performance?
    if (! ProjectLevelVcsManager.getInstance(myProject).checkVcsIsActive(name)) return false;

    return ContainerUtil.exists(myBrowser.getAffectedVcses(), (vcs) -> Comparing.equal(vcs.getName(), name));
  }

  @Override
  public void setCommitMessage(final String currentDescription) {
    setCommitMessageText(currentDescription);
    myCommitMessageArea.requestFocusInMessage();
  }

  private void setCommitMessageText(final String currentDescription) {
    myLastKnownComment = currentDescription;
    myCommitMessageArea.setText(currentDescription);
  }

  @NotNull
  @Override
  public String getCommitMessage() {
    return myCommitMessageArea.getComment();
  }

  @Override
  public void refresh() {
    ChangeListManager.getInstance(myProject).invokeAfterUpdate(new Runnable() {
      @Override
      public void run() {
        myBrowser.rebuildList();
        for (RefreshableOnComponent component : myAdditionalComponents) {
          component.refresh();
        }
      }
    }, InvokeAfterUpdateMode.SILENT, "commit dialog", ModalityState.current());   // title not shown for silently
  }

  @Override
  public void saveState() {
    for (RefreshableOnComponent component : myAdditionalComponents) {
      component.saveState();
    }
  }

  @Override
  public void restoreState() {
    for (RefreshableOnComponent component : myAdditionalComponents) {
      component.restoreState();
    }
  }

  // Used in plugins
  @SuppressWarnings("unused")
  @NotNull
  public List<RefreshableOnComponent> getAdditionalComponents() {
    return Collections.unmodifiableList(myAdditionalComponents);
  }

  private void updateButtons() {
    if (myDisposed || myUpdateDisabled) return;
    final boolean enabled = hasDiffs();
    if (myCommitAction != null) {
      myCommitAction.setEnabled(enabled);
    }
    for (CommitExecutorAction executorAction : myExecutorActions) {
      executorAction.updateEnabled(enabled);
    }
    myOKButtonUpdateAlarm.cancelAllRequests();
    myOKButtonUpdateAlarm.addRequest(myUpdateButtonsRunnable, 300, ModalityState.stateForComponent(myBrowser));
  }

  private void updateLegend() {
    if (myDisposed || myUpdateDisabled) return;
    myChangesInfoCalculator.update(myBrowser.getCurrentDisplayedChanges(), getIncludedChanges(), myBrowser.getUnversionedFilesCount(),
                                   myBrowser.getIncludedUnversionedFiles().size());
    myLegend.update();
  }

  @NotNull
  private List<Change> getIncludedChanges() {
    return myBrowser.getCurrentIncludedChanges();
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

  @Override
  public void calcData(DataKey key, DataSink sink) {
    if (key == Refreshable.PANEL_KEY) {
      sink.put(Refreshable.PANEL_KEY, this);
    }
    else {
      myBrowser.calcData(key, sink);
    }
  }

  static String trimEllipsis(final String title) {
    return StringUtil.trimEnd(title, "...");
  }

  private void ensureDataIsActual(final Runnable runnable) {
    if (myBrowser.isDataIsDirty()) {
      ChangeListManager.getInstance(myProject).invokeAfterUpdate(runnable, InvokeAfterUpdateMode.SYNCHRONOUS_CANCELLABLE,
                                                                 "Refreshing changelists...", ModalityState.current());
    }
    else {
      runnable.run();
    }
  }

  private class CommitExecutorAction extends AbstractAction {
    @NotNull private final CommitExecutor myCommitExecutor;

    public CommitExecutorAction(@NotNull CommitExecutor commitExecutor) {
      super(commitExecutor.getActionText());
      myCommitExecutor = commitExecutor;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      execute(myCommitExecutor);
    }

    public void updateEnabled(boolean hasDiffs) {
      setEnabled(hasDiffs
                 || (myCommitExecutor instanceof CommitExecutorBase) && !((CommitExecutorBase)myCommitExecutor).areChangesRequired());
    }
  }

  private static class DiffCommitMessageEditor extends CommitMessage implements Disposable {
    public DiffCommitMessageEditor(@NotNull Project project, @NotNull CommitMessage commitMessage) {
      super(project, commitMessage);
    }

    @Override
    public Dimension getPreferredSize() {
      // we don't want to be squeezed to one line
      return new Dimension(400, 120);
    }
  }

  private void changeDetails() {
    if (myDetailsSplitter.isOn()) {
      myDiffDetails.refresh();
    }
  }

  private class MyChangeProcessor extends CacheChangeProcessor {
    public MyChangeProcessor(@NotNull Project project) {
      super(project, DiffPlaces.COMMIT_DIALOG);

      putContextUserData(DiffUserDataKeysEx.SHOW_READ_ONLY_LOCK, true);
    }

    @NotNull
    @Override
    protected List<Change> getSelectedChanges() {
      return myBrowser.getSelectedChanges();
    }

    @NotNull
    @Override
    protected List<Change> getAllChanges() {
      return myBrowser.getAllChanges();
    }

    @Override
    protected void selectChange(@NotNull Change change) {
      //noinspection unchecked
      myBrowser.select((List)Collections.singletonList(change));
    }

    @Override
    protected void onAfterNavigate() {
      doCancelAction();
    }
  }

  private static class MyOptionsLayout extends AbstractLayoutManager {
    private final JComponent myPanel;
    private final JComponent myOptions;
    private final int myMinOptionsWidth;
    private final int myMaxOptionsWidth;

    public MyOptionsLayout(@NotNull JComponent panel, @NotNull JComponent options, int minOptionsWidth, int maxOptionsWidth) {
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
    public void layoutContainer(Container parent) {
      Rectangle bounds = parent.getBounds();
      int preferredWidth = myOptions.getPreferredSize().width;
      int optionsWidth = Math.max(Math.min(myMaxOptionsWidth, preferredWidth), myMinOptionsWidth);
      myPanel.setBounds(new Rectangle(0, 0, bounds.width - optionsWidth, bounds.height));
      myOptions.setBounds(new Rectangle(bounds.width - optionsWidth, 0, optionsWidth, bounds.height));
    }
  }
}

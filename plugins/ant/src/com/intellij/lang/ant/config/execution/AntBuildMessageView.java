// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.config.execution;

import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.OccurenceNavigator;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.actions.CloseTabToolbarAction;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.ide.actions.NextOccurenceToolbarAction;
import com.intellij.ide.actions.PreviousOccurenceToolbarAction;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntBuildFileBase;
import com.intellij.lang.ant.config.AntBuildListener;
import com.intellij.lang.ant.config.actions.*;
import com.intellij.lang.ant.config.impl.AntBuildFileImpl;
import com.intellij.lang.ant.config.impl.BuildFileProperty;
import com.intellij.lang.ant.config.impl.HelpID;
import com.intellij.lang.ant.segments.OutputPacketProcessor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.VetoableProjectManagerListener;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.ui.content.*;
import com.intellij.util.Alarm;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AntBuildMessageView extends JPanel
  implements UiDataProvider, OccurenceNavigator, Disposable {

  private static final Logger LOG = Logger.getInstance(AntBuildMessageView.class);

  public enum MessageType {
    BUILD,
    TARGET,
    TASK,
    MESSAGE,
    ERROR,
  }

  private static final Key<AntBuildMessageView> KEY = Key.create("BuildMessageView.KEY");

  public static final int PRIORITY_ERR = org.apache.tools.ant.Project.MSG_ERR;
  public static final int PRIORITY_WARN = org.apache.tools.ant.Project.MSG_WARN;
  public static final int PRIORITY_INFO = org.apache.tools.ant.Project.MSG_INFO;
  public static final int PRIORITY_VERBOSE = org.apache.tools.ant.Project.MSG_VERBOSE;
  public static final int PRIORITY_DEBUG = org.apache.tools.ant.Project.MSG_DEBUG;

  private OutputParser myParsingThread;
  private final Project myProject;
  private final JPanel myMessagePanel;
  private final JPanel myContentPanel;
  private final CardLayout myCardLayout;
  private AntBuildFileBase myBuildFile;
  private final List<String> myTargets;
  private final List<BuildFileProperty> myAdditionalProperties;
  @AntMessage.Priority
  private int myPriorityThreshold = PRIORITY_INFO;
  private volatile int myErrorCount;
  private volatile int myWarningCount;
  private volatile boolean myIsOutputPaused;

  @NotNull
  private volatile AntOutputView myCurrentView;

  private final PlainTextView myPlainTextView;
  private final TreeView myTreeView;

  private final java.util.List<LogCommand> myLog = Collections.synchronizedList(new ArrayList<>(1024));
  private volatile int myCommandsProcessedCount;

  private final Alarm myAlarm = new Alarm();
  private final Runnable myFlushLogRunnable = () -> {
    if (myCommandsProcessedCount < myLog.size()) {
      flushWhenSmart(true);
    }
  };

  private volatile boolean myIsAborted;
  private ActionToolbar myLeftToolbar;
  private ActionToolbar myRightToolbar;
  private final TreeExpander myTreeExpander = new TreeExpander() {
    @Override
    public boolean canCollapse() {
      return isTreeView();
    }

    @Override
    public boolean canExpand() {
      return isTreeView();
    }

    @Override
    public void collapseAll() {
      AntBuildMessageView.this.collapseAll();
    }

    @Override
    public void expandAll() {
      AntBuildMessageView.this.expandAll();
    }
  };
  @NonNls
  private static final String FILE_PREFIX = "file:";

  private AntBuildMessageView(Project project, AntBuildFileBase buildFile, List<String> targets, List<BuildFileProperty> additionalProperties) {
    super(new BorderLayout(2, 0));
    myProject = project;
    myBuildFile = buildFile;
    myTargets = targets;
    myAdditionalProperties = additionalProperties;
    setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

    myPlainTextView = new PlainTextView(project);
    myTreeView = new TreeView(project, buildFile);

    myCardLayout = new CardLayout();
    myContentPanel = new JPanel(myCardLayout);
    myContentPanel.add(myTreeView.getComponent(), myTreeView.getId());
    myContentPanel.add(myPlainTextView.getComponent(), myPlainTextView.getId());
    myMessagePanel = JBUI.Panels.simplePanel(myContentPanel);

    setVerboseMode(AntBuildFileImpl.VERBOSE.value(buildFile.getAllOptions()));

    add(createToolbarPanel(), BorderLayout.WEST);
    add(myMessagePanel, BorderLayout.CENTER);

    showAntView(AntBuildFileImpl.TREE_VIEW.value(buildFile.getAllOptions()));
  }

  @Override
  public void dispose() {
    Disposer.dispose(myAlarm);
    myBuildFile = null;
    myPlainTextView.dispose();
  }

  public void changeView() {
    showAntView(!isTreeView());
    if (myBuildFile != null) {
      myBuildFile.setTreeView(isTreeView());
    }
  }

  private boolean isTreeView() {
    return myCurrentView == myTreeView;
  }

  public void setVerboseMode(boolean verbose) {
    changeDetalizationLevel(verbose ? PRIORITY_DEBUG : PRIORITY_INFO);
    if (myBuildFile != null) {
      myBuildFile.setVerboseMode(verbose);
    }
  }

  public boolean isVerboseMode() {
    return myPriorityThreshold == PRIORITY_DEBUG;
  }

  private synchronized void changeDetalizationLevel(@AntMessage.Priority int priorityThreshold) {
    myPriorityThreshold = priorityThreshold;

    TreeView.TreeSelection selection = myTreeView.getSelection();
    myTreeView.clearAllMessages();
    myPlainTextView.clearAllMessages();
    myTreeView.setActionsEnabled(false);

    new OutputFlusher() {
      @Override
      public void doFlush() {
        final int processedCount = myCommandsProcessedCount;
        for (int i = 0; i < processedCount; i++) {
          LogCommand command = myLog.get(i);
          proceedOneCommand(command);
        }
        flushDelayedMessages();
      }
    }.doFlush();
    myTreeView.setActionsEnabled(true);
    if (!myTreeView.restoreSelection(selection)) {
      myTreeView.scrollToLastMessage();
    }
  }

  private void showAntView(boolean treeView) {
    final AntOutputView newView = getOutputView(!treeView);
    myCurrentView = newView;
    myCardLayout.show(myContentPanel, newView.getId());

    JComponent component = IdeFocusTraversalPolicy.getPreferredFocusedComponent(myMessagePanel);
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() ->
       IdeFocusManager.getGlobalInstance().requestFocus(component, true));
    repaint();
  }

  private AntOutputView getOutputView(boolean isText) {
    return isText ? myPlainTextView : myTreeView;
  }

  public AntBuildFileBase getBuildFile() {
    return myBuildFile;
  }

  /**
   * @return can be null if user cancelled operation
   */
  @Nullable
  static AntBuildMessageView openBuildMessageView(Project project,
                                                  AntBuildFileBase buildFile,
                                                  List<String> targets,
                                                  List<BuildFileProperty> additionalProperties) {
    final VirtualFile antFile = buildFile.getVirtualFile();
    if (!LOG.assertTrue(antFile != null)) {
      return null;
    }

    // check if there are running instances of the same build file

    MessageView ijMessageView = MessageView.getInstance(project);
    Content[] contents = ijMessageView.getContentManager().getContents();
    for (Content content : contents) {
      if (content.isPinned()) {
        continue;
      }
      AntBuildMessageView buildMessageView = content.getUserData(KEY);
      if (buildMessageView == null) {
        continue;
      }

      if (!antFile.equals(buildMessageView.getBuildFile().getVirtualFile())) {
        continue;
      }

      if (buildMessageView.isStopped()) {
        ijMessageView.getContentManager().removeContent(content, true);
        continue;
      }

      int result = Messages.showYesNoCancelDialog(AntBundle.message("ant.is.active.terminate.confirmation.text"),
                                                  AntBundle.message("starting.ant.build.dialog.title"), Messages.getQuestionIcon());

      switch (result) {
        case Messages.YES -> {  // yes
          buildMessageView.stopProcess();
          ijMessageView.getContentManager().removeContent(content, true);
        }
        case Messages.NO -> { // no
        }
        default -> { // cancel
          return null;
        }
      }
    }

    final AntBuildMessageView messageView = new AntBuildMessageView(project, buildFile, targets, additionalProperties);
    String contentName = buildFile.getPresentableName();
    contentName = getBuildContentName() + " (" + contentName + ")";

    final Content content = ContentFactory.getInstance().createContent(messageView.getComponent(), contentName, true);
    content.putUserData(KEY, messageView);
    ijMessageView.getContentManager().addContent(content);
    ijMessageView.getContentManager().setSelectedContent(content);
    content.setDisposer(() -> Disposer.dispose(messageView));
    new CloseListener(content, ijMessageView.getContentManager(), project).setupListeners();

    if (!buildFile.isRunInBackground()) {
      final ToolWindow tw = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.MESSAGES_WINDOW);
      if (tw != null) {
        tw.activate(null, false);
      }
    }

    return messageView;
  }

  void setParsingThread(OutputParser parsingThread) {
    myParsingThread = parsingThread;
    myIsAborted = false;
  }

  public void stopProcess() {
    if (myParsingThread != null) {
      myParsingThread.stopProcess();
    }
    myIsAborted = true;
    myLeftToolbar.updateActionsImmediately();
    myRightToolbar.updateActionsImmediately();
  }

  public boolean isStopped() {
    return myParsingThread == null || myParsingThread.isStopped();
  }

  private boolean isStoppedOrTerminateRequested() {
    return myParsingThread == null || myParsingThread.isTerminateInvoked() || isStopped();
  }

  private void close() {
    MessageView messageView = MessageView.getInstance(myProject);
    Content[] contents = messageView.getContentManager().getContents();
    for (Content content : contents) {
      if (content.getComponent() == this) {
        messageView.getContentManager().removeContent(content, true);
        return;
      }
    }
  }

  private JPanel createToolbarPanel() {
    RunAction runAction = new RunAction(this);
    runAction.registerCustomShortcutSet(CommonShortcuts.getRerun(), this);

    DefaultActionGroup leftActionGroup = new DefaultActionGroup();
    leftActionGroup.add(runAction);
    leftActionGroup.add(new PauseOutputAction(this));
    leftActionGroup.add(new StopAction(this));
    leftActionGroup.add(new CloseAction());
    leftActionGroup.add(new PreviousOccurenceToolbarAction(this));
    leftActionGroup.add(new NextOccurenceToolbarAction(this));
    leftActionGroup.add(new ContextHelpAction(HelpID.ANT));

    DefaultActionGroup rightActionGroup = new DefaultActionGroup();
    rightActionGroup.add(new ChangeViewAction(this));
    rightActionGroup.add(new VerboseAction(this));
    rightActionGroup.add(CommonActionsManager.getInstance().createExpandAllAction(myTreeExpander, this));
    rightActionGroup.add(CommonActionsManager.getInstance().createCollapseAllAction(myTreeExpander, this));
    rightActionGroup.add(myTreeView.createToggleAutoscrollAction());

    myLeftToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.ANT_MESSAGES_TOOLBAR, leftActionGroup, false);
    myLeftToolbar.setTargetComponent(this);
    JPanel toolbarPanel = new JPanel(new GridLayout(1, 2, 2, 0));
    toolbarPanel.add(myLeftToolbar.getComponent());
    myRightToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.ANT_MESSAGES_TOOLBAR, rightActionGroup, false);
    myRightToolbar.setTargetComponent(this);
    toolbarPanel.add(myRightToolbar.getComponent());

    return toolbarPanel;
  }

  public final class CloseAction extends CloseTabToolbarAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      close();
    }
  }


  private synchronized void addCommand(LogCommand command) {
    if (!myAlarm.isDisposed()) {
      myLog.add(command);
      myAlarm.cancelAllRequests();
      myAlarm.addRequest(myFlushLogRunnable, 100L);
    }
  }

  void startBuild(@Nls String buildName) {
    addCommand(new StartBuildCommand(buildName));
  }

  void buildFailed(@Nls String buildName) {
    addCommand(new BuildFailedCommand(buildName));
  }

  void startTarget(@Nls String targetName) {
    addCommand(new StartTargetCommand(targetName));
  }

  void startTask(@Nls String taskName) {
    addCommand(new StartTaskCommand(taskName));
  }

  void outputMessage(final @Nls String text, @AntMessage.Priority int priority) {
    final AntMessage customizedMessage = getCustomizedMessage(text, priority);
    final AntMessage message = customizedMessage != null
                               ? customizedMessage
                               : new AntMessage(MessageType.MESSAGE, priority, text, null, 0, 0);
    updateErrorAndWarningCounters(message.getPriority());
    addCommand(new AddMessageCommand(message));
  }

  @Nullable
  private static AntMessage getCustomizedMessage(final @Nls String text, @AntMessage.Priority int priority) {
    AntMessage customizedMessage = null;

    for (AntMessageCustomizer customizer : AntMessageCustomizer.EP_NAME.getExtensionList()) {
      customizedMessage = customizer.createCustomizedMessage(text, priority);
      if (customizedMessage != null) {
        break;
      }
    }

    return customizedMessage;
  }

  void outputError(@Nls String error, @AntMessage.Priority int priority) {
    updateErrorAndWarningCounters(priority);
    final AntMessage message = createErrorMessage(priority, error);
    addCommand(new AddMessageCommand(message));
    VirtualFile file = message.getFile();
    if (file != null) {
      queueToWolf(file);
    }
  }

  void outputException(String exception) {
    updateErrorAndWarningCounters(PRIORITY_ERR);
    AntMessage message = createErrorMessage(PRIORITY_ERR, exception);
    addCommand(new AddExceptionCommand(message));
    VirtualFile file = message.getFile();
    if (file != null) {
      queueToWolf(file);
    }
  }


  private void updateErrorAndWarningCounters(@AntMessage.Priority int priority) {
    if (priority == PRIORITY_ERR) {
      myErrorCount++;
    }
    else if (priority == PRIORITY_WARN) {
      myWarningCount++;
    }
  }

  void finishTarget() {
    addCommand(new FinishTargetCommand());
  }

  void finishTask() {
    addCommand(new FinishTaskCommand());
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(PlatformCoreDataKeys.HELP_ID, HelpID.ANT);
    sink.set(PlatformDataKeys.TREE_EXPANDER, myTreeExpander);
    myCurrentView.uiDataSnapshot(sink);
  }

  private static AntMessage createErrorMessage(@AntMessage.Priority int priority, @NlsSafe String text) {
    text = StringUtil.trimStart(text, FILE_PREFIX);

    int afterLineNumberIndex = text.indexOf(": "); // end of file_name_and_line_number sequence
    if (afterLineNumberIndex != -1) {
      String fileAndLineNumber = text.substring(0, afterLineNumberIndex);
      int index = fileAndLineNumber.lastIndexOf(':');
      if (index != -1) {
        try {
          String lineNumberStr = fileAndLineNumber.substring(index + 1).trim();
          int line = Integer.parseInt(lineNumberStr);

          String fileName = fileAndLineNumber.substring(0, index);
          final File file = new File(fileName);
          final VirtualFile result = ReadAction.compute(() -> {
            String url =
              VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, file.getAbsolutePath().replace(File.separatorChar, '/'));
            return VirtualFileManager.getInstance().findFileByUrl(url);
          });

          // convert separators
          text = fileName.replace('/', File.separatorChar) + ':' + line + text.substring(afterLineNumberIndex);

          return new AntMessage(MessageType.ERROR, priority, text, result, line, 1);
        }
        catch (NumberFormatException ignored) {
        }
      }
    }

    return new AntMessage(MessageType.ERROR, priority, text, null, 0, 0);
  }

  void outputJavacMessage(MessageType type, String[] text, VirtualFile file, String url, int line, int column) {
    int priority = type == MessageType.ERROR ? PRIORITY_ERR : PRIORITY_VERBOSE;
    updateErrorAndWarningCounters(priority);
    final AntMessage message = new AntMessage(type, priority, text, file, line, column);
    addCommand(new AddJavacMessageCommand(message, url));
    if (type == MessageType.ERROR && file != null) {
      queueToWolf(file);
    }
  }

  private void queueToWolf(@NotNull VirtualFile file) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      ApplicationManager.getApplication().executeOnPooledThread(() -> doQueueToWolf(file));
    }
    else {
      doQueueToWolf(file);
    }
  }

  private void doQueueToWolf(@NotNull VirtualFile file) {
    ReadAction.run(() -> {
      if (!myProject.isDisposed()) {
        WolfTheProblemSolver.getInstance(myProject).queue(file);
      }
    });
  }

  private JComponent getComponent() {
    return this;
  }

  void emptyAll() {
    myLog.clear();
    myCommandsProcessedCount = 0;
    myErrorCount = 0;
    myWarningCount = 0;
    myPlainTextView.clearAllMessages();
    myTreeView.clearAllMessages();
  }

  private void collapseAll() {
    myTreeView.collapseAll();
  }

  private void expandAll() {
    myTreeView.expandAll();
  }

  private static final class CloseListener implements VetoableProjectManagerListener, ContentManagerListener {
    private Content myContent;
    private boolean myCloseAllowed;
    private final ContentManager myContentManager;
    private final Project myProject;

    private CloseListener(Content content, ContentManager contentManager, Project project) {
      myContent = content;
      myContentManager = contentManager;
      myProject = project;
    }

    private void setupListeners() {
      myContentManager.addContentManagerListener(this);
      ProjectManager.getInstance().addProjectManagerListener(myProject, this);

      Disposer.register(myContent, () -> {
        myContentManager.removeContentManagerListener(this);
        ProjectManager.getInstance().removeProjectManagerListener(myProject, this);
      });
    }

    @Override
    public void contentRemoved(@NotNull ContentManagerEvent event) {
      if (event.getContent() == myContent) {
        AntBuildMessageView buildMessageView = myContent.getUserData(KEY);
        if (!myCloseAllowed) {
          buildMessageView.stopProcess();
        }
        myContent.release();
        myContent = null;
      }
    }

    @Override
    public void contentRemoveQuery(@NotNull ContentManagerEvent event) {
      if (event.getContent() == myContent) {
        boolean canClose = closeQuery();
        if (!canClose) {
          event.consume();
        }
      }
    }

    @Override
    public void projectClosed(@NotNull Project project) {
      if (myContent != null) {
        myContentManager.removeContent(myContent, true);
      }
    }

    @Override
    public boolean canClose(@NotNull Project project) {
      return closeQuery();
    }

    /**
     * @return true if content can be closed
     */
    private boolean closeQuery() {
      if (myContent == null) {
        return true;
      }

      final AntBuildMessageView messageView = myContent.getUserData(KEY);

      if (messageView == null || messageView.isStoppedOrTerminateRequested()) {
        return true;
      }

      if (myCloseAllowed) {
        return true;
      }

      final int result = Messages.showYesNoCancelDialog(
        AntBundle.message("ant.process.is.active.terminate.confirmation.text"),
        AntBundle.message("close.ant.build.messages.dialog.title"), Messages.getQuestionIcon()
      );

      if (result == 0) { // yes
        messageView.stopProcess();
        myCloseAllowed = true;
        return true;
      }

      if (result == 1) { // no
        // close content and leave the process running
        myCloseAllowed = true;
        return true;
      }

      return false;
    }

  }

  private abstract static class LogCommand {
    @AntMessage.Priority
    private final int myPriority;

    LogCommand(@AntMessage.Priority int priority) {
      myPriority = priority;
    }

    @AntMessage.Priority
    final int getPriority() {
      return myPriority;
    }

    abstract void execute(AntOutputView outputView);
  }

  private abstract static class MessageCommand extends LogCommand {
    private final AntMessage myMessage;

    MessageCommand(@NotNull AntMessage message) {
      super(message.getPriority());
      myMessage = message;
    }

    @NotNull
    final AntMessage getMessage() {
      return myMessage;
    }
  }

  private static final class StartBuildCommand extends MessageCommand {
    StartBuildCommand(@Nls String buildName) {
      super(new AntMessage(MessageType.BUILD, PRIORITY_ERR, buildName, null, 0, 0));
    }

    @Override
    void execute(AntOutputView outputView) {
      outputView.startBuild(getMessage());
    }
  }

  private static final class BuildFailedCommand extends MessageCommand {
    BuildFailedCommand(String buildName) {
      super(new AntMessage(MessageType.ERROR, PRIORITY_ERR, AntBundle.message("cannot.start.build.name.error.message", buildName), null, 0, 0));
    }

    @Override
    void execute(AntOutputView outputView) {
      outputView.buildFailed(getMessage());
    }
  }

  private static final class FinishBuildCommand extends LogCommand {
    private final @Nls String myFinishStatusText;

    FinishBuildCommand(@Nls String finishStatusText) {
      super(PRIORITY_ERR);
      myFinishStatusText = finishStatusText;
    }

    @Override
    void execute(AntOutputView outputView) {
      outputView.finishBuild(myFinishStatusText);
    }
  }

  private static final class StartTargetCommand extends MessageCommand {
    StartTargetCommand(@Nls String targetName) {
      super(new AntMessage(MessageType.TARGET, PRIORITY_ERR, targetName, null, 0, 0));
    }

    @Override
    void execute(AntOutputView outputView) {
      outputView.startTarget(getMessage());
    }
  }

  private static final class FinishTargetCommand extends LogCommand {
    FinishTargetCommand() {
      super(PRIORITY_ERR);
    }

    @Override
    void execute(AntOutputView outputView) {
      outputView.finishTarget();
    }
  }

  private static final class StartTaskCommand extends MessageCommand {
    StartTaskCommand(@Nls String taskName) {
      super(new AntMessage(MessageType.TASK, PRIORITY_ERR, taskName, null, 0, 0));
    }

    @Override
    void execute(AntOutputView outputView) {
      outputView.startTask(getMessage());
    }
  }

  private static final class FinishTaskCommand extends LogCommand {
    FinishTaskCommand() {
      super(PRIORITY_ERR);
    }

    @Override
    public void execute(AntOutputView outputView) {
      outputView.finishTask();
    }
  }

  private static final class AddMessageCommand extends MessageCommand {
    AddMessageCommand(AntMessage antMessage) {
      super(antMessage);
    }

    @Override
    void execute(AntOutputView outputView) {
      outputView.addMessage(getMessage());
    }
  }

  private final class AddExceptionCommand extends MessageCommand {
    AddExceptionCommand(AntMessage antMessage) {
      super(antMessage);
    }

    @Override
    void execute(AntOutputView outputView) {
      outputView.addException(getMessage(), isVerboseMode());
    }
  }

  private static final class AddJavacMessageCommand extends MessageCommand {
    private final String myUrl;

    AddJavacMessageCommand(AntMessage antMessage, String url) {
      super(antMessage);
      myUrl = url;
    }

    @Override
    void execute(AntOutputView outputView) {
      outputView.addJavacMessage(getMessage(), myUrl);
    }
  }

  public List<String> getTargets() {
    return myTargets;
  }

  public List<BuildFileProperty> getAdditionalProperties() {
    return myAdditionalProperties;
  }

  private int getErrorCount() {
    return myErrorCount;
  }

  private int getWarningCount() {
    return myWarningCount;
  }

  void buildFinished(boolean isProgressAborted, final long buildTimeInMilliseconds, @NotNull final AntBuildListener antBuildListener, OutputPacketProcessor dispatcher) {
    final boolean aborted = isProgressAborted || myIsAborted;

    dispatcher.processOutput(__ -> {
      if (!myProject.isDisposed()) { // if not disposed
        final String message = getFinishStatusText(aborted, buildTimeInMilliseconds);
        addCommand(new FinishBuildCommand(message));
        final StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
        if (statusBar != null) {
          statusBar.setInfo(message);
        }
        final AntBuildFileBase buildFile = myBuildFile;
        final boolean isBackground = buildFile != null && buildFile.isRunInBackground();
        final boolean shouldActivate = !isBackground || getErrorCount() > 0;
        UIUtil.invokeLaterIfNeeded(() -> {
          final Runnable finishRunnable = () -> {
            final int errorCount = getErrorCount();
            try {
              final AntBuildFileBase buildFile1 = myBuildFile;
              if (buildFile1 != null) {
                if (errorCount == 0 && buildFile1.isViewClosedWhenNoErrors()) {
                  close();
                }
                else if (errorCount > 0) {
                  myTreeView.scrollToFirstError();
                }
                else {
                  myTreeView.scrollToStatus();
                }
              }
              else {
                myTreeView.scrollToLastMessage();
              }
            }
            finally {
              VirtualFileManager.getInstance().asyncRefresh(
                () -> antBuildListener.buildFinished(aborted ? AntBuildListener.ABORTED : AntBuildListener.FINISHED_SUCCESSFULLY, errorCount));
            }
          };
          if (shouldActivate) {
            final ToolWindow toolWindow = !myProject.isDisposed() ? ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.MESSAGES_WINDOW) : null;
            if (toolWindow != null) { // can be null if project is closed
              toolWindow.activate(finishRunnable, false);
            }
            else {
              finishRunnable.run();
            }
          }
          else {
            finishRunnable.run();
          }
        });
      }
    });
    ApplicationManager.getApplication().invokeLater(() -> flushWhenSmart(false), ModalityState.any(), myProject.getDisposed());
  }

  private void flushWhenSmart(boolean scroll) {
    DumbService.getInstance(myProject).runWhenSmart(() -> {
      if (!myIsOutputPaused) {
        new OutputFlusher().doFlush();
        if (scroll) {
          myTreeView.scrollToLastMessage();
        }
      }
    });
  }

  private @Nls String getFinishStatusText(boolean isAborted, long buildTimeInMilliseconds) {
    final String theDateAsString = DateFormatUtil.formatDateTime(Clock.getTime());
    final String formattedBuildTime = formatBuildTime(buildTimeInMilliseconds / 1000);
    if (isAborted) {
      return AntBundle.message("build.finished.status.ant.build.aborted", formattedBuildTime, theDateAsString);
    }
    final int errors = getErrorCount();
    final int warnings = getWarningCount();
    if (errors == 0 && warnings == 0) {
      return AntBundle.message("build.finished.status.ant.build.completed.successfully", formattedBuildTime, theDateAsString);
    }
    if (errors == 0) {
      return AntBundle.message("build.finished.status.ant.build.completed.with.warnings", warnings, formattedBuildTime, theDateAsString);
    }
    return AntBundle.message("build.finished.status.ant.build.completed.with.errors.warnings", errors, warnings, formattedBuildTime, theDateAsString);
  }

  private static String formatBuildTime(long seconds) {
    if (seconds == 0) {
      return "0s";
    }
    final StringBuilder sb = new StringBuilder();
    if (seconds >= 3600) {
      sb.append(seconds / 3600).append("h ");
      seconds %= 3600;
    }
    if (seconds >= 60 || sb.length() > 0) {
      sb.append(seconds / 60).append("m ");
      seconds %= 60;
    }
    if (seconds > 0 || sb.length() > 0) {
      sb.append(seconds).append("s");
    }
    return sb.toString();
  }

  public boolean isOutputPaused() {
    return myIsOutputPaused;
  }

  public synchronized void setOutputPaused(boolean outputPaused) {
    if (outputPaused == myIsOutputPaused) return;
    if (myIsOutputPaused) {
      new OutputFlusher().doFlush();
    }
    myIsOutputPaused = outputPaused;
  }

  private class OutputFlusher {
    private final ArrayList<AntMessage> myDelayedMessages = new ArrayList<>();

    public void doFlush() {
      int currentProcessedCount = myCommandsProcessedCount;
      while (currentProcessedCount < myLog.size()) {
        final LogCommand command = myLog.get(currentProcessedCount++);
        proceedOneCommand(command);
      }
      myCommandsProcessedCount = currentProcessedCount;
      flushDelayedMessages();
    }

    final void proceedOneCommand(LogCommand command) {
      if (command.getPriority() > myPriorityThreshold) {
        return;
      }
      // proceed messages in a special way
      if (command instanceof AddMessageCommand addMessageCommand) {
        myDelayedMessages.add(addMessageCommand.getMessage());
      }
      else {
        flushDelayedMessages(); // message type changed -> flush
        final AntOutputView firstView = myCurrentView;
        final AntOutputView secondView = firstView == myTreeView? myPlainTextView : myTreeView;
        command.execute(firstView);
        command.execute(secondView);
      }
    }

    final void flushDelayedMessages() {
      if (!myDelayedMessages.isEmpty()) {
        final AntMessage[] messages = myDelayedMessages.toArray(new AntMessage[0]);
        myDelayedMessages.clear();
        myTreeView.addMessages(messages);
        myPlainTextView.addMessages(messages);
      }
    }
  }

  @NotNull
  @Override
  public String getNextOccurenceActionName() {
    return myTreeView.getNextOccurenceActionName();
  }

  @NotNull
  @Override
  public String getPreviousOccurenceActionName() {
    return myTreeView.getPreviousOccurenceActionName();
  }

  @Override
  public OccurenceInfo goNextOccurence() {
    return isTreeView() ? myTreeView.goNextOccurence() : null;
  }

  @Override
  public OccurenceInfo goPreviousOccurence() {
    return isTreeView() ? myTreeView.goPreviousOccurence() : null;
  }

  @Override
  public boolean hasNextOccurence() {
    return isTreeView() && myTreeView.hasNextOccurence();
  }

  @Override
  public boolean hasPreviousOccurence() {
    return isTreeView() && myTreeView.hasPreviousOccurence();
  }

  void setBuildCommandLine(String commandLine) {
    myPlainTextView.setBuildCommandLine(commandLine);
  }

  static @Nls String getBuildContentName() {
    return AntBundle.message("ant.build.tab.content.title");
  }
}
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
package com.intellij.lang.ant.config.execution;

import com.intellij.execution.testframework.Printable;
import com.intellij.execution.testframework.Printer;
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AntBuildMessageView extends JPanel implements DataProvider, OccurenceNavigator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ant.execution.AntBuildMessageView");

  public enum MessageType {
    BUILD,
    TARGET,
    TASK,
    MESSAGE,
    ERROR,
  }

  private static final Key<AntBuildMessageView> KEY = Key.create("BuildMessageView.KEY");
  private static final String BUILD_CONTENT_NAME = AntBundle.message("ant.build.tab.content.title");

  public static final int PRIORITY_ERR = 0;
  public static final int PRIORITY_WARN = 1;
  public static final int PRIORITY_BRIEF = 2;
  public static final int PRIORITY_VERBOSE = 3;

  private OutputParser myParsingThread;
  private final Project myProject;
  private final JPanel myMessagePanel;
  private final JPanel myContentPanel;
  private final CardLayout myCardLayout;
  private AntBuildFileBase myBuildFile;
  private final String[] myTargets;
  private final List<BuildFileProperty> myAdditionalProperties;
  private int myPriorityThreshold = PRIORITY_BRIEF;
  private volatile int myErrorCount;
  private volatile int myWarningCount;
  private volatile boolean myIsOutputPaused = false;

  @NotNull
  private volatile AntOutputView myCurrentView;

  private final PlainTextView myPlainTextView;
  private final TreeView myTreeView;

  private final java.util.List<LogCommand> myLog = Collections.synchronizedList(new ArrayList<LogCommand>(1024));
  private volatile int myCommandsProcessedCount = 0;

  private final AntMessageCustomizer[] myMessageCustomizers = AntMessageCustomizer.EP_NAME.getExtensions();

  private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private final Runnable myFlushLogRunnable = new Runnable() {
    @Override
    public void run() {
      if (myCommandsProcessedCount < myLog.size()) {
        if (!myIsOutputPaused) {
          new OutputFlusher().doFlush();
          myTreeView.scrollToLastMessage();
        }
      }
    }
  };

  private volatile boolean myIsAborted;
  private ActionToolbar myLeftToolbar;
  private ActionToolbar myRightToolbar;
  private final TreeExpander myTreeExpander = new TreeExpander() {
    public boolean canCollapse() {
      return isTreeView();
    }

    public boolean canExpand() {
      return isTreeView();
    }

    public void collapseAll() {
      AntBuildMessageView.this.collapseAll();
    }

    public void expandAll() {
      AntBuildMessageView.this.expandAll();
    }
  };
  @NonNls public static final String FILE_PREFIX = "file:";

  private AntBuildMessageView(Project project, AntBuildFileBase buildFile, String[] targets, List<BuildFileProperty> additionalProperties) {
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
    changeDetalizationLevel(verbose ? PRIORITY_VERBOSE : PRIORITY_BRIEF);
    if (myBuildFile != null) {
      myBuildFile.setVerboseMode(verbose);
    }
  }

  public boolean isVerboseMode() {
    return myPriorityThreshold == PRIORITY_VERBOSE;
  }

  private synchronized void changeDetalizationLevel(int priorityThreshold) {
    myPriorityThreshold = priorityThreshold;

    TreeView.TreeSelection selection = myTreeView.getSelection();
    myTreeView.clearAllMessages();
    myPlainTextView.clearAllMessages();
    myTreeView.setActionsEnabled(false);

    new OutputFlusher() {
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
    component.requestFocus();
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
  public static AntBuildMessageView openBuildMessageView(Project project, AntBuildFileBase buildFile, String[] targets, List<BuildFileProperty> additionalProperties) {
    final VirtualFile antFile = buildFile.getVirtualFile();
    if (!LOG.assertTrue(antFile != null)) {
      return null;
    }

    // check if there are running instances of the same build file

    MessageView ijMessageView = MessageView.SERVICE.getInstance(project);
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
        case Messages.YES:  // yes
          buildMessageView.stopProcess();
          ijMessageView.getContentManager().removeContent(content, true);
          continue;
        case Messages.NO: // no
          continue;
        default: // cancel
          return null;
      }
    }

    final AntBuildMessageView messageView = new AntBuildMessageView(project, buildFile, targets, additionalProperties);
    String contentName = buildFile.getPresentableName();
    contentName = BUILD_CONTENT_NAME + " (" + contentName + ")";

    final Content content = ContentFactory.SERVICE.getInstance().createContent(messageView.getComponent(), contentName, true);
    content.putUserData(KEY, messageView);
    ijMessageView.getContentManager().addContent(content);
    ijMessageView.getContentManager().setSelectedContent(content);
    content.setDisposer(new Disposable() {
      @Override
      public void dispose() {
        Disposer.dispose(messageView.myAlarm);
      }
    });
    new CloseListener(content, ijMessageView.getContentManager(), project);

    if (!buildFile.isRunInBackground()) {
      final ToolWindow tw = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.MESSAGES_WINDOW);
      if (tw != null) {
        tw.activate(null, false);
      }
    }

    return messageView;
  }

  public void setParsingThread(OutputParser parsingThread) {
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

  public boolean isStoppedOrTerminateRequested() {
    return myParsingThread == null || myParsingThread.isTerminateInvoked() || isStopped();
  }

  private void close() {
    MessageView messageView = MessageView.SERVICE.getInstance(myProject);
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
    JPanel toolbarPanel = new JPanel(new GridLayout(1, 2, 2, 0));
    toolbarPanel.add(myLeftToolbar.getComponent());
    myRightToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.ANT_MESSAGES_TOOLBAR, rightActionGroup, false);
    toolbarPanel.add(myRightToolbar.getComponent());

    return toolbarPanel;
  }

  public final class CloseAction extends CloseTabToolbarAction {
    public void actionPerformed(AnActionEvent e) {
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

  public void startBuild(String buildName) {
    addCommand(new StartBuildCommand(buildName));
  }

  public void buildFailed(String buildName) {
    addCommand(new BuildFailedCommand(buildName));
  }

  public void startTarget(String targetName) {
    addCommand(new StartTargetCommand(targetName));
  }

  public void startTask(String taskName) {
    addCommand(new StartTaskCommand(taskName));
  }

  public void outputMessage(final String text, final int priority) {
    final AntMessage customizedMessage = getCustomizedMessage(text, priority);
    final AntMessage message = customizedMessage != null
                               ? customizedMessage
                               : new AntMessage(MessageType.MESSAGE, priority, text, null, 0, 0);
    updateErrorAndWarningCounters(message.getPriority());
    addCommand(new AddMessageCommand(message));
  }

  @Nullable
  private AntMessage getCustomizedMessage(final String text, final int priority) {
    AntMessage customizedMessage = null;

    for (AntMessageCustomizer customizer : myMessageCustomizers) {
      customizedMessage = customizer.createCustomizedMessage(text, priority);
      if (customizedMessage != null) {
        break;
      }
    }

    return customizedMessage;
  }

  public void outputError(String error, int priority) {
    updateErrorAndWarningCounters(priority);
    final AntMessage message = createErrorMessage(priority, error);
    addCommand(new AddMessageCommand(message));
    WolfTheProblemSolver wolf = WolfTheProblemSolver.getInstance(myProject);
    wolf.queue(message.getFile());
  }

  public void outputException(String exception) {
    updateErrorAndWarningCounters(PRIORITY_ERR);
    AntMessage message = createErrorMessage(PRIORITY_ERR, exception);
    addCommand(new AddExceptionCommand(message));
    WolfTheProblemSolver wolf = WolfTheProblemSolver.getInstance(myProject);
    wolf.queue(message.getFile());
  }


  private void updateErrorAndWarningCounters(int priority) {
    if (priority == PRIORITY_ERR) {
      myErrorCount++;
    }
    else if (priority == PRIORITY_WARN) {
      myWarningCount++;
    }
  }

  public void finishTarget() {
    addCommand(new FinishTargetCommand());
  }

  public void finishTask() {
    addCommand(new FinishTaskCommand());
  }

  public Object getData(String dataId) {
    Object data = myCurrentView.getData(dataId);
    if (data != null) {
      return data;
    }
    if (PlatformDataKeys.HELP_ID.is(dataId)) {
      return HelpID.ANT;
    }
    else if (PlatformDataKeys.TREE_EXPANDER.is(dataId)) {
      return myTreeExpander;
    }
    return null;
  }

  private static AntMessage createErrorMessage(int priority, String text) {
    text = StringUtil.trimStart(text, FILE_PREFIX);

    int afterLineNumberIndex = text.indexOf(": "); // end of file_name_and_line_number sequence
    if (afterLineNumberIndex != -1) {
      String fileAndLineNumber = text.substring(0, afterLineNumberIndex);
      int index = fileAndLineNumber.lastIndexOf(':');
      if (index != -1) {
        String fileName = fileAndLineNumber.substring(0, index);
        String lineNumberStr = fileAndLineNumber.substring(index + 1, fileAndLineNumber.length()).trim();
        try {
          int line = Integer.parseInt(lineNumberStr);

          final File file = new File(fileName);
          final VirtualFile result = ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile>() {
            public VirtualFile compute() {
              String url =
                VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, file.getAbsolutePath().replace(File.separatorChar, '/'));
              return VirtualFileManager.getInstance().findFileByUrl(url);
            }
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

  public void outputJavacMessage(MessageType type, String[] text, VirtualFile file, String url, int line, int column) {
    int priority = type == MessageType.ERROR ? PRIORITY_ERR : PRIORITY_VERBOSE;
    updateErrorAndWarningCounters(priority);
    final AntMessage message = new AntMessage(type, priority, text, file, line, column);
    addCommand(new AddJavacMessageCommand(message, url));
    if (type == MessageType.ERROR) {
      WolfTheProblemSolver wolf = WolfTheProblemSolver.getInstance(myProject);
      wolf.queue(file);
    }
  }

  private JComponent getComponent() {
    return this;
  }

  public void emptyAll() {
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

  private static final class CloseListener extends ContentManagerAdapter implements ProjectManagerListener {
    private Content myContent;
    private boolean myCloseAllowed = false;
    private final ContentManager myContentManager;
    private final Project myProject;

    private CloseListener(Content content, ContentManager contentManager, Project project) {
      myContent = content;
      myContentManager = contentManager;
      myProject = project;
      contentManager.addContentManagerListener(this);
      ProjectManager.getInstance().addProjectManagerListener(myProject, this);
    }

    public void contentRemoved(ContentManagerEvent event) {
      if (event.getContent() == myContent) {
        myContentManager.removeContentManagerListener(this);
        AntBuildMessageView buildMessageView = myContent.getUserData(KEY);
        if (!myCloseAllowed) {
          buildMessageView.stopProcess();
        }
        ProjectManager.getInstance().removeProjectManagerListener(myProject, this);
        myContent.release();
        myContent = null;
        buildMessageView.myBuildFile = null;
        buildMessageView.myPlainTextView.dispose();
      }
    }

    public void contentRemoveQuery(ContentManagerEvent event) {
      if (event.getContent() == myContent) {
        boolean canClose = closeQuery();
        if (!canClose) {
          event.consume();
        }
      }
    }

    public void projectClosed(Project project) {
      if (myContent != null) {
        myContentManager.removeContent(myContent, true);
      }
    }

    public boolean canCloseProject(Project project) {
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
    private final int myPriority;

    LogCommand(int priority) {
      myPriority = priority;
    }

    final int getPriority() {
      return myPriority;
    }

    abstract void execute(AntOutputView outputView);
  }

  private abstract static class MessageCommand extends LogCommand {
    private final AntMessage myMessage;

    protected MessageCommand(@NotNull AntMessage message) {
      super(message.getPriority());
      myMessage = message;
    }

    @NotNull
    final AntMessage getMessage() {
      return myMessage;
    }
  }

  private static final class StartBuildCommand extends MessageCommand {
    StartBuildCommand(String buildName) {
      super(new AntMessage(MessageType.BUILD, 0, buildName, null, 0, 0));
    }

    void execute(AntOutputView outputView) {
      outputView.startBuild(getMessage());
    }
  }

  private static final class BuildFailedCommand extends MessageCommand {
    BuildFailedCommand(String buildName) {
      super(new AntMessage(MessageType.ERROR, 0, AntBundle.message("cannot.start.build.name.error.message", buildName), null, 0, 0));
    }

    void execute(AntOutputView outputView) {
      outputView.buildFailed(getMessage());
    }
  }

  private static final class FinishBuildCommand extends LogCommand {
    private final String myFinishStatusText;

    FinishBuildCommand(String finishStatusText) {
      super(0);
      myFinishStatusText = finishStatusText;
    }

    void execute(AntOutputView outputView) {
      outputView.finishBuild(myFinishStatusText);
    }
  }

  private static final class StartTargetCommand extends MessageCommand {
    StartTargetCommand(String targetName) {
      super(new AntMessage(MessageType.TARGET, 0, targetName, null, 0, 0));
    }

    void execute(AntOutputView outputView) {
      outputView.startTarget(getMessage());
    }
  }

  private static final class FinishTargetCommand extends LogCommand {
    FinishTargetCommand() {
      super(0);
    }

    void execute(AntOutputView outputView) {
      outputView.finishTarget();
    }
  }

  private static final class StartTaskCommand extends MessageCommand {
    StartTaskCommand(String taskName) {
      super(new AntMessage(MessageType.TASK, 0, taskName, null, 0, 0));
    }

    void execute(AntOutputView outputView) {
      outputView.startTask(getMessage());
    }
  }

  private static final class FinishTaskCommand extends LogCommand {
    FinishTaskCommand() {
      super(0);
    }

    public void execute(AntOutputView outputView) {
      outputView.finishTask();
    }
  }

  private static final class AddMessageCommand extends MessageCommand {
    AddMessageCommand(AntMessage antMessage) {
      super(antMessage);
    }

    void execute(AntOutputView outputView) {
      outputView.addMessage(getMessage());
    }
  }

  private final class AddExceptionCommand extends MessageCommand {
    AddExceptionCommand(AntMessage antMessage) {
      super(antMessage);
    }

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

    void execute(AntOutputView outputView) {
      outputView.addJavacMessage(getMessage(), myUrl);
    }
  }

  public String[] getTargets() {
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

    dispatcher.processOutput(new Printable() {
      @Override
      public void printOn(Printer printer) {
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
      }
    });
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(() -> {
      if (!myProject.isDisposed()) {
        DumbService.getInstance(myProject).runWhenSmart(() -> {
          if (!myIsOutputPaused) {
            new OutputFlusher().doFlush();
          }
        });
      }
    });
  }

  private String getFinishStatusText(boolean isAborted, long buildTimeInMilliseconds) {
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

    protected final void proceedOneCommand(LogCommand command) {
      if (command.getPriority() > myPriorityThreshold) {
        return;
      }
      // proceed messages in a special way
      if (command instanceof AddMessageCommand) {
        AddMessageCommand addMessageCommand = (AddMessageCommand)command;
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

    protected final void flushDelayedMessages() {
      if (!myDelayedMessages.isEmpty()) {
        final AntMessage[] messages = myDelayedMessages.toArray(new AntMessage[myDelayedMessages.size()]);
        myDelayedMessages.clear();
        myTreeView.addMessages(messages);
        myPlainTextView.addMessages(messages);
      }
    }
  }

  public String getNextOccurenceActionName() {
    return myTreeView.getNextOccurenceActionName();
  }

  public String getPreviousOccurenceActionName() {
    return myTreeView.getPreviousOccurenceActionName();
  }

  public OccurenceInfo goNextOccurence() {
    return isTreeView() ? myTreeView.goNextOccurence() : null;
  }

  public OccurenceInfo goPreviousOccurence() {
    return isTreeView() ? myTreeView.goPreviousOccurence() : null;
  }

  public boolean hasNextOccurence() {
    return isTreeView() && myTreeView.hasNextOccurence();
  }

  public boolean hasPreviousOccurence() {
    return isTreeView() && myTreeView.hasPreviousOccurence();
  }

  public void setBuildCommandLine(String commandLine) {
    myPlainTextView.setBuildCommandLine(commandLine);

  }
}

package com.intellij.lang.ant.config.execution;

import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.OccurenceNavigator;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.actions.CloseTabToolbarAction;
import com.intellij.ide.actions.CommonActionsFactory;
import com.intellij.ide.actions.NextOccurenceToolbarAction;
import com.intellij.ide.actions.PreviousOccurenceToolbarAction;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntBuildFileBase;
import com.intellij.lang.ant.config.AntBuildListener;
import com.intellij.lang.ant.config.actions.*;
import com.intellij.lang.ant.config.impl.AntBuildFileImpl;
import com.intellij.lang.ant.config.impl.HelpID;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.peer.PeerFactory;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiFile;
import com.intellij.rt.ant.execution.AntMain2;
import com.intellij.ui.content.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;

public final class AntBuildMessageView extends JPanel implements DataProvider, OccurenceNavigator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ant.execution.AntBuildMessageView");

  public enum MessageType {
    BUILD,
    TARGET,
    TASK,
    MESSAGE,
    ERROR, }

  private static final Key<AntBuildMessageView> KEY = Key.create("BuildMessageView.KEY");
  private static final String BUILD_CONTENT_NAME = AntBundle.message("ant.build.tab.content.title");

  private OutputParser myParsingThread;
  private final Project myProject;
  private final JPanel myMessagePanel;
  private final AntBuildFileBase myBuildFile;
  private final String[] myTargets;
  private static final int VERBOSE_MODE = AntMain2.MSG_VERBOSE;
  private static final int BRIEF_MODE = AntMain2.MSG_VERBOSE - 1;
  private int myPriorityThreshold = BRIEF_MODE;
  private int myErrorCount;
  private int myWarningCount;
  private boolean myIsOutputPaused = false;

  private AntOutputView myCurrentView;

  private final PlainTextView myPlainTextView;
  private final TreeView myTreeView;

  private final ArrayList<LogCommand> myLog = new ArrayList<LogCommand>(1024);
  private int myCommandsProcessedCount = 0;

  private JLabel myProgressStatisticsLabel;
  private JLabel myProgressTextLabel;
  private JPanel myProgressPanel;

  private final Timer myScrollerTimer = new Timer(1000, new ActionListener() {
    public void actionPerformed(ActionEvent e) {
      if (myTreeView != null && myCommandsProcessedCount < myLog.size()) {
        if (!myIsOutputPaused) {
          new OutputFlusher().doFlush();
          myTreeView.scrollToLastMessage();
        }
      }
    }
  });
  private boolean myIsAborted;
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

  private AntBuildMessageView(Project project, AntBuildFileBase buildFile, String[] targets) {
    super(new BorderLayout(2, 0));
    myProject = project;
    setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

    myPlainTextView = new PlainTextView(project);
    myTreeView = new TreeView(project, buildFile);

    myMessagePanel = new JPanel(new BorderLayout());
    myBuildFile = buildFile;
    myTargets = targets;

    showAntView(AntBuildFileImpl.TREE_VIEW.value(buildFile.getAllOptions()));
    setVerboseMode(AntBuildFileImpl.VERBOSE.value(buildFile.getAllOptions()));

    add(createToolbarPanel(), BorderLayout.WEST);
    add(myMessagePanel, BorderLayout.CENTER);
  }

  public void changeView() {
    showAntView(!isTreeView());
    myBuildFile.setTreeView(isTreeView());
  }

  private boolean isTreeView() {
    return myCurrentView == myTreeView;
  }

  public void setVerboseMode(boolean verbose) {
    changeDetalizationLevel(verbose ? VERBOSE_MODE : BRIEF_MODE);
    myBuildFile.setVerboseMode(verbose);
  }

  public boolean isVerboseMode() {
    return myPriorityThreshold == VERBOSE_MODE;
  }

  private synchronized void changeDetalizationLevel(int priorityThreshold) {
    myPriorityThreshold = priorityThreshold;

    TreeView.TreeSelection selection = myTreeView.getSelection();
    myTreeView.clearAllMessages();
    myPlainTextView.clearAllMessages();
    myTreeView.setActionsEnabled(false);

    new OutputFlusher() {
      public void doFlush() {
        for (int i = 0; i < /*myLog.size()*/ myCommandsProcessedCount; i++) {
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
    AntOutputView oldView = getOutputView(treeView);
    AntOutputView newView = getOutputView(!treeView);
    myCurrentView = newView;
    myMessagePanel.remove(oldView.getComponent());
    myMessagePanel.add(newView.getComponent(), BorderLayout.CENTER);
    myMessagePanel.validate();

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
  public static AntBuildMessageView openBuildMessageView(Project project, AntBuildFileBase buildFile, String[] targets) {
    final PsiFile antFile = buildFile.getAntFile();
    if (!LOG.assertTrue(antFile != null)) {
      return null;
    }

    // check if there are running instances of the same build file

    MessageView ijMessageView = project.getComponent(MessageView.class);
    Content[] contents = ijMessageView.getContents();
    for (Content content : contents) {
      if (content.isPinned()) {
        continue;
      }
      AntBuildMessageView buildMessageView = content.getUserData(KEY);
      if (buildMessageView == null) {
        continue;
      }

      if (!antFile.equals(buildMessageView.getBuildFile().getAntFile())) {
        continue;
      }

      if (buildMessageView.isStopped()) {
        ijMessageView.removeContent(content);
        continue;
      }

      int result = Messages.showYesNoCancelDialog(AntBundle.message("ant.is.active.terminate.confirmation.text"),
                                                  AntBundle.message("starting.ant.build.dialog.title"), Messages.getQuestionIcon());

      switch (result) {
        case 0:  // yes
          buildMessageView.stopProcess();
          ijMessageView.removeContent(content);
          continue;
        case 1: // no
          continue;
        default: // cancel
          return null;
      }
    }

    final AntBuildMessageView messageView = new AntBuildMessageView(project, buildFile, targets);
    String contentName = buildFile.getPresentableName();
    contentName = BUILD_CONTENT_NAME + " (" + contentName + ")";

    final Content content = PeerFactory.getInstance().getContentFactory().createContent(messageView.getComponent(), contentName, true);
    content.putUserData(KEY, messageView);
    ijMessageView.addContent(content);
    ijMessageView.setSelectedContent(content);

    new CloseListener(content, ijMessageView, project);
    // Do not inline next two variabled. Seeking for NPE.
    ToolWindow messageToolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.MESSAGES_WINDOW);
    messageToolWindow.activate(null);
    return messageView;
  }

  public void setProgressStatistics(final String s) {
    initProgressPanel();
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        myProgressStatisticsLabel.setText(s);
      }
    });
  }

  public void setProgressText(final String s) {
    initProgressPanel();
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        myProgressTextLabel.setText(s);
      }
    });
  }

  private void initProgressPanel() {
    if (myProgressPanel == null) {
      myProgressPanel = new JPanel(new GridLayout(1, 2));
      myProgressStatisticsLabel = new JLabel();
      myProgressPanel.add(myProgressStatisticsLabel);
      myProgressTextLabel = new JLabel();
      myProgressPanel.add(myProgressTextLabel);
      myMessagePanel.add(myProgressPanel, BorderLayout.SOUTH);
      // fix of 9377
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          myMessagePanel.validate();
        }
      });
    }
  }

  public void removeProgressPanel() {
    if (myProgressPanel != null) {
      myMessagePanel.remove(myProgressPanel);
      // fix of 9377
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          myMessagePanel.validate();
        }
      });
      myProgressPanel = null;
    }
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
    MessageView messageView = myProject.getComponent(MessageView.class);
    Content[] contents = messageView.getContents();
    for (Content content : contents) {
      if (content.getComponent() == this) {
        messageView.removeContent(content);
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
    leftActionGroup.add(CommonActionsFactory.getCommonActionsFactory().createContextHelpAction(HelpID.ANT));

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
    myLog.add(command);
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

  public void outputMessage(String message, int priority) {
    updateErrorAndWarningCounters(priority);
    addMessage(MessageType.MESSAGE, priority, message, null, 0, 0);
  }

  public void outputError(String error, int priority) {
    //updateErrorAndWarningCounters(priority);
    AntMessage message = createErrorMessage(MessageType.ERROR, priority, error);
    addMessage(MessageType.ERROR, priority, error, message.getFile(), message.getLine(), message.getColumn());
    WolfTheProblemSolver wolf = WolfTheProblemSolver.getInstance(myProject);
    wolf.weHaveGotProblem(wolf.convertToProblem(message.getFile(), message.getLine(), message.getColumn(), message.getTextLines()));
  }

  public void outputException(String exception) {
    updateErrorAndWarningCounters(0);
    AntMessage message = createErrorMessage(MessageType.ERROR, 0, exception);
    addCommand(new AddExceptionCommand(message));
    WolfTheProblemSolver wolf = WolfTheProblemSolver.getInstance(myProject);
    wolf.weHaveGotProblem(wolf.convertToProblem(message.getFile(), message.getLine(), message.getColumn(), message.getTextLines()));
  }


  private void updateErrorAndWarningCounters(int priority) {
    if (priority == AntMain2.MSG_ERR) {
      myErrorCount++;
    }
    else if (priority == AntMain2.MSG_WARN) {
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
    if (data != null) return data;
    if (DataConstants.HELP_ID.equals(dataId)) {
      return HelpID.ANT;
    }
    else if (DataConstantsEx.TREE_EXPANDER.equals(dataId)) {
      return myTreeExpander;
    }
    return null;
  }

  private static AntMessage createErrorMessage(MessageType type, int priority, String text) {
    if (text.startsWith(FILE_PREFIX)) {
      text = text.substring(FILE_PREFIX.length());
    }

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

          return new AntMessage(type, priority, text, result, line, 1);
        }
        catch (NumberFormatException e) {
        }
      }
    }

    return new AntMessage(type, priority, text, null, 0, 0);
  }

  private void addMessage(MessageType type, int priority, String text, VirtualFile file, int line, int column) {
    AntMessage message = new AntMessage(type, priority, text, file, line, column);
    addCommand(new AddMessageCommand(message));
  }

  public void outputJavacMessage(MessageType type, String[] text, VirtualFile file, String url, int line, int column) {
    int priority = type == MessageType.ERROR ? AntMain2.MSG_ERR : AntMain2.MSG_VERBOSE;
    updateErrorAndWarningCounters(priority);
    AntMessage message = new AntMessage(type, priority, text, file, line, column);
    addCommand(new AddJavacMessageCommand(message, url));
    if (type == MessageType.ERROR) {
      WolfTheProblemSolver wolf = WolfTheProblemSolver.getInstance(myProject);
      wolf.weHaveGotProblem(wolf.convertToProblem(file, line, column, text));
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

    public CloseListener(Content content, ContentManager contentManager, Project project) {
      myContent = content;
      myContentManager = contentManager;
      myProject = project;
      contentManager.addContentManagerListener(this);
      ProjectManager.getInstance().addProjectManagerListener(myProject, this);
    }

    public void contentRemoved(ContentManagerEvent event) {
      if (event.getContent() == myContent) {
        AntBuildMessageView buildMessageView = myContent.getUserData(KEY);
        if (!myCloseAllowed) buildMessageView.stopProcess();
        myContentManager.removeContentManagerListener(this);
        ProjectManager.getInstance().removeProjectManagerListener(myProject, this);
        myContent.release();
        myContent = null;
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

    public void projectOpened(Project project) {
    }

    public void projectClosed(Project project) {
      if (myContent != null) {
        myContentManager.removeContent(myContent);
      }
    }

    public void projectClosing(Project project) {
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

      AntBuildMessageView messageView = myContent.getUserData(KEY);

      if (messageView.isStoppedOrTerminateRequested()) {
        return true;
      }

      if (myCloseAllowed) return true;

      int result = Messages.showYesNoCancelDialog(AntBundle.message("ant.process.is.active.terminate.confirmation.text"),
                                                  AntBundle.message("close.ant.build.messages.dialog.title"), Messages.getQuestionIcon());
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

  abstract static class LogCommand {
    private final int myPriority;

    LogCommand(int priority) {
      myPriority = priority;
    }

    final int getPriority() {
      return myPriority;
    }

    abstract void execute(AntOutputView outputView);
  }

  static final class StartBuildCommand extends LogCommand {
    private final AntMessage myMessage;

    StartBuildCommand(String buildName) {
      super(0);
      myMessage = new AntMessage(MessageType.BUILD, 0, buildName, null, 0, 0);
    }

    void execute(AntOutputView outputView) {
      outputView.startBuild(myMessage);
    }
  }

  static final class BuildFailedCommand extends LogCommand {
    private final AntMessage myMessage;

    BuildFailedCommand(String buildName) {
      super(0);
      myMessage = new AntMessage(MessageType.ERROR, 0, AntBundle.message("cannot.start.build.name.error.message", buildName), null, 0, 0);
    }

    void execute(AntOutputView outputView) {
      outputView.buildFailed(myMessage);
    }
  }

  static final class FinishBuildCommand extends LogCommand {
    private final String myFinishStatusText;

    FinishBuildCommand(String finishStatusText) {
      super(0);
      myFinishStatusText = finishStatusText;
    }

    void execute(AntOutputView outputView) {
      outputView.finishBuild(myFinishStatusText);
    }
  }

  static final class StartTargetCommand extends LogCommand {
    private final AntMessage myMessage;

    StartTargetCommand(String targetName) {
      super(0);
      myMessage = new AntMessage(MessageType.TARGET, 0, targetName, null, 0, 0);
    }

    void execute(AntOutputView outputView) {
      outputView.startTarget(myMessage);
    }
  }

  static final class FinishTargetCommand extends LogCommand {
    FinishTargetCommand() {
      super(0);
    }

    void execute(AntOutputView outputView) {
      outputView.finishTarget();
    }
  }


  static final class StartTaskCommand extends LogCommand {
    private final AntMessage myMessage;

    StartTaskCommand(String taskName) {
      super(0);
      myMessage = new AntMessage(MessageType.TASK, 0, taskName, null, 0, 0);
    }

    void execute(AntOutputView outputView) {
      outputView.startTask(myMessage);
    }
  }

  static final class FinishTaskCommand extends LogCommand {
    FinishTaskCommand() {
      super(0);
    }

    public void execute(AntOutputView outputView) {
      outputView.finishTask();
    }
  }


  static final class AddMessageCommand extends LogCommand {
    protected final AntMessage myAntMessage;

    AddMessageCommand(AntMessage antMessage) {
      super(antMessage.getPriority());
      myAntMessage = antMessage;
    }

    void execute(AntOutputView outputView) {
      outputView.addMessage(myAntMessage);
    }
  }

  final class AddExceptionCommand extends LogCommand {
    private final AntMessage myAntMessage;

    AddExceptionCommand(AntMessage antMessage) {
      super(antMessage.getPriority());
      myAntMessage = antMessage;
    }

    void execute(AntOutputView outputView) {
      outputView.addException(myAntMessage, isVerboseMode());
    }
  }

  static final class AddJavacMessageCommand extends LogCommand {
    private final String myUrl;
    private final AntMessage myAntMessage;

    public AddJavacMessageCommand(AntMessage antMessage, String url) {
      super(antMessage.getPriority());
      myAntMessage = antMessage;
      myUrl = url;
    }

    void execute(AntOutputView outputView) {
      outputView.addJavacMessage(myAntMessage, myUrl);
    }
  }

  public String[] getTargets() {
    return myTargets;
  }

  private int getErrorCount() {
    return myErrorCount;
  }

  private int getWarningCount() {
    return myWarningCount;
  }

  void startScrollerThread() {
    myScrollerTimer.start();
  }

  void stopScrollerThread() {
    myScrollerTimer.stop();
  }

  void buildFinished(boolean isProgressAborted, long buildTimeInMilliseconds, final AntBuildListener antBuildListener) {
    LOG.assertTrue(antBuildListener != null);
    final boolean aborted = isProgressAborted || myIsAborted;
    String message = getFinishStatusText(aborted, buildTimeInMilliseconds);
    WindowManager.getInstance().getStatusBar(myProject).setInfo(message);
    addCommand(new FinishBuildCommand(message));
    if (!myIsOutputPaused) {
      new OutputFlusher().doFlush();
      myTreeView.scrollToLastMessage();
    }
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (getErrorCount() == 0 && myBuildFile.isViewClosedWhenNoErrors()) {
          close();
        }
        else if (getErrorCount() > 0) {
          myTreeView.scrollToFirstError();
        }
        else {
          myTreeView.scrollToStatus();
        }

        VirtualFileManager.getInstance().refresh(true, new Runnable() {
          public void run() {
            antBuildListener.buildFinished(aborted ? AntBuildListener.ABORTED : AntBuildListener.FINISHED_SUCCESSFULLY, getErrorCount());
          }
        });
      }
    });
  }

  private String getFinishStatusText(boolean isAborted, long buildTimeInMilliseconds) {
    int errors = getErrorCount();
    int warnings = getWarningCount();
    final String theDateAsString = DateFormat.getTimeInstance().format(new Date());

    long buildTimeInSeconds = buildTimeInMilliseconds / 1000;

    if (isAborted) {
      return AntBundle.message("build.finished.status.ant.build.aborted", buildTimeInSeconds, theDateAsString);
    }
    else if (errors == 0 && warnings == 0) {
      return AntBundle.message("build.finished.status.ant.build.completed.successfully", buildTimeInSeconds, theDateAsString);
    }
    else if (errors == 0) {
      return AntBundle
        .message("build.finished.status.ant.build.completed.with.warnings", warnings, buildTimeInSeconds, theDateAsString);
    }
    else {
      return AntBundle
        .message("build.finished.status.ant.build.completed.with.errors.warnings", errors, warnings, buildTimeInSeconds, theDateAsString);
    }
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
    private final ArrayList<AntMessage> myDelayedMessages = new ArrayList<AntMessage>();

    public void doFlush() {
      for (; myCommandsProcessedCount < myLog.size(); myCommandsProcessedCount++) {
        LogCommand command = myLog.get(myCommandsProcessedCount);
        proceedOneCommand(command);
      }
      flushDelayedMessages();
    }

    protected final void proceedOneCommand(LogCommand command) {
      if (command.getPriority() > myPriorityThreshold) return;
      // proceed messages in a special way
      if (command instanceof AddMessageCommand) {
        AddMessageCommand addMessageCommand = (AddMessageCommand)command;
        myDelayedMessages.add(addMessageCommand.myAntMessage);
      }
      else {
        flushDelayedMessages(); // message type changed -> flush
        command.execute(myTreeView);
        command.execute(myPlainTextView);
      }
    }

    protected final void flushDelayedMessages() {
      if (myDelayedMessages.size() > 0) {
        AntMessage[] messages = myDelayedMessages.toArray(new AntMessage[myDelayedMessages.size()]);
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

  public OccurenceNavigator.OccurenceInfo goNextOccurence() {
    return isTreeView() ? myTreeView.goNextOccurence() : null;
  }

  public OccurenceNavigator.OccurenceInfo goPreviousOccurence() {
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

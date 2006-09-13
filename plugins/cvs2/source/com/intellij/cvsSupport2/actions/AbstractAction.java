package com.intellij.cvsSupport2.actions;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContextWrapper;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutor;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutorCallback;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.cvshandlers.FileSetToBeUpdated;
import com.intellij.cvsSupport2.ui.CvsTabbedWindow;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.localVcs.LocalVcs;
import com.intellij.openapi.localVcs.LvcsAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;


public abstract class AbstractAction extends AnAction {
  protected static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.actions.AbstractAction");
  private LvcsAction myLvcsAction = LvcsAction.EMPTY;
  private final boolean myStartLvcsAction;

  public AbstractAction(boolean startLvcsAction) {
    myStartLvcsAction = startLvcsAction;
  }

  public AbstractAction(boolean startLvcsAction, String name, Icon icon) {
    super(name, null, icon);
    myStartLvcsAction = startLvcsAction;
  }

  protected void beforeActionPerformed(VcsContext context) {

  }

  public void actionPerformed(AnActionEvent e) {
    actionPerformed(CvsContextWrapper.createCachedInstance(e));
  }

  private static LocalVcs getLvcs(Project project) {
    if (project == null) return null;
    return LocalVcs.getInstance(project);
  }

  protected abstract String getTitle(VcsContext context);

  protected abstract CvsHandler getCvsHandler(CvsContext context);

  public void actionPerformed(final CvsContext context) {
    Runnable beforeAction = new Runnable() {
      public void run() {
        beforeActionPerformed(context);
      }
    };

    Runnable afterAction = new Runnable() {
      public void run() {
        CvsHandler handler;

        synchronized (AbstractAction.class) {
          try {
            handler = getCvsHandler(context);
          }
          catch (Exception ex) {
            LOG.error(ex);
            handler = CvsHandler.NULL;
          }
        }

        LOG.assertTrue(handler != null);

        actionPerformed(context, handler);

      }
    };

    if (ProgressManager.getInstance().getProgressIndicator() != null) {
      beforeAction.run();
      afterAction.run();
    }
    else {
      if (ProgressManager.getInstance().runProcessWithProgressSynchronously(beforeAction, getTitle(context), true, context.getProject())) {
        afterAction.run();
      }

    }


  }

  public void actionPerformed(final CvsContext context, CvsHandler handler) {
    start(context);

    final Project project = context.getProject();

    try {
      performAction(project, handler, context);
    }
    catch (Exception e1) {
      LOG.error(e1);
    }
  }

  private void startAction(VcsContext context) {
    if (!myStartLvcsAction) return;
    final Project project = context.getProject();
    if (project == null) return;
    LocalVcs lvcs = getLvcs(project);
    if (lvcs != null && getTitle(context) != null) {

      synchronized (lvcs) {
        myLvcsAction = lvcs.startAction(CvsBundle.getCvsDisplayName() + ": " + getTitle(context), "", true);
      }
    }
  }

  protected static void start(VcsContext context) {
    final Project project = context.getProject();
    if (project != null) {

      if (ApplicationManager.getApplication().isDispatchThread()) {
        ApplicationManager.getApplication().saveAll();
      }
    }
  }

  protected void performAction(final Project project, final CvsHandler handler, final CvsContext context) {
    final CvsOperationExecutor executor = new CvsOperationExecutor(project);
    executor.performActionSync(handler, new MyCvsOperationExecutorCallback(context, handler, executor));
  }


  protected void onActionPerformed(CvsContext context,
                                   CvsTabbedWindow tabbedWindow,
                                   boolean successfully,
                                   CvsHandler handler) {
    if (handler == CvsHandler.NULL) return;
    Refreshable refreshablePanel = context.getRefreshableDialog();
    if (refreshablePanel != null) {
      refreshablePanel.refresh();
    }

    refreshFileView(context.getProject());
  }

  protected static void refreshFileView(Project project) {
    if (project == null) return;
    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);

    ToolWindow fileViewToolWindow = toolWindowManager
      .getToolWindow(ProjectLevelVcsManager.FILE_VIEW_TOOL_WINDOW_ID);
    if (fileViewToolWindow == null) return;
    if (!fileViewToolWindow.isAvailable()) return;
    ((Refreshable)fileViewToolWindow.getComponent()).refresh();
  }

  protected void endAction() {
    myLvcsAction.finish();
    myLvcsAction = LvcsAction.EMPTY;
  }

  protected static void adjustName(boolean showDialogOptions, AnActionEvent e) {
    boolean actualShow = showDialogOptions || shiftPressed(e);
    Presentation presentation = e.getPresentation();
    String itemText = e.getPresentation().getText();
    if (itemText == null) return;
    if (itemText.endsWith("...")) {
      if (actualShow) return;
      presentation.setText(itemText.substring(0, itemText.length() - 3));
    }
    else {
      if (!actualShow) return;
      presentation.setText(itemText + "...");
    }
  }

  private static boolean shiftPressed(AnActionEvent e) {
    InputEvent inputEvent = e.getInputEvent();
    return inputEvent != null && (inputEvent.getModifiers() & Event.SHIFT_MASK) != 0;
  }

  private class MyCvsOperationExecutorCallback implements CvsOperationExecutorCallback {
    private final CvsContext myContext;
    private final CvsHandler myHandler;
    private final CvsOperationExecutor myExecutor;

    public MyCvsOperationExecutorCallback(CvsContext context, CvsHandler handler, CvsOperationExecutor executor) {
      myContext = context;
      myHandler = handler;
      myExecutor = executor;
    }

    public void executeInProgressAfterAction(ModalityContext modalityContext) {
      startAction(myContext);
      FileSetToBeUpdated files = myHandler.getFiles();

      files.refreshFilesAsync(new Runnable() {
        public void run() {
          endAction();
        }
      });
    }

    public void executionFinished(boolean successfully) {
      CvsTabbedWindow tabbedWindow = myExecutor.openTabbedWindow(myHandler);
      onActionPerformed(myContext, tabbedWindow, successfully, myHandler);
    }


    public void executionFinishedSuccessfully() {
    }
  }
}

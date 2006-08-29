package com.intellij.cvsSupport2.cvsExecution;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsResultEx;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.config.ui.ConfigureCvsGlobalSettingsDialog;
import com.intellij.cvsSupport2.config.ui.CvsConfigurationsListEditor;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.errorHandling.CvsException;
import com.intellij.cvsSupport2.ui.CvsTabbedWindow;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.cvsIntegration.CvsResult;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.errorView.ContentManagerProvider;
import com.intellij.util.ui.ErrorTreeView;
import com.intellij.util.ui.MessageCategory;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * author: lesya
 */
public class CvsOperationExecutor {

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutor");

  private final CvsResultEx myResult = new CvsResultEx();

  private final boolean myShowProgress;
  private final Project myProject;
  private final ModalityContext myExecutor;
  private boolean myShowErrors = true;
  private boolean myIsQuietOperation = false;

  public CvsOperationExecutor(boolean showProgress, Project project, ModalityState modalityState) {
    myProject = project;
    myShowProgress = showProgress;
    myExecutor = new ModalityContextImpl(modalityState, false);
  }

  public CvsOperationExecutor(boolean showProgress, Project project, ModalityContext modalityContext) {
    myProject = project;
    myShowProgress = showProgress;
    myExecutor = modalityContext;
  }

  public CvsOperationExecutor(Project project) {
    this(true, project, ModalityState.defaultModalityState());
  }

  public CvsOperationExecutor(Project project, ModalityState modalityState) {
    this(true, project, modalityState);
  }

  public void performActionSync(final CvsHandler handler,
                                final CvsOperationExecutorCallback callback) {

    final CvsTabbedWindow tabbedWindow = myIsQuietOperation ? null : openTabbedWindow(handler);

    final Runnable finish = new Runnable() {
      public void run() {
        try {
          myResult.addAllErrors(handler.getErrors());
          myResult.addAllWarnings(handler.getWarnings());
          handler.runComplitingActivities();
          if (myProject != null) {
            showErrors(handler.getErrors(), handler.getWarnings(), tabbedWindow);
          }
        }
        finally {
          try {
            if (myResult.finishedUnsuccessfully(true, handler)) {
              callback.executionFinished(false);
            }
            else {
              if (handler.getErrors().isEmpty()) callback.executionFinishedSuccessfully();
              callback.executionFinished(true);
            }
          }
          finally {

            if ((myProject != null) && (handler != CvsHandler.NULL)) {
              StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
              if (statusBar != null) {
                statusBar.setInfo(getStatusMessage(handler));
              }
            }
          }
        }

      }
    };

    Runnable cvsAction = new Runnable() {
      public void run() {
        try {
          if (handler == CvsHandler.NULL) return;
          setText(CvsBundle.message("progress.text.preparing.for.login"));

          handler.beforeLogin();


          if (myResult.finishedUnsuccessfully(false, handler)) return;

          setText(CvsBundle.message("progress.text.login"));
          setCancelText(handler);

          login(handler);
          if (myResult.finishedUnsuccessfully(true, handler)) return;

          setText(CvsBundle.message("progress.text.preparing.for.action", handler.getTitle()));

          handler.run(myExecutor);
          if (myResult.finishedUnsuccessfully(true, handler)) return;

        }
        catch (ProcessCanceledException ex) {
          myResult.setIsCanceled();
        }
        finally {
          callback.executeInProgressAfterAction(myExecutor);
        }
      }
    };

    if (doNotShowProgress()) {
      cvsAction.run();
      myExecutor.runInDispatchThread(finish);
    }
    else {
      if (ProgressManager.getInstance().runProcessWithProgressSynchronously(cvsAction, handler.getTitle(), handler.canBeCanceled(), myProject)) {
        finish.run();
      }

    }
  }

  private void login(final CvsHandler handler) {
    myExecutor.runInDispatchThread(new Runnable() {
      public void run() {
        try {
          if (handler.login(myExecutor)) {
            myResult.setIsLoggedIn();
          }
        }
        catch (ProcessCanceledException ex) {
          myResult.setIsCanceled();
        }
        catch (CvsException ex) {
          Set<VcsException> vcsExceptions = Collections.singleton((VcsException)ex);
          myResult.addAllErrors(vcsExceptions);
          showErrors(new ArrayList<VcsException>(vcsExceptions),
                     openTabbedWindow(handler));

        }
        catch (Exception e) {
          Set<VcsException> vcsExceptions = Collections.singleton(new VcsException(e));
          myResult.addAllErrors(vcsExceptions);
          showErrors(new ArrayList<VcsException>(vcsExceptions),
                     openTabbedWindow(handler));
        }

      }
    });
  }

  private static void setText(String text) {
    ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    if (progressIndicator != null) {
      progressIndicator.setText(text);
    }
  }

  private static void setCancelText(final CvsHandler handler) {
    ProgressManager progressManager = ProgressManager.getInstance();
    String cancelButtonText = handler.getCancelButtonText();
    if (cancelButtonText != null) {
      progressManager.setCancelButtonText(cancelButtonText);
    }
  }

  private boolean doNotShowProgress() {
    return isInProgress() || isInTestMode() || !myShowProgress || !ApplicationManager.getApplication().isDispatchThread();
  }

  private static boolean isInTestMode() {
    return ApplicationManager.getApplication().isUnitTestMode();
  }

  private static boolean isInProgress() {
    return (ProgressManager.getInstance().getProgressIndicator() != null);
  }

  protected void showErrors(final List errors, CvsTabbedWindow tabbedWindow) {
    showErrors(errors, new ArrayList(), tabbedWindow);
  }

  protected void showErrors(final List errors,
                            final List warnings,
                            final CvsTabbedWindow tabbedWindow) {
    if (!myShowErrors || myIsQuietOperation) return;
    if (tabbedWindow == null) return;
    if (errors.isEmpty() && warnings.isEmpty()) {
      tabbedWindow.hideErrors();
    }
    else {
      ErrorTreeView errorTreeView = tabbedWindow.addErrorsTreeView(PeerFactory.getInstance().getErrorViewFactory()
                                                                   .createErrorTreeView(myProject,
                                                                                        null,
                                                                                        true,
                                                                                        new AnAction[]{
                                                                                          (DefaultActionGroup)ActionManager.getInstance()
                                                                                                              .getAction("CvsActions")},
                                                                                        new AnAction[]{new AnAction(
                                                                                          CvsBundle.message(
                                                                                            "configure.global.cvs.settings.action.name"), null,
                                                                                          IconLoader.getIcon("/nodes/cvs_global.png")) {
                                                                                          public void actionPerformed(AnActionEvent e) {
                                                                                            new ConfigureCvsGlobalSettingsDialog().show();
                                                                                          }
                                                                                        }, new ReconfigureCvsRootAction()
                                                                                        },
                                                                                        new ContentManagerProvider() {
                                                                                          public ContentManager getParentContent() {
                                                                                            return tabbedWindow.getContentManager();
                                                                                          }
                                                                                        }));
      fillErrors(errors, warnings, errorTreeView);
      tabbedWindow.ensureVisible(myProject);
    }
  }

  private void fillErrors(final List errors, final List warnings, final ErrorTreeView errorTreeView) {
    for (Iterator i = errors.iterator(); i.hasNext();) {
      VcsException exception = (VcsException)i.next();
      errorTreeView.addMessage(MessageCategory.ERROR, exception.getMessages(), exception.getVirtualFile(), -1, -1, exception);
    }

    for (Iterator i = warnings.iterator(); i.hasNext();) {
      VcsException exception = (VcsException)i.next();
      errorTreeView.addMessage(MessageCategory.WARNING, exception.getMessages(), exception.getVirtualFile(), -1, -1, exception);
    }

  }

  @NotNull private static Editor createView(Project project) {
    EditorFactory editorFactory = EditorFactory.getInstance();
    Document document = editorFactory.createDocument("");
    Editor result = editorFactory.createViewer(document, project);

    EditorSettings editorSettings = result.getSettings();
    editorSettings.setLineMarkerAreaShown(false);
    editorSettings.setLineNumbersShown(false);
    editorSettings.setFoldingOutlineShown(false);
    return result;
  }

  private static String getStatusMessage(final CvsHandler handler) {
    final String actionName = handler.getTitle();
    if (handler.getErrors().isEmpty()) {
      return CvsBundle.message("status.text.action.completed", actionName);
    } else {
      return CvsBundle.message("status.text.action.completed.with.errors", actionName);
    }
  }


  public CvsTabbedWindow openTabbedWindow(final CvsHandler output) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return null;
    if (myProject != null) {
      final CvsTabbedWindow tabbedWindow = CvsTabbedWindow.getInstance(myProject);
      if (CvsConfiguration.getInstance(myProject).SHOW_OUTPUT && !myIsQuietOperation) {
        if (ApplicationManager.getApplication().isDispatchThread()) {
          connectToOutput(output, tabbedWindow);
        } else {
          ApplicationManager.getApplication().invokeAndWait(new Runnable() {
            public void run() {
              connectToOutput(output, tabbedWindow);
            }
          }, ModalityState.defaultModalityState());
        }
      }
      return tabbedWindow;
    }
    return null;
  }

  private void connectToOutput(CvsHandler output, CvsTabbedWindow tabbedWindow) {
    Editor editor = tabbedWindow.getOutput();
    if (editor == null) {
      output.connectToOutputView(tabbedWindow.addOutput(createView(myProject)), myProject);
    } else {
      output.connectToOutputView(editor, myProject);
    }
  }

  public VcsException getFirstError() {
    return myResult.composeError();
  }

  public boolean isLoggedIn() {
    return myResult.isLoggedIn();
  }

  public boolean hasNoErrors() {
    return myResult.hasNoErrors();
  }

  public CvsResult getResult() {
    return myResult;
  }

  private class ReconfigureCvsRootAction extends AnAction {
    public ReconfigureCvsRootAction() {
      super(CvsBundle.message("action.name.reconfigure.cvs.root"), null, IconLoader.getIcon("/nodes/cvs_roots.png"));
    }

    public void update(AnActionEvent e) {
      super.update(e);
      Object data = e.getDataContext().getData(ErrorTreeView.CURRENT_EXCEPTION_DATA);
      e.getPresentation().setEnabled(data instanceof CvsException);

    }

    public void actionPerformed(AnActionEvent e) {
      Object data = e.getDataContext().getData(ErrorTreeView.CURRENT_EXCEPTION_DATA);
      CvsConfigurationsListEditor.reconfigureCvsRoot(((CvsException)data).getCvsRoot(), myProject);
    }
  }

  public void setShowErrors(boolean showErrors) {
    myShowErrors = showErrors;
  }

  public void setIsQuietOperation(final boolean isQuietOperation) {
    myIsQuietOperation = isQuietOperation;
  }
}

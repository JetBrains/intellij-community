package com.intellij.cvsSupport2.cvsExecution;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;

/**
 * author: lesya
 */
public class ModalityContext {
  private final ModalityState myDefaultModalityState;
  public static final ModalityContext NON_MODAL = new ModalityContext(ModalityState.NON_MMODAL, false);
  private final boolean myIsForTemporaryConfiguration;

  public ModalityContext(boolean forTemp) {
    this(ModalityState.current(), forTemp);
  }

  public ModalityContext(ModalityState defaultModalityState, boolean forTemp) {
    myDefaultModalityState = defaultModalityState;
    myIsForTemporaryConfiguration = forTemp;
  }

  public void runInDispatchThread(Runnable action) {
    Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode() || application.isDispatchThread()) {
      action.run();
    }
    else {
      ModalityState modalityState = getCurrentModalityState();
      application.invokeAndWait(action, modalityState);
    }
  }

  public ModalityState getCurrentModalityState() {
    ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    ModalityState modalityState = progressIndicator == null
                                  ? myDefaultModalityState
                                  : progressIndicator.getModalityState();
    if (modalityState == null) modalityState = ModalityState.defaultModalityState();
    return modalityState;
  }

  public void addRequest(Runnable action) {
    Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode()) {
      action.run();
    }
    else {
      application.invokeLater(action, getCurrentModalityState());
    }

  }

  public boolean isForTemporaryConfiguration(){
    return myIsForTemporaryConfiguration;
  }
}

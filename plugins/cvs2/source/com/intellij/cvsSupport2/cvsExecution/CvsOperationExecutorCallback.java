package com.intellij.cvsSupport2.cvsExecution;


/**
 * author: lesya
 */
public interface CvsOperationExecutorCallback {

  CvsOperationExecutorCallback EMPTY = new CvsOperationExecutorCallback(){
    public void executionFinished(boolean successfully) {
    }

    public void executionFinishedSuccessfully() {
    }

    public void executeInProgressAfterAction(ModalityContext modaityContext) {
    }
  };

  void executionFinished(boolean successfully);
  void executionFinishedSuccessfully();
  void executeInProgressAfterAction(ModalityContext modaityContext);
}

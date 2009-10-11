package com.intellij.cvsSupport2.cvsoperations.common;

import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.cvsoperations.cvsErrors.ErrorProcessor;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsMessagesListener;
import com.intellij.cvsSupport2.javacvsImpl.io.ReadWriteStatistics;
import org.netbeans.lib.cvsclient.ICvsCommandStopper;

/**
 * author: lesya
 */




public class CvsExecutionEnvironment {

  public final static ICvsCommandStopper DUMMY_STOPPER = new ICvsCommandStopper() {
    public boolean isAborted() {
      return false;
    }

    public boolean isAlive() {
      return true;
    }

    public void resetAlive() {
    }
  };

  private final CvsMessagesListener myListener;
  private final ICvsCommandStopper myCommandStopper;
  private final ErrorProcessor myErrorProcessor;
  private final ModalityContext myExecutor;
  private final PostCvsActivity myPostCvsActivity;
  private ReadWriteStatistics myReadWriteStatistics;

  public CvsExecutionEnvironment(CvsMessagesListener listener,
                                 ICvsCommandStopper commandStopper,
                                 ErrorProcessor errorProcessor,
                                 ModalityContext executor,
                                 PostCvsActivity postCvsActivity) {
    myListener = listener;
    myCommandStopper = commandStopper;
    myErrorProcessor = errorProcessor;
    myExecutor = executor;
    myPostCvsActivity = postCvsActivity;
  }

  public CvsMessagesListener getCvsMessagesListener() {
    return myListener;
  }

  public ICvsCommandStopper getCvsCommandStopper() {
    return myCommandStopper;
  }

  public ErrorProcessor getErrorProcessor() { return myErrorProcessor; }

  public ModalityContext getExecutor(){
    return myExecutor;
  }

  public PostCvsActivity getPostCvsActivity() {
    return myPostCvsActivity;
  }

  public ReadWriteStatistics getReadWriteStatistics() {
    if (myReadWriteStatistics == null){
      myReadWriteStatistics = new ReadWriteStatistics();
    }
    return myReadWriteStatistics;
  }
}

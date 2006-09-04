package com.intellij.cvsSupport2.cvsoperations.common;

import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.errorHandling.CannotFindCvsRootException;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.EnvironmentUtil;
import org.netbeans.lib.cvsclient.command.CommandAbortedException;
import org.netbeans.lib.cvsclient.command.GlobalOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

public abstract class CvsOperation {

  private final Collection<Runnable> myFinishActions = new ArrayList<Runnable>();

  public abstract void execute(CvsExecutionEnvironment executionEnvironment) throws VcsException, CommandAbortedException;

  public boolean login(ModalityContext executor) throws CannotFindCvsRootException {
    return login(new HashSet<CvsRootProvider>(), executor);
  }

  protected abstract boolean login(Collection<CvsRootProvider> processedCvsRoots, ModalityContext executor) throws CannotFindCvsRootException;

  public void addFinishAction(Runnable action) {
    myFinishActions.add(action);
  }

  public void executeFinishActions() {
    for (final Runnable myFinishAction : myFinishActions) {
      myFinishAction.run();
    }
  }

  protected void modifyOptions(GlobalOptions options) {
    options.setUseGzip(CvsApplicationLevelConfiguration.getInstance().USE_GZIP);
    if (CvsApplicationLevelConfiguration.getInstance().SEND_ENVIRONMENT_VARIABLES_TO_SERVER) {
      options.setEnvVariables(EnvironmentUtil.getEnviromentProperties());
    }
  }

  public int getFilesToProcessCount() {
    return CvsHandler.UNKNOWN_COUNT;
  }

  public static int calculateFilesIn(File file) {
    if (!file.isDirectory()) {
      return 1;
    }

    int result = 0;
    File[] subFiles = file.listFiles();
    if (subFiles == null) {
      subFiles = new File[0];
    }

    for (File subFile : subFiles) {
      result += calculateFilesIn(subFile);
    }

    return result;
  }

  public abstract String getLastProcessedCvsRoot();

  public boolean runInReadThread() {
    return true;
  }
}

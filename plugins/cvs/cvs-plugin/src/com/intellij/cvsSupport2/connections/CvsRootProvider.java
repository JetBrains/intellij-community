package com.intellij.cvsSupport2.connections;

import com.intellij.cvsSupport2.connections.login.CvsLoginWorker;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.errorHandling.CannotFindCvsRootException;
import com.intellij.cvsSupport2.javacvsImpl.io.ReadWriteStatistics;
import com.intellij.openapi.project.Project;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.netbeans.lib.cvsclient.CvsRoot;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.connection.IConnection;

import java.io.File;

/**
 * author: lesya
 */
public abstract class CvsRootProvider implements CvsEnvironment{
  private File myLocalRoot;
  private File myAdminRoot;
  protected final CvsEnvironment myCvsEnvironment;

  public static CvsRootProvider createOn(File file) throws CannotFindCvsRootException {
    return CvsRootOnFileSystem.createMeOn(file);
  }

  public static CvsRootProvider createOn(File file, CvsEnvironment env){
    return new CvsRootOnEnvironment(file, env);
  }

  public CvsRootProvider(File rootFile, CvsEnvironment cvsRoot) {
    myLocalRoot = rootFile;
    myAdminRoot = rootFile;
    myCvsEnvironment = cvsRoot;
  }

  public void changeLocalRootTo(@NotNull File localRoot){
    myLocalRoot = localRoot;
  }

  public IConnection createConnection(ReadWriteStatistics statistics) {
    return myCvsEnvironment.createConnection(statistics);
  }

  public CvsLoginWorker getLoginWorker(ModalityContext executor, Project project) {
    return myCvsEnvironment.getLoginWorker(executor, project);
  }

  public CvsRoot getCvsRoot() {
    return myCvsEnvironment.getCvsRoot();
  }

  public String getCvsRootAsString() {
    return myCvsEnvironment.getCvsRootAsString();
  }

  public File getLocalRoot() {
    return myLocalRoot;
  }

  public File getAdminRoot() {
    return myAdminRoot;
  }

  public void changeAdminRootTo(File directory) {
    myAdminRoot = directory;
  }

  public boolean isValid() {
    return myCvsEnvironment.isValid();
  }

  public CommandException processException(CommandException t) {
    return myCvsEnvironment.processException(t);
  }

  public boolean isOffline() {
    return myCvsEnvironment.isOffline();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CvsRootProvider)) return false;

    CvsRootProvider that = (CvsRootProvider)o;

    if (myAdminRoot != null ? !myAdminRoot.equals(that.myAdminRoot) : that.myAdminRoot != null) return false;
    if (myLocalRoot != null ? !myLocalRoot.equals(that.myLocalRoot) : that.myLocalRoot != null) return false;

    final ThreeState checkEnv = checkNulls(myCvsEnvironment, that.myCvsEnvironment);
    if (! ThreeState.UNSURE.equals(checkEnv)) return ThreeState.YES.equals(checkEnv);

    final ThreeState checkRoot = checkNulls(myCvsEnvironment.getCvsRoot(), that.myCvsEnvironment.getCvsRoot());
    if (! ThreeState.UNSURE.equals(checkRoot)) return ThreeState.YES.equals(checkRoot);

    if (myCvsEnvironment.getCvsRoot().getRepositoryPath() != null ?
        ! myCvsEnvironment.getCvsRoot().getRepositoryPath().equals(that.myCvsEnvironment.getCvsRoot().getRepositoryPath()) :
        that.myCvsEnvironment.getCvsRoot().getRepositoryPath() != null) return false;

    if (myCvsEnvironment.getCvsRoot().getCvsRoot() != null ?
        ! myCvsEnvironment.getCvsRoot().getCvsRoot().equals(that.myCvsEnvironment.getCvsRoot().getCvsRoot()) :
        that.myCvsEnvironment.getCvsRoot().getCvsRoot() != null) return false;
    return true;
  }

  @NotNull
  private ThreeState checkNulls(final Object one, final Object two) {
    if ((one == null) ^ (two == null)) return ThreeState.NO;
    if (one == null) return ThreeState.YES;
    return ThreeState.UNSURE;
  }

  @Override
  public int hashCode() {
    int result = myLocalRoot != null ? myLocalRoot.hashCode() : 0;
    result = 31 * result + (myAdminRoot != null ? myAdminRoot.hashCode() : 0);
    if (myCvsEnvironment != null && myCvsEnvironment.getCvsRoot() != null) {
      final CvsRoot root = myCvsEnvironment.getCvsRoot();
      result = 31 * result + (root.getRepositoryPath() != null ? root.getRepositoryPath().hashCode() : 0);
      result = 31 * result + (root.getCvsRoot() != null ? root.getCvsRoot().hashCode() : 0);
    }
    return result;
  }
}

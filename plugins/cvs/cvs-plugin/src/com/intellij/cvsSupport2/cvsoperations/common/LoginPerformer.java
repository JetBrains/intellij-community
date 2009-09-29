package com.intellij.cvsSupport2.cvsoperations.common;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.connections.login.CvsLoginWorker;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.errorHandling.CvsException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public abstract class LoginPerformer<T extends CvsEnvironment> {
  private final Collection<T> myRoots;
  private final Consumer<VcsException> myExceptionConsumer;
  private boolean myForceCheck;

  public LoginPerformer(Collection<T> roots, Consumer<VcsException> exceptionConsumer) {
    myRoots = roots;
    myExceptionConsumer = exceptionConsumer;
    myForceCheck = false;
  }

  public void setForceCheck(boolean forceCheck) {
    myForceCheck = forceCheck;
  }

  @Nullable
  protected abstract Project getProject(T root);

  public boolean loginAll(final ModalityContext executor) {
    for (T root : myRoots) {
      final Project project = getProject(root);

      final CvsLoginWorker worker = root.getLoginWorker(executor, project);
      final Ref<Boolean> promptResult = new Ref<Boolean>();
      final Runnable prompt = new Runnable() {
        public void run() {
          promptResult.set(worker.promptForPassword());
        }
      };
      while (true) {
        final ThreeState state = worker.silentLogin(myForceCheck);
        if (ThreeState.YES.equals(state)) break;  // check others
        if (ThreeState.NO.equals(state)) return false;
        executor.runInDispatchThread(prompt, project);
        if (! Boolean.TRUE.equals(promptResult.get())) {
          worker.goOffline();
          myExceptionConsumer.consume(new CvsException("Authentication canceled", root.getCvsRootAsString()));
          return false;
        }
        myForceCheck = true;
      }
    }
    return true;
  }

  public static class MyProjectKnown extends LoginPerformer<CvsEnvironment> {
    private final Project myProject;

    public MyProjectKnown(final Project project, final Collection<CvsEnvironment> roots, final Consumer<VcsException> exceptionConsumer) {
      super(roots, exceptionConsumer);
      myProject = project;
    }

    @Override
    protected Project getProject(CvsEnvironment root) {
      return myProject;
    }
  }

  public static class MyForRootProvider extends LoginPerformer<CvsRootProvider> {
    private ProjectLocator myProjectLocator;
    private LocalFileSystem myLfs;

    public MyForRootProvider(Collection<CvsRootProvider> roots, Consumer<VcsException> exceptionConsumer) {
      super(roots, exceptionConsumer);
      myProjectLocator = ProjectLocator.getInstance();
      myLfs = LocalFileSystem.getInstance();
    }

    @Nullable
    @Override
    protected Project getProject(CvsRootProvider root) {
      final VirtualFile vf = root.getLocalRoot() == null ? null : myLfs.findFileByIoFile(root.getLocalRoot());
      return (vf == null) ? null : myProjectLocator.guessProjectForFile(vf);
    }
  }
}

/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vcs.rollback.RollbackProgressListener;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vcs.update.SequentialUpdatesContext;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.openapi.vcs.update.UpdateSession;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.vcs.AbstractJunitVcsTestCase;
import com.intellij.util.NullableFunction;
import com.intellij.util.PairConsumer;
import com.intellij.util.ui.UIUtil;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author irengrig
 *         Date: 12/21/10
 *         Time: 2:03 PM
 */
public class VcsEventsListenerTest extends AbstractJunitVcsTestCase {
  private AbstractVcs myVcs;
  private ProjectLevelVcsManagerImpl myVcsManager;
  private ChangeListManager myChangeListManager;
  private TempDirTestFixture myTempDirFixture;
  private File myClientRoot;

  @Before
  public void setUp() {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          final IdeaTestFixtureFactory fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory();
          myTempDirFixture = fixtureFactory.createTempDirTestFixture();
          myTempDirFixture.setUp();

          myClientRoot = new File(myTempDirFixture.getTempDirPath(), "clientroot");
          myClientRoot.mkdir();

          initProject(myClientRoot, VcsEventsListenerTest.this.getTestName());

          ((StartupManagerImpl)StartupManager.getInstance(myProject)).runPostStartupActivities();

          myChangeListManager = ChangeListManager.getInstance(myProject);
          myVcs = VcsActiveEnvironmentsProxy.proxyVcs(new MyVcs(myProject, "mock"));
          myVcsManager = (ProjectLevelVcsManagerImpl)ProjectLevelVcsManager.getInstance(myProject);
          myVcsManager.registerVcs(myVcs);
          myVcsManager.setDirectoryMapping(myWorkingCopyDir.getPath(), myVcs.getName());
        }
        catch (Exception e) {
          tearDown();
          throw new RuntimeException(e);
        }
      }
    });
  }

  @After
  public void tearDown() {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          if (myVcsManager != null && myVcs != null) {
            myVcsManager.unregisterVcs(myVcs);
          }

          tearDownProject();
          if (myTempDirFixture != null) {
            myTempDirFixture.tearDown();
            myTempDirFixture = null;
          }
          FileUtil.delete(myClientRoot);
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
        finally {
          try {
            UsefulTestCase.clearFields(this);
          }
          catch (IllegalAccessException e) {
            //noinspection ThrowFromFinallyBlock
            throw new RuntimeException(e);
          }
        }
      }
    });
  }

  @Test
  public void testSimpleListeningWithProxy() throws Exception {
    final VcsEventsListenerManager manager = myVcsManager.getVcsEventsListenerManager();
    final List<VirtualFile> list = Arrays.asList(new VirtualFile[]{myWorkingCopyDir});
    final MyCheckinListener listener = new MyCheckinListener(list);
    final Object key = manager.addCheckin(new ForwardingListener<CheckinEnvironment>(listener));

    myVcs.getCheckinEnvironment().scheduleUnversionedFilesForAddition(list);

    Assert.assertTrue(listener.isChecked());
    listener.assertCheckOk();

    listener.reset();

    manager.removeCheckin(key);
    listener.reset();
    myVcs.getCheckinEnvironment().scheduleUnversionedFilesForAddition(list);
    Assert.assertFalse(listener.isChecked());
  }

  @Test
  public void testSimpleListeningWithoutProxy() throws Exception {
    myVcsManager.setDirectoryMapping(myWorkingCopyDir.getPath(), "svn");

    testSimpleListeningWithProxy();
  }

  private static class MyVcs extends MockAbstractVcs {
    private MyVcs(Project project, String name) {
      super(project, name);
    }

    @Override
    protected UpdateEnvironment createUpdateEnvironment() {
      return new UpdateEnvironment() {
        @Override
        public void fillGroups(UpdatedFiles updatedFiles) {
          //To change body of implemented methods use File | Settings | File Templates.
        }

        @NotNull
        @Override
        public UpdateSession updateDirectories(@NotNull FilePath[] contentRoots,
                                               UpdatedFiles updatedFiles,
                                               ProgressIndicator progressIndicator,
                                               @NotNull Ref<SequentialUpdatesContext> context) throws ProcessCanceledException {
          return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public Configurable createConfigurable(Collection<FilePath> files) {
          return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public boolean validateOptions(Collection<FilePath> roots) {
          return false;  //To change body of implemented methods use File | Settings | File Templates.
        }
      };
    }

    @Override
    protected RollbackEnvironment createRollbackEnvironment() {
      return new RollbackEnvironment() {
        @Override
        public String getRollbackOperationName() {
          return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public void rollbackChanges(List<Change> changes,
                                    List<VcsException> vcsExceptions,
                                    @NotNull RollbackProgressListener listener) {
          //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public void rollbackMissingFileDeletion(List<FilePath> files,
                                                List<VcsException> exceptions,
                                                RollbackProgressListener listener) {
          //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public void rollbackModifiedWithoutCheckout(List<VirtualFile> files,
                                                    List<VcsException> exceptions,
                                                    RollbackProgressListener listener) {
          //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public void rollbackIfUnchanged(VirtualFile file) {
          //To change body of implemented methods use File | Settings | File Templates.
        }
      };
    }

    @Override
    protected CheckinEnvironment createCheckinEnvironment() {
      return new CheckinEnvironment() {
        @Override
        public RefreshableOnComponent createAdditionalOptionsPanel(CheckinProjectPanel panel,
                                                                   PairConsumer<Object, Object> additionalDataConsumer) {
          return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public String getDefaultMessageFor(FilePath[] filesToCheckin) {
          return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public String getHelpId() {
          return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public String getCheckinOperationName() {
          return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public List<VcsException> commit(List<Change> changes, String preparedComment) {
          return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public List<VcsException> commit(List<Change> changes,
                                         String preparedComment,
                                         @NotNull NullableFunction<Object, Object> parametersHolder, Set<String> feedback) {
          return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public List<VcsException> scheduleMissingFileForDeletion(List<FilePath> files) {
          return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public List<VcsException> scheduleUnversionedFilesForAddition(List<VirtualFile> files) {
          return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public boolean keepChangeListAfterCommit(ChangeList changeList) {
          return false;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public boolean isRefreshAfterCommitNeeded() {
          return true;
        }
      };
    }
  }

  private static class MyCheckinListener implements CheckinEnvironment {
    private boolean myChecked;
    private boolean myAssertOk;
    private final List<VirtualFile> myCheckList;

    public MyCheckinListener(final List<VirtualFile> checkList) {
      myCheckList = checkList;
      myChecked = false;
      myAssertOk = true;
    }

    @Override
    public RefreshableOnComponent createAdditionalOptionsPanel(CheckinProjectPanel panel,
                                                               PairConsumer<Object, Object> additionalDataConsumer) {
      return null;
    }
    @Override
    public String getDefaultMessageFor(FilePath[] filesToCheckin) {
      return null;
    }
    @Override
    public String getHelpId() {
      return null;
    }
    @Override
    public String getCheckinOperationName() {
      return null;
    }
    @Override
    public List<VcsException> commit(List<Change> changes, String preparedComment) {
      return null;
    }

    @Override
    public List<VcsException> commit(List<Change> changes,
                                     String preparedComment,
                                     @NotNull NullableFunction<Object, Object> parametersHolder, Set<String> feedback) {
      return null;
    }

    @Override
    public List<VcsException> scheduleMissingFileForDeletion(List<FilePath> files) {
      return null;
    }

    @Override
    public List<VcsException> scheduleUnversionedFilesForAddition(List<VirtualFile> files) {
      myChecked = true;
      myAssertOk = myCheckList.equals(files);
      return null;
    }

    @Override
    public boolean keepChangeListAfterCommit(ChangeList changeList) {
      return false;
    }

    @Override
    public boolean isRefreshAfterCommitNeeded() {
      return true;
    }

    public boolean isChecked() {
      return myChecked;
    }

    public void assertCheckOk() {
      Assert.assertTrue(myAssertOk);
    }

    public void reset() {
      myAssertOk = true;
      myChecked = false;
    }
  }
}

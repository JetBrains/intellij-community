/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package git4idea.history;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsAnnotationLocalChangesListener;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.update.CommonUpdateProjectAction;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.repo.GitRepository;
import git4idea.test.GitTest;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.util.Collections;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 11/26/12
 * Time: 1:00 PM
 */
public class GitAnnotationsClosedTest extends GitTest {
  private VirtualFile first;
  private VirtualFile second;

  private volatile boolean myFirstClosed;
  private volatile boolean mySecondClosed;
  private VcsDirtyScopeManager myVcsDirtyScopeManager;
  private ChangeListManager myChangeListManager;

  @BeforeMethod
  @Override
  public void setUp(Method testMethod) throws Exception {
    super.setUp(testMethod);
    final String[] messages = {"one", "two", "alien1", "three", "alien2"};
    final String[] content1 = {"1\n2\n3\n", "1\n2+\n3\n", "a\nb\nc", "1\n2+\n3-\n", "a\nb++\nc"};

    first = myRepo.createVFile("a.txt", "init");
    second = myRepo.createVFile("b.txt", "init");
    myRepo.addCommit("init");

    final VirtualFile[] files = {first, first, second, first, second};

    for (int i = 0; i < content1.length; i++) {
      editFileInCommand(myProject, files[i], content1[i]);
      myRepo.addCommit(messages[i]);
    }

    myFirstClosed = false;
    mySecondClosed = false;

    myVcsDirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);
    myChangeListManager = ChangeListManager.getInstance(myProject);
  }

  @Test
  public void testClosedByCommitFromIdea() throws Exception {
    annotateFirst(first);

    editFileInCommand(myProject, first, "1\n2+\n3-ttt\n");
    Assert.assertFalse(myFirstClosed);

    myVcsDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    final Change change = myChangeListManager.getChange(first);
    Assert.assertNotNull(change);

    myVcs.getCheckinEnvironment().commit(Collections.singletonList(change), "***");
    myVcsDirtyScopeManager.fileDirty(first);

    myChangeListManager.ensureUpToDate(false);
    myChangeListManager.ensureUpToDate(false); //after-notifiers
    sleep(100);  // zipper-updater

    Assert.assertTrue(myFirstClosed);
  }

  @Test
  public void testClosedByExternalCommit() throws Exception {
    annotateFirst(first);

    editFileInCommand(myProject, first, "1\n2+\n3-ttt\n");
    Assert.assertFalse(myFirstClosed);

    myRepo.addCommit("external_commit");

    imitateEvent(myWorkingCopyDir);
    sleep(100);  // zipper-updater

    Assert.assertTrue(myFirstClosed);
  }

  @Test
  public void testClosedByExternalUpdate() throws Exception {
    final String head = myRepo.lastCommit();
    final String prevHead = myRepo.log("--pretty=format:%H", "-1", "HEAD^");
    myRepo.checkout(prevHead);
    annotateFirst(first);
    annotateSecond();
    Assert.assertFalse(myFirstClosed);
    Assert.assertFalse(mySecondClosed);

    myRepo.checkout(head);
    imitateEvent(myWorkingCopyDir);
    sleep(100);  // zipper-updater
    Assert.assertFalse(myFirstClosed);
    Assert.assertTrue(mySecondClosed);
  }

  @Test
  public void testClosedByInternalUpdate() throws Exception {
    myParentRepo.createFile("d.txt", "d");
    myParentRepo.addCommit("empty");
    myParentRepo.createBranch("someotherbranch");
    annotateFirst(first);
    annotateSecond();
    Assert.assertFalse(myFirstClosed);
    Assert.assertFalse(mySecondClosed);
    myRepo.pull();
    myRepo.push();
    myBrotherRepo.pull();

    myBrotherRepo.checkout("master");
    final VirtualFile child = myBrotherRepo.getVFRootDir().findChild("a.txt");
    Assert.assertNotNull(child);
    editFileInCommand(myProject, child, "1\n2+\n3---\n");
    myBrotherRepo.addCommit("brother_commit");
    myBrotherRepo.push();

    Assert.assertFalse(myFirstClosed);
    Assert.assertFalse(mySecondClosed);

    imitInternalUpdate();
    Assert.assertTrue(myFirstClosed);
    Assert.assertFalse(mySecondClosed);
  }

  @Test
  public void testAnnotationsAndRenaming() throws Exception {
    final String newName = "e.txt";
    renameFileInCommand(myProject, first, newName);
    final VirtualFile renamed = myRepo.getVFRootDir().findChild(newName);
    Assert.assertNotNull(renamed);
    sleep(500);
    myRepo.run("status");
    annotateFirst(renamed);
    annotateSecond();
    renameFileInCommand(myProject, second, "f.txt");

    Assert.assertFalse(myFirstClosed);
    Assert.assertFalse(mySecondClosed);
  }

  private void imitateEvent(final VirtualFile root) {
    final GitRepository repository = GitUtil.getRepositoryManager(myProject).getRepositoryForRoot(root);
    repository.update();
  }

  private void imitInternalUpdate() {
    final ProjectLevelVcsManagerEx ex = ProjectLevelVcsManagerEx.getInstanceEx(myProject);
    ex.setDirectoryMappings(Collections.singletonList(
      new VcsDirectoryMapping(FileUtil.toSystemIndependentName(myWorkingCopyDir.getPath()), GitVcs.NAME)));
    ex.getOptions(VcsConfiguration.StandardOption.UPDATE).setValue(false);
    final CommonUpdateProjectAction action = new CommonUpdateProjectAction();
    action.getTemplatePresentation().setText("1");
    action.actionPerformed(new AnActionEvent(null,
                                             new DataContext() {
                                               @Nullable
                                               @Override
                                               public Object getData(@NonNls String dataId) {
                                                 if (CommonDataKeys.PROJECT.is(dataId)) {
                                                   return myProject;
                                                 }
                                                 return null;
                                               }
                                             }, "test", new Presentation(), null, 0));

    myChangeListManager.ensureUpToDate(false);
    myChangeListManager.ensureUpToDate(false);  // wait for after-events like annotations recalculation
    sleep(100); // zipper updater
  }

  private void annotateFirst(final VirtualFile first) throws VcsException {
    final VcsAnnotationLocalChangesListener listener = ProjectLevelVcsManager.getInstance(myProject).getAnnotationLocalChangesListener();
    final FileAnnotation annotation = createTestAnnotation(myVcs.getAnnotationProvider(), first);

    annotation.setCloser(new Runnable() {
      @Override
      public void run() {
        myFirstClosed = true;
        listener.unregisterAnnotation(first, annotation);
      }
    });
    listener.registerAnnotation(first, annotation);
  }

  private void annotateSecond() throws VcsException {
    final VcsAnnotationLocalChangesListener listener = ProjectLevelVcsManager.getInstance(myProject).getAnnotationLocalChangesListener();
    final FileAnnotation annotation = createTestAnnotation(myVcs.getAnnotationProvider(), second);
    annotation.setCloser(new Runnable() {
      @Override
      public void run() {
        mySecondClosed = true;
        listener.unregisterAnnotation(second, annotation);
      }
    });
    listener.registerAnnotation(second, annotation);
  }

  private void sleep(int time) {
    try {
      Thread.sleep(time);
    } catch (InterruptedException e) {
      //
    }
  }
}

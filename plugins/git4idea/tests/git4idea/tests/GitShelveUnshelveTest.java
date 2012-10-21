/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea.tests;

import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.hash.HashSet;
import git4idea.test.GitTest;
import git4idea.test.GitTestUtil;
import junit.framework.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 7/23/12
 * Time: 7:53 PM
 */
public class GitShelveUnshelveTest extends GitTest {
  private Map<String,VirtualFile> myFiles;
  private VirtualFile myFile;
  private ChangeListManager myChangeListManager;
  private ShelveChangesManager myShelveChangesManager;

  @BeforeMethod
  @Override
  public void setUp(Method method) throws Exception {
    super.setUp(method);
    myFiles = GitTestUtil.createFileStructure(myProject, myRepo, "a.txt", "b.txt", "dir/c.txt", "dir/subdir/d.txt");
    myRepo.addCommit();
    myRepo.refresh();

    myFile = myFiles.get("a.txt"); // the file is commonly used, so save it in a field.

    ((StartupManagerImpl) StartupManager.getInstance(myProject)).runPostStartupActivities();
    myChangeListManager = ChangeListManager.getInstance(myProject);
    myShelveChangesManager = ShelveChangesManager.getInstance(myProject);
  }

  @Test
  public void testUnshelveCreated() throws Exception {
    final Collection<Change> changes = createEditAddChanges();
    final ShelvedChangeList list = myShelveChangesManager.shelveChanges(changes, "try", true);
    checkEmpty();
    unshelve(list);
  }

  private void unshelve(ShelvedChangeList list) {
    final LocalChangeList changeList = myChangeListManager.addChangeList("another list", "");
    myShelveChangesManager.unshelveChangeList(list, null, null, changeList);
    refreshChanges();

    final List<LocalChangeList> lists = myChangeListManager.getChangeLists();
    Assert.assertEquals(2, lists.size());

    LocalChangeList newList = null;
    final Set<String> listNames = new HashSet<String>();
    listNames.addAll(Arrays.asList("Default", "another list"));
    for (LocalChangeList localChangeList : lists) {
      listNames.remove(localChangeList.getName());
      if ("another list".equals(localChangeList.getName())) {
        newList = localChangeList;
      }
    }
    Assert.assertEquals(0, listNames.size());

    checkListContents(newList);
  }

  private void checkEmpty() {
    refreshChanges();
    final List<LocalChangeList> lists = myChangeListManager.getChangeLists();
    Assert.assertEquals(1, lists.size());
    Assert.assertEquals("Default", lists.get(0).getName());
    Assert.assertEquals(0, lists.get(0).getChanges().size());
  }

  private Collection<Change> createEditAddChanges() {
    editFileInCommand(myProject, myFile, "changes contents");
    createFileInCommand(myRepo.getVFRootDir(), "added.txt", "added contents");
    refreshChanges();

    final List<LocalChangeList> lists = myChangeListManager.getChangeLists();
    Assert.assertTrue(lists.size() == 1);
    Assert.assertEquals("Default", lists.get(0).getName());
    final LocalChangeList cl = lists.get(0);
    final Collection<Change> changes = checkListContents(cl);
    return changes;
  }

  private Collection<Change> checkListContents(LocalChangeList cl) {
    final Collection<Change> changes = cl.getChanges();

    final Set<String> names = new HashSet<String>();
    names.addAll(Arrays.asList("added.txt", "a.txt"));

    Assert.assertEquals(2, changes.size());
    for (Change change : changes) {
      final FilePath fp = ChangesUtil.getFilePath(change);
      names.remove(fp.getName());
    }
    Assert.assertEquals(0, names.size());
    return changes;
  }

  private void refreshChanges() {
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);
  }
}

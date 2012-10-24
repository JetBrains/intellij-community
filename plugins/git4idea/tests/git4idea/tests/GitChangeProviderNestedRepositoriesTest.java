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

import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitVcs;
import git4idea.test.GitTestRepository;
import git4idea.test.GitTestUtil;
import junit.framework.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 9/5/12
 * Time: 10:32 AM
 * @deprecated Use {@link GitLightTest}
 */
@Deprecated
public class GitChangeProviderNestedRepositoriesTest extends GitChangeProviderTest {
  private File myChildRepoDir;
  private GitTestRepository myChildRepo;
  private ProjectLevelVcsManager myVcsManager;
  private ChangeListManager myChangeListManager;
  private Map<String,VirtualFile> myChildFiles;
  private VcsDirtyScopeManager myDirtyScopeManager;

  @BeforeMethod
  public void setUp() throws Exception {
    final File repoRoot = myRepo.getRootDir();
    myChildRepoDir = new File(repoRoot, "child");
    myChildRepoDir.mkdir();
    myChildRepo = GitTestRepository.init(myChildRepoDir);
    myChildRepo.setName(MAIN_USER_NAME, MAIN_USER_EMAIL);
    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(myChildRepo.getRootDir());
    myChildRepo.refresh();

    myChildFiles = GitTestUtil.createFileStructure(myProject, myChildRepo, "in1.txt", "in2.txt", "dirr/inin1.txt", "dirr/inin2.txt");
    myChildRepo.addCommit();
    myChildRepo.refresh();

    myVcsManager = ProjectLevelVcsManager.getInstance(myProject);
    myVcsManager.setDirectoryMappings(Arrays.asList(new VcsDirectoryMapping(myRepo.getRootDir().getPath(), GitVcs.getKey().getName()),
                                                    new VcsDirectoryMapping(myChildRepo.getRootDir().getPath(), GitVcs.getKey().getName())));
    myChangeListManager = ChangeListManager.getInstance(myProject);
    myDirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);
  }

  @Test
  public void testReproduceChangeListsRotten() throws Exception {
    editFileInCommand(myProject, myFiles.get("a.txt"), "123");
    VirtualFile in1 = myChildFiles.get("in1.txt");
    editFileInCommand(myProject, in1, "321");
    VirtualFile in2 = myChildFiles.get("in2.txt");
    editFileInCommand(myProject, in2, "321*");

    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    final LocalChangeList localChangeList = myChangeListManager.addChangeList("new", "new");
    Change change1 = myChangeListManager.getChange(in1);
    Change change2 = myChangeListManager.getChange(in2);
    myChangeListManager.moveChangesTo(localChangeList, change1, change2);

    myDirtyScopeManager.filesDirty(Collections.singletonList(in1), Collections.singletonList(myRepo.getVFRootDir()));
    myChangeListManager.ensureUpToDate(false);
    LocalChangeList list = myChangeListManager.getChangeList(in1);
    Assert.assertNotNull(list);
    Assert.assertEquals("new", list.getName());
    LocalChangeList list2 = myChangeListManager.getChangeList(in2);
    Assert.assertNotNull(list2);
    Assert.assertEquals("new", list2.getName());
    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);
    list = myChangeListManager.getChangeList(in1);
    Assert.assertNotNull(list);
    Assert.assertEquals("new", list.getName());
    list2 = myChangeListManager.getChangeList(in2);
    Assert.assertNotNull(list2);
    Assert.assertEquals("new", list2.getName());
  }
}

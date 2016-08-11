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
package com.intellij.openapi.vcs;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs;
import com.intellij.openapi.vcs.changes.ui.ChangesComparator;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.impl.LocalChangesUnderRoots;
import com.intellij.openapi.vcs.impl.projectlevelman.AllVcses;
import com.intellij.openapi.vcs.impl.projectlevelman.AllVcsesI;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.vcs.MockChangeListManager;
import com.intellij.testFramework.vcs.MockContentRevision;
import com.intellij.vcsUtil.VcsUtil;

import java.lang.reflect.Field;
import java.util.*;

/**
 * @author Kirill Likhodedov
 */
public class LocalChangesUnderRootsTest extends PlatformTestCase {

  private LocalChangesUnderRoots myLocalChangesUnderRoots;
  private MockChangeListManager myChangeListManager;
  private VirtualFile myBaseDir;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myChangeListManager = new MockChangeListManager();
    myBaseDir = myProject.getBaseDir();
    myLocalChangesUnderRoots = new LocalChangesUnderRoots(ChangeListManager.getInstance(myProject),
                                                          ProjectLevelVcsManager.getInstance(myProject));

    substituteChangeListManager();
  }

  @Override
  protected void tearDown() throws Exception {
    ((ChangeListManagerImpl) ChangeListManager.getInstance(myProject)).stopEveryThingIfInTestMode();
    super.tearDown();
  }

  // This is not good, but declaring MockChangeListManager might break other tests
  private void substituteChangeListManager() throws NoSuchFieldException, IllegalAccessException {
    Field myChangeManager = LocalChangesUnderRoots.class.getDeclaredField("myChangeManager");
    myChangeManager.setAccessible(true);
    myChangeManager.set(myLocalChangesUnderRoots, myChangeListManager);
  }

  public void testChangesInTwoGitRoots() {
    AllVcsesI myVcses = AllVcses.getInstance(myProject);
    myVcses.registerManually(new MockAbstractVcs(myProject, "Mock"));

    List<VirtualFile> roots = createRootStructure(
      Pair.create(myBaseDir.getPath(), "Mock"),
      Pair.create("community", "Mock")
    );

    Change changeBeforeCommunity = createChangeForPath("a.txt");
    Change changeAfterCommunity = createChangeForPath("readme.txt");
    Change changeInCommunity = createChangeForPath("community/com.txt");
    myChangeListManager.addChanges(changeBeforeCommunity, changeAfterCommunity, changeInCommunity);
    
    Map<VirtualFile, Collection<Change>> expected = new HashMap<>();
    expected.put(roots.get(0), Arrays.asList(changeBeforeCommunity, changeAfterCommunity));
    expected.put(roots.get(1), Collections.singletonList(changeInCommunity));

    Map<VirtualFile, Collection<Change>> changesUnderRoots = myLocalChangesUnderRoots.getChangesUnderRoots(roots);
    assertEqualMaps(expected, changesUnderRoots);
  }

  private static void assertEqualMaps(Map<VirtualFile, Collection<Change>> expected, Map<VirtualFile, Collection<Change>> actual) {
    assertEquals("Maps size is different. " + expectedActualMessage(expected, actual), expected.size(), actual.size());
    for (Map.Entry<VirtualFile, Collection<Change>> expectedEntry : expected.entrySet()) {
      VirtualFile root = expectedEntry.getKey();
      if (!actual.containsKey(root)) {
        fail("Didn't find root [" + root + "]. " + expectedActualMessage(expected, actual));
      }
      List<Change> expectedChanges = new ArrayList<>(expectedEntry.getValue());
      List<Change> actualChanges = new ArrayList<>(actual.get(root));
      Collections.sort(expectedChanges, ChangesComparator.getInstance(false));
      Collections.sort(actualChanges, ChangesComparator.getInstance(false));
      assertEquals("Changes not equal for root [" + root + "]. " + expectedActualMessage(expected, actual), expectedChanges, actualChanges);
    }
  }

  private static String expectedActualMessage(Object expected, Object actual) {
    return "\nExpected:\n " + expected + "\nActual:\n" + actual;
  }

  private List<VirtualFile> createRootStructure(Pair<String, String>... pathAndVcs) {
    List<VirtualFile> roots = new ArrayList<>();
    List<VcsDirectoryMapping> mappings = new ArrayList<>();
    for (Pair<String, String> pathAndVc : pathAndVcs) {
      String path = pathAndVc.first;
      String vcs = pathAndVc.second;
      
      VirtualFile vf;
      if (path.equals(myBaseDir.getPath())) {
        vf = myBaseDir;
      } else {
        vf = VfsTestUtil.createDir(myBaseDir, path);
      }

      mappings.add(new VcsDirectoryMapping(vf.getPath(), vcs));
      roots.add(vf);
    }
    ProjectLevelVcsManager.getInstance(myProject).setDirectoryMappings(mappings);
    return roots;
  }

  private Change createChangeForPath(String path) {
    VirtualFile file = VfsTestUtil.createFile(myBaseDir, path);
    FilePath filePath = VcsUtil.getFilePath(file);
    ContentRevision beforeRevision = new MockContentRevision(filePath, new VcsRevisionNumber.Int(1));
    ContentRevision afterRevision = new MockContentRevision(filePath, new VcsRevisionNumber.Int(2));
    return new Change(beforeRevision, afterRevision);
  }
  
}

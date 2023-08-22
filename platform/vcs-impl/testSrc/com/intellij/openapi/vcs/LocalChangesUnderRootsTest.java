// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs;
import com.intellij.openapi.vcs.changes.ui.ChangesComparator;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.impl.LocalChangesUnderRoots;
import com.intellij.openapi.vcs.impl.projectlevelman.AllVcses;
import com.intellij.openapi.vcs.impl.projectlevelman.AllVcsesI;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.vcs.MockChangeListManager;
import com.intellij.testFramework.vcs.MockContentRevision;
import com.intellij.vcsUtil.VcsUtil;

import java.util.*;

public class LocalChangesUnderRootsTest extends HeavyPlatformTestCase {
  private static final String MOCK = "Mock";

  private MockChangeListManager myChangeListManager;
  private VirtualFile myBaseDir;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myChangeListManager = new MockChangeListManager();
    myBaseDir = PlatformTestUtil.getOrCreateProjectBaseDir(myProject);
  }

  public void testChangesInTwoGitRoots() {
    AllVcsesI myVcses = AllVcses.getInstance(myProject);
    myVcses.registerManually(new MockAbstractVcs(myProject, MOCK));

    List<VirtualFile> roots = createRootStructure(
      Pair.create(myBaseDir.getPath(), MOCK),
      Pair.create("community", MOCK)
    );

    Change changeBeforeCommunity = createChangeForPath("a.txt");
    Change changeAfterCommunity = createChangeForPath("readme.txt");
    Change changeInCommunity = createChangeForPath("community/com.txt");
    myChangeListManager.addChanges(changeBeforeCommunity, changeAfterCommunity, changeInCommunity);

    Map<VirtualFile, Collection<Change>> expected = new HashMap<>();
    expected.put(roots.get(0), Arrays.asList(changeBeforeCommunity, changeAfterCommunity));
    expected.put(roots.get(1), Collections.singletonList(changeInCommunity));

    Map<VirtualFile, Collection<Change>> changesUnderRoots = LocalChangesUnderRoots.getChangesUnderRoots(roots, myChangeListManager, myProject);
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

package com.intellij.openapi.vcs;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs;
import com.intellij.testFramework.vcs.MockContentRevision;
import com.intellij.openapi.vcs.changes.ui.ChangesComparator;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.impl.LocalChangesUnderRoots;
import com.intellij.openapi.vcs.impl.projectlevelman.AllVcses;
import com.intellij.openapi.vcs.impl.projectlevelman.AllVcsesI;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.vcs.MockChangeListManager;
import com.intellij.vcsUtil.VcsUtil;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.*;

/**
 * @author Kirill Likhodedov
 */
public class LocalChangesUnderRootsTest extends PlatformTestCase {

  private LocalChangesUnderRoots myLocalChangesUnderRoots;
  private MockChangeListManager myChangeListManager;
  private VirtualFile myBaseDir;

  @Before
  protected void setUp() throws Exception {
    super.setUp();

    myChangeListManager = new MockChangeListManager();
    myBaseDir = myProject.getBaseDir();
    myLocalChangesUnderRoots = new LocalChangesUnderRoots(ChangeListManager.getInstance(myProject),
                                                          ProjectLevelVcsManager.getInstance(myProject));

    substituteChangeListManager();
  }

  // This is not good, but declaring MockChangeListManager might break other tests
  private void substituteChangeListManager() throws NoSuchFieldException, IllegalAccessException {
    Field myChangeManager = LocalChangesUnderRoots.class.getDeclaredField("myChangeManager");
    myChangeManager.setAccessible(true);
    myChangeManager.set(myLocalChangesUnderRoots, myChangeListManager);
  }

  @Test
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
    
    Map<VirtualFile, Collection<Change>> expected = new HashMap<VirtualFile, Collection<Change>>();
    expected.put(roots.get(0), Arrays.asList(changeBeforeCommunity, changeAfterCommunity));
    expected.put(roots.get(1), Arrays.asList(changeInCommunity));

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
      List<Change> expectedChanges = new ArrayList<Change>(expectedEntry.getValue());
      List<Change> actualChanges = new ArrayList<Change>(actual.get(root));
      Collections.sort(expectedChanges, ChangesComparator.getInstance(false));
      Collections.sort(actualChanges, ChangesComparator.getInstance(false));
      assertEquals("Changes not equal for root [" + root + "]. " + expectedActualMessage(expected, actual), expectedChanges, actualChanges);
    }
  }

  private static String expectedActualMessage(Object expected, Object actual) {
    return "\nExpected:\n " + expected + "\nActual:\n" + actual;
  }

  private List<VirtualFile> createRootStructure(Pair<String, String>... pathAndVcs) {
    List<VirtualFile> roots = new ArrayList<VirtualFile>();
    List<VcsDirectoryMapping> mappings = new ArrayList<VcsDirectoryMapping>();
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

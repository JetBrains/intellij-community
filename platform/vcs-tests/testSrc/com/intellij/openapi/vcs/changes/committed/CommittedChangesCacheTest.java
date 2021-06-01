// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vcs.update.FileGroup;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.RunAll;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class CommittedChangesCacheTest extends HeavyPlatformTestCase {
  private MockAbstractVcs myVcs;
  private MockCommittedChangesProvider myProvider;
  private MockDiffProvider myDiffProvider;
  private CommittedChangesCache myCache;
  private ProjectLevelVcsManagerImpl myVcsManager;
  private VirtualFile myContentRoot;
  private MockListener myListener;
  private MessageBusConnection myConnection;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myVcsManager = (ProjectLevelVcsManagerImpl)ProjectLevelVcsManager.getInstance(getProject());

    myVcs = new MockAbstractVcs(getProject());
    myProvider = new MockCommittedChangesProvider();
    myVcs.setCommittedChangesProvider(myProvider);
    myDiffProvider = new MockDiffProvider();
    myVcs.setDiffProvider(myDiffProvider);

    File tempDir = createTempDirectory();
    myContentRoot = getVirtualFile(tempDir);
    PsiTestUtil.addContentRoot(myModule, myContentRoot);

    myVcsManager.registerVcs(myVcs);
    myVcsManager.setDirectoryMappings(Collections.singletonList(new VcsDirectoryMapping(myContentRoot.getPath(), myVcs.getName())));
    myVcsManager.waitForInitialized();
    assertTrue(myVcsManager.hasActiveVcss());

    myCache = CommittedChangesCache.getInstance(getProject());
    assertEquals(1, myCache.getCachesHolder().getAllCaches().size());
    getTempDir().scheduleDelete(myCache.getCachesHolder().getCacheBasePath());
  }

  @Override
  protected void tearDown() {
    RunAll.runAll(
      () -> {
        if (myConnection != null) myConnection.disconnect();
      },
      () -> myVcsManager.unregisterVcs(myVcs),
      () -> myCache.clearCaches(EmptyRunnable.INSTANCE),
      () -> clearFields(this),
      () -> super.tearDown()
    );
  }

  public void testEmpty() throws Exception {
    final List<CommittedChangeList> list = myCache.getChanges(myProvider.createDefaultSettings(), myContentRoot, myVcs, 0, false,
                                                              myProvider, myProvider.getLocationFor(VcsUtil.getFilePath(myContentRoot)));
    assertEquals(0, list.size());
  }

  public void testSimple() throws Exception {
    myProvider.registerChangeList("test");
    final List<CommittedChangeList> list = myCache.getChanges(myProvider.createDefaultSettings(), myContentRoot, myVcs, 0, false,
                                                              myProvider, myProvider.getLocationFor(VcsUtil.getFilePath(myContentRoot)));
    assertEquals(1, list.size());
    assertEquals("test", list.get(0).getName());
  }

  public void testIncomingChangesSimple() throws Exception {
    myProvider.registerChangeList("test");
    myCache.refreshAllCaches();
    final List<CommittedChangeList> list = getIncomingChangesFromCache();
    assertEquals(1, list.size());
  }

  private List<CommittedChangeList> getIncomingChangesFromCache() {
    final List<CommittedChangeList> result = new ArrayList<>();
    // this is actually synchronous in tests
    myCache.loadIncomingChangesAsync(committedChangeLists -> result.addAll(committedChangeLists), true);
    return result;
  }

  public void testUpdatedFilesSimple() throws Exception {
    final Change change = createChange("1.txt", 2);
    myProvider.registerChangeList("test", change);
    myCache.refreshAllCaches();
    assertEquals(1, getIncomingChangesFromCache().size());
    attachListener();
    myCache.processUpdatedFiles(createUpdatedFiles(change));
    assertEquals(0, getIncomingChangesFromCache().size());
    assertEquals(2, myListener.getIncomingChangesUpdateCount());
    final List<CommittedChangeList> list = myListener.getIncomingChangesUpdate(0);
    assertEquals(1, list.size());
  }

  public void testFileUpdatedTwice() throws Exception {
    final Change change = createChange("1.txt", 2);
    final Change change2 = createChange("1.txt", 3);
    myProvider.registerChangeList("test", change);
    myProvider.registerChangeList("test 2", change2);
    myCache.refreshAllCaches();
    int count = myProvider.getRefreshCount();
    assertEquals(2, getIncomingChangesFromCache().size());
    myCache.processUpdatedFiles(createUpdatedFiles(change));
    assertEquals(1, getIncomingChangesFromCache().size());
    assertEquals(count, myProvider.getRefreshCount());
  }

  public void testFileUpdatedTwiceInOneStep() throws Exception {
    final Change change = createChange("1.txt", 2);
    final Change change2 = createChange("1.txt", 3);
    myProvider.registerChangeList("test", change);
    myProvider.registerChangeList("test 2", change2);
    myCache.refreshAllCaches();
    assertEquals(2, getIncomingChangesFromCache().size());
    myCache.processUpdatedFiles(createUpdatedFiles(change2));
    assertEquals(0, getIncomingChangesFromCache().size());
  }

  public void testIncomingNotLast() throws Exception {
    final Change change = createChange("1.txt", 2);
    final Change change2 = createChange("2.txt", 2);
    myProvider.registerChangeList("test", change);
    myProvider.registerChangeList("test 2", change2);
    myCache.refreshAllCaches();
    assertEquals(2, getIncomingChangesFromCache().size());
    myCache.processUpdatedFiles(createUpdatedFiles(change2));
    assertEquals(1, getIncomingChangesFromCache().size());
  }

  public void testRefreshRequired() throws Exception {
    myCache.refreshAllCaches();
    final Change change = createChange("1.txt", 2);
    myProvider.registerChangeList("test", change);
    int count = myProvider.getRefreshCount();
    myCache.processUpdatedFiles(createUpdatedFiles(change));
    assertEquals(0, getIncomingChangesFromCache().size());
    assertEquals(count+1, myProvider.getRefreshCount());
  }

  public void testCachedDate() throws Exception {
    final Change change = createChange("1.txt", 2);
    final CommittedChangeList list = myProvider.registerChangeList("test", change);
    myCache.refreshAllCaches();
    RepositoryLocation location = myProvider.getLocationFor(VcsUtil.getFilePath(myContentRoot));
    final ChangesCacheFile cacheFile = myCache.getCachesHolder().getCacheFile(myVcs, myContentRoot, location);
    assertEquals(list.getCommitDate(), cacheFile.getLastCachedDate());
    assertEquals(list.getCommitDate(), cacheFile.getFirstCachedDate());
    assertEquals(list.getNumber(), cacheFile.getLastCachedChangelist());
    assertTrue(cacheFile.hasCompleteHistory());
  }

  public void testPartialUpdate() throws Exception {
    final Change change = createChange("1.txt", 2);
    final Change change2 = createChange("2.txt", 2);
    myProvider.registerChangeList("test", change, change2);
    myCache.refreshAllCaches();
    assertEquals(1, getIncomingChangesFromCache().size());
    myCache.processUpdatedFiles(createUpdatedFiles(change));
    assertEquals(1, getIncomingChangesFromCache().size());
    myCache.processUpdatedFiles(createUpdatedFiles(change2));
    assertEquals(0, getIncomingChangesFromCache().size());
  }

  public void testRefreshIncoming() throws Exception {
    final String fileName = "1.txt";
    final File testFile = createTestFile(fileName);
    final Change change = createChange(fileName, 2);
    myProvider.registerChangeList("test", change);
    myCache.refreshAllCaches();
    assertEquals(1, getIncomingChangesFromCache().size());
    myDiffProvider.setCurrentRevision(getVirtualFile(testFile), new VcsRevisionNumber.Int(2));
    boolean result = myCache.refreshIncomingChanges();
    assertTrue(result);
    assertEquals(0, getIncomingChangesFromCache().size());
    result = myCache.refreshIncomingChanges();
    assertFalse(result);
  }

  public void testDelete() throws Exception {
    final Change change = MockCommittedChangesProvider.createMockDeleteChange(new File(myContentRoot.getPath(), "1.txt").toString(), 1);
    myProvider.registerChangeList("test", change);
    myCache.refreshAllCaches();
    assertEquals(1, getIncomingChangesFromCache().size());
    attachListener();
    myCache.processUpdatedFiles(createUpdatedFiles(change));
    assertEquals(0, getIncomingChangesFromCache().size());
    assertEquals(2, myListener.getIncomingChangesUpdateCount());
    final List<CommittedChangeList> list = myListener.getIncomingChangesUpdate(0);
    assertEquals(1, list.size());
  }

  public void testRefreshIncomingDeleted() throws Exception {
    final Change change = createChange("1.txt", 2);
    final Change change2 = MockCommittedChangesProvider.createMockDeleteChange(new File(myContentRoot.getPath(), "1.txt").toString(), 2);
    myProvider.registerChangeList("test", change);
    myProvider.registerChangeList("test 2", change2);
    myCache.refreshAllCaches();
    assertEquals(2, getIncomingChangesFromCache().size());
    myCache.refreshIncomingChanges();
    assertEquals(0, getIncomingChangesFromCache().size());
  }

  public void testRefreshIncomingCDC() throws Exception {
    final String fileName = "1.txt";
    final File testFile = createTestFile(fileName);
    final Change change = createChange(fileName, 2);
    final Change change2 = MockCommittedChangesProvider.createMockDeleteChange(testFile.toString(), 2);
    final Change change3 = MockCommittedChangesProvider.createMockCreateChange(testFile.toString(), 3);
    myProvider.registerChangeList("test", change);
    myProvider.registerChangeList("test 2", change2);
    myProvider.registerChangeList("test 3", change3);
    myCache.refreshAllCaches();
    assertEquals(3, getIncomingChangesFromCache().size());
    myDiffProvider.setCurrentRevision(getVirtualFile(testFile), new VcsRevisionNumber.Int(3));
    boolean result = myCache.refreshIncomingChanges();
    assertTrue(result);
    assertEquals(0, getIncomingChangesFromCache().size());
  }

  public void testUpdatedFilesNotify() throws Exception {
    final Change change = createChange("1.txt", 2);
    myProvider.registerChangeList("test", change);
    myCache.refreshAllCaches();
    assertEquals(1, getIncomingChangesFromCache().size());

    attachListener();
    myCache.processUpdatedFiles(createUpdatedFiles(change));
    assertEquals(1, myListener.getIncomingChangesUpdateCount());
  }

  public void testGetIncomingChangelist() throws Exception {
    final String fileName = "1.txt";
    final File testFile = createTestFile(fileName);
    final Change change = createChange(fileName, 2);
    final CommittedChangeList list = myProvider.registerChangeList("test", change);
    myCache.refreshAllCaches();
    assertEquals(1, getIncomingChangesFromCache().size());
    myCache.refreshIncomingChanges();
    myCache.loadIncomingChangesAsync(null, true);
    final Pair<CommittedChangeList, Change> incomingList = myCache.getIncomingChangeList(getVirtualFile(testFile));
    assertNotNull(incomingList);
    assertEquals(list.getName(), incomingList.first.getName());
  }

  public void testGetIncomingChangelistPartial() throws Exception {
    final String fileName = "1.txt";
    final String fileName2 = "2.txt";
    final File testFile = createTestFile(fileName);
    final File testFile2 = createTestFile(fileName2);
    final Change change = createChange(fileName, 2);
    final Change change2 = createChange(fileName2, 2);
    final CommittedChangeList list = myProvider.registerChangeList("test", change, change2);
    myCache.refreshAllCaches();
    final VirtualFile vFile = getVirtualFile(testFile);
    final VirtualFile vFile2 = getVirtualFile(testFile2);
    myDiffProvider.setCurrentRevision(vFile, new VcsRevisionNumber.Int(2));
    myDiffProvider.setCurrentRevision(vFile2, new VcsRevisionNumber.Int(1));
    myCache.refreshIncomingChanges();
    myCache.loadIncomingChanges(false);
    assertNull(myCache.getIncomingChangeList(vFile));
    final Pair<CommittedChangeList, Change> incomingList = myCache.getIncomingChangeList(vFile2);
    assertNotNull(incomingList);
    assertEquals(list.getName(), incomingList.first.getName());
  }

  public void testInitCacheNotify() throws Exception {
    final Change change = createChange("1.txt", 2);
    myProvider.registerChangeList("test", change);
    attachListener();
    myCache.refreshAllCaches();
    assertEquals(1, myListener.getLoadedChanges().size());
  }

  private void attachListener() {
    myConnection = getProject().getMessageBus().connect();
    myListener = new MockListener();
    myConnection.subscribe(CommittedChangesCache.COMMITTED_TOPIC, myListener);
  }

  private File createTestFile(final String fileName) throws IOException {
    final File testFile = new File(myContentRoot.getPath(), fileName);
    testFile.createNewFile();
    ApplicationManager.getApplication().runWriteAction(() -> {
      VirtualFileManager.getInstance().syncRefresh();
    });
    return testFile;
  }

  private UpdatedFiles createUpdatedFiles(final Change... changes) {
    UpdatedFiles files = UpdatedFiles.create();
    for(Change change: changes) {
      final ContentRevision afterRevision = change.getAfterRevision();
      if (afterRevision != null) {
        files.getGroupById(FileGroup.MODIFIED_ID).add(afterRevision.getFile().getIOFile().getPath(),
                                                      myVcs.getKeyInstanceMethod(), afterRevision.getRevisionNumber());
      }
      else {
        final ContentRevision beforeRevision = change.getBeforeRevision();
        assert beforeRevision != null;
        files.getGroupById(FileGroup.REMOVED_FROM_REPOSITORY_ID).add(beforeRevision.getFile().getIOFile().getPath(),
                                                                     myVcs.getKeyInstanceMethod(), beforeRevision.getRevisionNumber());
      }
    }
    return files;
  }

  private Change createChange(final String path, final int revision) {
    return MockCommittedChangesProvider.createMockChange(new File(myContentRoot.getPath(), path).toString(), revision);
  }

  private static class MockListener implements CommittedChangesListener {
    private final List<CommittedChangeList> myLoadedChanges = new ArrayList<>();
    private final List<List<CommittedChangeList>> myIncomingChangesUpdates = new ArrayList<>();

    @Override
    public void changesLoaded(@NotNull RepositoryLocation location, @NotNull List<CommittedChangeList> changes) {
      myLoadedChanges.addAll(changes);
    }

    @Override
    public void incomingChangesUpdated(@Nullable List<CommittedChangeList> receivedChanges) {
      myIncomingChangesUpdates.add(receivedChanges);
    }

    public int getIncomingChangesUpdateCount() {
      return myIncomingChangesUpdates.size();
    }

    public List<CommittedChangeList> getLoadedChanges() {
      return myLoadedChanges;
    }

    public List<CommittedChangeList> getIncomingChangesUpdate(int index) {
      return myIncomingChangesUpdates.get(index);
    }
  }
}

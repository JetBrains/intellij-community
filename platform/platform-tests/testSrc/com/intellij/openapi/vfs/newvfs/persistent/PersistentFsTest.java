// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.CacheSwitcher;
import com.intellij.ide.plugins.DynamicPluginsTestUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.openapi.vfs.impl.jar.JarFileSystemImpl;
import com.intellij.openapi.vfs.newvfs.*;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.testFramework.*;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.testFramework.utils.vfs.CheckVFSHealthRule;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.Compressor;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.SuperUserStatus;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndGet;
import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.UsefulTestCase.*;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

public class PersistentFsTest extends BareTestFixtureTestCase {
  @Rule public final TempDirectory tempDirectory = new TempDirectory();
  @Rule public final CheckVFSHealthRule checkVFSHealth = new CheckVFSHealthRule();

  @Test
  public void testAccessingFileByID() {
    File file = tempDirectory.newFile("test.txt");
    VirtualFile vFile = refreshAndFind(file);
    int id = ((VirtualFileWithId)vFile).getId();

    assertEquals("File is unique identified by its fileId",
                 vFile,
                 PersistentFS.getInstance().findFileById(id)
    );

    VfsTestUtil.deleteFile(vFile);
    assertNull("Deleted file can't be found by its fileId anymore",
               PersistentFS.getInstance().findFileById(id));
  }

  @Test
  public void testFileContentHash() throws Exception {
    File file = tempDirectory.newFile("test.txt", "one".getBytes(UTF_8));
    VirtualFile vFile = refreshAndFind(file);
    PersistentFSImpl pfs = (PersistentFSImpl)PersistentFS.getInstance();

    byte[] hash = pfs.contentHashIfStored(vFile);
    assertNull(hash);  // content is not yet loaded

    vFile.contentsToByteArray();
    hash = pfs.contentHashIfStored(vFile);
    assertNotNull(hash);

    WriteAction.runAndWait(() -> VfsUtil.saveText(vFile, "two"));
    byte[] newHash = pfs.contentHashIfStored(vFile);
    assertNotNull(newHash);
    assertFalse(Arrays.equals(hash, newHash));  // different contents should have different hashes

    WriteAction.runAndWait(() -> VfsUtil.saveText(vFile, "one"));
    newHash = pfs.contentHashIfStored(vFile);
    assertArrayEquals(hash, newHash);  // equal contents should have the equal hashes

    VfsTestUtil.deleteFile(vFile);
    assertNotNull(pfs.contentsToByteArray(vFile));  // deleted files preserve content, and thus hash
    assertArrayEquals(hash, pfs.contentHashIfStored(vFile));
  }

  @Test
  public void testFindRootShouldNotBeFooledByRelativePath() {
    File x = tempDirectory.newFile("x.jar");
    VirtualFile vx = refreshAndFind(x);
    JarFileSystem jfs = JarFileSystem.getInstance();
    VirtualFile root = jfs.getJarRootForLocalFile(vx);
    String path = vx.getPath() + "/../" + vx.getName() + JarFileSystem.JAR_SEPARATOR;
    assertSame(PersistentFS.getInstance().findRoot(path, jfs), root);
  }

  @Test
  public void testFindRootMustCreateFileWithCanonicalPath() {
    checkMustCreateRootWithCanonicalPath("x.jar");
  }

  @Test
  public void testFindRootMustCreateFileWithStillCanonicalPath() {
    checkMustCreateRootWithCanonicalPath("x..jar");
  }

  @Test
  public void testFindRootMustCreateFileWithYetAnotherCanonicalPath() {
    checkMustCreateRootWithCanonicalPath("x...jar");
  }

  @Test
  public void testDeleteSubstRoots() {
    IoTestUtil.assumeWindows();
    Ref<VirtualFile> subst = new Ref<>();
    Ref<String> substPath = new Ref<>();

    IoTestUtil.performTestOnWindowsSubst(tempDirectory.getRoot().getPath(), substRoot -> {
      substPath.set(substRoot.getPath());
      subst.set(refreshAndFind(substRoot));
      assertNotNull(substRoot.listFiles());
    });
    subst.get().refresh(false, true);

    VirtualFile[] roots = PersistentFS.getInstance().getRoots(LocalFileSystem.getInstance());
    for (VirtualFile root : roots) {
      String prefix = StringUtil.commonPrefix(root.getPath(), substPath.get());
      assertTrue(prefix, prefix.isEmpty());
    }
  }

  @Test
  public void testLocalRoots() {
    VirtualFile tempRoot = VirtualFileManager.getInstance().findFileByUrl("temp:///");
    assertNotNull(tempRoot);

    for (VirtualFile root : PersistentFS.getInstance().getLocalRoots()) {
      assertTrue("root=" + root, root.isInLocalFileSystem());
      VirtualFileSystem fs = root.getFileSystem();
      assertTrue("fs=" + fs, fs instanceof LocalFileSystem);
      assertFalse("fs=" + fs, fs instanceof TempFileSystem);
    }
  }

  @Ignore("Unstable (see FIXME inside)")
  @Test
  public void rootsAreReloadedAfterVFSReconnect() {
    PersistentFS pfs = PersistentFS.getInstance();
    VirtualFile root0 = VirtualFileManager.getInstance().findFileByUrl("temp:///");
    NewVirtualFileSystem tempFS = (NewVirtualFileSystem)root0.getFileSystem();
    NewVirtualFile root1 = pfs.findRoot("/root1", tempFS);
    NewVirtualFile root2 = pfs.findRoot("/root2", tempFS);
    NewVirtualFile root3 = pfs.findRoot("/root3", tempFS);
    for (VirtualFile root : pfs.getRoots()) {
      System.out.println(root);
    }

    VirtualFile root0TestFile = VfsTestUtil.createFile(root0, "/test-file");
    VirtualFile root1TestFile = VfsTestUtil.createFile(root1, "/test-file");
    VirtualFile root2TestFile = VfsTestUtil.createFile(root2, "/test-file");
    VirtualFile root3TestFile = VfsTestUtil.createFile(root3, "/test-file");

    //reconnect:
    ((PersistentFSImpl)pfs).disconnect();
    //FIXME in a short window while PFS is disconnected async services could come and get 'already disposed'
    //      exception, which fails the test in its .tearDown()
    ((PersistentFSImpl)pfs).connect();

    assertEquals("No roots must be loaded yet", pfs.getRoots().length, 0);

    NewVirtualFile root1_2 = pfs.findRoot("/root1", tempFS);
    assertArrayEquals("root1 must be loaded since explicitly queried",
                      pfs.getRoots(), new VirtualFile[]{root1_2});

    assertNotNull(
      "/root1/test-file must be successfully found (root1 was explicitly loaded)",
      pfs.findFileById(((VirtualFileWithId)root1TestFile).getId())
    );

    assertNotNull(
      "temp:///test-file must be successfully found (root0 must be auto-loaded)",
      pfs.findFileById(((VirtualFileWithId)root0TestFile).getId())
    );

    assertNotNull(
      "/root2/test-file must be successfully found (root2 must be auto-loaded)",
      pfs.findFileById(((VirtualFileWithId)root2TestFile).getId())
    );
    assertNotNull(
      "/root3/test-file must be successfully found (root3 must be auto-loaded)",
      pfs.findFileById(((VirtualFileWithId)root3TestFile).getId())
    );
  }

  @Test
  public void testInvalidJarRootsIgnored() {
    File file = tempDirectory.newFile("file.txt");
    String url = "jar://" + FileUtil.toSystemIndependentName(file.getPath()) + "!/";
    assertNull(VirtualFileManager.getInstance().findFileByUrl(url));
  }

  @Test
  public void testBrokenJarRoots() throws IOException {
    File jarFile = tempDirectory.newFile("empty.jar");
    VirtualFile local = refreshAndFind(jarFile);
    String rootUrl = "jar://" + local.getPath() + "!/";
    String entryUrl = rootUrl + JarFile.MANIFEST_NAME;

    int[] logCount = {0};
    LoggedErrorProcessor.executeWith(new LoggedErrorProcessor() {
      @Override
      public boolean processWarn(@NotNull String category, @NotNull String message, Throwable t) {
        if (message.contains(jarFile.getName())) logCount[0]++;
        return super.processWarn(category, message, t);
      }
    }, () -> {
      VirtualFile jarRoot = VirtualFileManager.getInstance().findFileByUrl(rootUrl);
      assertNotNull(jarRoot);
      assertTrue(jarRoot.isValid());
      assertArrayEquals(VirtualFile.EMPTY_ARRAY, jarRoot.getChildren());
      assertNull(VirtualFileManager.getInstance().findFileByUrl(entryUrl));

      try (Compressor.Jar jar = new Compressor.Jar(jarFile)) {
        jar.addManifest(new Manifest());
      }
      local.refresh(false, false);
      assertTrue(jarRoot.isValid());
      assertEquals(1, jarRoot.getChildren().length);
      assertNotNull(VirtualFileManager.getInstance().findFileByUrl(entryUrl));
    });

    assertEquals(1, logCount[0]);
  }

  @Test
  public void testIterInDbChildrenWorksForRemovedDirsAfterRestart() throws IOException {
    // The test (re)creates .../subDir/subSubDir/Foo.txt hierarchy outside of a watched project and checks for removal events.
    // It starts the real testing "after a restart" - i.e. when launched for the second time using the same system directory.
    // In terms of the persistence, "subDir/" is partially loaded and "subSubDir/" is fully loaded.

    String testDirName = TEMP_DIR_MARKER + getTestName(false);
    Path nestedTestDir = tempDirectory.getRootPath().getParent().resolve(testDirName + "/subDir/subSubDir");
    boolean secondRun = Files.exists(nestedTestDir.getParent().getParent());

    StringBuilder eventLog = new StringBuilder();

    if (secondRun) {
      MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable());
      connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
        @Override
        public void before(@NotNull List<? extends @NotNull VFileEvent> events) {
          for (VFileEvent event : events) {
            if (event instanceof VFileDeleteEvent) {
              process(((VFileDeleteEvent)event).getFile());
            }
          }
        }

        private void process(VirtualFile file) {
          String path = file.getPath();
          eventLog.append(path, path.indexOf(testDirName) + testDirName.length() + 1, path.length()).append('\n');
          ((NewVirtualFile)file).iterInDbChildren().forEach(child -> process(child));
        }
      });
    }

    // Recreating the structure fires VFS removal events.
    VirtualFile vNestedTestDir = WriteAction.computeAndWait(() -> {
      VirtualFile dir = VfsUtil.createDirectoryIfMissing(nestedTestDir.toString());
      dir.createChildData(null, "Foo.txt");
      return dir;
    });
    // Making the directory "fully loaded" in terms of the persistence.
    vNestedTestDir.getChildren();
    // Removing .../subDir via java.io to have VFS events on the next launch.
    FileUtil.delete(nestedTestDir.getParent());

    assumeTrue("Not yet exists: " + nestedTestDir.getParent().getParent(), secondRun);

    assertEquals("""
                   subDir
                   subDir/subSubDir
                   subDir/subSubDir/Foo.txt
                   """,
                 eventLog.toString());
  }

  @Test
  public void testGlobalFSModCountIncreasesOnMostFileAttributesModifications() throws IOException {
    VirtualFile vFile = tempDirectory.newVirtualFile("file.txt");
    HeavyPlatformTestCase.setBinaryContent(vFile, "x".getBytes(UTF_8)); // make various listeners update their VFS views
    ManagingFS managingFS = ManagingFS.getInstance();
    int globalFsModCount = managingFS.getFilesystemModificationCount();

    WriteAction.runAndWait(() -> vFile.setWritable(false));

    assertEquals(globalFsModCount + 1, managingFS.getFilesystemModificationCount());

    FSRecords.getInstance().force();
    assertFalse("FSRecords.force() was just called, must be !dirty",
                FSRecords.getInstance().isDirty());
    ++globalFsModCount;

    int finalGlobalModCount = globalFsModCount;

    HeavyProcessLatch.INSTANCE.performOperation(HeavyProcessLatch.Type.Processing, "This test wants no indices flush", () -> {
      WriteAction.runAndWait(() -> {
        try {
          vFile.setWritable(true);  // 1 change
          vFile.setBinaryContent("foo".getBytes(Charset.defaultCharset())); // content change + length change + maybe timestamp change
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }

        //TODO RC: I don't think it is worth to check exact number of 'changes' -- those look like
        //         an implementation detail. Better to just check _there are_ changes (i.e. modCount
        //         increases) after any sensible change.

        // we check in write action to avoid observing background thread to index stuff
        final int changesCount = 3; //flags, content, length, +...
        assertTrue(
          "fsModCount(=" + managingFS.getFilesystemModificationCount() + ") should +3 at least since before (=" + finalGlobalModCount + ")",
          managingFS.getFilesystemModificationCount() >= finalGlobalModCount + changesCount);
      });
    });
  }

  @Ignore("Now all that changes DO lead to modCount increments")
  @Test
  public void testGlobalFsModCountIncreasesOnAddingFileAttributeOrLengthOrTimestampChanges() throws IOException {
    final VirtualFile vFile = tempDirectory.newVirtualFile("file.txt");
    ManagingFS managingFS = ManagingFS.getInstance();
    final int globalFsModCountBefore = managingFS.getFilesystemModificationCount();

    FSRecordsImpl vfs = FSRecords.getInstance();
    vfs.force();
    assertFalse(vfs.isDirty());

    FileAttribute attribute = new FileAttribute("test.attribute", 1, true);
    WriteAction.runAndWait(() -> {
      try (DataOutputStream output = attribute.writeFileAttribute(vFile)) {
        DataInputOutputUtil.writeINT(output, 1);
      }
    });

    assertEquals(globalFsModCountBefore, managingFS.getFilesystemModificationCount());

    assertTrue(vfs.isDirty());
    vfs.force();
    assertFalse(vfs.isDirty());

    int fileId = ((VirtualFileWithId)vFile).getId();
    vfs.setTimestamp(fileId, vfs.getTimestamp(fileId));
    vfs.setLength(fileId, vfs.getLength(fileId));

    assertEquals(globalFsModCountBefore, managingFS.getFilesystemModificationCount());
    assertFalse(vfs.isDirty());
  }

  @Test
  public void testProcessEventsMustIgnoreDeleteDuplicates() {
    VirtualFile file = tempDirectory.newVirtualFile("file.txt");

    checkEvents("""
                  Before: VFileDeleteEvent->file.txt
                  After: VFileDeleteEvent->file.txt
                  """,

                new VFileDeleteEvent(this, file),
                new VFileDeleteEvent(this, file));
  }

  @Test
  public void testProcessEventsMustGroupDependentEventsCorrectly() {
    VirtualFile file = tempDirectory.newVirtualFile("file.txt");

    checkEvents("""
                  Before: VFileCreateEvent->xx.created VFileDeleteEvent->file.txt
                  After: VFileCreateEvent->xx.created VFileDeleteEvent->file.txt
                  """,

                new VFileDeleteEvent(this, file),
                new VFileCreateEvent(this, file.getParent(), "xx.created", false, null, null, null),
                new VFileDeleteEvent(this, file));
  }

  @Test
  public void testProcessEventsMustBeAwareOfDeleteEventsDomination() {
    VirtualFile file = tempDirectory.newVirtualFile("d/x.txt");

    checkEvents("""
                  Before: VFileDeleteEvent->d
                  After: VFileDeleteEvent->d
                  """,

                new VFileDeleteEvent(this, file.getParent()),
                new VFileDeleteEvent(this, file),
                new VFileDeleteEvent(this, file));
  }

  @Test
  public void testProcessCreateEventsMustFilterOutDuplicates() {
    VirtualFile file = tempDirectory.newVirtualFile("d/x.txt");

    checkEvents("""
                  Before: VFileCreateEvent->xx.created
                  After: VFileCreateEvent->xx.created
                  """,

                new VFileCreateEvent(this, file.getParent(), "xx.created", false, null, null, null),
                new VFileCreateEvent(this, file.getParent(), "xx.created", false, null, null, null));
  }

  @Test
  public void testProcessEventsMustGroupDependentEventsCorrectly2() {
    VirtualFile file = tempDirectory.newVirtualFile("a/b/c/test.txt");

    checkEvents("""
                  Before: VFileCreateEvent->xx.created VFileCreateEvent->xx.created2 VFileDeleteEvent->test.txt
                  After: VFileCreateEvent->xx.created VFileCreateEvent->xx.created2 VFileDeleteEvent->test.txt
                  Before: VFileDeleteEvent->c
                  After: VFileDeleteEvent->c
                  """,

                new VFileDeleteEvent(this, file),
                new VFileCreateEvent(this, file.getParent(), "xx.created", false, null, null, null),
                new VFileCreateEvent(this, file.getParent(), "xx.created2", false, null, null, null),
                new VFileDeleteEvent(this, file.getParent()));
  }

  @Test
  public void testProcessEventsMustGroupDependentEventsCorrectly3() {
    VirtualFile vFile = tempDirectory.newVirtualFile("a/b/c/test.txt");

    checkEvents("""
                  Before: VFileContentChangeEvent->c
                  After: VFileContentChangeEvent->c
                  Before: VFileDeleteEvent->test.txt
                  After: VFileDeleteEvent->test.txt
                  """,

                new VFileContentChangeEvent(this, vFile.getParent(), 0, 0),
                new VFileDeleteEvent(this, vFile));
  }

  @Test
  public void testProcessNestedDeletions() {
    VirtualFile file = tempDirectory.newVirtualFile("a/b/c/test.txt");
    VirtualFile file2 = tempDirectory.newVirtualFile("a/b/c/test2.txt");

    checkEvents("""
                  Before: VFileDeleteEvent->test.txt
                  After: VFileDeleteEvent->test.txt
                  Before: VFileDeleteEvent->c
                  After: VFileDeleteEvent->c
                  """,

                new VFileDeleteEvent(this, file),
                new VFileDeleteEvent(this, file.getParent()),
                new VFileDeleteEvent(this, file2));
  }

  @Test
  public void testProcessContentChangedLikeReconcilableEventsMustResultInSingleBatch() {
    VirtualFile file = tempDirectory.newVirtualFile("a/b/c/test.txt");

    checkEvents("""
                  Before: VFileContentChangeEvent->test.txt VFilePropertyChangeEvent->test.txt VFilePropertyChangeEvent->test.txt
                  After: VFileContentChangeEvent->test.txt VFilePropertyChangeEvent->test.txt VFilePropertyChangeEvent->test.txt
                  """,

                new VFileContentChangeEvent(this, file, 0, 1),
                new VFilePropertyChangeEvent(this, file, VirtualFile.PROP_WRITABLE, false, true),
                new VFilePropertyChangeEvent(this, file, VirtualFile.PROP_ENCODING, ISO_8859_1, UTF_8));
  }

  @Test
  public void testProcessCompositeMoveEvents() {
    VirtualFile testTxt = tempDirectory.newVirtualFile("a/b/c/test.txt");
    VirtualFile newParent = tempDirectory.newVirtualDirectory("a/b/d");

    checkEvents("""
                  Before: VFileMoveEvent->test.txt
                  After: VFileMoveEvent->test.txt
                  Before: VFileDeleteEvent->d
                  After: VFileDeleteEvent->d
                  """,

                new VFileMoveEvent(this, testTxt, newParent),
                new VFileDeleteEvent(this, newParent));
  }

  @Test
  public void testProcessCompositeCopyEvents() {
    VirtualFile file = tempDirectory.newVirtualFile("a/b/c/test.txt");
    VirtualFile newParent = tempDirectory.newVirtualDirectory("a/b/d");

    checkEvents("""
                  Before: VFileCopyEvent->new.txt
                  After: VFileCopyEvent->new.txt
                  Before: VFileDeleteEvent->test.txt
                  After: VFileDeleteEvent->test.txt
                  """,

                new VFileCopyEvent(this, file, newParent, "new.txt"),
                new VFileDeleteEvent(this, file));
  }

  @Test
  public void testProcessCompositeRenameEvents() {
    VirtualFile file = tempDirectory.newVirtualFile("a/b/c/test.txt");
    VirtualFile file2 = tempDirectory.newVirtualFile("a/b/c/test2.txt");

    checkEvents("""
                  Before: VFileDeleteEvent->test2.txt
                  After: VFileDeleteEvent->test2.txt
                  Before: VFilePropertyChangeEvent->test.txt
                  After: VFilePropertyChangeEvent->test2.txt
                  """,

                new VFileDeleteEvent(this, file2),
                new VFilePropertyChangeEvent(this, file, VirtualFile.PROP_NAME, file.getName(), file2.getName()));
  }

  @Test
  public void testCreateNewDirectoryEntailsLoadingAllChildren() throws Exception {
    tempDirectory.newFile("d/d1/x.txt");
    Path source = tempDirectory.getRootPath().resolve("d");
    Path target = tempDirectory.getRootPath().resolve("target");
    VirtualFile vTemp = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDirectory.getRoot());
    assertNotNull(vTemp);
    vTemp.refresh(false, true);
    assertEquals("d", assertOneElement(vTemp.getChildren()).getName());

    Project project = ProjectManager.getInstance().loadAndOpenProject(tempDirectory.getRoot().getPath());
    Disposer.register(getTestRootDisposable(), () -> ProjectManager.getInstance().closeAndDispose(project));

    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    vTemp.refresh(false, true);
    assertChildrenAreLoaded(vTemp);
    VirtualFile vTarget = assertOneElement(((VirtualDirectoryImpl)vTemp).getCachedChildren());
    assertEquals("target", vTarget.getName());
    assertChildrenAreLoaded(vTarget);
    VirtualFile vd1 = assertOneElement(((VirtualDirectoryImpl)vTarget).getCachedChildren());
    assertEquals("d1", vd1.getName());
    assertChildrenAreLoaded(vd1);
    VirtualFile vx = assertOneElement(((VirtualDirectoryImpl)vd1).getCachedChildren());
    assertEquals("x.txt", vx.getName());
  }

  @Test
  public void testCreateNewDirectoryEntailsLoadingAllChildrenExceptExcluded() throws Exception {
    tempDirectory.newFile("d/d1/x.txt");
    Path source = tempDirectory.getRootPath().resolve("d");
    Path target = tempDirectory.getRootPath().resolve("target");
    VirtualFile vTemp = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDirectory.getRoot());
    assertNotNull(vTemp);
    vTemp.refresh(false, true);
    assertEquals("d", assertOneElement(vTemp.getChildren()).getName());

    Project project = ProjectManager.getInstance().loadAndOpenProject(tempDirectory.getRoot().getPath());
    Disposer.register(getTestRootDisposable(), () -> ProjectManager.getInstance().closeAndDispose(project));

    String imlPath = tempDirectory.getRootPath().resolve("temp.iml").toString();
    String url = VfsUtilCore.pathToUrl(target.resolve("d1").toString());
    WriteAction.runAndWait(() -> {
      Module module = ModuleManager.getInstance(project).newModule(imlPath, ModuleTypeManager.getInstance().getDefaultModuleType().getId());
      ModuleRootModificationUtil.updateModel(module, model -> {
        ContentEntry contentEntry = model.addContentEntry(url);
        contentEntry.addExcludeFolder(url);
      });
    });

    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    vTemp.refresh(false, true);
    assertChildrenAreLoaded(vTemp);
    VirtualFile vTarget = assertOneElement(((VirtualDirectoryImpl)vTemp).getCachedChildren());
    assertEquals("target", vTarget.getName());
    assertChildrenAreLoaded(vTarget);
    VirtualFile vd1 = assertOneElement(((VirtualDirectoryImpl)vTarget).getCachedChildren());
    assertEquals("d1", vd1.getName());
    assertFalse(((VirtualDirectoryImpl)vd1).allChildrenLoaded());
    assertEquals(Collections.emptyList(), ((VirtualDirectoryImpl)vd1).getCachedChildren());
  }

  @Test
  public void testRenameInBackgroundDoesntLeadToDuplicateFilesError() throws IOException {
    assumeFalse("Case-insensitive OS expected, can't run on " + SystemInfo.OS_NAME, SystemInfo.isFileSystemCaseSensitive);

    File file = tempDirectory.newFile("rename.txt", "x".getBytes(UTF_8));
    VirtualFile vfile = refreshAndFind(file);
    VirtualDirectoryImpl vTemp = (VirtualDirectoryImpl)vfile.getParent();
    assertFalse(vTemp.allChildrenLoaded());
    VfsUtil.markDirty(true, false, vTemp);
    Files.move(file.toPath(), file.toPath().resolveSibling(file.getName().toUpperCase()), StandardCopyOption.ATOMIC_MOVE);
    VirtualFile[] newChildren = vTemp.getChildren();
    assertOneElement(newChildren);
  }

  @Test
  public void testPersistentFsCacheDoesntContainInvalidFiles() {
    File file = tempDirectory.newFile("subDir1/subDir2/subDir3/file.txt");
    VirtualFileSystemEntry vFile = (VirtualFileSystemEntry)refreshAndFind(file);
    VirtualFileSystemEntry vSubDir3 = vFile.getParent();
    VirtualFileSystemEntry vSubDir2 = vSubDir3.getParent();
    VirtualFileSystemEntry vSubDir1 = vSubDir2.getParent();
    PersistentFSImpl fs = (PersistentFSImpl)PersistentFS.getInstance();
    VirtualFileSystemEntry[] hardReferenceHolder = {vFile, vSubDir3, vSubDir2, vSubDir1};

    VfsTestUtil.deleteFile(vSubDir1);

    for (VirtualFileSystemEntry f : hardReferenceHolder) {
      assertFalse("file is valid but deleted " + f.getName(), f.isValid());
    }

    for (VirtualFileSystemEntry f : hardReferenceHolder) {
      assertNull(fs.getCachedDir(f.getId()));
      assertNull(fs.findFileById(f.getId()));
    }

    for (VirtualFileSystemEntry f : fs.getDirCache()) {
      assertTrue(f.isValid());
    }
  }

  @Test
  public void testConcurrentListAllDoesntCauseDuplicateFileIds() throws Exception {
    PersistentFSImpl pfs = (PersistentFSImpl)PersistentFS.getInstance();
    Application application = ApplicationManager.getApplication();
    int enoughAttempts = 1000;
    for (int attempt = 0; attempt < enoughAttempts; attempt++) {
      File file1 = tempDirectory.newFile("dir." + attempt + "/file1", "text1".getBytes(UTF_8));
      Path file2 = file1.toPath().resolveSibling("file2");

      VirtualDirectoryImpl vTemp = (VirtualDirectoryImpl)refreshAndFind(file1).getParent();
      assertFalse(vTemp.allChildrenLoaded());
      Files.writeString(file2, "text2");

      Future<List<? extends ChildInfo>> f1 = application.executeOnPooledThread(() -> pfs.listAll(vTemp));
      Future<List<? extends ChildInfo>> f2 = application.executeOnPooledThread(() -> pfs.listAll(vTemp));
      List<? extends ChildInfo> children1 = f1.get();
      List<? extends ChildInfo> children2 = f2.get();

      int[] nameIds1 = children1.stream().mapToInt(n -> n.getNameId()).toArray();
      int[] nameIds2 = children2.stream().mapToInt(n -> n.getNameId()).toArray();

      // there can be one or two children, depending on whether the VFS refreshed in time or not.
      // but in any case, there must not be duplicate ids (i.e. files with the same name but different getId())
      for (int nameIndexIn1 = 0; nameIndexIn1 < nameIds1.length; nameIndexIn1++) {
        int nameId1 = nameIds1[nameIndexIn1];
        int nameIndexIn2 = ArrayUtil.find(nameIds2, nameId1);
        if (nameIndexIn2 >= 0) {
          int childId1 = children1.get(nameIndexIn1).getId();
          int childId2 = children2.get(nameIndexIn2).getId();
          assertEquals("[attempt: " + attempt + "] duplicate ids found: \nchildren1=" + children1 + "\nchildren2=" + children2,
                       childId1, childId2);
        }
      }
    }
  }

  @Test
  public void testMustNotDuplicateIdsOnRenameWithCaseChanged() {
    PersistentFSImpl fs = (PersistentFSImpl)PersistentFS.getInstance();

    File file = tempDirectory.newFile("file.txt", "x".getBytes(UTF_8));
    VirtualFile vDir = refreshAndFind(file.getParentFile());
    VirtualFile vf = assertOneElement(vDir.getChildren());
    assertEquals("file.txt", vf.getName());
    List<Future<?>> futures = new ArrayList<>();
    String oldName = file.getName();
    for (int i = 0; i < 100; i++) {
      int u = i % oldName.length();
      Future<?> f = ApplicationManager.getApplication().executeOnPooledThread(() -> {
        String newName = oldName.substring(0, u) + Character.toUpperCase(oldName.charAt(u)) + oldName.substring(u + 1);
        try {
          Files.move(file.toPath(), file.toPath().resolveSibling(newName), StandardCopyOption.ATOMIC_MOVE);
        }
        catch (NoSuchFileException ignored) {
        }
        catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      });
      futures.add(f);
    }
    for (int i = 0; i < 10; i++) {
      Future<?> f = ApplicationManager.getApplication().executeOnPooledThread(() -> {
        for (int u = 0; u < 100; u++) {
          List<? extends ChildInfo> infos = fs.listAll(vDir);
          assertOneElement(infos);
        }
      });
      futures.add(f);
    }

    runInEdtAndWait(() -> {
      for (Future<?> future : futures) {
        PlatformTestUtil.waitForFuture(future, 10_000);
      }
    });
  }

  @Test
  public void testReadOnlyFsCachesLength() throws IOException {
    String text = "<virtualFileSystem implementationClass=\"" +
                  TracingJarFileSystemTestWrapper.class.getName() +
                  "\" key=\"jar-wrapper\" physical=\"true\"/>";
    Disposable disposable = runInEdtAndGet(() -> DynamicPluginsTestUtil.loadExtensionWithText(text, "com.intellij"));

    try {
      File generationDir = tempDirectory.newDirectory("gen");
      File testDir = tempDirectory.newDirectory("test");
      String jarName = "test.jar";
      String entryName = "Some.java";
      String[] contents = {"class Some {}", "class Some { void m() {} }", "class Some { void mmm() {} }"};

      File zipFile = zipWithEntry(jarName, generationDir, testDir, entryName, contents[0]);
      String url = "jar-wrapper://" + FileUtil.toSystemIndependentName(zipFile.getPath()) + "!/" + entryName;
      VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
      file.refresh(false, false);
      assertTrue(file.isValid());
      assertEquals(contents[0], new String(file.contentsToByteArray(), UTF_8));
      TracingJarFileSystemTestWrapper fs = (TracingJarFileSystemTestWrapper)file.getFileSystem();

      zipFile = zipWithEntry(jarName, generationDir, testDir, entryName, contents[1]);
      VfsUtil.markDirtyAndRefresh(false, true, true, zipFile);
      int attrCallCount = fs.getAttributeCallCount();
      file.refresh(false, false);
      assertTrue(file.isValid());
      assertEquals(contents[1], new String(file.contentsToByteArray(), UTF_8));

      zipFile = zipWithEntry(jarName, generationDir, testDir, entryName, contents[2]);
      VfsUtil.markDirtyAndRefresh(false, true, true, zipFile);
      assertNotEquals(attrCallCount, fs.getAttributeCallCount());  // we should read length from physical FS
      file.refresh(false, false);
      assertTrue(file.isValid());
      assertEquals(contents[2], new String(file.contentsToByteArray(), UTF_8));

      attrCallCount = fs.getAttributeCallCount();
      for (int i = 0; i < 3; i++) {
        file.getLength();
        assertEquals(attrCallCount, fs.getAttributeCallCount());  // ensure it's cached
      }
    }
    finally {
      runInEdtAndWait(() -> Disposer.dispose(disposable));
    }
  }

  @Test
  public void testDoNotRecalculateLengthIfEndOfInputStreamIsNotReached() throws IOException {
    String text = "<virtualFileSystem implementationClass=\"" +
                  TracingJarFileSystemTestWrapper.class.getName() +
                  "\" key=\"jar-wrapper\" physical=\"true\"/>";
    Disposable disposable = runInEdtAndGet(() -> DynamicPluginsTestUtil.loadExtensionWithText(text, "com.intellij"));

    try {
      File generationDir = tempDirectory.newDirectory("gen");
      File testDir = tempDirectory.newDirectory("test");
      String jarName = "test.jar";
      String entryName = "Some.java";
      String content = "class Some {}";

      File zipFile = zipWithEntry(jarName, generationDir, testDir, entryName, content);
      String url = "jar-wrapper://" + FileUtil.toSystemIndependentName(zipFile.getPath()) + "!/" + entryName;
      VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
      file.refresh(false, false);

      TracingJarFileSystemTestWrapper fs = (TracingJarFileSystemTestWrapper)file.getFileSystem();
      int attributeCallCount = fs.getAttributeCallCount();

      try (InputStream stream = file.getInputStream()) {
        // just read single byte
        @SuppressWarnings("unused") int read = stream.read();
      }
      assertEquals(attributeCallCount, fs.getAttributeCallCount());

      //noinspection EmptyTryBlock,unused
      try (InputStream stream = file.getInputStream()) {
        // just close
      }
      assertEquals(attributeCallCount, fs.getAttributeCallCount());
    }
    finally {
      runInEdtAndWait(() -> Disposer.dispose(disposable));
    }
  }

  @Test
  public void testDeleteJarRootInsideJarMustCauseDeleteLocalJarFile() throws IOException {
    File generationDir = tempDirectory.newDirectory("gen");
    File testDir = tempDirectory.newDirectory("test");

    File jarFile = zipWithEntry("test.jar", generationDir, testDir, "Some.java", "class Some {}");
    VirtualFile vFile = VirtualFileManager.getInstance().refreshAndFindFileByUrl(VfsUtilCore.pathToUrl(jarFile.getPath()));
    VirtualFile jarVFile = JarFileSystem.getInstance().getJarRootForLocalFile(vFile);
    FileUtil.delete(jarFile);
    List<VFileEvent> events = new ArrayList<>();
    ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable())
      .subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
        @Override
        public void after(@NotNull List<? extends @NotNull VFileEvent> e) {
          events.addAll(e);
        }
      });

    jarVFile.refresh(false, false);
    events.sort(Comparator.comparing((VFileEvent e) -> e.getFile().getUrl()));
    assertEqualUnorderedEvents(events, new VFileDeleteEvent(this, vFile), new VFileDeleteEvent(this, jarVFile));
  }

  @Test
  public void testDeleteFileDeepInsideJarFileMustCauseContentChangeForLocalJar() throws IOException {
    File generationDir = tempDirectory.newDirectory("gen");
    File testDir = tempDirectory.newDirectory("test");

    File jarFile = zipWithEntry("test.jar", generationDir, testDir, "web.xml", "<web/>");
    VirtualFile vFile = VirtualFileManager.getInstance().refreshAndFindFileByUrl(VfsUtilCore.pathToUrl(jarFile.getPath()));
    VirtualFile jarVFile = JarFileSystem.getInstance().getJarRootForLocalFile(vFile);
    VirtualFile webXml = jarVFile.findChild("web.xml");
    File newJarFile = zipWithEntry("test2.jar", generationDir, testDir, "x.java", "class X{}");
    FileUtil.copy(newJarFile, jarFile);
    List<VFileEvent> events = new ArrayList<>();
    ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable())
      .subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
        @Override
        public void after(@NotNull List<? extends @NotNull VFileEvent> e) {
          events.addAll(e);
        }
      });

    ((JarFileSystemImpl)JarFileSystem.getInstance()).markDirtyAndRefreshVirtualFileDeepInsideJarForTest(webXml);

    assertEqualUnorderedEvents(events, new VFileDeleteEvent(this, webXml),
                               new VFileContentChangeEvent(this, vFile, 0, 0));
  }

  @Test
  public void testFileContentChangeEventsMustDifferentiateCaseSensitivityToggledFiles() throws IOException {
    IoTestUtil.assumeWindows();
    assumeTrue("'fsutil.exe' needs elevated privileges to work", SuperUserStatus.isSuperUser());

    File dir = tempDirectory.newDirectory();
    VirtualFile vDir = refreshAndFind(dir);
    IoTestUtil.setCaseSensitivity(dir, true);
    File file = new File(dir, "file.txt");
    assertTrue(file.createNewFile());
    File FILE = new File(dir, "FILE.TXT");
    assertTrue(FILE.createNewFile());
    VirtualFile vFile = refreshAndFind(file);
    VirtualFile vFILE = refreshAndFind(FILE);

    List<VFileEvent> events = new ArrayList<>();
    ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable())
      .subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
        @Override
        public void after(@NotNull List<? extends @NotNull VFileEvent> e) {
          for (VFileEvent event : e) {
            VirtualFile evFile = event.getFile();
            if (vDir.equals(evFile.getParent())) {
              events.add(event);
            }
          }
        }
      });

    FileUtil.writeToFile(file, "content");
    vFile.refresh(false, false);
    vFILE.refresh(false, false);
    assertEqualUnorderedEvents(events,
                               new VFileContentChangeEvent(this, vFile, -1, -1));

    events.clear();

    FileUtil.writeToFile(FILE, "content");
    vFile.refresh(false, false);
    vFILE.refresh(false, false);
    assertEqualUnorderedEvents(events,
                               new VFileContentChangeEvent(this, vFILE, -1, -1));

    events.clear();

    FileUtil.writeToFile(file, "content2");
    FileUtil.writeToFile(FILE, "content2");
    vDir.refresh(false, true);
    assertEqualUnorderedEvents(events,
                               new VFileContentChangeEvent(this, vFile, -1, -1),
                               new VFileContentChangeEvent(this, vFILE, -1, -1));

    events.clear();

    FileUtil.delete(file);
    FileUtil.delete(FILE);
    vDir.refresh(false, true);
    assertEqualUnorderedEvents(events,
                               new VFileDeleteEvent(this, vFile),
                               new VFileDeleteEvent(this, vFILE));

    events.clear();

    assertTrue(file.createNewFile());
    assertTrue(FILE.createNewFile());
    vDir.refresh(false, true);
    assertEqualUnorderedEvents(events,
                               new VFileCreateEvent(this, vDir, vFile.getName(), false, null, null, null),
                               new VFileCreateEvent(this, vDir, vFILE.getName(), false, null, null, null));
  }

  @Test
  public void testSetBinaryContentMustGenerateVFileContentChangedEventWithCorrectOldLength() throws IOException {
    VirtualFile vDir = refreshAndFind(tempDirectory.newDirectory());
    VirtualFile vFile = HeavyPlatformTestCase.createChildData(vDir, "file.txt");
    UIUtil.pump();
    List<VFileEvent> events = new ArrayList<>();
    ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable())
      .subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
        @Override
        public void after(@NotNull List<? extends @NotNull VFileEvent> e) {
          for (VFileEvent event : e) {
            VirtualFile evFile = event.getFile();
            if (vDir.equals(evFile.getParent())) {
              events.add(event);
            }
          }
        }
      });

    WriteAction.runAndWait(() -> vFile.setBinaryContent(new byte[]{1, 2, 3}));
    VFileEvent event = assertOneElement(events);
    assertInstanceOf(event, VFileContentChangeEvent.class);

    assertEquals(0, ((VFileContentChangeEvent)event).getOldLength());
    assertEquals(3, ((VFileContentChangeEvent)event).getNewLength());

    events.clear();
  }

  //@IJIgnore(issue = "IJPL-149673")
  @Test
  public void testChildMove() throws IOException {
    final File firstDirIoFile = tempDirectory.newDirectory("dir1");
    final File secondDirIoFile = tempDirectory.newDirectory("dir2");

    final VirtualFile firstDir = refreshAndFind(firstDirIoFile);
    final VirtualFile secondDir = refreshAndFind(secondDirIoFile);

    final VirtualFile xxx = WriteAction.computeAndWait(() -> firstDir.createChildDirectory(this, "xxx"));
    final VirtualFile xxxFooBar = WriteAction.computeAndWait(() -> xxx.createChildData(this, "foo.bar"));
    final VirtualFile someTxt = WriteAction.computeAndWait(() -> firstDir.createChildData(this, "some.txt"));

    assertEquals(List.of(xxx, someTxt), Arrays.asList(firstDir.getChildren()));
    assertEquals(List.of(xxxFooBar), Arrays.asList(xxx.getChildren()));

    assertEmpty(secondDir.getChildren());

    final int firstDirId = ((NewVirtualFile)firstDir).getId();
    final int secondDirId = ((NewVirtualFile)secondDir).getId();

    final int xxxId = ((NewVirtualFile)xxx).getId();
    final int xxxFooBarId = ((NewVirtualFile)xxxFooBar).getId();
    final int someTxtId = ((NewVirtualFile)someTxt).getId();

    CacheSwitcher.INSTANCE.switchIndexAndVfs(null, null, "resetting vfs", () -> {
      return null;
    });

    PersistentFSImpl.moveChildrenRecords(firstDirId, secondDirId);

    assertEmpty(refreshAndFind(firstDirIoFile).getChildren());

    final VirtualFile[] movedChildren = refreshAndFind(secondDirIoFile).getChildren();
    assertEquals(List.of(xxxId, someTxtId), ContainerUtil.map(movedChildren, it -> ((NewVirtualFile)it).getId()));
    assertEquals(List.of("xxx", "some.txt"), ContainerUtil.map(movedChildren, VirtualFile::getName));

    final VirtualFile[] movedGrandChildren = ContainerUtil.find(movedChildren, it -> "xxx".equals(it.getName())).getChildren();
    assertEquals(List.of(xxxFooBarId), ContainerUtil.map(movedGrandChildren, it -> ((NewVirtualFile)it).getId()));
    assertEquals(List.of("foo.bar"), ContainerUtil.map(movedGrandChildren, VirtualFile::getName));
  }

  @Test
  public void testContentReadingWhileModification() throws IOException {
    byte[] initialContent = StringUtil.repeat(
      "one_two",
      PersistentFSConstants.MAX_FILE_LENGTH_TO_CACHE / "one_two".length() - 1
    ).getBytes(UTF_8);
    File file = tempDirectory.newFile("test.txt", initialContent);
    VirtualFile vFile = refreshAndFind(file);
    int id = ((VirtualFileWithId)vFile).getId();
    vFile.contentsToByteArray();

    InputStream stream = FSRecords.getInstance().readContent(id);
    assertNotNull("Content must be cached", stream);
    byte[] bytes = stream.readNBytes(initialContent.length);
    assertArrayEquals(initialContent, bytes);
    InputStream stream2 = FSRecords.getInstance().readContent(id);
    byte[] portion1 = stream2.readNBytes(40);
    FSRecords.getInstance().writeContent(id, ByteArraySequence.EMPTY, true);
    InputStream stream3 = FSRecords.getInstance().readContent(id);
    assertEquals(-1, stream3.read());
    byte[] portion2 = stream2.readNBytes(initialContent.length - 40);
    assertArrayEquals(initialContent, ArrayUtil.mergeArrays(portion1, portion2));
  }

  @Test
  public void testHugeFileContentIsNotCachedInVFS() throws IOException {
    byte[] hugeContent = StringUtil.repeat(
      "anything",
      PersistentFSConstants.MAX_FILE_LENGTH_TO_CACHE / "anything".length() + 1
    ).getBytes(UTF_8);
    assertTrue("Content must be larger than limit",
               hugeContent.length > PersistentFSConstants.MAX_FILE_LENGTH_TO_CACHE);

    File file = tempDirectory.newFile("test.txt", hugeContent);
    VirtualFile vFile = refreshAndFind(file);
    int id = ((VirtualFileWithId)vFile).getId();

    //load content: for smaller files this should trigger file content caching, but not for huge files
    vFile.contentsToByteArray();

    assertNull("Content must NOT be cached in VFS during reading",
               FSRecords.getInstance().readContent(id));

    //save content: for smaller files this should trigger file content caching, but not for huge files
    hugeContent[10] = 'A';//introduce a change
    ApplicationManager.getApplication().invokeAndWait(() -> {
      try {
        ApplicationManager.getApplication().runWriteAction((ThrowableComputable<Object, IOException>)() -> {
          try (var out = vFile.getOutputStream(this)) {
            out.write(hugeContent);
          }
          return null;
        });
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
    assertNull("Content must NOT be cached in VFS during saving",
               FSRecords.getInstance().readContent(id));
  }

  @Test
  public void testSearchingForJarRootWhenItsNotCached() throws IOException {
    // IDEA-341011 PersistentFSImpl.cacheMissedRootFromPersistence fails to find jars because it uses name instead of path
    PersistentFSImpl fs = (PersistentFSImpl)PersistentFS.getInstance();
    // ensure clean VFS
    fs.disconnect();
    fs.connect();

    File root = tempDirectory.newDirectory("out");
    FileUtil.writeToFile(new File(root, "fileInJar.txt"), "some text");

    File jar = IoTestUtil.createTestJar(tempDirectory.newFile("test.jar"), root);
    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(jar);
    VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(vFile);

    VirtualFile fileInJar = jarRoot.findChild("fileInJar.txt");
    assertNotNull(fileInJar);

    // clear in-memory caches:
    fs.disconnect();
    fs.connect();

    // this will trigger PersistentFSImpl.cacheMissedRootFromPersistence which should be able to find jar root
    VirtualFile fileInJar2 = ManagingFS.getInstance().findFileById(((VirtualFileWithId)fileInJar).getId());

    assertNotNull(fileInJar2);
  }


  private static void checkEvents(String expectedEvents, VFileEvent... eventsToApply) {
    StringBuilder log = new StringBuilder();

    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
    try {
      connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
        @Override
        public void before(@NotNull List<? extends @NotNull VFileEvent> events) {
          log("Before:", events);
        }

        @Override
        public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
          log("After:", events);
        }

        private void log(String prefix, List<? extends VFileEvent> events) {
          log.append(prefix);
          for (VFileEvent e : events) {
            log.append(' ').append(e.getClass().getSimpleName()).append("->").append(PathUtil.getFileName(e.getPath()));
          }
          log.append('\n');
        }
      });

      WriteCommandAction.runWriteCommandAction(null, () -> RefreshQueue.getInstance().processEvents(false, List.of(eventsToApply)));
    }
    finally {
      connection.disconnect();
    }

    assertEquals(expectedEvents, log.toString());
  }

  private static void assertChildrenAreLoaded(VirtualFile file) {
    assertTrue("children not loaded: " + file, ((VirtualDirectoryImpl)file).allChildrenLoaded());
    assertTrue("children not loaded: " + file, PersistentFS.getInstance().areChildrenLoaded(file));
  }

  private static class TracingJarFileSystemTestWrapper extends JarFileSystemImpl {
    private final AtomicInteger myAttributeCallCount = new AtomicInteger();

    @Override
    public @Nullable FileAttributes getAttributes(@NotNull VirtualFile file) {
      myAttributeCallCount.incrementAndGet();
      return super.getAttributes(file);
    }

    private int getAttributeCallCount() {
      return myAttributeCallCount.get();
    }

    @Override
    public @NotNull String getProtocol() {
      return "jar-wrapper";
    }
  }

  private static File zipWithEntry(String fileName, File generationDir, File outputDir, String entryName, String entryContent)
    throws IOException {
    File zipFile = new File(generationDir, fileName);
    try (Compressor.Zip zip = new Compressor.Zip(zipFile)) {
      zip.addFile(entryName, entryContent.getBytes(UTF_8));
    }

    File outputFile = new File(outputDir, fileName);
    try (OutputStream out = Files.newOutputStream(outputFile.toPath())) {
      Files.copy(zipFile.toPath(), out);  // unlike `Files#copy(Path, Path)`, allows to overwrite an opened file on Windows
    }
    VfsUtil.markDirtyAndRefresh(false, true, true, outputFile);
    return outputFile;
  }

  private void assertEqualUnorderedEvents(List<? extends VFileEvent> actual, VFileEvent... expected) {
    Set<VFileEvent> act = new HashSet<>(ContainerUtil.map(actual, e -> ignoreCrazyVFileContentChangedEquals(e)));
    Set<VFileEvent> exp = new HashSet<>(ContainerUtil.map(expected, e -> ignoreCrazyVFileContentChangedEquals(e)));
    if (!act.equals(exp)) {
      String expectedString = UsefulTestCase.toString(Arrays.asList(expected));
      String actualString = UsefulTestCase.toString(actual);
      assertEquals(expectedString, actualString);
      fail("Warning! 'toString' does not reflect the difference.\nExpected: " + expectedString + "\nActual: " + actualString);
    }
  }

  private VFileEvent ignoreCrazyVFileContentChangedEquals(VFileEvent exp) {
    if (exp instanceof VFileContentChangeEvent) {
      exp = new VFileContentChangeEvent(this, ((VFileContentChangeEvent)exp).getFile(), 0, 0, -1, -1, -1, -1);
    }
    return exp;
  }

  @NotNull
  private static VirtualFile refreshAndFind(File file) {
    return Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file), file.getPath());
  }

  private void checkMustCreateRootWithCanonicalPath(String jarName) {
    File x = tempDirectory.newFile(jarName);
    refreshAndFind(x);
    JarFileSystem jfs = JarFileSystem.getInstance();
    String path = x.getPath() + "/../" + x.getName() + JarFileSystem.JAR_SEPARATOR;
    NewVirtualFile root = PersistentFS.getInstance().findRoot(path, jfs);
    assertNotNull(path, root);
    assertFalse(root.getPath(), root.getPath().contains("../"));
    assertFalse(root.getPath(), root.getPath().contains("/.."));
  }
}

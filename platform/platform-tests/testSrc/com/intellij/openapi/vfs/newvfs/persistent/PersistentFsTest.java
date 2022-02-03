// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.CacheSwitcher;
import com.intellij.ide.plugins.DynamicPluginsTestUtil;
import com.intellij.openapi.Disposable;
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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.openapi.vfs.impl.jar.JarFileSystemImpl;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.testFramework.*;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.testFramework.rules.TempDirectory;
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
import org.junit.Rule;
import org.junit.Test;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

public class PersistentFsTest extends BareTestFixtureTestCase {
  @Rule public TempDirectory tempDirectory = new TempDirectory();

  @Test
  public void testAccessingFileByID() {
    File file = tempDirectory.newFile("test.txt");
    VirtualFile vFile = refreshAndFind(file);
    int id = ((VirtualFileWithId)vFile).getId();
    assertEquals(vFile, PersistentFS.getInstance().findFileById(id));
    VfsTestUtil.deleteFile(vFile);
    assertNull(PersistentFS.getInstance().findFileById(id));
  }

  @NotNull
  private static VirtualFile refreshAndFind(File file) {
    return Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file), file.getPath());
  }

  @Test
  public void testFileContentHash() throws Exception {
    File file = tempDirectory.newFile("test.txt", "one".getBytes(StandardCharsets.UTF_8));
    VirtualFile vFile = refreshAndFind(file);
    PersistentFSImpl fs = (PersistentFSImpl)PersistentFS.getInstance();

    byte[] hash = PersistentFSImpl.getContentHashIfStored(vFile);
    assertNull(hash);  // content is not yet loaded

    vFile.contentsToByteArray();
    hash = PersistentFSImpl.getContentHashIfStored(vFile);
    assertNotNull(hash);

    WriteAction.runAndWait(() -> VfsUtil.saveText(vFile, "two"));
    byte[] newHash = PersistentFSImpl.getContentHashIfStored(vFile);
    assertNotNull(newHash);
    assertFalse(Arrays.equals(hash, newHash));  // different contents should have different hashes

    WriteAction.runAndWait(() -> VfsUtil.saveText(vFile, "one"));
    newHash = PersistentFSImpl.getContentHashIfStored(vFile);
    assertArrayEquals(hash, newHash);  // equal contents should have the equal hashes

    VfsTestUtil.deleteFile(vFile);
    assertNotNull(fs.contentsToByteArray(vFile));  // deleted files preserve content, and thus hash
    assertArrayEquals(hash, PersistentFSImpl.getContentHashIfStored(vFile));
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

  @Test
  public void testDeleteSubstRoots() {
    IoTestUtil.assumeWindows();

    File substRoot = IoTestUtil.createSubst(tempDirectory.getRoot().getPath());
    VirtualFile subst;
    try {
      subst = refreshAndFind(substRoot);
      assertNotNull(substRoot.listFiles());
    }
    finally {
      IoTestUtil.deleteSubst(substRoot.getPath());
    }
    subst.refresh(false, true);

    VirtualFile[] roots = PersistentFS.getInstance().getRoots(LocalFileSystem.getInstance());
    for (VirtualFile root : roots) {
      String prefix = StringUtil.commonPrefix(root.getPath(), substRoot.getPath());
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
      public boolean processWarn(@NotNull String category, String message, Throwable t) {
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

    String testDirName = UsefulTestCase.TEMP_DIR_MARKER + getTestName(false);
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

    assertEquals("subDir\n" +
                 "subDir/subSubDir\n" +
                 "subDir/subSubDir/Foo.txt\n",
                 eventLog.toString());
  }

  @Test
  public void testModCountIncreases() throws IOException {
    VirtualFile vFile = tempDirectory.newVirtualFile("file.txt");
    HeavyPlatformTestCase.setBinaryContent(vFile, "x".getBytes(StandardCharsets.UTF_8)); // make various listeners update their VFS views
    ManagingFS managingFS = ManagingFS.getInstance();
    int inSessionModCount = managingFS.getModificationCount();
    int globalModCount = managingFS.getFilesystemModificationCount();
    int parentModCount = managingFS.getModificationCount(vFile.getParent());

    WriteAction.runAndWait(() -> vFile.setWritable(false));

    assertEquals(globalModCount + 1, managingFS.getModificationCount(vFile));
    assertEquals(globalModCount + 1, managingFS.getFilesystemModificationCount());
    assertEquals(parentModCount, managingFS.getModificationCount(vFile.getParent()));
    assertEquals(inSessionModCount + 1, managingFS.getModificationCount());

    FSRecords.force();
    assertFalse(FSRecords.isDirty());
    ++globalModCount;

    int finalGlobalModCount = globalModCount;

    HeavyProcessLatch.INSTANCE.performOperation(HeavyProcessLatch.Type.Processing, "This test wants no indices flush", ()-> {
      WriteAction.runAndWait(() -> {
        long timestamp = vFile.getTimeStamp();
        int finalInSessionModCount = managingFS.getModificationCount();
        try {
          vFile.setWritable(true);  // 1 change
          vFile.setBinaryContent("foo".getBytes(Charset.defaultCharset())); // content change + length change + maybe timestamp change
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }

        // we check in write action to avoid observing background thread to index stuff
        int changesCount = timestamp == vFile.getTimeStamp() ? 3 : 4;
        assertEquals(finalGlobalModCount + changesCount, managingFS.getModificationCount(vFile));
        assertEquals(finalGlobalModCount + changesCount, managingFS.getFilesystemModificationCount());
        assertEquals(finalInSessionModCount + changesCount, managingFS.getModificationCount());
        assertEquals(parentModCount, managingFS.getModificationCount(vFile.getParent()));
      });
    });
  }

  @Test
  public void testModCountNotIncreases() throws IOException {
    VirtualFile vFile = tempDirectory.newVirtualFile("file.txt");
    ManagingFS managingFS = ManagingFS.getInstance();
    int globalModCount = managingFS.getFilesystemModificationCount();
    int parentModCount = managingFS.getModificationCount(vFile.getParent());
    int fileModCount = managingFS.getModificationCount(vFile);
    int inSessionModCount = managingFS.getModificationCount();

    FSRecords.force();
    assertFalse(FSRecords.isDirty());

    FileAttribute attribute = new FileAttribute("test.attribute", 1, true);
    WriteAction.runAndWait(() -> {
      try(DataOutputStream output = attribute.writeAttribute(vFile)) {
        DataInputOutputUtil.writeINT(output, 1);
      }
    });

    assertEquals(fileModCount, managingFS.getModificationCount(vFile));
    assertEquals(globalModCount, managingFS.getFilesystemModificationCount());
    assertEquals(parentModCount, managingFS.getModificationCount(vFile.getParent()));
    assertEquals(inSessionModCount + 1, managingFS.getModificationCount());

    assertTrue(FSRecords.isDirty());
    FSRecords.force();
    assertFalse(FSRecords.isDirty());

    int fileId = ((VirtualFileWithId)vFile).getId();
    FSRecords.setTimestamp(fileId, FSRecords.getTimestamp(fileId));
    FSRecords.setLength(fileId, FSRecords.getLength(fileId));

    assertEquals(fileModCount, managingFS.getModificationCount(vFile));
    assertEquals(globalModCount, managingFS.getFilesystemModificationCount());
    assertEquals(parentModCount, managingFS.getModificationCount(vFile.getParent()));
    assertEquals(inSessionModCount + 1, managingFS.getModificationCount());
    assertFalse(FSRecords.isDirty());
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

      WriteCommandAction.runWriteCommandAction(null, () -> PersistentFS.getInstance().processEvents(Arrays.asList(eventsToApply)));
    }
    finally {
      connection.disconnect();
    }

    assertEquals(expectedEvents, log.toString());
  }

  @Test
  public void testProcessEventsMustIgnoreDeleteDuplicates() {
    VirtualFile file = tempDirectory.newVirtualFile("file.txt");

    checkEvents("Before: VFileDeleteEvent->file.txt\n" +
                "After: VFileDeleteEvent->file.txt\n",

                new VFileDeleteEvent(this, file, false),
                new VFileDeleteEvent(this, file, false));
  }

  @Test
  public void testProcessEventsMustGroupDependentEventsCorrectly() {
    VirtualFile file = tempDirectory.newVirtualFile("file.txt");

    checkEvents("Before: VFileCreateEvent->xx.created VFileDeleteEvent->file.txt\n" +
                "After: VFileCreateEvent->xx.created VFileDeleteEvent->file.txt\n",

                new VFileDeleteEvent(this, file, false),
                new VFileCreateEvent(this, file.getParent(), "xx.created", false, null, null, false, null),
                new VFileDeleteEvent(this, file, false));
  }

  @Test
  public void testProcessEventsMustBeAwareOfDeleteEventsDomination() {
    VirtualFile file = tempDirectory.newVirtualFile("d/x.txt");

    checkEvents("Before: VFileDeleteEvent->d\n" +
                "After: VFileDeleteEvent->d\n",

                new VFileDeleteEvent(this, file.getParent(), false),
                new VFileDeleteEvent(this, file, false),
                new VFileDeleteEvent(this, file, false));
  }

  @Test
  public void testProcessCreateEventsMustFilterOutDuplicates() {
    VirtualFile file = tempDirectory.newVirtualFile("d/x.txt");

    checkEvents("Before: VFileCreateEvent->xx.created\n" +
                "After: VFileCreateEvent->xx.created\n",

                new VFileCreateEvent(this, file.getParent(), "xx.created", false, null, null, false, null),
                new VFileCreateEvent(this, file.getParent(), "xx.created", false, null, null, false, null)                );
  }

  @Test
  public void testProcessEventsMustGroupDependentEventsCorrectly2() {
    VirtualFile file = tempDirectory.newVirtualFile("a/b/c/test.txt");

    checkEvents("Before: VFileCreateEvent->xx.created VFileCreateEvent->xx.created2 VFileDeleteEvent->test.txt\n" +
                "After: VFileCreateEvent->xx.created VFileCreateEvent->xx.created2 VFileDeleteEvent->test.txt\n" +
                "Before: VFileDeleteEvent->c\n" +
                "After: VFileDeleteEvent->c\n",

                new VFileDeleteEvent(this, file, false),
                new VFileCreateEvent(this, file.getParent(), "xx.created", false, null, null, false, null),
                new VFileCreateEvent(this, file.getParent(), "xx.created2", false, null, null, false, null),
                new VFileDeleteEvent(this, file.getParent(), false));
  }

  @Test
  public void testProcessEventsMustGroupDependentEventsCorrectly3() {
    VirtualFile vFile = tempDirectory.newVirtualFile("a/b/c/test.txt");

    checkEvents("Before: VFileContentChangeEvent->c\n" +
                "After: VFileContentChangeEvent->c\n" +
                "Before: VFileDeleteEvent->test.txt\n" +
                "After: VFileDeleteEvent->test.txt\n",

                new VFileContentChangeEvent(this, vFile.getParent(), 0, 0, false),
                new VFileDeleteEvent(this, vFile, false));
  }

  @Test
  public void testProcessNestedDeletions() {
    VirtualFile file = tempDirectory.newVirtualFile("a/b/c/test.txt");
    VirtualFile file2 = tempDirectory.newVirtualFile("a/b/c/test2.txt");

    checkEvents("Before: VFileDeleteEvent->test.txt\n" +
                "After: VFileDeleteEvent->test.txt\n" +
                "Before: VFileDeleteEvent->c\n" +
                "After: VFileDeleteEvent->c\n",

                new VFileDeleteEvent(this, file, false),
                new VFileDeleteEvent(this, file.getParent(), false),
                new VFileDeleteEvent(this, file2, false));
  }

  @Test
  public void testProcessContentChangedLikeReconcilableEventsMustResultInSingleBatch() {
    VirtualFile file = tempDirectory.newVirtualFile("a/b/c/test.txt");

    checkEvents("Before: VFileContentChangeEvent->test.txt VFilePropertyChangeEvent->test.txt VFilePropertyChangeEvent->test.txt\n" +
                "After: VFileContentChangeEvent->test.txt VFilePropertyChangeEvent->test.txt VFilePropertyChangeEvent->test.txt\n",

                new VFileContentChangeEvent(this, file, 0, 1, false),
                new VFilePropertyChangeEvent(this, file, VirtualFile.PROP_WRITABLE, false, true, false),
                new VFilePropertyChangeEvent(this, file, VirtualFile.PROP_ENCODING, StandardCharsets.ISO_8859_1, StandardCharsets.UTF_8, false));
  }

  @Test
  public void testProcessCompositeMoveEvents() {
    VirtualFile testTxt = tempDirectory.newVirtualFile("a/b/c/test.txt");
    VirtualFile newParent = tempDirectory.newVirtualDirectory("a/b/d");

    checkEvents("Before: VFileMoveEvent->test.txt\n" +
                "After: VFileMoveEvent->test.txt\n" +
                "Before: VFileDeleteEvent->d\n" +
                "After: VFileDeleteEvent->d\n",

                new VFileMoveEvent(this, testTxt, newParent),
                new VFileDeleteEvent(this, newParent, false));
  }

  @Test
  public void testProcessCompositeCopyEvents() {
    VirtualFile file = tempDirectory.newVirtualFile("a/b/c/test.txt");
    VirtualFile newParent = tempDirectory.newVirtualDirectory("a/b/d");

    checkEvents("Before: VFileCopyEvent->new.txt\n" +
                "After: VFileCopyEvent->new.txt\n" +
                "Before: VFileDeleteEvent->test.txt\n" +
                "After: VFileDeleteEvent->test.txt\n",

                new VFileCopyEvent(this, file, newParent, "new.txt"),
                new VFileDeleteEvent(this, file, false));
  }

  @Test
  public void testProcessCompositeRenameEvents() {
    VirtualFile file = tempDirectory.newVirtualFile("a/b/c/test.txt");
    VirtualFile file2 = tempDirectory.newVirtualFile("a/b/c/test2.txt");

    checkEvents("Before: VFileDeleteEvent->test2.txt\n" +
                "After: VFileDeleteEvent->test2.txt\n" +
                "Before: VFilePropertyChangeEvent->test.txt\n" +
                "After: VFilePropertyChangeEvent->test2.txt\n",

                new VFileDeleteEvent(this, file2, false),
                new VFilePropertyChangeEvent(this, file, VirtualFile.PROP_NAME, file.getName(), file2.getName(), false));
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

  private static void assertChildrenAreLoaded(VirtualFile file) {
    assertTrue("children not loaded: " + file, ((VirtualDirectoryImpl)file).allChildrenLoaded());
    assertTrue("children not loaded: " + file, PersistentFS.getInstance().areChildrenLoaded(file));
  }

  @Test
  public void testRenameInBackgroundDoesntLeadToDuplicateFilesError() throws IOException {
    assumeFalse("Case-insensitive OS expected, can't run on " + SystemInfo.OS_NAME, SystemInfo.isFileSystemCaseSensitive);

    File file = tempDirectory.newFile("rename.txt", "x".getBytes(StandardCharsets.UTF_8));
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
    PersistentFSImpl fs = (PersistentFSImpl)PersistentFS.getInstance();

    for (int i = 0; i < 10; i++) {
      File file = tempDirectory.newFile("d" + i + "/file.txt", "x".getBytes(StandardCharsets.UTF_8));
      VirtualDirectoryImpl vTemp = (VirtualDirectoryImpl)refreshAndFind(file).getParent();
      assertFalse(vTemp.allChildrenLoaded());
      Files.writeString(file.toPath().resolveSibling("new.txt"), "new");
      Future<List<? extends ChildInfo>> f1 = ApplicationManager.getApplication().executeOnPooledThread(() -> fs.listAll(vTemp));
      Future<List<? extends ChildInfo>> f2 = ApplicationManager.getApplication().executeOnPooledThread(() -> fs.listAll(vTemp));
      List<? extends ChildInfo> children1 = f1.get();
      List<? extends ChildInfo> children2 = f2.get();
      int[] nameIds1 = children1.stream().mapToInt(n -> n.getNameId()).toArray();
      int[] nameIds2 = children2.stream().mapToInt(n -> n.getNameId()).toArray();

      // there can be one or two children, depending on whether the VFS refreshed in time or not.
      // but in any case, there must not be duplicate ids (i.e. files with the same name but different getId())
      for (int i1 = 0; i1 < nameIds1.length; i1++) {
        int nameId1 = nameIds1[i1];
        int i2 = ArrayUtil.find(nameIds2, nameId1);
        if (i2 >= 0) {
          int id1 = children1.get(i1).getId();
          int id2 = children2.get(i2).getId();
          assertEquals("Duplicate ids found. children1=" + children1 + "; children2=" + children2, id1, id2);
        }
      }
    }
  }

  @Test
  public void testMustNotDuplicateIdsOnRenameWithCaseChanged() {
    PersistentFSImpl fs = (PersistentFSImpl)PersistentFS.getInstance();

    File file = tempDirectory.newFile("file.txt", "x".getBytes(StandardCharsets.UTF_8));
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
        catch (NoSuchFileException ignored) { }
        catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      });
      futures.add(f);
    }
    for (int i = 0; i < 10; i++) {
      Future<?> f = ApplicationManager.getApplication().executeOnPooledThread(() -> {
        for (int u=0; u<100; u++) {
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

  public static class TracingJarFileSystemTestWrapper extends JarFileSystemImpl {
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

  private static File zipWithEntry(String fileName, File generationDir, File outputDir, String entryName, String entryContent) throws IOException {
    File zipFile = new File(generationDir, fileName);
    try (Compressor.Zip zip = new Compressor.Zip(zipFile)) {
      zip.addFile(entryName, entryContent.getBytes(StandardCharsets.UTF_8));
    }

    File outputFile = new File(outputDir, fileName);
    try (OutputStream out = Files.newOutputStream(outputFile.toPath())) {
      Files.copy(zipFile.toPath(), out);  // unlike `Files#copy(Path, Path)`, allows to overwrite an opened file on Windows
    }
    VfsUtil.markDirtyAndRefresh(false, true, true, outputFile);
    return outputFile;
  }

  @Test
  public void testReadOnlyFsCachesLength() throws IOException {
    String text = "<virtualFileSystem implementationClass=\"" + TracingJarFileSystemTestWrapper.class.getName() + "\" key=\"jar-wrapper\" physical=\"true\"/>";
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
      assertEquals(contents[0], new String(file.contentsToByteArray(), StandardCharsets.UTF_8));
      TracingJarFileSystemTestWrapper fs = (TracingJarFileSystemTestWrapper)file.getFileSystem();

      zipFile = zipWithEntry(jarName, generationDir, testDir, entryName, contents[1]);
      VfsUtil.markDirtyAndRefresh(false, true, true, zipFile);
      int attrCallCount = fs.getAttributeCallCount();
      file.refresh(false, false);
      assertTrue(file.isValid());
      assertEquals(contents[1], new String(file.contentsToByteArray(), StandardCharsets.UTF_8));

      zipFile = zipWithEntry(jarName, generationDir, testDir, entryName, contents[2]);
      VfsUtil.markDirtyAndRefresh(false, true, true, zipFile);
      assertNotEquals(attrCallCount, fs.getAttributeCallCount());  // we should read length from physical FS
      file.refresh(false, false);
      assertTrue(file.isValid());
      assertEquals(contents[2], new String(file.contentsToByteArray(), StandardCharsets.UTF_8));

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
    String text = "<virtualFileSystem implementationClass=\"" + TracingJarFileSystemTestWrapper.class.getName() + "\" key=\"jar-wrapper\" physical=\"true\"/>";
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
    ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable()).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> e) {
        events.addAll(e);
      }
    });

    jarVFile.refresh(false, false);
    events.sort(Comparator.comparing((VFileEvent e) ->e.getFile().getUrl()));
    assertEqualUnorderedEvents(events, new VFileDeleteEvent(this, vFile, false), new VFileDeleteEvent(this, jarVFile, false));
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
    ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable()).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> e) {
        events.addAll(e);
      }
    });

    ((JarFileSystemImpl)JarFileSystem.getInstance()).markDirtyAndRefreshVirtualFileDeepInsideJarForTest(webXml);

    assertEqualUnorderedEvents(events, new VFileDeleteEvent(this, webXml, false),
                 new VFileContentChangeEvent(this, vFile, 0, 0, false));
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
    ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable()).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
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
                               new VFileContentChangeEvent(this, vFile, -1, -1, true));

    events.clear();

    FileUtil.writeToFile(FILE, "content");
    vFile.refresh(false, false);
    vFILE.refresh(false, false);
    assertEqualUnorderedEvents(events,
                               new VFileContentChangeEvent(this, vFILE,-1,-1, true));

    events.clear();

    FileUtil.writeToFile(file, "content2");
    FileUtil.writeToFile(FILE, "content2");
    vDir.refresh(false, true);
    assertEqualUnorderedEvents(events,
                               new VFileContentChangeEvent(this, vFile,-1,-1,true),
                               new VFileContentChangeEvent(this, vFILE,-1,-1,true));

    events.clear();

    FileUtil.delete(file);
    FileUtil.delete(FILE);
    vDir.refresh(false, true);
    assertEqualUnorderedEvents(events,
                               new VFileDeleteEvent(this, vFile,false),
                               new VFileDeleteEvent(this, vFILE,false));

    events.clear();

    assertTrue(file.createNewFile());
    assertTrue(FILE.createNewFile());
    vDir.refresh(false, true);
    assertEqualUnorderedEvents(events,
                               new VFileCreateEvent(this, vDir, vFile.getName(),false, null, null, true, null),
                               new VFileCreateEvent(this, vDir, vFILE.getName(),false, null, null, true, null));
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
      exp = new VFileContentChangeEvent(this, ((VFileContentChangeEvent)exp).getFile(), 0, 0, -1, -1, -1, -1, true);
    }
    return exp;
  }

  @Test
  public void testSetBinaryContentMustGenerateVFileContentChangedEventWithCorrectOldLength() throws IOException {
    VirtualFile vDir = refreshAndFind(tempDirectory.newDirectory());
    VirtualFile vFile = HeavyPlatformTestCase.createChildData(vDir, "file.txt");
    UIUtil.pump();
    List<VFileEvent> events = new ArrayList<>();
    ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable()).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
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

    WriteAction.runAndWait(()->vFile.setBinaryContent(new byte[]{1,2,3}));
    VFileEvent event = assertOneElement(events);
    assertInstanceOf(event, VFileContentChangeEvent.class);

    assertEquals(0, ((VFileContentChangeEvent)event).getOldLength());
    assertEquals(3, ((VFileContentChangeEvent)event).getNewLength());

    events.clear();
  }

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

    CacheSwitcher.INSTANCE.switchIndexAndVfs(null, null, "resetting vfs", () -> { return null; });

    PersistentFSImpl.moveChildrenRecords(firstDirId, secondDirId);

    assertEmpty(refreshAndFind(firstDirIoFile).getChildren());

    final VirtualFile[] movedChildren = refreshAndFind(secondDirIoFile).getChildren();
    assertEquals(List.of(xxxId, someTxtId), ContainerUtil.map(movedChildren, it -> ((NewVirtualFile)it).getId()));
    assertEquals(List.of("xxx", "some.txt"), ContainerUtil.map(movedChildren, VirtualFile::getName));

    final VirtualFile[] movedGrandChildren = ContainerUtil.find(movedChildren, it -> "xxx".equals(it.getName())).getChildren();
    assertEquals(List.of(xxxFooBarId), ContainerUtil.map(movedGrandChildren, it -> ((NewVirtualFile)it).getId()));
    assertEquals(List.of("foo.bar"), ContainerUtil.map(movedGrandChildren, VirtualFile::getName));
  }
}

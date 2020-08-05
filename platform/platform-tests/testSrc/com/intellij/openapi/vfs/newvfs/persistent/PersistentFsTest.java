// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.ide.plugins.DynamicPluginsTestUtilKt;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
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
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.PathKt;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.io.zip.JBZipFile;
import com.intellij.util.messages.MessageBusConnection;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarFile;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotEquals;

public class PersistentFsTest extends HeavyPlatformTestCase {
  public void testAccessingFileByID() throws Exception {
    File dir = createTempDirectory();
    File file = new File(dir, "test.txt");
    assertTrue(file.createNewFile());

    VirtualFile vFile = find(file);
    assertNotNull(vFile);

    int id = ((VirtualFileWithId)vFile).getId();
    assertEquals(vFile, PersistentFS.getInstance().findFileById(id));

    delete(vFile);
    assertNull(PersistentFS.getInstance().findFileById(id));
  }

  public void testFileContentHash() throws Exception {
    File dir = createTempDirectory();
    File file = new File(dir, "test.txt");
    assertTrue(file.createNewFile());
    FileUtil.writeToFile(file, "one");

    VirtualFile vFile = find(file);
    assertNotNull(vFile);

    PersistentFSImpl fs = (PersistentFSImpl)PersistentFS.getInstance();

    // content is not yet loaded
    byte[] hash = PersistentFSImpl.getContentHashIfStored(vFile);
    assertNull(hash);

    vFile.contentsToByteArray();
    hash = PersistentFSImpl.getContentHashIfStored(vFile);
    assertNotNull(hash);

    // different contents should have different hashes
    setFileText(vFile, "two");
    byte[] newHash = PersistentFSImpl.getContentHashIfStored(vFile);
    assertNotNull(newHash);
    assertFalse(Arrays.equals(hash, newHash));

    // equal contents should have the equal hashes
    setFileText(vFile, "one");
    assertArrayEquals(hash, PersistentFSImpl.getContentHashIfStored(vFile));

    // deleted files preserve content, and thus hash
    delete(vFile);
    assertNotNull(fs.contentsToByteArray(vFile));
    assertArrayEquals(hash, PersistentFSImpl.getContentHashIfStored(vFile));
  }

  public void testFindRootShouldNotBeFooledByRelativePath() throws Exception {
    File tmp = createTempDirectory();
    File x = new File(tmp, "x.jar");
    assertTrue(x.createNewFile());

    VirtualFile vx = find(x);
    assertNotNull(vx);

    JarFileSystem jfs = JarFileSystem.getInstance();
    VirtualFile root = jfs.getJarRootForLocalFile(vx);
    String path = vx.getPath() + "/../" + vx.getName() + JarFileSystem.JAR_SEPARATOR;
    assertSame(PersistentFS.getInstance().findRoot(path, jfs), root);
  }

  public void testFindRootMustCreateFileWithCanonicalPath() throws Exception {
    checkMustCreateRootWithCanonicalPath("x.jar");
  }

  private void checkMustCreateRootWithCanonicalPath(String jarName) throws IOException {
    File tmp = createTempDirectory();
    File x = new File(tmp, jarName);
    assertTrue(x.createNewFile());
    assertNotNull(find(x));

    JarFileSystem jfs = JarFileSystem.getInstance();
    String path = x.getPath() + "/../" + x.getName() + JarFileSystem.JAR_SEPARATOR;
    NewVirtualFile root = Objects.requireNonNull(PersistentFS.getInstance().findRoot(path, jfs));
    assertFalse(root.getPath(), root.getPath().contains("../"));
    assertFalse(root.getPath(), root.getPath().contains("/.."));
  }

  public void testFindRootMustCreateFileWithStillCanonicalPath() throws Exception {
    checkMustCreateRootWithCanonicalPath("x..jar");
  }

  public void testFindRootMustCreateFileWithYetAnotherCanonicalPath() throws Exception {
    checkMustCreateRootWithCanonicalPath("x...jar");
  }

  public void testDeleteSubstRoots() throws Exception {
    if (!SystemInfo.isWindows) return;

    File tempDirectory = FileUtil.createTempDirectory(getTestName(false), null);
    File substRoot = IoTestUtil.createSubst(tempDirectory.getPath());
    VirtualFile subst = find(substRoot);
    assertNotNull(subst);

    try {
      final File[] children = substRoot.listFiles();
      assertNotNull(children);
    }
    finally {
      IoTestUtil.deleteSubst(substRoot.getPath());
    }
    subst.refresh(false, true);

    VirtualFile[] roots = PersistentFS.getInstance().getRoots(LocalFileSystem.getInstance());
    for (VirtualFile root : roots) {
      String rootPath = root.getPath();
      String prefix = StringUtil.commonPrefix(rootPath, substRoot.getPath());
      assertEmpty(prefix);
    }
  }

  public void testLocalRoots() {
    VirtualFile tempRoot = VirtualFileManager.getInstance().findFileByUrl("temp:///");
    assertNotNull(tempRoot);

    VirtualFile[] roots = PersistentFS.getInstance().getLocalRoots();
    for (VirtualFile root : roots) {
      assertTrue("root=" + root, root.isInLocalFileSystem());
      VirtualFileSystem fs = root.getFileSystem();
      assertTrue("fs=" + fs, fs instanceof LocalFileSystem);
      assertFalse("fs=" + fs, fs instanceof TempFileSystem);
    }
  }

  public void testInvalidJarRootsIgnored() {
    File file = IoTestUtil.createTestFile("file.txt");
    String url = "jar://" + FileUtil.toSystemIndependentName(file.getPath()) + "!/";
    assertNull(VirtualFileManager.getInstance().findFileByUrl(url));
  }

  public void testBrokenJarRoots() {
    final File jarFile = IoTestUtil.createTestFile("empty.jar");

    final int[] logCount = {0};
    LoggedErrorProcessor.setNewInstance(new LoggedErrorProcessor() {
      @Override
      public void processWarn(String message, Throwable t, @NotNull Logger logger) {
        super.processWarn(message, t, logger);
        if (message.contains(jarFile.getName())) logCount[0]++;
      }
    });

    try {
      String rootUrl = "jar://" + FileUtil.toSystemIndependentName(jarFile.getPath()) + "!/";
      assertNotNull(getVirtualFile(jarFile));
      VirtualFile jarRoot = VirtualFileManager.getInstance().findFileByUrl(rootUrl);
      assertNotNull(jarRoot);
      assertTrue(jarRoot.isValid());
      assertEmpty(jarRoot.getChildren());
      String entryUrl = rootUrl + JarFile.MANIFEST_NAME;
      assertNull(VirtualFileManager.getInstance().findFileByUrl(entryUrl));

      VirtualFile local = JarFileSystem.getInstance().getVirtualFileForJar(jarRoot);
      assertNotNull(local);
      IoTestUtil.createTestJar(jarFile);
      local.refresh(false, false);
      assertTrue(jarRoot.isValid());
      assertEquals(1, jarRoot.getChildren().length);
      assertNotNull(VirtualFileManager.getInstance().findFileByUrl(entryUrl));
    }
    finally {
      LoggedErrorProcessor.restoreDefaultProcessor();
    }

    assertEquals(1, logCount[0]);
  }

  public void testIterInDbChildrenWorksForRemovedDirsAfterRestart() throws IOException {
    // test (re)creates <testName>/subDir/subSubDir/Foo.txt outside tested/watched project and checks removal events on subDir / subSubDir / Foo.txt
    // test starts real testing ("after restart") after launching second time using same VFS
    // hours spent writing this test: 4
    VirtualFile projectStructure = createTestProjectStructure();
    String testName = getTestName(false);

    // wrt persistence subDir becomes partially loaded and subSubDir becomes fully loaded
    File nestedDirOutsideTheProject = new File(projectStructure.getPath() + "../../../"+testName + "/subDir", "subSubDir").getCanonicalFile();
    Disposable disposable = null;

    try {
      boolean atLeastSecondRun = nestedDirOutsideTheProject.getParentFile().getParentFile().exists();
      StringBuilder eventLog = new StringBuilder();

      if (atLeastSecondRun) {
        disposable = Disposer.newDisposable();
        getProject().getMessageBus().connect(disposable).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
          @Override
          public void before(@NotNull List<? extends VFileEvent> events) {
            for (VFileEvent event : events) {
              if (event instanceof VFileDeleteEvent) process(((VFileDeleteEvent)event).getFile());
            }
          }
          private void process(VirtualFile file) {
            String path = file.getPath();
            eventLog.append(path.substring(path.indexOf(testName) + testName.length() + 1)).append("\n");
            Iterable<VirtualFile> files = ((NewVirtualFile)file).iterInDbChildren();
            for (VirtualFile nested : files) process(nested);
          }
        });
      }

      // recreating structure will fire vfs removal events
      VirtualFile nestedDirOutsideTheProjectFile = VfsUtil.createDirectories(nestedDirOutsideTheProject.getPath());
      WriteAction.run(() -> nestedDirOutsideTheProjectFile.createChildData(null, "Foo.txt"));

      // subSubDir becomes fully loaded wrt persistence
      nestedDirOutsideTheProjectFile.getChildren();

      if (atLeastSecondRun) {
        assertEquals("subDir\n" +
                     "subDir/subSubDir\n" +
                     "subDir/subSubDir/Foo.txt\n",
                     eventLog.toString());
      }
    }
    finally {
      if (disposable != null) Disposer.dispose(disposable);
      // remove <testName>/subDir via java.io to have vfs events on next test launch
      FileUtil.delete(nestedDirOutsideTheProject.getParentFile());
    }
  }

  public void testModCountIncreases() throws IOException {
    VirtualFile vFile = setupFile();
    ManagingFS managingFS = ManagingFS.getInstance();
    int inSessionModCount = managingFS.getModificationCount();
    int globalModCount = managingFS.getFilesystemModificationCount();
    final int parentModCount = managingFS.getModificationCount(vFile.getParent());

    WriteAction.run(() -> vFile.setWritable(false));

    assertEquals(globalModCount + 1, managingFS.getModificationCount(vFile));
    assertEquals(globalModCount + 1, managingFS.getFilesystemModificationCount());
    assertEquals(parentModCount, managingFS.getModificationCount(vFile.getParent()));
    assertEquals(inSessionModCount + 1, managingFS.getModificationCount());

    FSRecords.force();
    assertFalse(FSRecords.isDirty());
    ++globalModCount;

    int finalGlobalModCount = globalModCount;

    try (AccessToken ignore = HeavyProcessLatch.INSTANCE.processStarted("This test wants no indices flush")) {
      WriteAction.run(() -> {
        final long timestamp = vFile.getTimeStamp();
        int finalInSessionModCount = managingFS.getModificationCount();
        vFile.setWritable(true);  // 1 change
        vFile.setBinaryContent("foo".getBytes(Charset.defaultCharset())); // content change + length change + maybe timestamp change

        // we check in write action to avoid observing background thread to index stuff
        final int changesCount = timestamp == vFile.getTimeStamp() ? 3 : 4;
        assertEquals(finalGlobalModCount + changesCount, managingFS.getModificationCount(vFile));
        assertEquals(finalGlobalModCount + changesCount, managingFS.getFilesystemModificationCount());
        assertEquals(finalInSessionModCount + changesCount, managingFS.getModificationCount());
        assertEquals(parentModCount, managingFS.getModificationCount(vFile.getParent()));
      });
    }
  }

  @NotNull
  private static VirtualFile setupFile() {
    File file = IoTestUtil.createTestFile("file.txt");
    VirtualFile vFile = find(file);
    assertNotNull(vFile);
    return vFile;
  }

  public void testModCountNotIncreases() throws IOException {
    VirtualFile vFile = setupFile();
    ManagingFS managingFS = ManagingFS.getInstance();
    final int globalModCount = managingFS.getFilesystemModificationCount();
    final int parentModCount = managingFS.getModificationCount(vFile.getParent());
    int inSessionModCount = managingFS.getModificationCount();

    FSRecords.force();
    assertFalse(FSRecords.isDirty());

    FileAttribute attribute = new FileAttribute("test.attribute", 1, true);
    WriteAction.run(() -> {
      try(DataOutputStream output = attribute.writeAttribute(vFile)) {
        DataInputOutputUtil.writeINT(output, 1);
      }
    });

    assertEquals(globalModCount, managingFS.getModificationCount(vFile));
    assertEquals(globalModCount, managingFS.getFilesystemModificationCount());
    assertEquals(parentModCount, managingFS.getModificationCount(vFile.getParent()));
    assertEquals(inSessionModCount + 1, managingFS.getModificationCount());

    assertTrue(FSRecords.isDirty());
    FSRecords.force();
    assertFalse(FSRecords.isDirty());

    //
    int fileId = ((VirtualFileWithId)vFile).getId();
    FSRecords.setTimestamp(fileId, FSRecords.getTimestamp(fileId));
    FSRecords.setLength(fileId, FSRecords.getLength(fileId));

    assertEquals(globalModCount, managingFS.getModificationCount(vFile));
    assertEquals(globalModCount, managingFS.getFilesystemModificationCount());
    assertEquals(parentModCount, managingFS.getModificationCount(vFile.getParent()));
    assertEquals(inSessionModCount + 1, managingFS.getModificationCount());
    assertFalse(FSRecords.isDirty());
  }

  public void testProcessEventsMustIgnoreDeleteDuplicates() {
    VirtualFile vFile = setupFile();
    checkEvents("Before:[VFileDeleteEvent->file.txt]\nAfter:[VFileDeleteEvent->file.txt]\n",
                new VFileDeleteEvent(this, vFile, false),
                new VFileDeleteEvent(this, vFile, false));
  }

  private void checkEvents(String expectedEvents, VFileEvent... eventsToApply) {
    final StringBuilder log = new StringBuilder();
    Disposable disposable = Disposer.newDisposable();
    getProject().getMessageBus().connect(disposable).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void before(@NotNull List<? extends VFileEvent> events) {
        log("Before:", events);
      }

      private void log(String msg, @NotNull List<? extends VFileEvent> events) {
        List<String> names = ContainerUtil.map(events, e -> e.getClass().getSimpleName() + "->" + PathUtil.getFileName(e.getPath()));
        log.append(msg).append(names).append('\n');
      }

      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        log("After:", events);
      }
    });
    WriteCommandAction.runWriteCommandAction(getProject(), () -> PersistentFS.getInstance().processEvents(Arrays.asList(eventsToApply)));
    Disposer.dispose(disposable);
    assertEquals(expectedEvents, log.toString());
  }

  public void testProcessEventsMustGroupDependentEventsCorrectly() {
    VirtualFile vFile = setupFile();
    checkEvents("Before:[VFileCreateEvent->xx.created, VFileDeleteEvent->file.txt]\n" +
                "After:[VFileCreateEvent->xx.created, VFileDeleteEvent->file.txt]\n",
                new VFileDeleteEvent(this, vFile, false),
                new VFileCreateEvent(this, vFile.getParent(), "xx.created", false, null, null, false, null),
                new VFileDeleteEvent(this, vFile, false));
  }

  public void testProcessEventsMustBeAwareOfDeleteEventsDominations() throws IOException {
    File temp = createTempDirectory();

    File d = new File(temp, "d");
    assertTrue(d.mkdir());
    File x = new File(d, "x.txt");
    assertTrue(x.createNewFile());
    VirtualFile vXTxt = find(x);

    checkEvents("Before:[VFileDeleteEvent->d]\n" +
                "After:[VFileDeleteEvent->d]\n",

                new VFileDeleteEvent(this, vXTxt.getParent(), false),
                new VFileDeleteEvent(this, vXTxt, false),
                new VFileDeleteEvent(this, vXTxt, false)
                );
  }

  public void testProcessCreateEventsMustFilterOutDuplicates() throws IOException {
    File temp = createTempDirectory();

    File d = new File(temp, "d");
    assertTrue(d.mkdir());
    File x = new File(d, "x.txt");
    assertTrue(x.createNewFile());
    VirtualFile vXTxt = find(x);

    checkEvents("Before:[VFileCreateEvent->xx.created]\n" +
                "After:[VFileCreateEvent->xx.created]\n",

                new VFileCreateEvent(this, vXTxt.getParent(), "xx.created", false, null, null, false, null),
                new VFileCreateEvent(this, vXTxt.getParent(), "xx.created", false, null, null, false, null)
                );
  }

  public void testProcessEventsMustGroupDependentEventsCorrectly2() throws IOException {
    File file = new File(createTempDirectory(), "a/b/c/test.txt");
    assertTrue(file.getParentFile().mkdirs());
    assertTrue(file.createNewFile());

    VirtualFile vFile = find(file);
    assertNotNull(vFile);

    checkEvents("Before:[VFileCreateEvent->xx.created, VFileCreateEvent->xx.created2, VFileDeleteEvent->test.txt]\n" +
                "After:[VFileCreateEvent->xx.created, VFileCreateEvent->xx.created2, VFileDeleteEvent->test.txt]\n" +
                "Before:[VFileDeleteEvent->c]\n" +
                "After:[VFileDeleteEvent->c]\n",
                new VFileDeleteEvent(this, vFile, false),
                new VFileCreateEvent(this, vFile.getParent(), "xx.created", false, null, null, false, null),
                new VFileCreateEvent(this, vFile.getParent(), "xx.created2", false, null, null, false, null),
                new VFileDeleteEvent(this, vFile.getParent(), false));
  }

  public void testProcessEventsMustGroupDependentEventsCorrectly3() throws IOException {
    File file = new File(createTempDirectory(), "a/b/c/test.txt");
    assertTrue(file.getParentFile().mkdirs());
    assertTrue(file.createNewFile());

    VirtualFile vFile = find(file);
    assertNotNull(vFile);

    checkEvents("Before:[VFileContentChangeEvent->c]\n" +
                "After:[VFileContentChangeEvent->c]\n" +
                "Before:[VFileDeleteEvent->test.txt]\n" +
                "After:[VFileDeleteEvent->test.txt]\n",

                new VFileContentChangeEvent(this, vFile.getParent(), 0, 0, false),
                new VFileDeleteEvent(this, vFile, false));
  }

  public void testProcessNestedDeletions() throws IOException {
    File temp = createTempDirectory();
    File file = new File(temp, "a/b/c/test.txt");
    assertTrue(file.getParentFile().mkdirs());
    assertTrue(file.createNewFile());
    VirtualFile testTxt = find(file);
    File file2 = new File(temp, "a/b/c/test2.txt");
    assertTrue(file2.createNewFile());
    VirtualFile test2Txt = find(file2);

    checkEvents("Before:[VFileDeleteEvent->test.txt]\n" +
                "After:[VFileDeleteEvent->test.txt]\n" +
                "Before:[VFileDeleteEvent->c]\n" +
                "After:[VFileDeleteEvent->c]\n",

                new VFileDeleteEvent(this, testTxt, false),
                new VFileDeleteEvent(this, testTxt.getParent(), false),
                new VFileDeleteEvent(this, test2Txt, false));
  }

  public void testProcessContentChangedLikeReconcilableEventsMustResultInSingleBatch() throws IOException {
    File temp = createTempDirectory();
    File file = new File(temp, "a/b/c/test.txt");
    assertTrue(file.getParentFile().mkdirs());
    assertTrue(file.createNewFile());
    VirtualFile testTxt = find(file);

    checkEvents("Before:[VFileContentChangeEvent->test.txt, VFilePropertyChangeEvent->test.txt, VFilePropertyChangeEvent->test.txt]\n" +
                "After:[VFileContentChangeEvent->test.txt, VFilePropertyChangeEvent->test.txt, VFilePropertyChangeEvent->test.txt]\n",

                new VFileContentChangeEvent(this, testTxt, 0, 1, false),
                new VFilePropertyChangeEvent(this, testTxt, VirtualFile.PROP_WRITABLE, false, true, false),
                new VFilePropertyChangeEvent(this, testTxt, VirtualFile.PROP_ENCODING, StandardCharsets.ISO_8859_1, StandardCharsets.UTF_8, false));
  }

  public void testProcessCompositeMoveEvents() throws IOException {
    File temp = createTempDirectory();
    File file = new File(temp, "a/b/c/test.txt");
    assertTrue(file.getParentFile().mkdirs());
    assertTrue(file.createNewFile());
    VirtualFile testTxt = find(file);

    File newParentF = new File(temp, "a/b/d");
    assertTrue(newParentF.mkdirs());
    VirtualFile newParent = find(newParentF);

    checkEvents("Before:[VFileMoveEvent->test.txt]\n" +
                "After:[VFileMoveEvent->test.txt]\n" +
                "Before:[VFileDeleteEvent->d]\n" +
                "After:[VFileDeleteEvent->d]\n",

                new VFileMoveEvent(this, testTxt, newParent),
                new VFileDeleteEvent(this, newParent, false));
  }

  public void testProcessCompositeCopyEvents() throws IOException {
    File temp = createTempDirectory();
    File file = new File(temp, "a/b/c/test.txt");
    assertTrue(file.getParentFile().mkdirs());
    assertTrue(file.createNewFile());
    VirtualFile testTxt = find(file);

    File newParentF = new File(temp, "a/b/d");
    assertTrue(newParentF.mkdirs());
    VirtualFile newParent = find(newParentF);

    checkEvents("Before:[VFileCopyEvent->new.txt]\n" +
                "After:[VFileCopyEvent->new.txt]\n" +
                "Before:[VFileDeleteEvent->test.txt]\n" +
                "After:[VFileDeleteEvent->test.txt]\n",

                new VFileCopyEvent(this, testTxt, newParent,"new.txt"),
                new VFileDeleteEvent(this, testTxt, false));
  }

  private static @NotNull VirtualFile find(@NotNull Path file) {
    return Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file));
  }

  private static @NotNull VirtualFile find(@NotNull File file) {
    return Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file));
  }

  public void testProcessCompositeRenameEvents() throws IOException {
    File temp = createTempDirectory();
    File file = new File(temp, "a/b/c/test.txt");
    assertTrue(file.getParentFile().mkdirs());
    assertTrue(file.createNewFile());
    VirtualFile testTxt = find(file);

    File file2 = new File(temp, "a/b/c/test2.txt");
    assertTrue(file2.createNewFile());
    VirtualFile test2Txt = find(file2);

    checkEvents("Before:[VFileDeleteEvent->test2.txt]\n" +
                "After:[VFileDeleteEvent->test2.txt]\n" +
                "Before:[VFilePropertyChangeEvent->test.txt]\n" +
                "After:[VFilePropertyChangeEvent->test2.txt]\n",

                new VFileDeleteEvent(this, test2Txt, false),
                new VFilePropertyChangeEvent(this, testTxt, VirtualFile.PROP_NAME, file.getName(), file2.getName(), false));
  }

  public void testCreateNewDirectoryEntailsLoadingAllChildren() throws IOException {
    File temp = createTempDirectory();

    File d = new File(temp, "d");
    assertTrue(d.mkdir());
    File d1 = new File(d, "d1");
    assertTrue(d1.mkdir());
    File x = new File(d1, "x.txt");
    assertTrue(x.createNewFile());
    VirtualDirectoryImpl vtemp = (VirtualDirectoryImpl)find(temp);
    assertNotNull(vtemp);
    vtemp.refresh(false, true);
    assertEquals("d", UsefulTestCase.assertOneElement(vtemp.getChildren()).getName());
    File target = new File(temp, "target");

    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable());
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
          if (event instanceof VFileCreateEvent) {
            VirtualFile createdFile = event.getFile();
            assertLoadedChildrenRecursively(createdFile);
          }
        }
      }
    });

    assertTrue(d.renameTo(target));
    vtemp.refresh(false, true);
    assertLoadedChildren(vtemp);
    VirtualFile vdt = UsefulTestCase.assertOneElement(vtemp.getCachedChildren());
    assertEquals("target", vdt.getName());
    assertLoadedChildren(vdt);
    VirtualFile vd1 = UsefulTestCase.assertOneElement(((VirtualDirectoryImpl)vdt).getCachedChildren());
    assertEquals("d1", vd1.getName());
    assertLoadedChildren(vd1);
    VirtualFile vx = UsefulTestCase.assertOneElement(((VirtualDirectoryImpl)vd1).getCachedChildren());
    assertEquals("x.txt", vx.getName());
  }

  private static void assertLoadedChildren(VirtualFile file) {
    assertTrue(((VirtualDirectoryImpl)file).allChildrenLoaded());
    assertTrue(PersistentFS.getInstance().areChildrenLoaded(file));
  }

  private static void assertLoadedChildrenRecursively(@NotNull VirtualFile file) {
    if (file instanceof VirtualDirectoryImpl) {
      assertLoadedChildren(file);
      for (VirtualFile child : ((VirtualDirectoryImpl)file).getCachedChildren()) {
        assertLoadedChildrenRecursively(child);
      }
    }
  }

  private static void addExcludedDir(Module module, String path) {
    ModuleRootModificationUtil.updateModel(module, model -> {
      ContentEntry contentEntry = model.addContentEntry(VfsUtilCore.pathToUrl(path));
      contentEntry.addExcludeFolder(VfsUtilCore.pathToUrl(path));
    });
  }

  public void testCreateNewDirectoryEntailsLoadingAllChildrenExceptExcluded() throws IOException {
    File temp = createTempDirectory();

    File d = new File(temp, "d");
    assertTrue(d.mkdir());
    File d1 = new File(d, "d1");
    assertTrue(d1.mkdir());
    File x = new File(d1, "x.txt");
    assertTrue(x.createNewFile());
    VirtualDirectoryImpl vtemp = (VirtualDirectoryImpl)find(temp);
    assertNotNull(vtemp);
    vtemp.refresh(false, true);
    assertEquals("d", UsefulTestCase.assertOneElement(vtemp.getChildren()).getName());
    File target = new File(temp, "target");

    addExcludedDir(myModule, new File(target, "d1").getPath());

    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable());
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
          if (event instanceof VFileCreateEvent) {
            VirtualFile createdFile = event.getFile();
            assertLoadedChildren(createdFile);
          }
        }
      }
    });

    assertTrue(d.renameTo(target));
    vtemp.refresh(false, true);
    assertLoadedChildren(vtemp);
    VirtualFile vdt = UsefulTestCase.assertOneElement(vtemp.getCachedChildren());
    assertEquals("target", vdt.getName());
    assertLoadedChildren(vdt);
    VirtualFile vd1 = UsefulTestCase.assertOneElement(((VirtualDirectoryImpl)vdt).getCachedChildren());
    assertEquals("d1", vd1.getName());
    assertFalse(((VirtualDirectoryImpl)vd1).allChildrenLoaded());
    UsefulTestCase.assertEmpty(((VirtualDirectoryImpl)vd1).getCachedChildren());
  }

  public void testRenameInBackgroundDoesntLeadToDuplicateFilesError() throws IOException {
    IoTestUtil.assumeWindows();
    Path temp = getTempDir().newPath();
    Path file = temp.resolve("rename.txt");
    PathKt.write(file, "x");
    VirtualFile vfile = find(file);
    VirtualDirectoryImpl vTemp = (VirtualDirectoryImpl)vfile.getParent();
    assertFalse(vTemp.allChildrenLoaded());
    VfsUtil.markDirty(true, false, vTemp);
    Files.move(file, temp.resolveSibling(file.getFileName().toString().toUpperCase()));
    VirtualFile[] newChildren = vTemp.getChildren();
    assertOneElement(newChildren);
  }

  public void testPersistentFsCacheDoesntContainInvalidFiles() throws IOException {
    PersistentFSImpl fs = (PersistentFSImpl)PersistentFS.getInstance();

    File dir = createTempDirectory();
    File subDir1 = new File(dir, "subDir1");
    File subDir2 = new File(subDir1, "subDir2");
    File subDir3 = new File(subDir2, "subDir3");
    File file = new File(subDir3, "file.txt");

    assertTrue(subDir3.mkdirs());
    assertTrue(file.createNewFile());
    VirtualFileSystemEntry vFile = (VirtualFileSystemEntry)find(file);
    VirtualFileSystemEntry vSubDir3 = vFile.getParent();
    VirtualFileSystemEntry vSubDir2 = vSubDir3.getParent();
    VirtualFileSystemEntry vSubDir1 = vSubDir2.getParent();

    VirtualFileSystemEntry[] hardReferenceHolder = new VirtualFileSystemEntry[]{vFile, vSubDir3, vSubDir2, vSubDir1};

    // delete directory with deep nested children
    delete(vSubDir1);

    for (VirtualFileSystemEntry f : hardReferenceHolder) {
      assertFalse("file is valid but deleted " + f.getName(), f.isValid());
    }

    for (VirtualFileSystemEntry f : hardReferenceHolder) {
      assertNull(fs.getCachedDir(f.getId()));
      assertNull(fs.findFileById(f.getId()));
    }

    for (VirtualFileSystemEntry f : fs.getIdToDirCache().values()) {
      assertTrue(f.isValid());
    }
  }

  public void testConcurrentListAllDoesntCauseDuplicateFileIds() throws Exception {
    PersistentFSImpl fs = (PersistentFSImpl)PersistentFS.getInstance();

    for (int i=0; i<10; i++) {
      File temp = getTempDir().createDir().toFile();
      File file = new File(temp, "file.txt");
      FileUtil.createParentDirs(file);
      FileUtil.writeToFile(file, "x");
      VirtualFile vfile = find(file);
      VirtualDirectoryImpl vTemp = (VirtualDirectoryImpl)vfile.getParent();
      assertFalse(vTemp.allChildrenLoaded());
      FileUtil.writeToFile(new File(temp, "new.txt"),"new" );
      Future<List<? extends ChildInfo>> f1 = ApplicationManager.getApplication().executeOnPooledThread(() -> fs.listAll(vTemp));
      Future<List<? extends ChildInfo>>  f2 = ApplicationManager.getApplication().executeOnPooledThread(() -> fs.listAll(vTemp));
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

  public void testMustNotDuplicateIdsOnRenameWithCaseChanged() throws IOException {
    PersistentFSImpl fs = (PersistentFSImpl)PersistentFS.getInstance();

    File temp = getTempDir().createDir().toFile();
    File file = new File(temp, "file.txt");
    FileUtil.createParentDirs(file);
    FileUtil.writeToFile(file, "x");
    VirtualFile vDir = find(file.getParentFile());
    VirtualFile vf = assertOneElement(vDir.getChildren());
    assertEquals("file.txt", vf.getName());
    List<Future<?>> futures = new ArrayList<>();
    String oldName = file.getName();
    for (int i=0; i<100; i++) {
      int u = i % oldName.length();
      Future<?> f = ApplicationManager.getApplication().executeOnPooledThread(() -> {
        String newName = oldName.substring(0, u) + Character.toUpperCase(oldName.charAt(u)) + oldName.substring(u + 1);
        try {
          FileUtil.rename(file, new File(temp, newName));
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
      futures.add(f);
    }
    for (int i=0; i<10; i++) {
      Future<?> f = ApplicationManager.getApplication().executeOnPooledThread(() -> {
        for (int u=0; u<100; u++) {
          List<? extends ChildInfo> infos = fs.listAll(vDir);
          assertOneElement(infos);
        }
      });
      futures.add(f);
    }

    for (Future<?> future : futures) {
      PlatformTestUtil.waitForFuture(future, 10_000);
    }
  }

  public void testReadOnlyFsCachesLength() throws IOException {
    String text = "<virtualFileSystem implementationClass=\"" + JarFileSystemTestWrapper.class.getName() + "\" key=\"jarwrapper\" physical=\"true\"/>";
    Disposable disposable = DynamicPluginsTestUtilKt.loadExtensionWithText(text, JarFileSystemTestWrapper.class.getClassLoader());

    try {
      File testDir = getTempDir().createDir().toFile();
      File generationDir = getTempDir().createDir().toFile();

      String jarName = "test.jar";
      String entryName = "Some.java";

      String content0 = "class Some {}";
      String content1 = "class Some { void m() {} }";
      String content2 = "class Some { void mmm() {} }";

      File zipFile = createZipWithEntry(jarName, entryName, content0, testDir, generationDir);
      assertTrue(zipFile.exists());

      VfsUtil.markDirtyAndRefresh(false, true, true, zipFile);

      String url = "jarwrapper://" + FileUtil.toSystemIndependentName(zipFile.getPath()) + "!/" + entryName;

      VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
      file.refresh(false, false);
      JarFileSystemTestWrapper fs = (JarFileSystemTestWrapper)file.getFileSystem();

      assertTrue(file.isValid());
      assertEquals(content0, new String(file.contentsToByteArray(), StandardCharsets.UTF_8));

      zipFile = createZipWithEntry(jarName, entryName, content1, testDir, generationDir);
      VfsUtil.markDirtyAndRefresh(false, true, true, zipFile);
      int attrCallCount = fs.getAttributeCallCount();

      file.refresh(false, false);
      assertTrue(file.isValid());
      assertEquals(content1, new String(file.contentsToByteArray(), StandardCharsets.UTF_8));

      zipFile = createZipWithEntry(jarName, entryName, content2, testDir, generationDir);
      VfsUtil.markDirtyAndRefresh(false, true, true, zipFile);

      // we should read length from physical FS
      assertNotEquals(attrCallCount, fs.getAttributeCallCount());
      file.refresh(false, false);
      assertTrue(file.isValid());
      assertEquals(content2, new String(file.contentsToByteArray(), StandardCharsets.UTF_8));

      attrCallCount = fs.getAttributeCallCount();
      file.getLength();
      assertEquals(attrCallCount, fs.getAttributeCallCount());

      // ensure it's cached
      file.getLength();
      assertEquals(attrCallCount, fs.getAttributeCallCount());

      // ensure it's cached
      file.getLength();
      assertEquals(attrCallCount, fs.getAttributeCallCount());
    } finally {
      Disposer.dispose(disposable);
    }
  }

  public static class JarFileSystemTestWrapper extends JarFileSystemImpl {
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
      return "jarwrapper";
    }
  }

  @NotNull
  private static File createZipWithEntry(@NotNull String fileName,
                                 @NotNull String entryName,
                                 @NotNull String entryContent,
                                 @NotNull File outputPath,
                                 @NotNull File generationDir) throws IOException {
    File zipFile = new File(generationDir, fileName);
    try (JBZipFile zip = new JBZipFile(zipFile)) {
      zip.getOrCreateEntry(entryName).setData(entryContent.getBytes(StandardCharsets.UTF_8));
    }

    File outputFile = new File(outputPath, fileName);
    FileUtil.copy(zipFile, outputFile);

    return outputFile;
  }
}
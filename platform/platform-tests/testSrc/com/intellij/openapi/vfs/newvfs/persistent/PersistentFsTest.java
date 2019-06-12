// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.messages.MessageBusConnection;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarFile;

public class PersistentFsTest extends PlatformTestCase {
  private PersistentFS myFs;
  private LocalFileSystem myLocalFs;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFs = PersistentFS.getInstance();
    myLocalFs = LocalFileSystem.getInstance();
  }

  @Override
  protected void tearDown() throws Exception {
    myLocalFs = null;
    myFs = null;
    super.tearDown();
  }

  public void testAccessingFileByID() throws Exception {
    File dir = createTempDirectory();
    File file = new File(dir, "test.txt");
    assertTrue(file.createNewFile());

    VirtualFile vFile = myLocalFs.refreshAndFindFileByIoFile(file);
    assertNotNull(vFile);

    int id = ((VirtualFileWithId)vFile).getId();
    assertEquals(vFile, myFs.findFileById(id));

    delete(vFile);
    assertNull(myFs.findFileById(id));
  }

  public void testFindRootShouldNotBeFooledByRelativePath() throws Exception {
    File tmp = createTempDirectory();
    File x = new File(tmp, "x.jar");
    assertTrue(x.createNewFile());

    VirtualFile vx = myLocalFs.refreshAndFindFileByIoFile(x);
    assertNotNull(vx);

    JarFileSystem jfs = JarFileSystem.getInstance();
    VirtualFile root = jfs.getJarRootForLocalFile(vx);
    String path = vx.getPath() + "/../" + vx.getName() + JarFileSystem.JAR_SEPARATOR;
    assertSame(myFs.findRoot(path, jfs), root);
  }

  public void testFindRootMustCreateFileWithCanonicalPath() throws Exception {
    checkMustCreateRootWithCanonicalPath("x.jar");
  }

  private void checkMustCreateRootWithCanonicalPath(String jarName) throws IOException {
    File tmp = createTempDirectory();
    File x = new File(tmp, jarName);
    assertTrue(x.createNewFile());
    assertNotNull(myLocalFs.refreshAndFindFileByIoFile(x));

    JarFileSystem jfs = JarFileSystem.getInstance();
    String path = x.getPath() + "/../" + x.getName() + JarFileSystem.JAR_SEPARATOR;
    NewVirtualFile root = ObjectUtils.notNull(myFs.findRoot(path, jfs));
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
    VirtualFile subst = myLocalFs.refreshAndFindFileByIoFile(substRoot);
    assertNotNull(subst);

    try {
      final File[] children = substRoot.listFiles();
      assertNotNull(children);
    }
    finally {
      IoTestUtil.deleteSubst(substRoot.getPath());
    }
    subst.refresh(false, true);

    VirtualFile[] roots = myFs.getRoots(myLocalFs);
    for (VirtualFile root : roots) {
      String rootPath = root.getPath();
      String prefix = StringUtil.commonPrefix(rootPath, substRoot.getPath());
      assertEmpty(prefix);
    }
  }

  public void testLocalRoots() {
    VirtualFile tempRoot = VirtualFileManager.getInstance().findFileByUrl("temp:///");
    assertNotNull(tempRoot);

    VirtualFile[] roots = myFs.getLocalRoots();
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
    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
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
    VirtualFile vXTxt = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(x);

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
    VirtualFile vXTxt = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(x);

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

    VirtualFile vFile = myLocalFs.refreshAndFindFileByIoFile(file);
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

    VirtualFile vFile = myLocalFs.refreshAndFindFileByIoFile(file);
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
    VirtualFile testTxt = ObjectUtils.assertNotNull(myLocalFs.refreshAndFindFileByIoFile(file));
    File file2 = new File(temp, "a/b/c/test2.txt");
    assertTrue(file2.createNewFile());
    VirtualFile test2Txt = ObjectUtils.assertNotNull(myLocalFs.refreshAndFindFileByIoFile(file2));

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
    VirtualFile testTxt = ObjectUtils.assertNotNull(myLocalFs.refreshAndFindFileByIoFile(file));

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
    VirtualFile testTxt = ObjectUtils.assertNotNull(myLocalFs.refreshAndFindFileByIoFile(file));

    File newParentF = new File(temp, "a/b/d");
    assertTrue(newParentF.mkdirs());
    VirtualFile newParent = ObjectUtils.assertNotNull(myLocalFs.refreshAndFindFileByIoFile(newParentF));

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
    VirtualFile testTxt = ObjectUtils.assertNotNull(myLocalFs.refreshAndFindFileByIoFile(file));

    File newParentF = new File(temp, "a/b/d");
    assertTrue(newParentF.mkdirs());
    VirtualFile newParent = ObjectUtils.assertNotNull(myLocalFs.refreshAndFindFileByIoFile(newParentF));

    checkEvents("Before:[VFileCopyEvent->new.txt]\n" +
                "After:[VFileCopyEvent->new.txt]\n" +
                "Before:[VFileDeleteEvent->test.txt]\n" +
                "After:[VFileDeleteEvent->test.txt]\n",

                new VFileCopyEvent(this, testTxt, newParent,"new.txt"),
                new VFileDeleteEvent(this, testTxt, false));
  }

  public void testProcessCompositeRenameEvents() throws IOException {
    File temp = createTempDirectory();
    File file = new File(temp, "a/b/c/test.txt");
    assertTrue(file.getParentFile().mkdirs());
    assertTrue(file.createNewFile());
    VirtualFile testTxt = ObjectUtils.assertNotNull(myLocalFs.refreshAndFindFileByIoFile(file));

    File file2 = new File(temp, "a/b/c/test2.txt");
    assertTrue(file2.createNewFile());
    VirtualFile test2Txt = ObjectUtils.assertNotNull(myLocalFs.refreshAndFindFileByIoFile(file2));

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
    VirtualDirectoryImpl vtemp = (VirtualDirectoryImpl)LocalFileSystem.getInstance().refreshAndFindFileByIoFile(temp);
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
    VirtualDirectoryImpl vtemp = (VirtualDirectoryImpl)LocalFileSystem.getInstance().refreshAndFindFileByIoFile(temp);
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
}
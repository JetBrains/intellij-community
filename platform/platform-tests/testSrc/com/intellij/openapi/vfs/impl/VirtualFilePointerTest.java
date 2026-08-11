// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl;

import com.intellij.CacheSwitcher;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.concurrency.Job;
import com.intellij.concurrency.JobLauncher;
import com.intellij.mock.MockVirtualFile;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.openapi.vfs.impl.jar.JarFileSystemImpl;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.Timings;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.TestTimeOut;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.UriUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.io.SuperUserStatus;
import com.intellij.util.system.OS;
import kotlin.Unit;
import kotlin.io.path.PathsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class VirtualFilePointerTest extends BareTestFixtureTestCase {
  private static final Logger LOG = Logger.getInstance(VirtualFilePointerTest.class);

  @Rule public TempDirectory tempDir = new TempDirectory();

  private final Disposable disposable = Disposer.newDisposable();
  private VirtualFilePointerManagerImpl myVirtualFilePointerManager;
  private Collection<VirtualFilePointer> pointersBefore;
  private int numberOfListenersBefore;

  @Before
  public void setUp() {
    myVirtualFilePointerManager = (VirtualFilePointerManagerImpl)VirtualFilePointerManager.getInstance();
    pointersBefore = myVirtualFilePointerManager.dumpAllPointers();
    numberOfListenersBefore = myVirtualFilePointerManager.numberOfListeners();
  }

  @After
  public void tearDown() {
    Disposer.dispose(disposable);
    var pointersAfter = myVirtualFilePointerManager.dumpAllPointers();
    var nListeners = myVirtualFilePointerManager.numberOfListeners();
    myVirtualFilePointerManager = null;
    assertEquals(numberOfListenersBefore, nListeners);
    assertEquals(pointersBefore, pointersAfter);
  }

  private VirtualFile getVirtualTempRoot() {
    return getVirtualFile(tempDir.getRootPath());
  }

  private static VirtualFile getVirtualFile(Path file) {
    return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file);
  }

  private VirtualFilePointer createPointerByFile(Path file, VirtualFilePointerListener fileListener) {
    var url = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, PathsKt.getInvariantSeparatorsPathString(file));
    var vFile = getVirtualFile(file);
    return (
      vFile == null
      ? myVirtualFilePointerManager.create(url, disposable, fileListener)
      : myVirtualFilePointerManager.create(vFile, disposable, fileListener)
    );
  }

  private static void verifyPointersInCorrectState(VirtualFilePointer[] pointers) {
    for (var pointer : pointers) {
      var file = pointer.getFile();
      assertTrue(file == null || file.isValid());
    }
  }

  private static final class LoggingListener implements VirtualFilePointerListener {
    private final boolean myVerbose;
    private final List<String> log = new ArrayList<>();

    private LoggingListener() {
      this(false);
    }

    private LoggingListener(boolean verbose) {
      myVerbose = verbose;
    }

    @Override
    public void beforeValidityChanged(VirtualFilePointer @NotNull [] pointers) {
      verifyPointersInCorrectState(pointers);
      log.add(buildMessage("before", pointers));
    }

    @Override
    public void validityChanged(VirtualFilePointer @NotNull [] pointers) {
      verifyPointersInCorrectState(pointers);
      log.add(buildMessage("after", pointers));
    }

    private String buildMessage(String startMsg, VirtualFilePointer[] pointers) {
      var buffer = new StringBuilder();
      buffer.append(startMsg).append(':');
      for (var i = 0; i < pointers.length; i++) {
        if (i > 0) buffer.append(':');
        var pointer = pointers[i];
        if (myVerbose) {
          buffer.append(pointer.getFileName()).append(":");
        }
        buffer.append(pointer.isValid());
      }
      return buffer.toString();
    }
  }

  @Test
  public void testDelete() {
    var fileToDelete = tempDir.newFileNio("toDelete.txt");
    var fileToDeleteListener = new LoggingListener();
    var fileToDeletePointer = createPointerByFile(fileToDelete, fileToDeleteListener);
    assertTrue(fileToDeletePointer.isValid());
    VfsTestUtil.deleteFile(getVirtualFile(fileToDelete));
    assertFalse(fileToDeletePointer.isValid());
    assertEquals("[before:true, after:false]", fileToDeleteListener.log.toString());
  }

  @Test//IJPL-176784
  public void testDeleteFileAndRecreateWithAnotherCase() {
    var pointersListener = new LoggingListener();

    var fileToDelete = tempDir.newFileNio("lower-case.txt");
    var pointerToFileToDelete = createPointerByFile(fileToDelete, pointersListener);
    assertTrue(pointerToFileToDelete.isValid());
    VfsTestUtil.deleteFile(getVirtualFile(fileToDelete));
    assertFalse(pointerToFileToDelete.isValid());

    var fileToReCreate = tempDir.newFileNio("lower-case.txt");
    var pointerToFileToReCreate = createPointerByFile(fileToReCreate, pointersListener);
    assertTrue(pointerToFileToReCreate.isValid());
    VfsTestUtil.deleteFile(getVirtualFile(fileToReCreate));
    assertFalse(pointerToFileToReCreate.isValid());

    var fileToReCreateWithAnotherCase = tempDir.newFileNio("LOWER-CASE.txt");
    var pointerToFileToReCreateAnotherCase = createPointerByFile(fileToReCreateWithAnotherCase, pointersListener);
    assertTrue(pointerToFileToReCreateAnotherCase.isValid());
  }

  @Test
  public void testSwitchingVfs() {
    var file = tempDir.newFileNio("myfile.txt");

    // avoiding root resolve in tempDir
    // otherwise there will be an exception on its remove using VirtualFile
    var pointer = myVirtualFilePointerManager.create(getVirtualFile(file), disposable, null);

    assertTrue(pointer.isValid());
    assertNotNull(pointer.getFile());

    CacheSwitcher.INSTANCE.switchIndexAndVfs(null, null, "resetting vfs", () -> {
      ((VirtualFilePointerManagerImpl)VirtualFilePointerManager.getInstance()).assertUrlBasedPointers();
      assertFalse(pointer.isValid());
      assertNull(pointer.getFile());
      return Unit.INSTANCE;
    });

    assertTrue(pointer.isValid());
    assertNotNull(pointer.getFile());
  }

  @Test
  public void testPointerAfterFileDeleteAndRecreateMustBeValid() {
    var fileToDelete = tempDir.newFileNio("toDelete.txt");
    var fileToDeleteListener = new LoggingListener();
    var fileToDeletePointer = createPointerByFile(fileToDelete, fileToDeleteListener);
    assertTrue(fileToDeletePointer.isValid());
    var file = fileToDeletePointer.getFile();
    assertNotNull(file);
    var parent = file.getParent();
    VfsTestUtil.deleteFile(getVirtualFile(fileToDelete));
    assertFalse(fileToDeletePointer.isValid());
    assertEquals("[before:true, after:false]", fileToDeleteListener.log.toString());
    fileToDeleteListener.log.clear();
    VfsTestUtil.createFile(parent, fileToDelete.getFileName().toString());
    assertTrue(fileToDeletePointer.isValid());
    assertEquals("[before:false, after:true]", fileToDeleteListener.log.toString());
  }

  @Test
  public void testPointerMustBeAlreadyUpToDateInAfterListener() {
    var fileToDelete = tempDir.newFileNio("file.txt");
    var pointer = createPointerByFile(fileToDelete, null);
    assertTrue(pointer.isValid());
    var file = pointer.getFile();
    assertNotNull(file);
    var connection = ApplicationManager.getApplication().getMessageBus().connect(disposable);
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        var url = ((VirtualFilePointerImpl)pointer).getNodeForTesting().getFileOrUrl();
        assertTrue(url.toString(), url instanceof String);
        assertFalse(pointer.isValid());
      }
    });
    VfsTestUtil.deleteFile(getVirtualFile(fileToDelete));
  }

  @Test
  public void testCreate() throws IOException {
    var fileToCreate = tempDir.getRootPath().resolve("toCreate.txt");
    var fileToCreateListener = new LoggingListener();
    var fileToCreatePointer = createPointerByFile(fileToCreate, fileToCreateListener);
    assertFalse(fileToCreatePointer.isValid());
    Files.createFile(fileToCreate);
    getVirtualTempRoot().refresh(false, true);
    assertTrue(fileToCreatePointer.isValid());
    assertEquals("[before:false, after:true]", fileToCreateListener.log.toString());
    var expectedUrl = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, PathsKt.getInvariantSeparatorsPathString(fileToCreate));
    assertEquals(expectedUrl.toUpperCase(Locale.US), fileToCreatePointer.getUrl().toUpperCase(Locale.US));
  }

  @Test//IJPL-187702
  public void pointerForFileSystemRoot_isEquivalentToPointerToUrlRoot() {
    var rootDir = Path.of("/");
    var rootPointerByFile = createPointerByFile(rootDir, null);
    assertTrue(rootPointerByFile.isValid());

    var rootPointerByUrl = myVirtualFilePointerManager.create("file:///", disposable, null);
    assertTrue(rootPointerByUrl.isValid());

    assertEquals("Root('/') access via File and via URL('file:///') should give the same results", rootPointerByFile, rootPointerByUrl);
  }

  @Test
  public void testPointerForFileSystemRoot1() {
    var rootDir = Path.of("/");
    assertThat(rootDir).exists();

    var pointer = createPointerByFile(rootDir, null);
    assertTrue(pointer.isValid());
  }

  @Test
  public void testPointerForFileSystemRoot2() {
    var pointer = myVirtualFilePointerManager.create(LocalFileSystem.PROTOCOL_PREFIX + "/", disposable, null);
    assertTrue(pointer.isValid());
  }

  @Test
  public void testPathNormalization() throws IOException {
    checkFileName("///", "");
  }

  @Test
  public void testPathNormalization2() throws IOException {
    checkFileName("\\\\", "/");
  }

  @Test
  public void testPathNormalization3() throws IOException {
    checkFileName("//", "/////");
  }

  @Test
  public void testPathNormalization4() throws IOException {
    checkFileName("/./", "/./");
  }

  private void checkFileName(String prefix, String suffix) throws IOException {
    var temp = getVirtualTempRoot();
    var name = "toCreate.txt";
    var path = (tempDir.getRootPath() + "/" + prefix + name + suffix).replace('\\', '/');
    var url = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, path);
    var fileToCreatePointer = myVirtualFilePointerManager.create(url, disposable, null);
    assertFalse(fileToCreatePointer.isValid());
    assertNull(fileToCreatePointer.getFile());

    var child = WriteAction.computeAndWait(() -> temp.createChildData(null, name));
    assertTrue(fileToCreatePointer.isValid());
    assertEquals(child, fileToCreatePointer.getFile());

    VfsTestUtil.deleteFile(child);
    assertFalse(fileToCreatePointer.isValid());
    assertNull(fileToCreatePointer.getFile());
  }

  @Test
  public void testMovePointedFile() throws IOException {
    var moveTarget = tempDir.newDirectoryPath("moveTarget");
    var fileToMove = tempDir.newFileNio("toMove.txt");

    var fileToMoveListener = new LoggingListener();
    var fileToMovePointer = createPointerByFile(fileToMove, fileToMoveListener);
    assertTrue(fileToMovePointer.isValid());
    doMove(fileToMove, moveTarget);
    assertTrue(fileToMovePointer.isValid());
    assertEquals("[before:true, after:true]", fileToMoveListener.log.toString());
  }

  @Test
  public void testMoveFileToExistingPointerMustValidatePointer() throws IOException {
    var moveTarget = tempDir.newDirectoryPath("moveTarget");
    var fileToMove = tempDir.newFileNio("toMove.txt");

    var listener = new LoggingListener();
    var fileToMoveTargetPointer = createPointerByFile(moveTarget.resolve(fileToMove.getFileName().toString()), listener);
    assertFalse(fileToMoveTargetPointer.isValid());
    doMove(fileToMove, moveTarget);
    assertTrue(fileToMoveTargetPointer.isValid());
    assertEquals("[before:false, after:true]", listener.log.toString());
  }

  @Test
  public void testMoveSrcDirUnderNewRootShouldGenerateRootsChanged() throws IOException {
    var moveTarget = tempDir.newDirectoryPath("moveTarget");
    var dirToMove = tempDir.newFileNio("dirToMove");

    var listener = new LoggingListener();
    var dirToMovePointer = createPointerByFile(dirToMove, listener);
    assertTrue(dirToMovePointer.isValid());
    doMove(dirToMove, moveTarget);
    assertTrue(dirToMovePointer.isValid());
    assertEquals("[before:true, after:true]", listener.log.toString());
  }

  @Test
  public void testMovePointedFileUnderAnotherPointer() throws IOException {
    var moveTarget = tempDir.newDirectoryPath("moveTarget");
    var fileToMove = tempDir.newFileNio("toMove.txt");

    var listener = new LoggingListener();
    var targetListener = new LoggingListener();

    var fileToMovePointer = createPointerByFile(fileToMove, listener);
    var fileToMoveTargetPointer = createPointerByFile(moveTarget.resolve(fileToMove.getFileName().toString()), targetListener);

    assertFalse(fileToMoveTargetPointer.isValid());
    doMove(fileToMove, moveTarget);
    assertTrue(fileToMovePointer.isValid());
    assertTrue(fileToMoveTargetPointer.isValid());
    assertEquals("[before:true, after:true]", listener.log.toString());
    assertEquals("[before:false, after:true]", targetListener.log.toString());
    assertEquals(VfsUtilCore.pathToUrl(getVirtualFile(moveTarget).getPath() + '/' + fileToMove.getFileName().toString()), fileToMovePointer.getUrl());
    assertEquals(VfsUtilCore.pathToUrl(getVirtualFile(moveTarget).getPath() + '/' + fileToMove.getFileName().toString()), fileToMoveTargetPointer.getUrl());
  }

  private void doMove(Path fileToMove, Path moveTarget) throws IOException {
    var virtualFile = getVirtualFile(fileToMove);
    assertTrue(virtualFile.isValid());
    var target = getVirtualFile(moveTarget);
    assertTrue(target.isValid());
    assertTrue(target.isDirectory());
    WriteAction.runAndWait(() -> virtualFile.move(this, target));
  }

  @Test
  public void testRenamingPointedFile() throws IOException {
    var file = tempDir.newFileNio("f1");
    var listener = new LoggingListener();
    var pointer = createPointerByFile(file, listener);
    assertTrue(pointer.isValid());
    WriteAction.runAndWait(() -> getVirtualFile(file).rename(this, "f2"));
    assertTrue(pointer.isValid());
    assertEquals("[before:true, after:true]", listener.log.toString());
  }

  @Test
  public void testRenameFileToExistingPointerMustValidatePointer() throws IOException {
    var file = tempDir.newFileNio("f1");
    var listener = new LoggingListener();
    var pointer = createPointerByFile(file.resolveSibling("f2"), listener);
    assertFalse(pointer.isValid());
    WriteAction.runAndWait(() -> getVirtualFile(file).rename(this, "f2"));
    assertTrue(pointer.isValid());
    assertEquals("[before:false, after:true]", listener.log.toString());
  }

  @Test
  public void testTwoPointersBecomeOneAfterFileRenamedUnderTheOtherName() throws IOException {
    var f1 = tempDir.newFileNio("f1");
    var vFile1 = getVirtualFile(f1);
    var url1 = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, PathsKt.getInvariantSeparatorsPathString(f1));
    var listener1 = new LoggingListener();
    var pointer1 = myVirtualFilePointerManager.create(url1, disposable, listener1);
    assertTrue(pointer1.isValid());

    var url2 = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, PathsKt.getInvariantSeparatorsPathString(f1.getParent()) + "/f2");
    var listener2 = new LoggingListener();
    var pointer2 = myVirtualFilePointerManager.create(url2, disposable, listener2);
    assertFalse(pointer2.isValid());

    WriteAction.runAndWait(() -> vFile1.rename(this, "f2"));

    assertTrue(pointer1.isValid());
    assertTrue(pointer2.isValid());
    assertEquals("[before:true, after:true]", listener1.log.toString());
    assertEquals("[before:false, after:true]", listener2.log.toString());
  }

  @Test
  public void testCreateFileToExistingPointerUrlMustValidatePointer() throws IOException {
    var fileToCreate = tempDir.getRootPath().resolve("toCreate1.txt");
    var fileToCreateListener = new LoggingListener();
    var fileToCreatePointer = createPointerByFile(fileToCreate, fileToCreateListener);
    assertFalse(fileToCreatePointer.isValid());
    Files.createFile(fileToCreate);
    getVirtualTempRoot().refresh(false, true);
    assertTrue(fileToCreatePointer.isValid());
    assertEquals("[before:false, after:true]", fileToCreateListener.log.toString());
    var expectedUrl = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, PathsKt.getInvariantSeparatorsPathString(fileToCreate));
    assertEquals(0, FileUtil.comparePaths(fileToCreatePointer.getUrl(), expectedUrl));
  }

  @Test
  public void testMultipleNotifications() throws IOException {
    var file1 = tempDir.getRootPath().resolve("f1");
    var file2 = tempDir.getRootPath().resolve("f2");
    var listener = new LoggingListener();
    var pointer1 = createPointerByFile(file1, listener);
    var pointer2 = createPointerByFile(file2, listener);
    assertFalse(pointer1.isValid());
    assertFalse(pointer2.isValid());
    Files.createFile(file1);
    Files.createFile(file2);
    getVirtualTempRoot().refresh(false, true);
    assertEquals("[before:false:false, after:true:true]", listener.log.toString());
  }

  @Test
  public void testJars() throws IOException {
    var vTemp = getVirtualTempRoot();
    var jarParent = tempDir.newDirectoryPath("jarParent");
    var jar = jarParent.resolve("x.jar");
    var originalJar = Path.of(PathManagerEx.getTestDataPath(), "psi/generics22/collect-2.2.jar");
    Files.copy(originalJar, jar);
    assertNotNull(getVirtualFile(jar));  // make sure we receive events when .jar changes

    var listener = new LoggingListener();
    var jarParentPointer = createPointerByFile(jarParent, listener);
    var jarUrl = VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, PathsKt.getInvariantSeparatorsPathString(jar) + JarFileSystem.JAR_SEPARATOR);
    var jarPointer = myVirtualFilePointerManager.create(jarUrl, disposable, listener);
    var pointersToWatch = new VirtualFilePointer[]{jarParentPointer, jarPointer};
    assertTrue(jarParentPointer.isValid());
    assertTrue(jarPointer.isValid());
    assertEquals(jarUrl, jarPointer.getUrl());
    assertEquals("x.jar", jarPointer.getFileName());

    Files.delete(jar);
    Files.delete(jarParent);
    vTemp.refresh(false, true);
    verifyPointersInCorrectState(pointersToWatch);
    assertFalse(jarParentPointer.isValid());
    assertFalse(jarPointer.isValid());
    assertThat(vTemp.getChildren()).isEmpty();

    Files.createDirectories(jarParent);
    Files.copy(originalJar, jar);
    Files.setLastModifiedTime(jar, FileTime.from(Instant.now()));
    vTemp.refresh(false, true);
    verifyPointersInCorrectState(pointersToWatch);
    assertTrue(jarParentPointer.isValid());
    assertTrue(jarPointer.isValid());

    var insideJar = jarUrl + "/META-INF/MANIFEST.MF";
    var insidePointer = myVirtualFilePointerManager.create(insideJar, disposable, null);
    assertTrue(insidePointer.isValid());

    JarFileSystemImpl.cleanupForNextTest();  // won't let delete jar otherwise
    Files.delete(jar);
    Files.delete(jarParent);
    vTemp.refresh(false, true);
    verifyPointersInCorrectState(pointersToWatch);
    assertFalse(jarParentPointer.isValid());
    assertFalse(jarPointer.isValid());

    assertFalse(insidePointer.isValid());
  }

  @Test
  public void testJars2() throws IOException {
    var vTemp = getVirtualTempRoot();
    var jarParent = tempDir.newDirectoryPath("jarParent");
    var jar = jarParent.resolve("x.jar");
    var originalJar = Path.of(PathManagerEx.getTestDataPath(), "psi/generics22/collect-2.2.jar");
    Files.copy(originalJar, jar);
    getVirtualFile(jar); // Make sure we receive events when jar changes

    var listener = new LoggingListener();
    var jarUrl = VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, PathsKt.getInvariantSeparatorsPathString(jar) + JarFileSystem.JAR_SEPARATOR);
    var jarPointer = myVirtualFilePointerManager.create(jarUrl, disposable, listener);
    var pointersToWatch = new VirtualFilePointer[]{jarPointer};
    Files.delete(jar);

    var t = TestTimeOut.setTimeout(10, TimeUnit.SECONDS);
    var i = 0;
    for (; !t.timedOut() && i < 30; i++) {
      vTemp.refresh(false, true);
      verifyPointersInCorrectState(pointersToWatch);
      assertFalse(jarPointer.isValid());
      assertThat(jarParent).exists();
      LOG.debug("before structureModificationCount=" + ManagingFS.getInstance().getStructureModificationCount());
      var vJar = getVirtualFile(jar);
      assertNull(vJar);

      LOG.debug("copying");
      Files.copy(originalJar, jar);
      vTemp.refresh(false, true);
      verifyPointersInCorrectState(pointersToWatch);
      vJar = getVirtualFile(jar);
      assertNotNull(vJar);
      LOG.debug("after structureModificationCount=" + ManagingFS.getInstance().getStructureModificationCount());
      assertTrue(jarPointer.isValid());

      Files.delete(jar);
      vTemp.refresh(false, true);
      verifyPointersInCorrectState(pointersToWatch);
      assertFalse(jarPointer.isValid());
    }
    LOG.debug("i = " + i);
  }

  @Test
  public void updateJarFilePointerWhenJarFileIsRestored() throws IOException {
    var jarParent = tempDir.newDirectoryPath("jarParent");
    var jar = jarParent.resolve("x.jar");
    var originalJar = Path.of(PathManagerEx.getTestDataPath(), "psi/generics22/collect-2.2.jar");
    Files.copy(originalJar, jar);
    var jarUrl = VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, PathsKt.getInvariantSeparatorsPathString(jar) + JarFileSystem.JAR_SEPARATOR);
    assertNotNull(getVirtualFile(jar));
    var jarFile1 = VirtualFileManager.getInstance().refreshAndFindFileByUrl(jarUrl);
    assertNotNull(jarFile1);
    Files.delete(jar);
    jarFile1.refresh(false, false);  // it's important to refresh only JAR file here, not the corresponding local file
    assertFalse(jarFile1.isValid());

    var listener = new LoggingListener();
    var jarPointer = myVirtualFilePointerManager.create(jarUrl, disposable, listener);
    assertFalse(jarPointer.isValid());
    assertNull(jarPointer.getFile());

    Files.copy(originalJar, jar);
    assertNotNull(getVirtualFile(jar));
    var jarFile2 = VirtualFileManager.getInstance().refreshAndFindFileByUrl(jarUrl);
    assertNotNull(jarFile2);
    assertTrue(jarPointer.isValid());
    assertEquals(jarFile2, jarPointer.getFile());
  }

  @Test
  public void testFilePointerUpdate() throws IOException {
    var vTemp = getVirtualTempRoot();
    var file = tempDir.getRootPath().resolve("f1");
    var pointer = createPointerByFile(file, null);
    assertFalse(pointer.isValid());

    Files.createFile(file);
    vTemp.refresh(false, true);
    assertTrue(pointer.isValid());

    Files.delete(file);
    vTemp.refresh(false, true);
    assertFalse(pointer.isValid());
  }

  @Test
  public void testDoubleDispose() {
    var file = tempDir.newFileNio("f1");
    var vFile = getVirtualFile(file);
    var disposable = Disposer.newDisposable();
    var pointer = myVirtualFilePointerManager.create(vFile, disposable, null);
    assertTrue(pointer.isValid());
    Disposer.dispose(disposable);
    assertFalse(pointer.isValid());
  }

  @PerformanceUnitTest
  @Test
  public void testThreadsPerformance() throws Exception {
    var vTemp = getVirtualTempRoot();
    var ioSandPtr = tempDir.newFileNio("parent2/f2");
    var ioPtr = tempDir.newFileNio("parent1/f1");

    vTemp.refresh(false, true);
    var pointer = createPointerByFile(ioPtr, null);
    assertTrue(pointer.isValid());
    assertNotNull(pointer.getFile());
    assertTrue(pointer.getFile().isValid());
    Collection<Job> reads = ConcurrentCollectionFactory.createConcurrentSet();
    var listener = new VirtualFileListener() {
      @Override
      public void fileCreated(@NotNull VirtualFileEvent event) {
        stressRead(pointer, reads);
      }

      @Override
      public void fileDeleted(@NotNull VirtualFileEvent event) {
        stressRead(pointer, reads);
      }
    };
    var connection = ApplicationManager.getApplication().getMessageBus().connect();
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkVirtualFileListenerAdapter(listener));
    try {
      var N = Timings.adjustAccordingToMySpeed(1000, false);
      LOG.debug("N = " + N);
      for (var i = 0; i < N; i++) {
        assertNotNull(pointer.getFile());
        NioFiles.deleteRecursively(ioPtr.getParent());
        vTemp.refresh(false, true);

        // ptr is now null, cached as map
        var v = requireNonNull(LocalFileSystem.getInstance().findFileByNioFile(ioSandPtr));
        WriteAction.runAndWait(() -> {
          v.delete(this); //inc FS modCount
          var file = requireNonNull(LocalFileSystem.getInstance().findFileByNioFile(ioSandPtr.getParent()));
          file.createChildData(this, ioSandPtr.getFileName().toString());
        });

        // ptr is still null
        Files.createDirectories(ioPtr.getParent());
        Files.createFile(ioPtr);
        stressRead(pointer, reads);
        vTemp.refresh(false, true);
      }
    }
    finally {
      connection.disconnect();  // unregister listener early
      for (var read : reads) {
        while (!read.isDone()) {
          read.waitForCompletion(1000);
        }
      }
    }
  }

  @SuppressWarnings("UseRunReadActionBlockingShortcut")
  private static void stressRead(VirtualFilePointer pointer, Collection<? super Job> reads) {
    for (var i = 0; i < 10; i++) {
      var reference = new AtomicReference<Job>();
      reference.set(JobLauncher.getInstance().submitToJobThread(() -> ApplicationManager.getApplication().runReadAction(() -> {
        var file = pointer.getFile();
        if (file != null && !file.isValid()) {
          throw new IncorrectOperationException("I've caught it. I am that good");
        }
      }), future -> {
        try {
          future.get();
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
        finally {
          reads.remove(reference.get());
        }
      }));
      reads.add(reference.get());
    }
  }

  @Test
  public void testTwoPointersMergingIntoOne() throws IOException {
    var root = getVirtualTempRoot();
    var dir1 = WriteAction.computeAndWait(() -> root.createChildDirectory(this, "dir1"));
    var dir2 = WriteAction.computeAndWait(() -> root.createChildDirectory(this, "dir2"));

    var p1 = myVirtualFilePointerManager.create(dir1, disposable, null);
    var p2 = myVirtualFilePointerManager.create(dir2, disposable, null);
    assertTrue(p1.isValid());
    assertEquals(dir1, p1.getFile());
    assertTrue(p2.isValid());
    assertEquals(dir2, p2.getFile());

    WriteAction.runAndWait(() -> dir1.delete(this));
    assertNull(p1.getFile());
    assertEquals(dir2, p2.getFile());
    myVirtualFilePointerManager.assertConsistency();

    WriteAction.runAndWait(() -> dir2.rename(this, "dir1"));
    assertEquals(dir2, p1.getFile());
    assertEquals(dir2, p2.getFile());

    WriteAction.runAndWait(() -> dir2.rename(this, "dir2"));
    assertEquals(dir2, p1.getFile());
    assertEquals(dir2, p2.getFile());

    WriteAction.runAndWait(() -> root.createChildDirectory(this, "dir1"));
    assertEquals(dir2, p1.getFile());
    assertEquals(dir2, p2.getFile());
    myVirtualFilePointerManager.assertConsistency();
  }

  @Test
  public void testDotDot() throws IOException {
    var root = getVirtualTempRoot();
    var dir1 = WriteAction.computeAndWait(() -> root.createChildDirectory(this, "dir1"));
    var file = WriteAction.computeAndWait(() -> dir1.createChildData(this, "x.txt"));
    var dir2 = WriteAction.computeAndWait(() -> root.createChildDirectory(this, "dir2"));
    var pointer = myVirtualFilePointerManager.create(file.getUrl(), disposable, null);
    assertTrue(pointer.isValid());
    pointer = myVirtualFilePointerManager.create(dir2.getUrl() + "/../" + dir1.getName() + "/" + file.getName(), disposable, null);
    assertEquals(file, pointer.getFile());
    var nonExistingPointer = myVirtualFilePointerManager.create(dir2.getUrl() + "/../" + dir1.getName() + "/non-existing.txt", disposable, null);
    assertNull(nonExistingPointer.getFile());
  }

  @Test
  public void testAlienVirtualFileSystemPointerRemovedFromUrlToIdentityCacheOnDispose() {
    VirtualFile mockVirtualFile = new MockVirtualFile("test_name", "test_text");
    var disposable = Disposer.newDisposable();
    var pointer = myVirtualFilePointerManager.create(mockVirtualFile, disposable, null);
    assertThat(pointer).isInstanceOf(IdentityVirtualFilePointer.class);
    assertTrue(pointer.isValid());

    VirtualFile virtualFileWithSameUrl = new MockVirtualFile("test_name", "test_text");
    var updatedPointer = myVirtualFilePointerManager.create(virtualFileWithSameUrl, disposable, null);
    assertThat(updatedPointer).isInstanceOf(IdentityVirtualFilePointer.class);
    assertTrue(pointer.isValid());
    assertEquals(1, myVirtualFilePointerManager.numberOfCachedUrlToIdentity());

    Disposer.dispose(disposable);
    assertEquals(0, myVirtualFilePointerManager.numberOfCachedUrlToIdentity());
  }

  @Test
  public void testListenerWorksInTempFileSystem() throws IOException {
    var tmpRoot = VirtualFileManager.getInstance().findFileByUrl("temp:///");
    assertNotNull(tmpRoot);
    assertThat(tmpRoot.getFileSystem()).isInstanceOf(TempFileSystem.class);
    var testDir = WriteAction.computeAndWait(() -> tmpRoot.createChildDirectory(this, getTestName(true)));
    Disposer.register(disposable, () -> VfsTestUtil.deleteFile(testDir));

    var child = WriteAction.computeAndWait(() -> testDir.createChildData(this, "a.txt"));
    var listener = new LoggingListener();
    var pointer = VirtualFilePointerManager.getInstance().create(child, disposable, listener);

    WriteAction.runAndWait(() -> child.delete(this));
    assertEquals("[before:true, after:false]", listener.log.toString());
    assertNull(pointer.getFile());

    listener.log.clear();
    var newChild = WriteAction.computeAndWait(() -> testDir.createChildData(this, "a.txt"));
    assertEquals("[before:false, after:true]", listener.log.toString());
    assertEquals(newChild, pointer.getFile());
  }

  @Test
  public void testStressConcurrentAccess() throws Exception {
    var fileToCreatePointer = createPointerByFile(tempDir.getRootPath(), null);
    var listener = new VirtualFilePointerListener() { };
    var t = TestTimeOut.setTimeout(30, TimeUnit.SECONDS);
    var run = new AtomicBoolean(false);
    var exception = new AtomicReference<Throwable>(null);
    int i;
    var nThreads = Runtime.getRuntime().availableProcessors();
    var fakeRoot = FilePartNodeRoot.createFakeRoot(LocalFileSystem.getInstance());
    for (i = 0; !t.timedOut(i) && i<50_000; i++) {
      var disposable = Disposer.newDisposable();
      try {
        // supply listener to separate pointers under one root so that it will be removed on dispose
        var bb = (VirtualFilePointerImpl)myVirtualFilePointerManager.create(fileToCreatePointer.getUrl() + "/bb", disposable, listener);

        var ready = new CountDownLatch(nThreads);
        Runnable read = () -> {
          try {
            ready.countDown();
            while (run.get()) {
              bb.getNodeForTesting().update(((VirtualFilePointerImpl)fileToCreatePointer).getNodeForTesting(), fakeRoot, "test", null);
            }
          }
          catch (Throwable e) {
            exception.set(e);
          }
        };

        run.set(true);
        List<Job> jobs = new ArrayList<>(nThreads);
        for (var it = 0; it < nThreads; it++) {
          jobs.add(JobLauncher.getInstance().submitToJobThread(read, null));
        }
        var isReady = ready.await(10, TimeUnit.SECONDS);
        assumeTrue("It took too long to start all jobs", isReady);

        myVirtualFilePointerManager.create(fileToCreatePointer.getUrl() + "/b/c", disposable, listener);

        run.set(false);
        for (var job : jobs) {
          job.waitForCompletion(2_000);
        }
        ExceptionUtil.rethrowAll(exception.get());
      }
      finally {
        Disposer.dispose(disposable);
      }
    }
    LOG.debug("i = " + i);
  }

  @Test
  public void testGetChildrenMustIncreaseModificationCountIfFoundNewFile() throws IOException {
    var vTemp = getVirtualTempRoot();
    var file = tempDir.getRootPath().resolve("x.txt");
    var pointer = createPointerByFile(file, null);

    var t = TestTimeOut.setTimeout(10, TimeUnit.SECONDS);
    int i;
    for (i = 0; !t.timedOut() && i < 30; i++) {
      LOG.info("i = " + i);
      Files.createFile(file);
      vTemp.refresh(false, true);
      @SuppressWarnings("deprecation")
      var future = ApplicationManager.getApplication().executeOnPooledThread(() -> ReadAction.run(() -> {
        for (var k = 0; k < 100; k++) {
          vTemp.getChildren();
        }
      }));
      TimeoutUtil.sleep(100);
      var vFile = requireNonNull(getVirtualFile(file));
      assertTrue(vFile.isValid());
      assertTrue(pointer.isValid());
      Files.delete(file);
      vTemp.refresh(false, true);
      assertFalse(pointer.isValid());
      while (!future.isDone()) {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      }
    }
    LOG.debug("i = " + i);
  }

  @Test
  public void testSeveralDirectoriesWithCommonPrefix() throws IOException {
    var vDir = getVirtualTempRoot();
    assertNotNull(vDir);
    vDir.getChildren();
    vDir.refresh(false, true);

    var listener = new LoggingListener();
    myVirtualFilePointerManager.create(vDir.getUrl() + "/d1/subdir", disposable, listener);
    myVirtualFilePointerManager.create(vDir.getUrl() + "/d2/subdir", disposable, listener);

    var dir = Path.of(vDir.getPath(), "d1");
    Files.createDirectories(dir);
    getVirtualFile(dir).getChildren();
    assertEquals("[]", listener.log.toString());
    listener.log.clear();

    var subDir = dir.resolve("subdir");
    Files.createDirectories(subDir);
    getVirtualFile(subDir);
    assertEquals("[before:false, after:true]", listener.log.toString());
  }

  @Test
  public void testDirectoryPointersWork() throws IOException {
    var vDir = getVirtualTempRoot();
    var deep = WriteAction.computeAndWait(() -> vDir.createChildDirectory(this, "deep"));

    var listener = new LoggingListener();
    var disposable = Disposer.newDisposable();
    myVirtualFilePointerManager.createDirectoryPointer(vDir.getUrl(), false, disposable, listener);

    WriteAction.runAndWait(() -> vDir.createChildData(this, "1"));
    assertEquals("[before:true, after:true]", listener.log.toString());
    Disposer.dispose(disposable);
    listener = new LoggingListener();
    myVirtualFilePointerManager.createDirectoryPointer(vDir.getUrl(), true, this.disposable, listener);

    WriteAction.runAndWait(() -> deep.createChildData(this, "1"));
    assertEquals("[before:true, after:true]", listener.log.toString());
  }

  @Test
  public void testNotQuiteCanonicalPath() throws IOException {
    var vDir = getVirtualTempRoot();
    var deep = WriteAction.computeAndWait(() -> vDir.createChildDirectory(this, "deep"));
    var file = WriteAction.computeAndWait(() -> deep.createChildData(this, "x..txt"));
    var ptr = myVirtualFilePointerManager.create(file, disposable, null);
    assertTrue(ptr.isValid());

    WriteAction.runAndWait(() -> vDir.createChildData(this, "existing.txt"));
    var ptr2 = myVirtualFilePointerManager.create(deep.getUrl() + "/../existing.txt", disposable, null);
    assertTrue(ptr2.isValid());

    var ptr3 = myVirtualFilePointerManager.create(deep.getUrl() + "/../madeUp.txt", disposable, null);
    assertFalse(ptr3.isValid());
  }

  @Test
  public void testVirtualPointerForSubdirMustNotFireWhenSuperDirectoryCreated() throws IOException {
    var vDir = getVirtualTempRoot();
    assertNotNull(vDir);
    vDir.getChildren();
    vDir.refresh(false, true);

    var listener = new LoggingListener();
    var subPtr = myVirtualFilePointerManager.create(vDir.getUrl() + "/cmake/subdir", disposable, listener);

    var cmake = WriteAction.computeAndWait(() -> vDir.createChildDirectory(vDir, "cmake"));
    assertEquals("[]", listener.log.toString());
    listener.log.clear();

    WriteAction.computeAndWait(() -> cmake.createChildDirectory(cmake, "subdir"));
    assertEquals("[before:false, after:true]", listener.log.toString());
    assertTrue(subPtr.isValid());

    listener.log.clear();
    FileUtil.rename(new File(cmake.getPath()), "newCmake");
    vDir.refresh(false, true);
    assertEquals("[before:true, after:false]", listener.log.toString());
    assertFalse(subPtr.isValid());
  }

  @Test
  public void testCreatePointerWithCrazyUrlContainingSlashDotMustNotLeadToException() {
    var dirToCreate = tempDir.getRootPath().resolve("ToCreate");

    var dirPointer = createPointerByFile(dirToCreate, null);
    var dirDotPointer = createPointerByFile(Path.of(dirToCreate.toString(), "."), null);
    assertSame(dirDotPointer, dirPointer);
  }

  @Test
  public void listenerIsFiredForPointerCreatedBetweenAsyncAndSyncVfsEventProcessing() throws IOException {
    var vDir = getVirtualTempRoot();
    var childName = "child";

    EdtTestUtil.runInEdtAndWait(()-> {
      assertNull(vDir.findChild(childName));
      assertTrue(new File(vDir.getPath(), childName).createNewFile());

      var semaphore = new Semaphore(1);
      VirtualFileManager.getInstance().addAsyncFileListener(_ -> {
        semaphore.up();
        return null;
      }, disposable);

      VfsUtil.markDirtyAndRefresh(true, true, false, vDir);
      assertTrue(semaphore.waitFor(10_000));

      var listener = new LoggingListener();
      var pointer = VirtualFilePointerManager.getInstance().create(vDir.getUrl() + "/" + childName, disposable, listener);
      assertNull(pointer.getFile());

      var start = System.currentTimeMillis();
      do {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        TimeoutUtil.sleep(1);
      } while (pointer.getFile() == null && System.currentTimeMillis() - start < 10_000);
      assertNotNull(pointer.getFile());

      assertEquals("[before:false, after:true]", listener.log.toString());
    }, false);
  }

  @Test
  public void testDifferentFileSystemsLocalFSAndJarFSWithSimilarUrlsMustReturnDifferentInstances() throws IOException {
    var vDir = getVirtualTempRoot();
    WriteAction.runAndWait(() -> {
      var v1 = vDir.createChildDirectory(this, "p1").createChildData(this, "p.jar");
      IoTestUtil.createTestJar(new File(v1.getPath()));
      v1.refresh(false, true);
      var j1 = JarFileSystem.getInstance().getJarRootForLocalFile(v1);
      assertNotNull(j1);
      var v2 = vDir.createChildDirectory(this, "p2").createChildData(this, "p.jar");
      IoTestUtil.createTestJar(new File(v2.getPath()));
      v2.refresh(false, true);
      var j2 = JarFileSystem.getInstance().getJarRootForLocalFile(v2);
      assertNotNull(j2);
      assertNotSame(j1, j2);
      assertNotSame(v1, v2);
      var p1 = myVirtualFilePointerManager.create(v1, disposable, null);
      var p2 = myVirtualFilePointerManager.create(v2, disposable, null);
      assertNotSame(p1, p2);
      var pj1 = myVirtualFilePointerManager.create(j1, disposable, null);
      var pj2 = myVirtualFilePointerManager.create(j2, disposable, null);
      assertNotSame(pj1, pj2);
      assertNotSame(p1, pj1);
      assertNotSame(p2, pj2);
    });
  }

  @Test
  public void testFileUrlNormalization() {
    assertEquals("file://", myVirtualFilePointerManager.create("file://", disposable, null).getUrl());

    if (OS.CURRENT == OS.Windows) {
      assertEquals("file://X:", myVirtualFilePointerManager.create("file://X:/", disposable, null).getUrl());
      assertEquals("file://X:", myVirtualFilePointerManager.create("file://X://", disposable, null).getUrl());

      assertEquals("file://X:/a", myVirtualFilePointerManager.create("file://X://a/", disposable, null).getUrl());
      assertEquals("file://X:/a", myVirtualFilePointerManager.create("file://X://a//", disposable, null).getUrl());
      assertEquals("file://X:/a", myVirtualFilePointerManager.create("file://X://a///", disposable, null).getUrl());
    }
    else {
      assertEquals("file:///", myVirtualFilePointerManager.create("file:///", disposable, null).getUrl());
      assertEquals("file:///", myVirtualFilePointerManager.create("file:////", disposable, null).getUrl());

      assertEquals("file:///a", myVirtualFilePointerManager.create("file:////a/", disposable, null).getUrl());
      assertEquals("file:///a", myVirtualFilePointerManager.create("file:////a//", disposable, null).getUrl());
      assertEquals("file:///a", myVirtualFilePointerManager.create("file:////a///", disposable, null).getUrl());
    }
  }

  @Test
  public void testCleanupPath() {
    IoTestUtil.assumeUnix();

    assertEquals("/", VirtualFilePointerManagerImpl.cleanupPath("/"));
    assertEquals("/", VirtualFilePointerManagerImpl.cleanupPath("//"));
    assertEquals("/", VirtualFilePointerManagerImpl.cleanupPath("///"));
    assertEquals("/", VirtualFilePointerManagerImpl.cleanupPath("////"));

    assertEquals("/a", VirtualFilePointerManagerImpl.cleanupPath("/a/"));
    assertEquals("/a", VirtualFilePointerManagerImpl.cleanupPath("//a//"));
    assertEquals("/a", VirtualFilePointerManagerImpl.cleanupPath("///a///"));
    assertEquals("/a", VirtualFilePointerManagerImpl.cleanupPath("////a////"));

    assertEquals("/a.jar", VirtualFilePointerManagerImpl.cleanupPath("/a.jar!/"));
    assertEquals("/a.jar", VirtualFilePointerManagerImpl.cleanupPath("//a.jar!/"));
    assertEquals("/a.jar", VirtualFilePointerManagerImpl.cleanupPath("///a.jar!/"));
    assertEquals("/a.jar", VirtualFilePointerManagerImpl.cleanupPath("////a.jar!/"));

    //TODO RC: should we compact '//' after '!' also?
    //    i.e. assertEquals("/a.jar", VirtualFilePointerManagerImpl.cleanupPath("////a.jar!//"));
  }

  @Test
  public void testUncPathNormalization() {
    IoTestUtil.assumeWindows();
    assertEquals("\\\\wsl$\\Ubuntu\\", createPointerByFile(Path.of("\\\\wsl$\\Ubuntu"), null).getPresentableUrl());
    assertEquals("\\\\wsl$\\Ubuntu\\bin", createPointerByFile(Path.of("//wsl$//Ubuntu//bin//"), null).getPresentableUrl());
  }

  @Test
  public void testSpacesOnlyFileNamesUnderUnixMustBeAllowed() {
    IoTestUtil.assumeUnix();
    var vDir = getVirtualTempRoot();
    var pointer = myVirtualFilePointerManager.create(vDir.getUrl() + "/xxx/ /c.txt", disposable, null);
    assertFalse(pointer.isValid());
  }

  @Test
  public void testCrazyExclamationMarkInFileNameMustBeAllowed() {
    IoTestUtil.assumeWindows();
    var vDir = getVirtualTempRoot();
    var rel = "/xxx/!/c.txt";
    var pointer = myVirtualFilePointerManager.create(vDir.getUrl() + rel, disposable, null);
    assertFalse(pointer.isValid());
    assertTrue(FileUtil.createIfDoesntExist(new File(vDir.getPath() + rel)));
    vDir.refresh(false, true);
    assertTrue(pointer.isValid());
  }

  @Test
  public void testUnc() throws IOException {
    IoTestUtil.assumeWindows();
    var uncRootPath = Paths.get(IoTestUtil.toLocalUncPath(tempDir.getRoot().getPath()));
    assumeTrue("Cannot access " + uncRootPath, Files.isDirectory(uncRootPath));

    var vTemp = LocalFileSystem.getInstance().refreshAndFindFileByPath(uncRootPath.toString());
    assertNotNull("not found: " + uncRootPath, vTemp);
    var jarParent = Files.createDirectory(uncRootPath.resolve("jarParent"));
    var jar = jarParent.resolve("x.jar");
    var originalJar = Paths.get(PathManagerEx.getTestDataPath(), "psi/generics22/collect-2.2.jar");
    Files.copy(originalJar, jar);
    assertNotNull(getVirtualFile(jar));  // make sure we receive events when jar changes

    var listener = new LoggingListener();
    var jarParentPointer = createPointerByFile(jarParent, listener);
    var jarUrl = VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, PathsKt.getInvariantSeparatorsPathString(jar) + JarFileSystem.JAR_SEPARATOR);
    var jarPointer = myVirtualFilePointerManager.create(jarUrl, disposable, listener);
    VirtualFilePointer[] pointersToWatch = {jarParentPointer, jarPointer};
    assertTrue("invalid: " + jarParentPointer, jarParentPointer.isValid());
    assertTrue("invalid: " + jarPointer, jarPointer.isValid());

    Files.delete(jar);
    Files.delete(jarParent);
    vTemp.refresh(false, true);
    verifyPointersInCorrectState(pointersToWatch);
    assertFalse("still valid: " + jarParentPointer, jarParentPointer.isValid());
    assertFalse("still valid: " + jarPointer, jarPointer.isValid());

    Files.createDirectory(jarParent);
    Files.copy(originalJar, jar);
    Files.setLastModifiedTime(jar, FileTime.from(Instant.now()));
    vTemp.refresh(false, true);
    verifyPointersInCorrectState(pointersToWatch);
    assertTrue("invalid: " + jarParentPointer, jarParentPointer.isValid());
    assertTrue("invalid: " + jarPointer, jarPointer.isValid());

    Files.delete(jar);
    Files.delete(jarParent);
    vTemp.refresh(false, true);
    verifyPointersInCorrectState(pointersToWatch);
    assertFalse("still valid: " + jarParentPointer, jarParentPointer.isValid());
    assertFalse("still valid: " + jarPointer, jarPointer.isValid());
  }

  @Test
  public void testProjectUnderNetworkMountDoesntOpenAnymoreAfterUpgradeTo2019_3() {
    assertNotNull(myVirtualFilePointerManager.create("file://Z://.idea/Q.iml", disposable, null));
  }

  @Test
  public void testCreateActualFileEventMustChangePointersCreatedEarlierWithWrongCase_InPartiallyChildrenLoadedDirectory() throws IOException {
    IoTestUtil.assumeWindows();
    var fileToCreateListener = new LoggingListener(true);
    var name = "toCreate.txt";
    var fileToCreate = tempDir.getRootPath().resolve(name);
    var p = createPointerByFile(fileToCreate.resolveSibling(name.toLowerCase(Locale.US)), fileToCreateListener);
    assertFalse(p.isValid());
    Files.createFile(fileToCreate);
    getVirtualTempRoot().refresh(false, true);
    assertTrue(p.isValid());
    assertEquals("[before:tocreate.txt:false, after:toCreate.txt:true]", fileToCreateListener.log.toString());
    var expectedUrl = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, PathsKt.getInvariantSeparatorsPathString(fileToCreate));
    assertEquals(expectedUrl.toUpperCase(Locale.US), p.getUrl().toUpperCase(Locale.US));

    var p3 = createPointerByFile(fileToCreate.resolveSibling(name.toUpperCase(Locale.US)), fileToCreateListener);
    assertTrue(p3.isValid());
  }

  @Test
  public void testCreateActualFileEventMustChangePointersCreatedEarlierWithWrongCase_InChildrenLoadedDirectory() throws IOException {
    IoTestUtil.assumeWindows();
    var vRoot = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(tempDir.getRootPath());
    assertThat(vRoot.getChildren()).isEmpty();
    var fileToCreateListener = new LoggingListener(true);
    var name = "toCreate.txt";
    var fileToCreate = tempDir.getRootPath().resolve(name);
    var p = createPointerByFile(fileToCreate.resolveSibling(name.toLowerCase(Locale.US)), fileToCreateListener);
    assertFalse(p.isValid());
    Files.createFile(fileToCreate);
    getVirtualTempRoot().refresh(false, true);
    assertTrue(p.isValid());
    assertEquals("[before:tocreate.txt:false, after:toCreate.txt:true]", fileToCreateListener.log.toString());
    var expectedUrl = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, PathsKt.getInvariantSeparatorsPathString(fileToCreate));
    assertEquals(expectedUrl.toUpperCase(Locale.US), p.getUrl().toUpperCase(Locale.US));

    var p3 = createPointerByFile(fileToCreate.resolveSibling(name.toUpperCase(Locale.US)), fileToCreateListener);
    assertTrue(p3.isValid());
  }

  @PerformanceUnitTest
  @Test
  public void testRawGetPerformance() {
    var vTemp = getVirtualTempRoot();
    var file = VfsTestUtil.createFile(vTemp, "f.txt");

    vTemp.refresh(false, true);
    var pointer = createPointerByFile(file.toNioPath(), null);
    assertTrue(pointer.isValid());
    assertNotNull(pointer.getFile());
    assertTrue(pointer.getFile().isValid());

    Benchmark.newBenchmark("get()", () -> {
      for (var i = 0; i < 200_000_000; i++) {
        assertNotNull(pointer.getFile());
      }
    }).start();
  }

  @Test
  public void testMoveJars() throws IOException {
    var jarParent = tempDir.newDirectoryPath("a/b/c");

    var jar = jarParent.resolve("x.jar");
    var originalJar = Path.of(PathManagerEx.getTestDataPath(), "psi/generics22/collect-2.2.jar");
    Files.copy(originalJar, jar);

    var listener = new LoggingListener();
    var jarUrl = VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, PathsKt.getInvariantSeparatorsPathString(jar) + JarFileSystem.JAR_SEPARATOR);
    var jarPointer = myVirtualFilePointerManager.create(jarUrl, disposable, listener);

    var newJarParent = tempDir.getRootPath().resolve("new_a");
    Files.createDirectories(newJarParent);
    doMove(jarParent, newJarParent);
    assertTrue(jarPointer.getFile() != null && jarPointer.getFile().isValid());
  }

  @Test
  public void pointerSortsChildrenWhenDirectoryCaseSensitivityChanges() throws IOException {
    IoTestUtil.assumeWindows();
    IoTestUtil.assumeWslPresence();
    assumeTrue("'fsutil.exe' needs elevated privileges to work", SuperUserStatus.isSuperUser());

    myVirtualFilePointerManager.assertConsistency();
    var dir = tempDir.getRootPath().resolve("dir");
    var file = dir.resolve("child.txt");
    var FILE = dir.resolve("CHILD.TXT");
    assertThat(dir).doesNotExist();
    assertEquals(tempDir.getRootPath().toString(), FileAttributes.CaseSensitivity.INSENSITIVE, FileSystemUtil.readParentCaseSensitivity(tempDir.getRootPath()));

    var pointer = myVirtualFilePointerManager.create(VfsUtilCore.pathToUrl(file.toString()), disposable, null);
    myVirtualFilePointerManager.assertConsistency();
    Files.createDirectories(dir);
    IoTestUtil.setCaseSensitivity(dir, true);
    Files.createFile(file);
    Files.createFile(FILE);

    var vFILE = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(FILE);
    assertNotNull(vFILE);
    assertEquals("CHILD.TXT", vFILE.getName());
    var POINTER = myVirtualFilePointerManager.create(VfsUtilCore.pathToUrl(FILE.toString()), disposable, null);
    assertNotSame(POINTER, pointer);
    myVirtualFilePointerManager.assertConsistency();

    var vFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file);
    assertNotNull(vFile);
    assertTrue(vFile.isCaseSensitive());
    assertEquals(FileAttributes.CaseSensitivity.SENSITIVE, FileSystemUtil.readParentCaseSensitivity(file));
    myVirtualFilePointerManager.assertConsistency();
  }

  @Test
  public void testCreateFilePointerFrom83AbbreviationAbominationUnderWindowsDoesntCrash() throws IOException {
    IoTestUtil.assumeWindows();
    myVirtualFilePointerManager.assertConsistency();

    var file = tempDir.getRootPath().resolve("Documents And Settings/Absolutely All Users/x.txt");
    assertThat(file).doesNotExist();
    Files.createDirectories(file.getParent());
    Files.createFile(file);
    assertThat(file).exists();
    var _83Abomination = tempDir.getRootPath().resolve("Docume~1/Absolu~1/x.txt");
    assumeTrue("This version of Windows doesn't support 8.3. abbreviated file names: " + OS.CURRENT, Files.exists(_83Abomination));
    var p1 = myVirtualFilePointerManager.create(VfsUtilCore.pathToUrl(file.toString()), disposable, null);
    assertEquals(VfsUtilCore.pathToUrl(file.toString()), p1.getUrl());
    var p2 = myVirtualFilePointerManager.create(VfsUtilCore.pathToUrl(_83Abomination.toString()), disposable, null);
    assertEquals(VfsUtilCore.pathToUrl(file.toString()), p2.getUrl());
  }

  @Test
  public void testRecursivePointersForSubdirectories() {
    var parentPointer = createRecursivePointer("parent");
    var dirPointer = createRecursivePointer("parent/dir");
    var subdirPointer = createRecursivePointer("parent/dir/subdir");
    var filePointer = createPointer("parent/dir/subdir/file.txt");
    var root = myDir();
    var parent = createChildDirectory(root, "parent");
    var dir = createChildDirectory(parent, "dir");
    var subdir = createChildDirectory(dir, "subdir");
    assertPointersUnder(subdir, "xxx.txt", parentPointer, dirPointer, subdirPointer);
    assertPointersUnder(subdir.getParent(), subdir.getName(), parentPointer, dirPointer, subdirPointer, filePointer);
    assertPointersUnder(dir.getParent(), dir.getName(), parentPointer, dirPointer, subdirPointer, filePointer);
    assertPointersUnder(parent.getParent(), parent.getName(), parentPointer, dirPointer, subdirPointer, filePointer);
  }

  @Test
  public void testRecursivePointersForDirectoriesWithCommonPrefix() {
    var parentPointer = createRecursivePointer("parent");
    var dir1Pointer = createRecursivePointer("parent/dir1");
    var dir2Pointer = createRecursivePointer("parent/dir2");
    var subdirPointer = createRecursivePointer("parent/dir1/subdir");
    var filePointer = createPointer("parent/dir1/subdir/file.txt");
    var root = myDir();
    var parent = createChildDirectory(root, "parent");
    var dir1 = createChildDirectory(parent, "dir1");
    var dir2 = createChildDirectory(parent, "dir2");
    var subdir = createChildDirectory(dir1, "subdir");
    assertPointersUnder(subdir, "xxx.txt", parentPointer, dir1Pointer, subdirPointer);
    assertPointersUnder(subdir.getParent(), subdir.getName(), parentPointer, dir1Pointer, subdirPointer, filePointer);
    assertPointersUnder(dir1.getParent(), dir1.getName(), parentPointer, dir1Pointer, subdirPointer, filePointer);
    assertPointersUnder(parent.getParent(), parent.getName(), parentPointer, dir1Pointer, dir2Pointer, subdirPointer, filePointer);
    assertPointersUnder(dir2.getParent(), dir2.getName(), parentPointer, dir2Pointer);
  }

  @Test
  public void testRecursivePointersUnderSiblingDirectory() {
    var innerPointer = createRecursivePointer("parent/dir/subdir1/inner/subinner");
    createPointer("parent/anotherDir");
    var root = myDir();
    var parent = HeavyPlatformTestCase.createChildDirectory(root, "parent");
    var dir = HeavyPlatformTestCase.createChildDirectory(parent, "dir");
    var subdir1 = createChildDirectory(dir, "subdir1");
    var subdir2 = createChildDirectory(dir, "subdir2");
    assertPointersUnder(subdir1, "inner", innerPointer);
    assertPointersUnder(subdir2, "xxx.txt");
  }

  @Test
  public void testRecursivePointersUnderDisparateDirectoriesNearRoot() {
    var innerPointer = createRecursivePointer("temp/res/ext-resources");
    var root = myDir();
    var parent = HeavyPlatformTestCase.createChildDirectory(root, "parent");
    var dir = createChildDirectory(parent, "dir");
    assertPointersUnder(dir, "inner");
    assertTrue(((VirtualFilePointerImpl)innerPointer).isRecursive());
  }

  @Test
  public void testUrlsHavingOnlyStartingSlashInCommon() {
    var p1 = createPointer("a/p1");
    var p2 = createPointer("b/p2");
    var root = myDir();
    var a = createChildDirectory(root, "a");
    var b = createChildDirectory(root, "b");
    assertThat(myVirtualFilePointerManager.getPointersUnder(a, "p1")).containsExactly(p1);
    assertThat(myVirtualFilePointerManager.getPointersUnder(b, "p2")).containsExactly(p2);
  }

  @Test
  public void testUrlsHavingOnlyStartingSlashInCommonAndInvalidUrlBetweenThem() {
    var p1 = createPointer("a/p1");
    createPointer("invalid/path");
    var p2 = createPointer("b/p2");
    var root = myDir();
    var a = createChildDirectory(root, "a");
    var b = createChildDirectory(root, "b");
    assertThat(myVirtualFilePointerManager.getPointersUnder(a, "p1")).containsExactly(p1);
    assertThat(myVirtualFilePointerManager.getPointersUnder(b, "p2")).containsExactly(p2);
  }

  private static VirtualFileSystemEntry createChildDirectory(VirtualFile root, String childName) {
    return (VirtualFileSystemEntry)HeavyPlatformTestCase.createChildDirectory(root, childName);
  }

  private void assertPointersUnder(VirtualFileSystemEntry file, String childName, VirtualFilePointer... pointers) {
    assertThat(myVirtualFilePointerManager.getPointersUnder(file, childName)).containsExactlyInAnyOrder(pointers);
  }

  private VirtualFilePointer createPointer(String relativePath) {
    return myVirtualFilePointerManager.create(myDir().getUrl() + "/" + relativePath, disposable, null);
  }

  private VirtualFilePointer createRecursivePointer(String relativePath) {
    var url = myDir().getUrl() + "/" + relativePath;
    return myVirtualFilePointerManager.createDirectoryPointer(url, true, disposable, new VirtualFilePointerListener() { });
  }

  private VirtualFile myDir() {
    return tempDir.getVirtualFileRoot();
  }

  @Test
  public void testIncorrectRelativeUrlMustNotCrashVirtualPointers() {
    var path = "$KOTLIN_BUNDLED$/lib/allopen-compiler-plugin.jar!/";
    var pointer = myVirtualFilePointerManager.create("jar://" + path, disposable, null);
    assertTrue(pointer.getUrl(), pointer.getUrl().endsWith(path));
    assertEquals("allopen-compiler-plugin.jar", pointer.getFileName());
  }

  @Test
  public void testJarUrlContainingInvalidExclamationInTheMiddleMustNotCrashAnything() {
    var root = tempDir.getRootPath();
    while (root.getParent() != null) root = root.getParent();
    var diskRoot = UriUtil.trimTrailingSlashes(PathsKt.getInvariantSeparatorsPathString(root));

    assertJarSeparatorParsedCorrectlyForFileInsideJar("/", "!/", null, "_");
    assertJarSeparatorParsedCorrectlyForFileInsideJar("!/", "!/", null, "_");
    assertJarSeparatorParsedCorrectlyForFileInsideJar("!/xxx", "!/xxx", "xxx", "xxx");
    assertJarSeparatorParsedCorrectlyForFileInsideJar("!/xxx/!/yyy", "!/xxx/!/yyy", "yyy", "xxx/!/yyy");
    if (OS.CURRENT == OS.Windows) {
      assertJarSeparatorParsedCorrectly("jar://" + diskRoot + "/!/abc", "jar://" + diskRoot + "!/abc", "abc");
      assertJarSeparatorParsedCorrectly("jar://" + diskRoot + "!/abc", "jar://" + diskRoot + "!/abc", "abc");
    }
  }

  private void assertJarSeparatorParsedCorrectlyForFileInsideJar(
    String relativePathInsideJar,
    String expectedPointerRelativeUrl,
    @Nullable String expectedPointerFileName,
    String expectedPathInsideJar
  ) {
    var abc = "abc" + new SecureRandom().nextLong() + ".jar";
    var tempRoot = UriUtil.trimTrailingSlashes(PathsKt.getInvariantSeparatorsPathString(tempDir.getRootPath()));
    var pointer = VirtualFilePointerManager.getInstance().create("jar://" + tempRoot + "/" + abc + relativePathInsideJar, disposable, null);
    assertEquals(expectedPointerRelativeUrl, StringUtil.trimStart(pointer.getUrl(), "jar://" + tempRoot + "/" + abc));
    var expectedPointerFileNameToCheck = expectedPointerFileName == null ? abc : expectedPointerFileName;
    assertEquals(expectedPointerFileNameToCheck, pointer.getFileName());
    assertEquals(JarFileSystem.getInstance(), ((VirtualFilePointerImpl)pointer).getFileSystemForTesting());
    assertFalse(pointer.isValid());

    var jar = IoTestUtil.createTestJar(new File(tempRoot, abc), List.of(Pair.create(expectedPathInsideJar, new byte[]{' ', ' '})));
    assertNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(jar.toPath()));

    assertTrue(pointer.isValid());
    var virtualFile = pointer.getFile();
    assertNotNull(virtualFile);
    assertEquals(expectedPointerFileNameToCheck, virtualFile.getName());
  }

  private void assertJarSeparatorParsedCorrectly(String sourceUrl, String expectedPointerUrl, @SuppressWarnings("SameParameterValue") String expectedPointerFileName) {
    var pointer = VirtualFilePointerManager.getInstance().create(sourceUrl, disposable, null);
    assertEquals(expectedPointerUrl, pointer.getUrl());
    assertEquals(expectedPointerFileName, pointer.getFileName());
    assertEquals(JarFileSystem.getInstance(), ((VirtualFilePointerImpl)pointer).getFileSystemForTesting());
    assertFalse(pointer.isValid());
  }

  @Test
  public void testDeadlockDuringConcurrentCreateDispose() {
    // also should not leak pointers, checked in tearDown()
    IntStream.range(0, 100_000)
      .parallel()
      .forEach(_ -> {
        var disposable = Disposer.newDisposable();
        myVirtualFilePointerManager.create(myDir().getUrl() + "/file.txt", disposable, null);
        Disposer.dispose(disposable);
      });
  }
}

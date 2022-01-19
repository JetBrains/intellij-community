// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.local;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.ZipHandlerBase;
import com.intellij.openapi.vfs.impl.jar.JarFileSystemImpl;
import com.intellij.openapi.vfs.impl.jar.TimedZipHandler;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.zip.JBZipFile;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import static com.intellij.testFramework.PlatformTestUtil.assertPathsEqual;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

public class JarFileSystemTest extends BareTestFixtureTestCase {
  @Rule public TempDirectory tempDir = new TempDirectory();

  @Test
  public void testFindFile() throws IOException {
    assertNull(JarFileSystem.getInstance().findFileByPath("/invalid/path"));

    String jarPath = PathManager.getJarPathForClass(Test.class);

    VirtualFile jarRoot = findByPath(jarPath + JarFileSystem.JAR_SEPARATOR);
    assertTrue(jarRoot.isDirectory());

    VirtualFile file2 = findByPath(jarPath + JarFileSystem.JAR_SEPARATOR + "org");
    assertTrue(file2.isDirectory());

    VirtualFile file3 = jarRoot.findChild("org");
    assertEquals(file2, file3);

    VirtualFile file4 = findByPath(jarPath + JarFileSystem.JAR_SEPARATOR + "org/junit/Test.class");
    assertFalse(file4.isDirectory());

    byte[] bytes = file4.contentsToByteArray();
    assertNotNull(bytes);
    assertTrue(bytes.length > 10);
    assertEquals(0xCAFEBABE, ByteBuffer.wrap(bytes).getInt());

    VirtualFile local = ((ArchiveFileSystem)StandardFileSystems.jar()).getLocalByEntry(file4);
    assertNotNull(local);
    assertEquals(local.getTimeStamp(), file4.getTimeStamp());
  }

  @Test
  public void testMetaInf() {
    VirtualFile jarRoot = findByPath(PathManager.getJarPathForClass(Test.class) + JarFileSystem.JAR_SEPARATOR);
    assertTrue(jarRoot.isDirectory());

    VirtualFile metaInf = jarRoot.findChild("META-INF");
    assertNotNull(metaInf);

    assertNotNull(metaInf.findChild("MANIFEST.MF"));
  }

  @Test
  public void testJarRefresh() throws IOException {
    File jar = IoTestUtil.createTestJar(tempDir.newFile("test.jar"));
    assertTrue(jar.setLastModified(jar.lastModified() - 1000));
    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(jar);
    assertNotNull(vFile);

    VirtualFile jarRoot = findByPath(jar.getPath() + JarFileSystem.JAR_SEPARATOR);
    assertThat(ContainerUtil.map(jarRoot.getChildren(), VirtualFile::getName)).containsExactly("META-INF");

    VirtualFile entry = findByPath(jar.getPath() + JarFileSystem.JAR_SEPARATOR + JarFile.MANIFEST_NAME);
    assertEquals("", VfsUtilCore.loadText(entry));

    Ref<Boolean> updated = Ref.create(false);
    ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable()).subscribe(
      VirtualFileManager.VFS_CHANGES,
      new BulkFileListener() {
        @Override
        public void before(@NotNull List<? extends @NotNull VFileEvent> events) {
          for (VFileEvent event : events) {
            if (event instanceof VFileContentChangeEvent && entry.equals(event.getFile())) {
              updated.set(true);
              break;
            }
          }
        }
      }
    );

    IoTestUtil.createTestJar(jar, JarFile.MANIFEST_NAME, "update", "some.txt", "some text");
    vFile.refresh(false, false);

    assertTrue(updated.get());
    assertTrue(entry.isValid());
    assertEquals("update", VfsUtilCore.loadText(entry));
    assertThat(ContainerUtil.map(jarRoot.getChildren(), VirtualFile::getName)).containsExactlyInAnyOrder("META-INF", "some.txt");

    VirtualFile newEntry = findByPath(jar.getPath() + JarFileSystem.JAR_SEPARATOR + "some.txt");
    assertEquals("some text", VfsUtilCore.loadText(newEntry));
  }

  @Test
  public void testTimedZipHandlerWithInvalidJar() throws Exception {
    final TimedZipHandler handler = new TimedZipHandler("some invalid path");
    Runnable failingIOAction = () -> {
      try {
        handler.getInputStream("").close();
        fail("Unexpected");
      }
      catch (IOException ignored) { }
    };
    failingIOAction.run();
    Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(failingIOAction);
    try {
      future.get(1, TimeUnit.SECONDS);
    }
    catch (TimeoutException exception) {
      fail("Deadlock detected");
    }
  }

  @Test
  public void testTimedZipHandlerConcurrency() throws Exception {
    try {
      int number = 40;
      List<TimedZipHandler> handlers = new ArrayList<>();
      for (int i = 0; i < number; ++i) {
        File jar = IoTestUtil.createTestJar(tempDir.newFile("test" + i + ".jar"));
        handlers.add(new TimedZipHandler(jar.getPath()));
      }

      int N = Math.max(2, Runtime.getRuntime().availableProcessors());
      for (int iteration = 0; iteration < 200; ++iteration) {
        List<Future<?>> futuresToWait = new ArrayList<>();
        CountDownLatch sameStartCondition = new CountDownLatch(N);

        for (int i = 0; i < N; ++i) {
          futuresToWait.add(ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
              sameStartCondition.countDown();
              sameStartCondition.await();
              Random random = new Random();
              for (int j = 0; j < 2 * number; ++j) {
                TimedZipHandler handler = handlers.get(random.nextInt(handlers.size()));
                if (random.nextBoolean()) {
                  assertNotNull(handler.getAttributes(JarFile.MANIFEST_NAME));
                }
                else {
                  assertNotNull(handler.contentsToByteArray(JarFile.MANIFEST_NAME));
                }
              }
            }
            catch (Throwable t) {
              t.printStackTrace();
            }
          }));
        }

        for (Future<?> future : futuresToWait) future.get(2, TimeUnit.SECONDS);
      }

      for (TimedZipHandler handler : handlers) handler.clearCaches();
    }
    catch (TimeoutException e) {
      fail("Deadlock detected");
    }
  }

  @Test
  public void testJarHandlerDoNotCreateCopyWhenListingArchive() throws Exception {
    File jar = IoTestUtil.createTestJar(tempDir.newFile("test.jar"));
    JarFileSystemImpl fs = (JarFileSystemImpl)JarFileSystem.getInstance();
    fs.setNoCopyJarForPath(jar.getPath());
    assertFalse(fs.isMakeCopyOfJar(jar));
  }

  @Test
  public void testInvalidZip() {
    VirtualFile zip = createJar(
      "/", "a", "a/b", "a/b/c.txt", "x\\y\\z.txt", "/x/f.txt", "d1/aB", "d1/ab", "D2/f1", "//d2//f2", "empty/", "w/../W");
    VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(zip);
    assertNotNull(jarRoot);
    int prefixLength = zip.getPath().length() + JarFileSystem.JAR_SEPARATOR.length();
    List<String> entries = new ArrayList<>();
    VfsUtilCore.visitChildrenRecursively(jarRoot, new VirtualFileVisitor<>() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        if (!jarRoot.equals(file)) {
          String path = file.getPath().substring(prefixLength);
          entries.add(file.isDirectory() ? path + '/' : path);
        }
        return true;
      }
    });
    assertThat(entries).containsExactlyInAnyOrder(
      "a/", "a/b/", "a/b/c.txt", "x/", "x/y/", "x/f.txt", "x/y/z.txt", "d1/", "d1/aB", "d1/ab", "D2/", "D2/f1", "d2/", "d2/f2", "empty/");
  }

  @Test
  public void testJarRootForLocalFile() {
    String jarPath = PathManager.getJarPathForClass(Test.class);

    VirtualFile jarFile = LocalFileSystem.getInstance().findFileByPath(jarPath);
    assertNotNull(jarFile);
    VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(jarFile);
    assertNotNull(jarRoot);

    VirtualFile entryFile = findByPath(jarPath + JarFileSystem.JAR_SEPARATOR + "org/junit/Test.class");
    VirtualFile entryRoot = JarFileSystem.getInstance().getJarRootForLocalFile(entryFile);
    assertNull(entryRoot);

    VirtualFile nonJarFile = LocalFileSystem.getInstance().findFileByPath(PlatformTestUtil.getJavaExe());
    assertNotNull(nonJarFile);
    VirtualFile nonJarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(nonJarFile);
    assertNull(nonJarRoot);
  }

  @Test
  public void testEnormousFileInputStream() throws IOException {
    File root = tempDir.newDirectory("out");
    FileUtil.writeToFile(new File(root, "small1"), "some text");
    FileUtil.writeToFile(new File(root, "small2"), "another text");
    try (InputStream is = new ZeroInputStream(); OutputStream os = new FileOutputStream(new File(root, "large"))) {
      FileUtil.copy(is, FileUtilRt.LARGE_FOR_CONTENT_LOADING * 2, os);
    }
    File jar = IoTestUtil.createTestJar(tempDir.newFile("test.jar"), root);

    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(jar);
    assertNotNull(vFile);
    VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(vFile);
    assertNotNull(jarRoot);

    VirtualFile small1 = jarRoot.findChild("small1");
    VirtualFile small2 = jarRoot.findChild("small2");
    VirtualFile large = jarRoot.findChild("large");
    try (InputStream is1 = small1.getInputStream();
         InputStream is2 = small2.getInputStream();
         InputStream il = large.getInputStream()) {
      assertSame(is1.getClass(), is2.getClass());
      assertNotSame(is1.getClass(), il.getClass());
    }
  }

  @NotNull
  private static VirtualFile findByPath(@NotNull String path) {
    VirtualFile file = JarFileSystem.getInstance().findFileByPath(path);
    assertNotNull(file);
    assertPathsEqual(path, file.getPath());
    return file;
  }

  private static class ZeroInputStream extends InputStream {
    @Override
    public int read() {
      return 0;
    }

    @Override
    public int read(byte @NotNull [] b, int off, int len) {
      return len;
    }
  }

  @Test
  public void testCrazyBackSlashesInZipEntriesMustBeTreatedAsRegularDirectorySeparators() {
    VirtualFile vFile = createJar("src\\core\\log/", "src\\core\\log/log4sql_conf.jsp", "META-INF/MANIFEST.MF");
    String jarPath = vFile.getPath();
    VirtualFile manifest = findByPath(jarPath + JarFileSystem.JAR_SEPARATOR + JarFile.MANIFEST_NAME);
    assertNotNull(manifest);

    VirtualFile jarRoot = JarFileSystem.getInstance().findFileByPath(jarPath + JarFileSystem.JAR_SEPARATOR);
    assertNotNull(jarRoot);
    assertNotNull(findByPath(jarPath + JarFileSystem.JAR_SEPARATOR + "src/core/log/log4sql_conf.jsp"));
    assertNull(jarRoot.findChild("src\\core\\log"));
    VirtualFile src = jarRoot.findChild("src");
    assertNotNull(src);
    VirtualFile core = src.findChild("core");
    assertNotNull(core);
    VirtualFile log = core.findChild("log");
    assertNotNull(log);
    VirtualFile jsp = log.findChild("log4sql_conf.jsp");
    assertNotNull(jsp);
  }

  @Test
  public void testCrazyJarWithDuplicateFileAndDirEntriesMustNotCrashAnything() {
    VirtualFile vFile = createJar("com", "/com/Hello.class");
    assertNotNull(vFile);

    VirtualFile jarRoot = JarFileSystem.getInstance().getRootByLocal(vFile);
    assertNotNull(jarRoot);
    String[] children = JarFileSystem.getInstance().list(jarRoot);
    assertEquals("com", UsefulTestCase.assertOneElement(children));
    assertEquals("Hello.class", UsefulTestCase.assertOneElement(JarFileSystem.getInstance().list(jarRoot.findFileByRelativePath("com"))));
  }

  private VirtualFile createJar(String... entryNames) {
    String[] namesAndTexts = Arrays.stream(entryNames).flatMap(n -> Stream.of(n, null)).toArray(String[]::new);
    File jar = IoTestUtil.createTestJar(tempDir.newFile("p.jar"), namesAndTexts);
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(jar);
  }

  @Test
  public void testCrazyJarWithBackSlashedLongEntryMustNotCrashAnything() {
    VirtualFile vFile = createJar("META-INF/MANIFEST.MF", "\\META-INF\\RET-LD00.00015.xml");

    VirtualFile jarRoot = JarFileSystem.getInstance().getRootByLocal(vFile);
    assertNotNull(jarRoot);
    VirtualFile child = UsefulTestCase.assertOneElement(jarRoot.getChildren());
    String[] children = JarFileSystem.getInstance().list(jarRoot);
    assertEquals("META-INF", UsefulTestCase.assertOneElement(children));

    child.getChildren();
    assertNotNull(jarRoot.findFileByRelativePath("META-INF/MANIFEST.MF"));
    assertNotNull(jarRoot.findFileByRelativePath("META-INF/RET-LD00.00015.xml"));
  }

  @Test
  public void testJarNameCouldBePrependedWithDotDot() {
    File jar = IoTestUtil.createTestJar(tempDir.newFile("..p.jar"));
    VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(jar);
    VirtualFile jarRoot = JarFileSystem.getInstance().getRootByLocal(vf);
    assertNotNull(jarRoot);
    assertNotNull(jarRoot.findFileByRelativePath(JarFile.MANIFEST_NAME));
  }

  @Test
  public void testJarFileMustInvalidateOnDeleteLocalEntryFile() {
    VirtualFile vf = createJar("a", "a/b");

    VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(vf);
    assertNotNull(jarRoot);
    VirtualFile a = jarRoot.findChild("a");
    assertNotNull(a);
    assertTrue(a.isValid());
    assertTrue(jarRoot.isValid());

    VirtualFile local = JarFileSystem.getInstance().getLocalVirtualFileFor(jarRoot);
    assertEquals(LocalFileSystem.getInstance(), local.getFileSystem());
    JarFileSystemImpl.cleanupForNextTest(); // WTF, won't let delete jar otherwise

    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable());
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        // jars must be invalidated immediately after deleting the local root
        assertFalse(a.isValid());
        assertFalse(jarRoot.isValid());
      }
    });
    VfsTestUtil.deleteFile(local);
    assertFalse(a.isValid());
    assertFalse(jarRoot.isValid());
  }

  @Test
  public void testUsingCrcAsTimestamp() throws IOException {
    final boolean value = ZipHandlerBase.getUseCrcInsteadOfTimestampPropertyValue();

    try {
      System.setProperty("zip.handler.uses.crc.instead.of.timestamp", Boolean.toString(true));

      File jar = IoTestUtil.createTestJar(
        tempDir.newFile("p.jar"),
        "file1.txt", "my_content", "file2.dat", "my_content"
      );

      VirtualFile jarRoot =
        JarFileSystem.getInstance().getJarRootForLocalFile(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(jar));

      VirtualFile file1Txt = jarRoot.findChild("file1.txt");
      VirtualFile file2Dat = jarRoot.findChild("file2.dat");

      assertEquals(file1Txt.getTimeStamp(), file2Dat.getTimeStamp());

      try (JBZipFile it = new JBZipFile(jar)) {
        it.getOrCreateEntry(file1Txt.getName()).setData("my_new_content".getBytes(StandardCharsets.UTF_8));
      }
      // LFS invalidates JarFS caches
      LocalFileSystem.getInstance().refreshNioFiles(List.of(jar.toPath()), false, true, null);
      assertNotEquals(file1Txt.getTimeStamp(), file2Dat.getTimeStamp());

      try (JBZipFile it = new JBZipFile(jar)) {
        it.getOrCreateEntry(file2Dat.getName()).setData("my_new_content".getBytes(StandardCharsets.UTF_8));
      }
      // LFS invalidates JarFS caches
      LocalFileSystem.getInstance().refreshNioFiles(List.of(jar.toPath()), false, true, null);
      assertEquals(file1Txt.getTimeStamp(), file2Dat.getTimeStamp());
    }
    finally {
      System.setProperty("zip.handler.uses.crc.instead.of.timestamp", Boolean.toString(value));
    }
  }
}

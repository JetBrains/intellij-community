// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.local;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.jar.BasicJarHandler;
import com.intellij.openapi.vfs.impl.jar.JarFileSystemImpl;
import com.intellij.openapi.vfs.impl.jar.JarHandler;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.intellij.openapi.util.io.IoTestUtil.assertTimestampsEqual;
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
        public void before(@NotNull List<? extends VFileEvent> events) {
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
  public void testBasicJarHandlerWithInvalidJar() throws Exception {
    final BasicJarHandler handler = new BasicJarHandler("some invalid path");
    Runnable failingIOAction = () -> {
      try {
        handler.getInputStream("").close();
        fail("Unexpected");
      }
      catch (IOException ignored) {
      }
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
  public void testBasicJarHandlerConcurrency() throws Exception {
    try {
      int number = 40;
      List<BasicJarHandler> handlers = new ArrayList<>();
      for (int i = 0; i < number; ++i) {
        File jar = IoTestUtil.createTestJar(tempDir.newFile("test" + i + ".jar"));
        handlers.add(new BasicJarHandler(jar.getPath()));
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
                BasicJarHandler handler = handlers.get(random.nextInt(handlers.size()));
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
    }
    catch (TimeoutException e) {
      fail("Deadlock detected");
    }
  }

  @After
  public void testDown() {
    JarFileSystemImpl.cleanupForNextTest();
  }

  @Test
  public void testJarHandlerDoNotCreateCopyWhenListingArchive() throws Exception {
    File jar = IoTestUtil.createTestJar(tempDir.newFile("test.jar"));
    JarHandler handler = new JarHandler(jar.getPath());
    FileAttributes attributes = handler.getAttributes(JarFile.MANIFEST_NAME);
    assertNotNull(attributes);
    assertEquals(0, attributes.length);
    assertTimestampsEqual(jar.lastModified(), attributes.lastModified);

    JarFileSystemImpl jarFileSystem = (JarFileSystemImpl)JarFileSystem.getInstance();
    if (jarFileSystem.isMakeCopyOfJar(jar)) {
      // for performance reasons we create file copy on windows when we read contents and have the handle open to the copy
      Field resolved = handler.getClass().getDeclaredField("myFileWithMirrorResolved");
      resolved.setAccessible(true);
      assertNull(resolved.get(handler));
    }

    jarFileSystem.setNoCopyJarForPath(jar.getPath() + JarFileSystem.JAR_SEPARATOR);
    assertFalse(jarFileSystem.isMakeCopyOfJar(jar));
  }

  @Test
  public void testInvalidZip() throws IOException {
    File testZip = tempDir.newFile("test.zip");
    try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(testZip))) {
      writeEntry(zip, "a");
      writeEntry(zip, "a/b");
      writeEntry(zip, "a/b/c.txt");
      writeEntry(zip, "x\\y\\z.txt");
    }

    String rootPath = FileUtil.toSystemIndependentName(testZip.getPath()) + JarFileSystem.JAR_SEPARATOR;
    VirtualFile jarRoot = JarFileSystem.getInstance().findFileByPath(rootPath);
    assertNotNull(jarRoot);
    List<String> entries = new ArrayList<>();
    VfsUtilCore.visitChildrenRecursively(jarRoot, new VirtualFileVisitor<Object>() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        if (!jarRoot.equals(file)) {
          String path = file.getPath().substring(rootPath.length());
          entries.add(file.isDirectory() ? path + '/' : path);
        }
        return true;
      }
    });
    assertThat(entries).containsExactlyInAnyOrder("a/", "a/b/", "a/b/c.txt", "x/", "x/y/", "x/y/z.txt");
  }

  private static void writeEntry(ZipOutputStream zip, String name) throws IOException {
    ZipEntry entry = new ZipEntry(name);
    zip.putNextEntry(entry);
    zip.closeEntry();
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
    File root = tempDir.newFolder("out");
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

  @Test
  public void testCrazyJarWithDuplicateEntriesMustNotCrashAnything() {
    String jarPath = PathManagerEx.getTestDataPath() + "/vfs/sample.jar";
    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(jarPath);
    assertNotNull(vFile);

    VirtualFile jarRoot = JarFileSystem.getInstance().getRootByLocal(vFile);
    assertNotNull(jarRoot);
    String[] children = JarFileSystem.getInstance().list(jarRoot);
    assertEquals("com", UsefulTestCase.assertOneElement(children));
    assertEquals("Hello.class", UsefulTestCase.assertOneElement(JarFileSystem.getInstance().list(jarRoot.findFileByRelativePath("com"))));
  }

  @NotNull
  private static VirtualFile findByPath(String path) {
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
}
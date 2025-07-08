// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.local;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.DefaultProjectFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.ZipHandler;
import com.intellij.openapi.vfs.impl.ZipHandlerBase;
import com.intellij.openapi.vfs.impl.jar.JarFileSystemImpl;
import com.intellij.openapi.vfs.impl.jar.TimedZipHandler;
import com.intellij.openapi.vfs.limits.FileSizeLimit;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.VfsImplUtil;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.platform.testFramework.DynamicPluginTestUtilsKt;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.zip.JBZipFile;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
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

    var jarPath = PathManager.getJarPathForClass(Test.class);

    var jarRoot = findByPath(jarPath + JarFileSystem.JAR_SEPARATOR);
    assertTrue(jarRoot.isDirectory());

    var file2 = findByPath(jarPath + JarFileSystem.JAR_SEPARATOR + "org");
    assertTrue(file2.isDirectory());

    var file3 = jarRoot.findChild("org");
    assertEquals(file2, file3);

    var file4 = findByPath(jarPath + JarFileSystem.JAR_SEPARATOR + "org/junit/Test.class");
    assertFalse(file4.isDirectory());

    var bytes = file4.contentsToByteArray();
    assertNotNull(bytes);
    assertTrue(bytes.length > 10);
    assertEquals(0xCAFEBABE, ByteBuffer.wrap(bytes).getInt());

    var local = ((ArchiveFileSystem)StandardFileSystems.jar()).getLocalByEntry(file4);
    assertNotNull(local);
    assertEquals(local.getTimeStamp(), file4.getTimeStamp());
  }

  @Test
  public void testMetaInf() {
    var jarRoot = findByPath(PathManager.getJarPathForClass(Test.class) + JarFileSystem.JAR_SEPARATOR);
    assertTrue(jarRoot.isDirectory());

    var metaInf = jarRoot.findChild("META-INF");
    assertNotNull(metaInf);

    assertNotNull(metaInf.findChild("MANIFEST.MF"));
  }

  @Test
  public void testJarRefresh() throws IOException {
    var jar = IoTestUtil.createTestJar(tempDir.newFile("test.jar"));
    assertTrue(jar.setLastModified(jar.lastModified() - 1000));
    var vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(jar);
    assertNotNull(vFile);

    var jarRoot = findByPath(jar.getPath() + JarFileSystem.JAR_SEPARATOR);
    assertThat(ContainerUtil.map(jarRoot.getChildren(), VirtualFile::getName)).containsExactly("META-INF");

    var entry = findByPath(jar.getPath() + JarFileSystem.JAR_SEPARATOR + JarFile.MANIFEST_NAME);
    assertEquals("", VfsUtilCore.loadText(entry));

    var updated = Ref.create(false);
    ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable()).subscribe(
      VirtualFileManager.VFS_CHANGES,
      new BulkFileListener() {
        @Override
        public void before(@NotNull List<? extends @NotNull VFileEvent> events) {
          for (var event : events) {
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

    var newEntry = findByPath(jar.getPath() + JarFileSystem.JAR_SEPARATOR + "some.txt");
    assertEquals("some text", VfsUtilCore.loadText(newEntry));
  }

  @Test
  public void testTimedZipHandlerWithInvalidJar() throws Exception {
    var handler = new TimedZipHandler("some invalid path");
    var failingIOAction = (Runnable)() -> {
      try {
        handler.getInputStream("").close();
        fail("Unexpected");
      }
      catch (IOException ignored) { }
    };
    failingIOAction.run();
    var future = ApplicationManager.getApplication().executeOnPooledThread(failingIOAction);
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
      var number = 40;
      var handlers = new ArrayList<TimedZipHandler>();
      for (var i = 0; i < number; ++i) {
        var jar = IoTestUtil.createTestJar(tempDir.newFile("test" + i + ".jar"));
        handlers.add(new TimedZipHandler(jar.getPath()));
      }

      var N = Math.max(2, Runtime.getRuntime().availableProcessors());
      for (var iteration = 0; iteration < 200; ++iteration) {
        var futuresToWait = new ArrayList<Future<?>>();
        var sameStartCondition = new CountDownLatch(N);

        for (var i = 0; i < N; ++i) {
          futuresToWait.add(ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
              sameStartCondition.countDown();
              sameStartCondition.await();
              var random = new Random();
              for (var j = 0; j < 2 * number; ++j) {
                var handler = handlers.get(random.nextInt(handlers.size()));
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

        ConcurrencyUtil.getAll(2L*futuresToWait.size(), TimeUnit.SECONDS, futuresToWait);
      }

      for (var handler : handlers) handler.clearCaches();
    }
    catch (TimeoutException e) {
      fail("Deadlock detected");
    }
  }

  @Test
  @SuppressWarnings("removal")
  public void testJarHandlerDoNotCreateCopyWhenListingArchive() {
    var jar = IoTestUtil.createTestJar(tempDir.newFile("test.jar"));
    var fs = (JarFileSystemImpl)JarFileSystem.getInstance();
    fs.setNoCopyJarForPath(jar.getPath());
    assertFalse(fs.isMakeCopyOfJar(jar));
  }

  @Test
  public void testNonConformantZipTolerance() {
    var zip = createJar(
      "/", "w/../W",                      // entries to be ignored
      "a", "a/b", "a/b/c.txt",            // duplicate 'a/b' file entry should be obscured by eponymous directory
      "x\\y\\z.txt", "/x///f.txt",        // separators should be normalized
      "d1/aB", "d1/ab", "D2/f1", "d2/f2", // entries are case-sensitive
      "empty/"                            // empty directory entries should be preserved
    );
    var jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(zip);
    assertNotNull(jarRoot);
    var prefixLength = zip.getPath().length() + JarFileSystem.JAR_SEPARATOR.length();
    var entries = new ArrayList<String>();
    VfsUtilCore.visitChildrenRecursively(jarRoot, new VirtualFileVisitor<>() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        if (!jarRoot.equals(file)) {
          var path = file.getPath().substring(prefixLength);
          entries.add(file.isDirectory() ? path + '/' : path);
        }
        return true;
      }
    });
    assertThat(entries).containsExactlyInAnyOrder(
      "a/", "a/b/", "a/b/c.txt",
      "x/", "x/y/", "x/f.txt", "x/y/z.txt",
      "d1/", "d1/aB", "d1/ab", "D2/", "D2/f1", "d2/", "d2/f2",
      "empty/"
    );
  }

  @Test
  public void testJarRootForLocalFile() {
    var jarPath = PathManager.getJarPathForClass(Test.class);

    var jarFile = LocalFileSystem.getInstance().findFileByPath(jarPath);
    assertNotNull(jarFile);
    var jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(jarFile);
    assertNotNull(jarRoot);

    var entryFile = findByPath(jarPath + JarFileSystem.JAR_SEPARATOR + "org/junit/Test.class");
    var entryRoot = JarFileSystem.getInstance().getJarRootForLocalFile(entryFile);
    assertNull(entryRoot);

    var nonJarFile = LocalFileSystem.getInstance().findFileByPath(PlatformTestUtil.getJavaExe());
    assertNotNull(nonJarFile);
    var nonJarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(nonJarFile);
    assertNull(nonJarRoot);
  }

  @Test
  public void testEnormousFileInputStream() throws IOException {
    var root = tempDir.newDirectoryPath("out");
    Files.writeString(root.resolve("small1"), "some text");
    Files.writeString(root.resolve("small2"), "another text");
    try (var os = Files.newOutputStream(root.resolve("large"))) {
      var data = new byte[8192];
      var numBlocks = FileSizeLimit.getDefaultContentLoadLimit() * 2 / data.length;
      for (int i = 0; i < numBlocks; i++) {
        os.write(data);
      }
    }
    var jar = IoTestUtil.createTestJar(tempDir.newFile("test.jar"), root.toFile());

    var vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(jar);
    assertNotNull(vFile);
    var jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(vFile);
    assertNotNull(jarRoot);

    var small1 = jarRoot.findChild("small1");
    var small2 = jarRoot.findChild("small2");
    var large = jarRoot.findChild("large");
    try (var is1 = small1.getInputStream(); var is2 = small2.getInputStream(); var il = large.getInputStream()) {
      assertSame(is1.getClass(), is2.getClass());
      assertNotSame(is1.getClass(), il.getClass());
    }
  }

  @Test
  public void testJarNameCouldBePrependedWithDotDot() {
    var jar = IoTestUtil.createTestJar(tempDir.newFile("..p.jar"));
    var vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(jar);
    var jarRoot = JarFileSystem.getInstance().getRootByLocal(vf);
    assertNotNull(jarRoot);
    assertNotNull(jarRoot.findFileByRelativePath(JarFile.MANIFEST_NAME));
  }

  @Test
  public void testJarFileMustInvalidateOnDeleteLocalEntryFile() throws IOException {
    var vf = createJar("a", "a/b");

    var jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(vf);
    assertNotNull(jarRoot);
    var a = jarRoot.findChild("a");
    assertNotNull(a);
    assertTrue(a.isValid());
    assertTrue(jarRoot.isValid());

    var local = JarFileSystem.getInstance().getLocalByEntry(jarRoot);
    assertEquals(LocalFileSystem.getInstance(), local.getFileSystem());
    JarFileSystemImpl.cleanupForNextTest(); // won't let delete the .jar otherwise

    var connection = ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable());
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
    PlatformTestUtil.withSystemProperty("zip.handler.uses.crc.instead.of.timestamp", "true", () -> {
      var jar = IoTestUtil.createTestJar(
        tempDir.newFile("p.jar"),
        "file1.txt", "my_content", "file2.dat", "my_content"
      );
      var jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(jar));

      var file1Txt = jarRoot.findChild("file1.txt");
      var file2Dat = jarRoot.findChild("file2.dat");
      assertEquals(file1Txt.getTimeStamp(), file2Dat.getTimeStamp());

      try (var it = new JBZipFile(jar)) {
        it.getOrCreateEntry(file1Txt.getName()).setData("my_new_content".getBytes(StandardCharsets.UTF_8));
      }
      // LFS invalidates JarFS caches
      LocalFileSystem.getInstance().refreshNioFiles(List.of(jar.toPath()), false, true, null);
      assertNotEquals(file1Txt.getTimeStamp(), file2Dat.getTimeStamp());

      try (var it = new JBZipFile(jar)) {
        it.getOrCreateEntry(file2Dat.getName()).setData("my_new_content".getBytes(StandardCharsets.UTF_8));
      }
      // LFS invalidates JarFS caches
      LocalFileSystem.getInstance().refreshNioFiles(List.of(jar.toPath()), false, true, null);
      assertEquals(file1Txt.getTimeStamp(), file2Dat.getTimeStamp());
    });
  }

  @Test
  public void testArchiveHandlerCacheInvalidation() throws IOException {
    var originalJar = Path.of(PathManager.getJarPathForClass(Test.class));
    var copiedJar = Files.copy(originalJar, tempDir.getRootPath().resolve("temp.jar"));
    var rootUrl = "corrupted-jar://" + FileUtil.toSystemIndependentName(copiedJar.toString()) + "!/";
    var fileUrl = rootUrl + "org/junit/Test.class";

    var fsExtText = "<virtualFileSystem implementationClass='" + CorruptedJarFileSystemTestWrapper.class.getName() + "' key='corrupted-jar' physical='true'/>";
    EdtTestUtil.runInEdtAndWait(() -> {
      assertNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(copiedJar));

      var fsRegistration = DynamicPluginTestUtilsKt.loadExtensionWithText(fsExtText, "com.intellij");
      try {
        Disposer.register(fsRegistration, JarFileSystemImpl::cleanupForNextTest);

        CorruptedJarFileSystemTestWrapper.corrupted = true;
        var root = VirtualFileManager.getInstance().refreshAndFindFileByUrl(rootUrl);
        assertNotNull("not found: " + rootUrl, root);
        assertTrue(root.isValid());
        assertNull(VirtualFileManager.getInstance().findFileByUrl(fileUrl));

        CorruptedJarFileSystemTestWrapper.corrupted = false;
        var event = TestActionEvent.createTestEvent(
          dataId -> CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId) ? new VirtualFile[]{root} :
                    CommonDataKeys.PROJECT.is(dataId) ? DefaultProjectFactory.getInstance().getDefaultProject() :
                    null);
        ActionManager.getInstance().getAction("SynchronizeCurrentFile").actionPerformed(event);

        assertNotNull(VirtualFileManager.getInstance().findFileByUrl(fileUrl));
      }
      finally {
        Disposer.dispose(fsRegistration);
      }
    });
  }

  @Test
  public void listWithAttributes_ReturnsSameDataAs_GetAttributes() {
    var jar = createJar(
      "a/a",
      "a/b",
      "a/c",

      "b/a/a",
      "b/b/b",
      "b/c/c",

      "c/a/a",
      "c/b/b",
      "c/c/c"
    );
    JarFileSystem jarFileSystem = JarFileSystem.getInstance();
    VirtualFile jarRoot = jarFileSystem.getJarRootForLocalFile(jar);

    checkAttributesAreEqual(jarRoot, jarFileSystem);
  }

  private static void checkAttributesAreEqual(@NotNull VirtualFile dir,
                                              @NotNull JarFileSystem jarFileSystem) {
    Map<String, FileAttributes> childrenWithAttributes = jarFileSystem.listWithAttributes(dir, null);
    String[] childrenNames = jarFileSystem.list(dir);

    assertThat(childrenWithAttributes.keySet())
      .containsExactlyInAnyOrder(childrenNames);

    for (String childName : childrenNames) {
      VirtualFile child = dir.findChild(childName);
      //System.out.println(child.getPath());

      FileAttributes attributesFromBatchMethod = childrenWithAttributes.get(childName);
      FileAttributes attributesFromSingleMethod = jarFileSystem.getAttributes(child);

      assertThat(attributesFromSingleMethod)
        .isEqualTo(attributesFromBatchMethod);

      Map<String, FileAttributes> singleEntryAttributes = jarFileSystem.listWithAttributes(dir, Set.of(childName));
      assertThat(singleEntryAttributes.get(childName))
        .isEqualTo(attributesFromSingleMethod);

      if (child.isDirectory()) {
        checkAttributesAreEqual(child, jarFileSystem);
      }
    }
  }

  private static VirtualFile findByPath(String path) {
    var file = JarFileSystem.getInstance().findFileByPath(path);
    assertNotNull(file);
    assertPathsEqual(path, file.getPath());
    return file;
  }

  private VirtualFile createJar(String... entryNames) {
    var namesAndTexts = Stream.of(entryNames).flatMap(n -> Stream.of(n, null)).toArray(String[]::new);
    var jar = IoTestUtil.createTestJar(tempDir.newFile("p.jar"), namesAndTexts);
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(jar);
  }

  private static final class CorruptedJarFileSystemTestWrapper extends JarFileSystemImpl {
    private static volatile boolean corrupted = false;

    @Override
    public @NotNull String getProtocol() {
      return "corrupted-jar";
    }

    @Override
    protected @NotNull ZipHandlerBase getHandler(@NotNull VirtualFile entryFile) {
      return VfsImplUtil.getHandler(this, entryFile, path -> new ZipHandler(path) {
        @Override
        protected @NotNull Map<String, EntryInfo> getEntriesMap() {
          return corrupted ? Map.of() : super.getEntriesMap();
        }
      });
    }
  }
}

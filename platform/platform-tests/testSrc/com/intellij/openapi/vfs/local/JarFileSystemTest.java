/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vfs.local;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.jar.JarFileSystemImpl;
import com.intellij.openapi.vfs.impl.jar.JarHandler;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import static com.intellij.openapi.util.io.IoTestUtil.assertTimestampsEqual;
import static com.intellij.testFramework.PlatformTestUtil.assertPathsEqual;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

public class JarFileSystemTest extends BareTestFixtureTestCase {
  @Rule public TempDirectory tempDir = new TempDirectory();

  @Test
  public void testFindFile() throws IOException {
    String rtJarPath = PlatformTestUtil.getRtJarPath();

    VirtualFile jarRoot = findByPath(rtJarPath + JarFileSystem.JAR_SEPARATOR);
    assertTrue(jarRoot.isDirectory());

    VirtualFile file2 = findByPath(rtJarPath + JarFileSystem.JAR_SEPARATOR + "java");
    assertTrue(file2.isDirectory());

    VirtualFile file3 = jarRoot.findChild("java");
    assertEquals(file2, file3);

    VirtualFile file4 = findByPath(rtJarPath + JarFileSystem.JAR_SEPARATOR + "java/lang/Object.class");
    assertTrue(!file4.isDirectory());

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
    VirtualFile jarRoot = findByPath(PlatformTestUtil.getRtJarPath() + JarFileSystem.JAR_SEPARATOR);
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
    assertThat(Stream.of(jarRoot.getChildren()).map(VirtualFile::getName)).containsExactly("META-INF");

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
    assertThat(Stream.of(jarRoot.getChildren()).map(VirtualFile::getName)).containsExactlyInAnyOrder("META-INF", "some.txt");
    VirtualFile newEntry = findByPath(jar.getPath() + JarFileSystem.JAR_SEPARATOR + "some.txt");
    assertEquals("some text", VfsUtilCore.loadText(newEntry));
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
      assertTrue(resolved.get(handler) == null);
    }
    
    jarFileSystem.setNoCopyJarForPath(jar.getPath() + JarFileSystem.JAR_SEPARATOR);
    assertTrue(!jarFileSystem.isMakeCopyOfJar(jar));
  }

  @Test
  public void testInvalidJar() {
    String jarPath = PathManagerEx.getTestDataPath() + "/vfs/maven-toolchain-1.0.jar";
    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(jarPath);
    assertNotNull(vFile);
    VirtualFile manifest = findByPath(jarPath + JarFileSystem.JAR_SEPARATOR + JarFile.MANIFEST_NAME);
    assertNotNull(manifest);
    VirtualFile classFile = findByPath(jarPath + JarFileSystem.JAR_SEPARATOR + "org/apache/maven/toolchain/java/JavaToolChain.class");
    assertNotNull(classFile);
  }

  @Test
  public void testJarRootForLocalFile() {
    String rtJarPath = PlatformTestUtil.getRtJarPath();

    VirtualFile rtJarFile = LocalFileSystem.getInstance().findFileByPath(rtJarPath);
    assertNotNull(rtJarFile);
    VirtualFile rtJarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(rtJarFile);
    assertNotNull(rtJarRoot);

    VirtualFile entryFile = findByPath(rtJarPath + JarFileSystem.JAR_SEPARATOR + "java/lang/Object.class");
    VirtualFile entryRoot = JarFileSystem.getInstance().getJarRootForLocalFile(entryFile);
    assertNull(entryRoot);

    VirtualFile nonJarFile = LocalFileSystem.getInstance().findFileByPath(SystemProperties.getJavaHome() + "/lib/calendars.properties");
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
    try (InputStream is1 = small1.getInputStream(); InputStream is2 = small2.getInputStream(); InputStream il = large.getInputStream()) {
      assertSame(is1.getClass(), is2.getClass());
      assertNotSame(is1.getClass(), il.getClass());
    }
  }

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
    public int read(@NotNull byte[] b, int off, int len) {
      return len;
    }
  }
}
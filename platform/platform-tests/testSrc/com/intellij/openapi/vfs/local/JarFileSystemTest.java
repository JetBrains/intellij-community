/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.move.MoveHandler;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.refactoring.rename.RenameHandlerRegistry;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.jar.JarFile;

import static com.intellij.testFramework.PlatformTestUtil.assertPathsEqual;

public class JarFileSystemTest extends PlatformTestCase {
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
  }

  public void testMetaInf() {
    VirtualFile jarRoot = findByPath(PlatformTestUtil.getRtJarPath() + JarFileSystem.JAR_SEPARATOR);
    assertTrue(jarRoot.isDirectory());

    VirtualFile metaInf = jarRoot.findChild("META-INF");
    assertNotNull(metaInf);

    assertNotNull(metaInf.findChild("MANIFEST.MF"));
  }

  public void testJarRefreshOnRenameOrMove() throws IOException {
    File jar = IoTestUtil.createTestJar();
    final VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(jar);
    assertNotNull(vFile);
    new WriteCommandAction.Simple(myProject) {

      @Override
      protected void run() throws Throwable {
        PsiTestUtil.addContentRoot(myModule, vFile.getParent());
      }
    }.execute();

    VirtualFile jarRoot = findByPath(jar.getPath() + JarFileSystem.JAR_SEPARATOR);
    final PsiFile file = getPsiManager().findFile(vFile);
    final String newName = vFile.getName() + ".jar";
    rename(file, newName);

    assertFalse(jarRoot.isValid());

    checkMove(jar, vFile, file);
  }

  private void checkMove(File jar, VirtualFile vFile, final PsiFile file) {
    VirtualFile jarRoot;
    File libDir = new File(jar.getParent(), "lib");
    assertTrue(libDir.mkdir());
    final VirtualFile vLibDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(libDir);
    assertNotNull(vLibDir);

    jarRoot = findByPath(vFile.getPath() + JarFileSystem.JAR_SEPARATOR);
    assertTrue(jarRoot.isValid());
    PsiDirectory directory = getPsiManager().findDirectory(vLibDir);
    final DataContext
      psiDataContext = SimpleDataContext.getSimpleContext(LangDataKeys.TARGET_PSI_ELEMENT.getName(), directory);
    new WriteCommandAction.Simple(myProject) {

      @Override
      protected void run() throws Throwable {
        new MoveHandler().invoke(myProject, new PsiElement[] {file}, psiDataContext);
      }
    }.execute();
    assertFalse(jarRoot.isValid());

    jarRoot = findByPath(vFile.getPath() + JarFileSystem.JAR_SEPARATOR);
    assertTrue(jarRoot.isValid());
    rename(directory, "lib2");
    assertFalse(jarRoot.isValid());
  }

  private static void rename(final PsiNamedElement file, final String newName) {
    final DataContext psiDataContext = SimpleDataContext.getSimpleContext(CommonDataKeys.PSI_ELEMENT.getName(), file);
    final RenameHandler renameHandler =
      RenameHandlerRegistry.getInstance().getRenameHandler(psiDataContext);
    assertNotNull(renameHandler);

    new WriteCommandAction.Simple(file.getProject()) {
      @Override
      public void run() {
        PsiElementRenameHandler.rename(file, file.getProject(), file, null, newName);
      }
    }.execute();
  }

  public void testJarRefresh() throws IOException {
    File jar = IoTestUtil.createTestJar();
    assertTrue(jar.setLastModified(jar.lastModified() - 1000));
    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(jar);
    assertNotNull(vFile);

    VirtualFile jarRoot = findByPath(jar.getPath() + JarFileSystem.JAR_SEPARATOR);
    assertEquals(1, jarRoot.getChildren().length);

    final VirtualFile entry = findByPath(jar.getPath() + JarFileSystem.JAR_SEPARATOR + JarFile.MANIFEST_NAME);
    assertContent(entry, "");

    final Ref<Boolean> updated = Ref.create(false);
    ApplicationManager.getApplication().getMessageBus().connect(myTestRootDisposable).subscribe(
      VirtualFileManager.VFS_CHANGES,
      new BulkFileListener.Adapter() {
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
    assertContent(entry, "update");
    assertEquals(2, jarRoot.getChildren().length);
    VirtualFile newEntry = findByPath(jar.getPath() + JarFileSystem.JAR_SEPARATOR + "some.txt");
    assertContent(newEntry, "some text");
  }

  public void testInvalidJar() {
    String jarPath = PathManagerEx.getTestDataPath() + "/vfs/maven-toolchain-1.0.jar";
    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(jarPath);
    assertNotNull(vFile);
    VirtualFile manifest = findByPath(jarPath + JarFileSystem.JAR_SEPARATOR + JarFile.MANIFEST_NAME);
    assertNotNull(manifest);
    VirtualFile classFile = findByPath(jarPath + JarFileSystem.JAR_SEPARATOR + "org/apache/maven/toolchain/java/JavaToolChain.class");
    assertNotNull(classFile);
  }

  public void testJarRootForLocalFile() {
    String rtJarPath = PlatformTestUtil.getRtJarPath();

    VirtualFile rtJarFile = LocalFileSystem.getInstance().findFileByPath(rtJarPath);
    assertNotNull(rtJarFile);
    VirtualFile rtJarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(rtJarFile);
    assertNotNull(rtJarRoot);

    VirtualFile entryFile = findByPath(rtJarPath + JarFileSystem.JAR_SEPARATOR + "java/lang/Object.class");
    VirtualFile entryRoot = JarFileSystem.getInstance().getJarRootForLocalFile(entryFile);
    assertNull(entryRoot);

    VirtualFile nonJarFile = LocalFileSystem.getInstance().findFileByPath(System.getProperty("java.home") + "/lib/calendars.properties");
    assertNotNull(nonJarFile);
    VirtualFile nonJarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(nonJarFile);
    assertNull(nonJarRoot);
  }

  private static VirtualFile findByPath(String path) {
    VirtualFile file = JarFileSystem.getInstance().findFileByPath(path);
    assertNotNull(file);
    assertPathsEqual(path, file.getPath());
    return file;
  }

  private static void assertContent(VirtualFile file, String expected) throws IOException {
    String content = new String(file.contentsToByteArray(), file.getCharset());
    assertEquals(expected, content);
  }
}

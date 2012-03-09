/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.GeneralSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.testFramework.PlatformLangTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.Arrays;

public class LocalFileSystemTest extends PlatformLangTestCase {
  public static void setContentOnDisk(File file, byte[] bom, String content, Charset charset) throws IOException {
    FileOutputStream stream = new FileOutputStream(file);
    if (bom != null) {
      stream.write(bom);
    }
    OutputStreamWriter writer = new OutputStreamWriter(stream, charset);
    try {
      writer.write(content);
    }
    finally {
      writer.close();
    }
  }

  public static VirtualFile createTempFile(@NonNls String ext, @Nullable byte[] bom, @NonNls String content, Charset charset) throws IOException {
    File temp = FileUtil.createTempFile("copy", "." + ext);
    setContentOnDisk(temp, bom, content, charset);

    myFilesToDelete.add(temp);
    final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(temp);
    assert file != null : temp;
    return file;
  }

  public void testChildrenAccessedButNotCached() throws Exception{
    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        @Override
        public void run() {
          try{
            File dir = createTempDirectory(false);
            final ManagingFS managingFS = ManagingFS.getInstance();

            VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(dir.getPath().replace(File.separatorChar, '/'));
            assertNotNull(vFile);
            assertFalse(managingFS.areChildrenLoaded(vFile));
            assertFalse(managingFS.wereChildrenAccessed(vFile));

            final File child = new File(dir, "child");
            final boolean created = child.createNewFile();
            assertTrue(created);

            final File subdir = new File(dir, "subdir");
            final boolean subdirCreated = subdir.mkdir();
            assertTrue(subdirCreated);

            final File subChild = new File(subdir, "subdir");
            final boolean subChildCreated = subChild.createNewFile();
            assertTrue(subChildCreated);

            final VirtualFile childVFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(child.getPath().replace(File.separatorChar, '/'));
            assertNotNull(childVFile);
            assertFalse(managingFS.areChildrenLoaded(vFile));
            assertTrue(managingFS.wereChildrenAccessed(vFile));

            final VirtualFile subdirVFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(subdir.getPath().replace(File.separatorChar, '/'));
            assertNotNull(subdirVFile);
            assertFalse(managingFS.areChildrenLoaded(subdirVFile));
            assertFalse(managingFS.wereChildrenAccessed(subdirVFile));

            assertFalse(managingFS.areChildrenLoaded(vFile));
            assertTrue(managingFS.wereChildrenAccessed(vFile));
            
            
            vFile.getChildren();
            assertTrue(managingFS.areChildrenLoaded(vFile));
            assertTrue(managingFS.wereChildrenAccessed(vFile));
            assertFalse(managingFS.areChildrenLoaded(subdirVFile));
            assertFalse(managingFS.wereChildrenAccessed(subdirVFile));
            
            final VirtualFile subChildVFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(subChild.getPath().replace(File.separatorChar, '/'));
            assertNotNull(subChildVFile);
            assertTrue(managingFS.areChildrenLoaded(vFile));
            assertTrue(managingFS.wereChildrenAccessed(vFile));
            assertFalse(managingFS.areChildrenLoaded(subdirVFile));
            assertTrue(managingFS.wereChildrenAccessed(subdirVFile));
          }
          catch(IOException e){
            LOG.error(e);
          }
        }
      }
    );
  }
  
  public void testRefreshAndFindFile() throws Exception{
    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        @Override
        public void run() {
          try{
            File dir = createTempDirectory();

            VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(dir.getPath().replace(File.separatorChar, '/'));
            assertNotNull(vFile);
            vFile.getChildren();

            for(int i = 0; i < 100; i++){
              File subdir = new File(dir, "a" + i);
              assertTrue(subdir.mkdir());
            }

            File subdir = new File(dir, "aaa");
            assertTrue(subdir.mkdir());

            VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(subdir.getPath().replace(File.separatorChar, '/'));
            assertNotNull(file);
          }
          catch(IOException e){
            LOG.error(e);
          }
        }
      }
    );
  }

  public void testCopyFile() throws Exception{
    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        @Override
        public void run() {
          try {
            File fromDir = createTempDirectory();
            File toDir = createTempDirectory();

            VirtualFile fromVDir = LocalFileSystem.getInstance().findFileByPath(fromDir.getPath().replace(File.separatorChar, '/'));
            VirtualFile toVDir = LocalFileSystem.getInstance().findFileByPath(toDir.getPath().replace(File.separatorChar, '/'));
            assertNotNull(fromVDir);
            assertNotNull(toVDir);
            final VirtualFile fileToCopy = fromVDir.createChildData(this, "temp_file");
            final byte[] byteContent = {0, 1, 2, 3};
            fileToCopy.setBinaryContent(byteContent);
            final String newName = "new_temp_file";
            final VirtualFile copy = fileToCopy.copy(this, toVDir, newName);
            assertEquals(newName, copy.getName());
            assertTrue(Arrays.equals(byteContent, copy.contentsToByteArray()));
          }
          catch (Exception e) {
            LOG.error(e);
          }
        }
      }
    );
  }

  public void testCopyDir() throws Exception{
    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        @Override
        public void run() {
          try{
            File fromDir = createTempDirectory();
            File toDir = createTempDirectory();

            VirtualFile fromVDir = LocalFileSystem.getInstance().findFileByPath(fromDir.getPath().replace(File.separatorChar, '/'));
            VirtualFile toVDir = LocalFileSystem.getInstance().findFileByPath(toDir.getPath().replace(File.separatorChar, '/'));
            assertNotNull(fromVDir);
            assertNotNull(toVDir);
            final VirtualFile dirToCopy = fromVDir.createChildDirectory(this, "dir");
            final VirtualFile file = dirToCopy.createChildData(this, "temp_file");
            file.setBinaryContent(new byte[]{0, 1, 2, 3});
            final String newName = "dir";
            final VirtualFile dirCopy = dirToCopy.copy(this, toVDir, newName);
            assertEquals(newName, dirCopy.getName());
            PlatformTestUtil.assertDirectoriesEqual(toVDir, fromVDir, null);
          }
          catch(Exception e){
            LOG.error(e);
          }
        }
      }
    );
  }

  public void testGermanLetters() throws Exception{
    final File dirFile = createTempDirectory();

    final String name = "te\u00dft123123123.txt";
    final File childFile = new File(dirFile, name);
    assert childFile.createNewFile() || childFile.exists() : childFile;

    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        @Override
        public void run() {
          try{
            final VirtualFile dir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dirFile);
            assertNotNull(dir);

            final VirtualFile child = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(childFile);
            assertNotNull(child);

          }
          catch(Exception e){
            LOG.error(e);
          }
        }
      }
    );

    assertTrue(childFile.delete());
  }

  public void testFindRoot() {
    VirtualFile file = LocalFileSystem.getInstance().findFileByPath("wrong_path");
    assertNull(file);

    if (SystemInfo.isWindows) {
      assertNotNull(LocalFileSystem.getInstance().findFileByPath("\\\\unit-133"));
      assertNotNull(LocalFileSystem.getInstance().findFileByIoFile(new File("\\\\unit-133")));
    }

    if (SystemInfo.isWindows && new File("c:").exists()) {
      VirtualFile root = LocalFileSystem.getInstance().findFileByPath("c:");
      assertNotNull(root);
    }
    if (SystemInfo.isUnix) {
      VirtualFile root = LocalFileSystem.getInstance().findFileByPath("/");
      assertNotNull(root);
    }

    VirtualFile root = LocalFileSystem.getInstance().findFileByPath("");
    assertNotNull(root);
  }

  public void testFileLength() throws Exception {
    File file = FileUtil.createTempFile("test", "txt");
    FileUtil.writeToFile(file, "hello");
    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    assertNotNull(virtualFile);
    String s = VfsUtilCore.loadText(virtualFile);
    assertEquals("hello", s);
    assertEquals(5, virtualFile.getLength());

    FileUtil.writeToFile(file, "new content");
    PersistentFS.cleanPersistedContents();
    s = VfsUtilCore.loadText(virtualFile);
    assertEquals("new content", s);
    assertEquals(11, virtualFile.getLength());
  }

  public void testHardLinks() throws Exception {
    if (!SystemInfo.isWindows && !SystemInfo.isUnix) return;

    final boolean safeWrite = GeneralSettings.getInstance().isUseSafeWrite();
    final File dir = FileUtil.createTempDirectory("hardlinks", "");
    try {
      GeneralSettings.getInstance().setUseSafeWrite(false);

      final File targetFile = new File(dir, "targetFile");
      assertTrue(targetFile.createNewFile());
      final File hardLinkFile = new File(dir, "hardLinkFile");

      if (SystemInfo.isWindows) {
        assertEquals("target=" + targetFile + " link=" + hardLinkFile,
                     0, ExecUtil.execAndGetResult("fsutil", "hardlink", "create", hardLinkFile.getPath(), targetFile.getPath()));
      }
      else if (SystemInfo.isUnix) {
        assertEquals("target=" + targetFile + " link=" + hardLinkFile,
                     0, ExecUtil.execAndGetResult("ln", targetFile.getPath(), hardLinkFile.getPath()));
      }

      final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(targetFile);
      assertNotNull(file);
      file.setBinaryContent("hello".getBytes(), 0, 0, new SafeWriteRequestor() {});

      final VirtualFile check = LocalFileSystem.getInstance().findFileByIoFile(hardLinkFile);
      assertNotNull(check);
      assertEquals("hello", VfsUtilCore.loadText(check));
    }
    finally {
      GeneralSettings.getInstance().setUseSafeWrite(safeWrite);
      FileUtil.delete(dir);
    }
  }
}

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

package com.intellij.history.integration;


import com.intellij.history.core.changes.Change;
import com.intellij.history.core.changes.DeleteChange;
import com.intellij.history.core.changes.StructuralChange;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.utils.RunnableAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.ReadOnlyAttributeUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class FileListeningTest extends IntegrationTestCase {
  public void testCreatingFiles() throws Exception {
    VirtualFile f = createFile("file.txt");
    assertEquals(2, getRevisionsFor(f).size());
  }

  public void testCreatingDirectories() throws Exception {
    VirtualFile f = createDirectory("dir");
    assertEquals(2, getRevisionsFor(f).size());
  }

  public void testIgnoringFilteredFileTypes() throws Exception {
    int before = getRevisionsFor(myRoot).size();
    createFile("file.hprof");

    assertEquals(before, getRevisionsFor(myRoot).size());
  }

  public void testIgnoringFilteredDirectories() throws Exception {
    int before = getRevisionsFor(myRoot).size();

    createDirectory(FILTERED_DIR_NAME);
    assertEquals(before, getRevisionsFor(myRoot).size());
  }

  public void testIgnoringFilesRecursively() throws Exception {
    addExcludedDir(myRoot.getPath() + "/dir/subdir");
    addContentRoot(createModule("foo"), myRoot.getPath() + "/dir/subdir/subsubdir1");

    String dir = createDirectoryExternally("dir");
    String dir1_file = createFileExternally("dir/f.txt");
    createFileExternally("dir/f.class");
    createFileExternally("dir/subdir/f.txt");
    String subsubdir1 = createDirectoryExternally("dir/subdir/subsubdir1");
    String subsubdir1_file = createFileExternally("dir/subdir/subsubdir1/f.txt");
    createDirectoryExternally("dir/subdir/subsubdir2");
    createFileExternally("dir/subdir/subsubdir2/f.txt");
 
    myRoot.refresh(false, true);

    List<Change> changes = getVcs().getChangeListInTests().getChangesInTests().get(0).getChanges();
    List<String> actual = new SmartList<>();
    for (Change each : changes) {
      actual.add(((StructuralChange)each).getPath());
    }

    List<String> expected = new ArrayList<>(Arrays.asList(dir, subsubdir1, dir1_file, subsubdir1_file));

    Collections.sort(actual);
    Collections.sort(expected);
    assertOrderedEquals(actual, expected);

    // ignored folders should not be loaded in VFS
    assertEquals("dir\n" +
                 " f.class\n" +
                 " f.txt\n" +
                 " subdir\n" +
                 "  subsubdir1\n" +
                 "   f.txt\n",
                 buildDBFileStructure(myRoot, 0, new StringBuilder()).toString()
    );
  }

  private static StringBuilder buildDBFileStructure(@NotNull VirtualFile from, int level, @NotNull StringBuilder builder) {
    List<VirtualFile> children = ContainerUtil.newArrayList(((NewVirtualFile)from).getCachedChildren());
    Collections.sort(children, (o1, o2) -> o1.getName().compareTo(o2.getName()));
    for (VirtualFile eachChild : children) {
      builder.append(StringUtil.repeat(" ", level)).append(eachChild.getName()).append("\n");
      buildDBFileStructure(eachChild, level + 1, builder);
    }
    return builder;
  }

  public void testChangingFileContent() throws Exception {
    VirtualFile f = createFile("file.txt");
    assertEquals(2, getRevisionsFor(f).size());

    setBinaryContent(f,new byte[]{1});
    assertEquals(3, getRevisionsFor(f).size());

    setBinaryContent(f,new byte[]{2});
    assertEquals(4, getRevisionsFor(f).size());
  }

  public void testRenamingFile() throws Exception {
    VirtualFile f = createFile("file.txt");
    assertEquals(2, getRevisionsFor(f).size());

    rename(f, "file2.txt");
    assertEquals(3, getRevisionsFor(f).size());
  }

  public void testRenamingFileOnlyAfterRenamedEvent() throws Exception {
    final VirtualFile f = createFile("old.txt");

    final int[] log = new int[2];
    VirtualFileListener l = new VirtualFileAdapter() {
      @Override
      public void beforePropertyChange(@NotNull VirtualFilePropertyEvent e) {
        log[0] = getRevisionsFor(f).size();
      }

      @Override
      public void propertyChanged(@NotNull VirtualFilePropertyEvent e) {
        log[1] = getRevisionsFor(f).size();
      }
    };

    assertEquals(2, getRevisionsFor(f).size());

    addFileListenerDuring(l, new RunnableAdapter() {
      @Override
      public void doRun() throws IOException {
        rename(f, "new.txt");
      }
    });

    assertEquals(2, log[0]);
    assertEquals(3, log[1]);
  }

  public void testRenamingFilteredFileToNonFiltered() throws Exception {
    int before = getRevisionsFor(myRoot).size();

    VirtualFile f = createFile("file.hprof");
    assertEquals(before, getRevisionsFor(myRoot).size());

    rename(f, "file.txt");
    assertEquals(before + 1, getRevisionsFor(myRoot).size());

    assertEquals(2, getRevisionsFor(f).size());
  }

  public void testRenamingNonFilteredFileToFiltered() throws Exception {
    int before = getRevisionsFor(myRoot).size();

    VirtualFile f = createFile("file.txt");
    assertEquals(before + 1, getRevisionsFor(myRoot).size());

    rename(f, "file.hprof");
    assertEquals(before + 2, getRevisionsFor(myRoot).size());
  }

  public void testRenamingFilteredDirectoriesToNonFiltered() throws Exception {
    int before = getRevisionsFor(myRoot).size();

    VirtualFile f = createFile(FILTERED_DIR_NAME);
    assertEquals(before, getRevisionsFor(myRoot).size());

    rename(f, "not_filtered");
    assertEquals(before + 1, getRevisionsFor(myRoot).size());

    assertEquals(2, getRevisionsFor(f).size());
  }

  public void testRenamingNonFilteredDirectoriesToFiltered() throws Exception {
    int before = getRevisionsFor(myRoot).size();

    VirtualFile f = createDirectory("not_filtered");
    assertEquals(before + 1, getRevisionsFor(myRoot).size());

    rename(f, FILTERED_DIR_NAME);
    assertEquals(before + 2, getRevisionsFor(myRoot).size());
  }

  public void testChangingROStatusForFile() throws Exception {
    VirtualFile f = createFile("f.txt");
    assertEquals(2, getRevisionsFor(f).size());

    setReadOnlyAttribute(f, true);
    assertEquals(3, getRevisionsFor(f).size());

    setReadOnlyAttribute(f, false);
    assertEquals(4, getRevisionsFor(f).size());
  }

  public void testIgnoringROStatusChangeForUnversionedFiles() throws Exception {
    int before = getRevisionsFor(myRoot).size();

    VirtualFile f = createFile("f.hprof");
    setReadOnlyAttribute(f, true);

    assertEquals(before, getRevisionsFor(myRoot).size());
  }

  private void setReadOnlyAttribute(VirtualFile f, boolean status) throws IOException {
    ApplicationManager.getApplication().runWriteAction(new ThrowableComputable<Object, IOException>() {
      @Override
      public Object compute() throws IOException {
        ReadOnlyAttributeUtil.setReadOnlyAttribute(f, status); // shouldn't throw
        return null;
      }
    });
  }

  public void testDeletion() throws Exception {
    VirtualFile f = createDirectory("f.txt");

    int before = getRevisionsFor(myRoot).size();

    delete(f);
    assertEquals(before + 1, getRevisionsFor(myRoot).size());
  }

  public void testDeletionOfFilteredDirectoryDoesNotThrowsException() throws Exception {
    int before = getRevisionsFor(myRoot).size();

    VirtualFile f = createDirectory(FILTERED_DIR_NAME);
    delete(f);
    assertEquals(before, getRevisionsFor(myRoot).size());
  }

  public void testDeletionDoesNotVersionIgnoredFilesRecursively() throws Exception {
    String dir1 = createDirectoryExternally("dir");
    createFileExternally("dir/f.txt");
    createFileExternally("dir/f.class");
    createFileExternally("dir/subdir/f.txt");
    createDirectoryExternally("dir/subdir/subdir2");
    createFileExternally("dir/subdir/subdir2/f.txt");

    LocalFileSystem.getInstance().refresh(false);

    addExcludedDir(myRoot.getPath() + "/dir/subdir");
    addContentRoot(myRoot.getPath() + "/dir/subdir/subdir2");

    final VirtualFile vDir1 = LocalFileSystem.getInstance().findFileByPath(dir1);
    assertNotNull(dir1, vDir1);
    delete(vDir1);

    List<Change> changes = getVcs().getChangeListInTests().getChangesInTests().get(0).getChanges();
    assertEquals(1, changes.size());
    Entry e = ((DeleteChange)changes.get(0)).getDeletedEntry();
    final List<Entry> children = e.getChildren();
    sortEntries(children);
    assertEquals(2, children.size());
    assertEquals("f.txt", children.get(0).getName());
    assertEquals("subdir", children.get(1).getName());
    assertEquals(1, children.get(1).getChildren().size());
    assertEquals("subdir2", children.get(1).getChildren().get(0).getName());
  }

  public void testCreationAndDeletionOfUnversionedFile() throws IOException {
    addExcludedDir(myRoot.getPath() + "/dir");

    Module m = createModule("foo");
    addContentRoot(m, myRoot.getPath() + "/dir/subDir");

    createFileExternally("dir/subDir/file.txt");
    LocalFileSystem.getInstance().refresh(false);

    FileUtil.delete(new File(myRoot.getPath() + "/dir"));
    LocalFileSystem.getInstance().refresh(false);

    createFileExternally("dir/subDir/file.txt");
    LocalFileSystem.getInstance().refresh(false);

    List<Revision> revs = getRevisionsFor(myRoot);
    assertEquals(4, revs.size());
    assertNotNull(revs.get(0).findEntry().findEntry("dir/subDir/file.txt"));
    assertNull(revs.get(1).findEntry().findEntry("dir/subDir/file.txt"));
    assertNotNull(revs.get(2).findEntry().findEntry("dir/subDir/file.txt"));
    assertNull(revs.get(3).findEntry().findEntry("dir/subDir/file.txt"));
  }

  public void testCreationAndDeletionOfFileUnderUnversionedDir() throws IOException {
    addExcludedDir(myRoot.getPath() + "/dir");

    Module m = createModule("foo");
    addContentRoot(m, myRoot.getPath() + "/dir/subDir");

    createFileExternally("dir/subDir/file.txt");
    LocalFileSystem.getInstance().refresh(false);

    FileUtil.delete(new File(myRoot.getPath() + "/dir/subDir"));
    LocalFileSystem.getInstance().refresh(false);

    createFileExternally("dir/subDir/file.txt");
    LocalFileSystem.getInstance().refresh(false);

    List<Revision> revs = getRevisionsFor(myRoot);
    assertEquals(4, revs.size());
    assertNotNull(revs.get(0).findEntry().findEntry("dir/subDir/file.txt"));
    assertNull(revs.get(1).findEntry().findEntry("dir/subDir"));
    assertNotNull(revs.get(2).findEntry().findEntry("dir/subDir/file.txt"));
    assertNull(revs.get(3).findEntry().findEntry("dir/subDir"));
  }

  private static void sortEntries(final List<Entry> entries) {
    Collections.sort(entries, (o1, o2) -> o1.getName().compareTo(o2.getName()));
  }
}

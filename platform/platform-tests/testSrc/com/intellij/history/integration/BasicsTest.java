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


import com.intellij.history.LocalHistory;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.utils.RunnableAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public class BasicsTest extends IntegrationTestCase {
  public void testProcessingCommands() {
    final VirtualFile[] f = new VirtualFile[1];

    ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance().executeCommand(myProject, new RunnableAdapter() {
      @Override
      public void doRun() throws IOException {
        f[0] = createChildData(myRoot, "f1.txt");
        f[0].setBinaryContent(new byte[]{1});
        f[0].setBinaryContent(new byte[]{2});
      }
    }, "name", null));


    assertEquals(2, getRevisionsFor(f[0]).size());
  }

  public void testPuttingUserLabel() {
    VirtualFile f = createChildData(myRoot, "f.txt");

    LocalHistory.getInstance().putUserLabel(myProject, "global");

    assertEquals(3, getRevisionsFor(f).size());
    assertEquals(4, getRevisionsFor(myRoot).size());

    LocalHistory.getInstance().putUserLabel(myProject, "file");

    List<Revision> rr = getRevisionsFor(f);
    assertEquals(4, rr.size());
    assertEquals("file", rr.get(1).getLabel());
    assertEquals(-1, rr.get(1).getLabelColor());
    assertEquals("global", rr.get(2).getLabel());
    assertEquals(-1, rr.get(2).getLabelColor());
  }

  public void testPuttingSystemLabel() {
    VirtualFile f = createChildData(myRoot, "file.txt");

    assertEquals(2, getRevisionsFor(f).size());
    assertEquals(3, getRevisionsFor(myRoot).size());

    LocalHistory.getInstance().putSystemLabel(myProject, "label");

    List<Revision> rr = getRevisionsFor(f);
    assertEquals(3, rr.size());
    assertEquals("label", rr.get(1).getLabel());

    rr = getRevisionsFor(myRoot);
    assertEquals(4, rr.size());
    assertEquals("label", rr.get(1).getLabel());
  }

  public void testPuttingLabelWithUnsavedDocuments() {
    VirtualFile f = createChildData(myRoot, "f.txt");
    setContent(f, "1");

    setDocumentTextFor(f, "2");
    LocalHistory.getInstance().putSystemLabel(myProject, "label");

    setDocumentTextFor(f, "3");
    LocalHistory.getInstance().putUserLabel(myProject, "label");

    setDocumentTextFor(f, "4");
    LocalHistory.getInstance().putUserLabel(myProject, "label");

    List<Revision> rr = getRevisionsFor(f);
    assertEquals(9, rr.size()); // 5 changes + 3 labels
    assertEquals("4", new String(rr.get(0).findEntry().getContent().getBytes()));
    assertEquals("4", new String(rr.get(1).findEntry().getContent().getBytes()));
    assertEquals("3", new String(rr.get(2).findEntry().getContent().getBytes()));
    assertEquals("3", new String(rr.get(3).findEntry().getContent().getBytes()));
    assertEquals("2", new String(rr.get(4).findEntry().getContent().getBytes()));
    assertEquals("2", new String(rr.get(5).findEntry().getContent().getBytes()));
    assertEquals("1", new String(rr.get(6).findEntry().getContent().getBytes()));
    assertEquals("", new String(rr.get(7).findEntry().getContent().getBytes()));
  }

  public void testDoNotRegisterSameUnsavedDocumentContentTwice() {
    VirtualFile f = createChildData(myRoot, "f.txt");
    setContent(f, "1");

    setDocumentTextFor(f, "2");
    LocalHistory.getInstance().putSystemLabel(myProject, "label");
    LocalHistory.getInstance().putUserLabel(myProject, "label");

    List<Revision> rr = getRevisionsFor(f);
    assertEquals(6, rr.size()); // 3 changes + 2 labels
    assertEquals("2", new String(rr.get(0).findEntry().getContent().getBytes()));
    assertEquals("2", new String(rr.get(1).findEntry().getContent().getBytes()));
    assertEquals("2", new String(rr.get(2).findEntry().getContent().getBytes()));
    assertEquals("1", new String(rr.get(3).findEntry().getContent().getBytes()));
    assertEquals("", new String(rr.get(4).findEntry().getContent().getBytes()));
  }

  public void testIsUnderControl() {
    VirtualFile f1 = createChildData(myRoot, "file.txt");
    VirtualFile f2 = createChildData(myRoot, "file.hprof");

    assertTrue(LocalHistory.getInstance().isUnderControl(f1));
    assertFalse(LocalHistory.getInstance().isUnderControl(f2));
  }

  public void testDoNotRegisterChangesNotInLocalFS() throws Exception {
    File f = new File(myRoot.getPath(), "f.jar");
    ApplicationManager.getApplication().runWriteAction(new ThrowableComputable<Object, IOException>() {
      @Override
      public Object compute() throws IOException {
    JarOutputStream jar = new JarOutputStream(new FileOutputStream(f));

    jar.putNextEntry(new JarEntry("file.txt"));
    jar.write(1);
    jar.closeEntry();
    jar.close();
        return null;
      }
    });

    VirtualFile vfile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);
    assertNotNull(vfile);

    VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(vfile);
    assertEquals(1, jarRoot.findChild("file.txt").contentsToByteArray()[0]);

    assertEquals(3, getRevisionsFor(myRoot).size());

    ApplicationManager.getApplication().runWriteAction(new ThrowableComputable<Object, IOException>() {
      @Override
      public Object compute() throws IOException {
        JarOutputStream jar = new JarOutputStream(new FileOutputStream(f));

        JarEntry e = new JarEntry("file.txt");
        e.setTime(f.lastModified() + 10000);
        jar.putNextEntry(e);
        jar.write(2);
        jar.closeEntry();
        jar.close();
        f.setLastModified(f.lastModified() + 10000);
        return null;
      }
    });


    LocalFileSystem.getInstance().refreshWithoutFileWatcher(false);
    JarFileSystem.getInstance().refreshWithoutFileWatcher(false);
    jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(vfile);
    assertEquals(2, jarRoot.findChild("file.txt").contentsToByteArray()[0]);

    assertEquals(3, getRevisionsFor(myRoot).size());
    assertEquals(2, getRevisionsFor(jarRoot).size());
  }
}

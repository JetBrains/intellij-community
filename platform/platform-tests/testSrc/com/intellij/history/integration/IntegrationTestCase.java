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

import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.core.LocalHistoryTestCase;
import com.intellij.history.core.Paths;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.RootEntry;
import com.intellij.history.utils.RunnableAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;

public abstract class IntegrationTestCase extends PlatformTestCase {
  protected static final int TIMESTAMP_INCREMENT = 3000;
  protected static final String FILTERED_DIR_NAME = "CVS";

  protected VirtualFile myRoot;
  protected IdeaGateway myGateway;

  // let it be as if someone (e.g. dumb mode indexing) has loaded the content so it's available to local history
  protected static void loadContent(VirtualFile f) throws IOException {
    f.contentsToByteArray();
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    LocalHistoryImpl.getInstanceImpl().cleanupForNextTest();
    
    Clock.reset();
    Paths.useSystemCaseSensitivity();

    myGateway = new IdeaGateway();

    ApplicationManager.getApplication().runWriteAction(new RunnableAdapter() {
      @Override
      public void doRun() throws Exception {
        setUpInWriteAction();
      }
    });
  }

  @Override
  protected File getIprFile() throws IOException {
    return new File(createTempDirectory(), "test.ipr");
  }

  protected void setUpInWriteAction() throws Exception {
    VirtualFile tmpTestDir =
      ObjectUtils.assertNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(FileUtil.getTempDirectory())));
    myRoot = tmpTestDir.createChildDirectory(null, "idea_test_integration");
    PsiTestUtil.addContentRoot(myModule, myRoot);
  }

  @Override
  protected void tearDown() throws Exception {
    Clock.reset();
    Paths.useSystemCaseSensitivity();
    super.tearDown();
  }

  protected VirtualFile createFile(String name) throws IOException {
    return createFile(name, null);
  }

  @NotNull
  protected VirtualFile createFile(String name, String content) throws IOException {
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(createFileExternally(name, content));
    assertNotNull(name, file);
    return file;
  }

  @NotNull
  protected VirtualFile createDirectory(String name) {
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(createDirectoryExternally(name));
    assertNotNull(name, file);
    return file;
  }

  protected void setContent(VirtualFile f, String content) {
    setContent(f, content, f.getTimeStamp() + TIMESTAMP_INCREMENT);
  }

  protected void setContent(VirtualFile f, String content, long timestamp) {
    setBinaryContent(f, content.getBytes(CharsetToolkit.UTF8_CHARSET), -1, timestamp,this);
  }

  protected String createFileExternally(String name) throws IOException {
    return createFileExternally(name, null);
  }

  protected String createFileExternally(String name, String content) throws IOException {
    File f = new File(myRoot.getPath(), name);
    assertTrue(f.getPath(), f.getParentFile().mkdirs() || f.getParentFile().isDirectory());
    assertTrue(f.getPath(), f.createNewFile() || f.exists());
    if (content != null) FileUtil.writeToFile(f, content.getBytes(CharsetToolkit.UTF8_CHARSET));
    return FileUtil.toSystemIndependentName(f.getPath());
  }

  protected String createDirectoryExternally(String name) {
    File f = new File(myRoot.getPath(), name);
    assertTrue(f.getPath(), f.mkdirs() || f.isDirectory());
    return FileUtil.toSystemIndependentName(f.getPath());
  }

  protected void setContentExternally(String path, String content) throws IOException {
    File f = new File(path);
    FileUtil.writeToFile(f, content.getBytes(CharsetToolkit.UTF8_CHARSET));
    assertTrue(f.getPath(), f.setLastModified(f.lastModified() + 2000));
  }

  protected void setDocumentTextFor(VirtualFile f, String text) {
    Document document = FileDocumentManager.getInstance().getDocument(f);
    assertNotNull(f.getPath(), document);
    ApplicationManager.getApplication().runWriteAction(() -> document.setText(text));
  }

  protected LocalHistoryFacade getVcs() {
    return LocalHistoryImpl.getInstanceImpl().getFacade();
  }

  protected List<Revision> getRevisionsFor(VirtualFile f) {
    return getRevisionsFor(f, null);
  }

  protected List<Revision> getRevisionsFor(final VirtualFile f, final String pattern) {
    return ReadAction
      .compute(() -> LocalHistoryTestCase.collectRevisions(getVcs(), getRootEntry(), f.getPath(), myProject.getLocationHash(), pattern));
  }

  protected RootEntry getRootEntry() {
    return myGateway.createTransientRootEntry();
  }

  protected void addContentRoot(String path) {
    addContentRoot(myModule, path);
  }

  protected static void addContentRoot(final Module module, final String path) {
    ApplicationManager.getApplication().runWriteAction(() -> ModuleRootModificationUtil.addContentRoot(module, FileUtil.toSystemIndependentName(path)));
  }

  protected void addExcludedDir(final String path) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      ModuleRootManager rm = ModuleRootManager.getInstance(myModule);
      ModifiableRootModel m = rm.getModifiableModel();
      for (ContentEntry e : m.getContentEntries()) {
        if (!Comparing.equal(e.getFile(), myRoot)) continue;
        e.addExcludeFolder(VfsUtilCore.pathToUrl(FileUtil.toSystemIndependentName(path)));
      }
      m.commit();
    });
  }

  protected static void addFileListenerDuring(VirtualFileListener l, Runnable r) {
    VirtualFileManager.getInstance().addVirtualFileListener(l);
    try {
      r.run();
    }
    finally {
      VirtualFileManager.getInstance().removeVirtualFileListener(l);
    }
  }

  protected static void assertContent(String expected, Entry e) {
    assertEquals(expected, new String(e.getContent().getBytes()));
  }
}

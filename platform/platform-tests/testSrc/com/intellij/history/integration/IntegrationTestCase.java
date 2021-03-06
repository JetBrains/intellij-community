// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.util.io.PathKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

public abstract class IntegrationTestCase extends HeavyPlatformTestCase {
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

  protected void setUpInWriteAction() throws Exception {
    myRoot = getTempDir().createVirtualDir();
    PsiTestUtil.addContentRoot(myModule, myRoot);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Clock.reset();
      Paths.useSystemCaseSensitivity();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  protected @NotNull VirtualFile createFile(@NotNull String name) {
    return createFile(name, null);
  }

  // tests fail if file created via API, so, refreshAndFindFileByNioFile is used
  protected @NotNull VirtualFile createFile(@NotNull String name, @Nullable String content) {
    Path file = myRoot.toNioPath().resolve(name);
    if (content == null) {
      PathKt.createFile(file);
    }
    else {
      PathKt.write(file, content);
    }
    return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file);
  }

  protected final @NotNull VirtualFile createDirectory(@NotNull String name) {
    return VfsTestUtil.createDir(myRoot, name);
  }

  protected final void setContent(@NotNull VirtualFile f, @NotNull String content) {
    setContent(f, content, f.getTimeStamp() + TIMESTAMP_INCREMENT);
  }

  protected final void setContent(VirtualFile f, String content, long timestamp) {
    setBinaryContent(f, content.getBytes(StandardCharsets.UTF_8), -1, timestamp, this);
  }

  protected final @NotNull String createFileExternally(@NotNull String name) {
    Path file = myRoot.toNioPath().resolve(name);
    PathKt.createFile(file);
    return file.toString().replace(File.separatorChar, '/');
  }

  protected final String createDirectoryExternally(String name) {
    File f = new File(myRoot.getPath(), name);
    assertTrue(f.getPath(), f.mkdirs() || f.isDirectory());
    return FileUtil.toSystemIndependentName(f.getPath());
  }

  protected static void setContentExternally(String path, String content) throws IOException {
    File f = new File(path);
    FileUtil.writeToFile(f, content.getBytes(StandardCharsets.UTF_8));
    assertTrue(f.getPath(), f.setLastModified(f.lastModified() + 2000));
  }

  protected static void setDocumentTextFor(VirtualFile f, String text) {
    Document document = FileDocumentManager.getInstance().getDocument(f);
    assertNotNull(f.getPath(), document);
    ApplicationManager.getApplication().runWriteAction(() -> document.setText(text));
  }

  protected LocalHistoryFacade getVcs() {
    return LocalHistoryImpl.getInstanceImpl().getFacade();
  }

  protected List<Revision> getRevisionsFor(@NotNull VirtualFile f) {
    return getRevisionsFor(f, null);
  }

  protected List<Revision> getRevisionsFor(@NotNull VirtualFile f, @Nullable String pattern) {
    return ReadAction.compute(() -> {
      return LocalHistoryTestCase.collectRevisions(getVcs(), getRootEntry(), f.getPath(), myProject.getLocationHash(), pattern);
    });
  }

  protected RootEntry getRootEntry() {
    return myGateway.createTransientRootEntry();
  }

  protected void addContentRoot(@NotNull String path) {
    addContentRoot(myModule, path);
  }

  protected static void addContentRoot(@NotNull Module module, @NotNull String path) {
    ApplicationManager.getApplication().runWriteAction(() -> ModuleRootModificationUtil.addContentRoot(module, FileUtil.toSystemIndependentName(path)));
  }

  protected void addExcludedDir(final String path) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      ModuleRootManager rm = ModuleRootManager.getInstance(myModule);
      ModifiableRootModel m = rm.getModifiableModel();
      for (ContentEntry e : m.getContentEntries()) {
        if (!Comparing.equal(e.getFile(), myRoot)) continue;
        e.addExcludeFolder(VfsUtilCore.pathToUrl(path));
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
    assertEquals(expected, new String(e.getContent().getBytes(), StandardCharsets.UTF_8));
  }
}

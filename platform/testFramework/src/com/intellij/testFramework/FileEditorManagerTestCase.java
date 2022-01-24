// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.fileEditor.impl.FileEditorProviderManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.docking.DockContainer;
import com.intellij.ui.docking.DockManager;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Dmitry Avdeev
 */
public abstract class FileEditorManagerTestCase extends BasePlatformTestCase {
  protected FileEditorManagerImpl myManager;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myManager = new FileEditorManagerImpl(getProject());
    ServiceContainerUtil.registerComponentInstance(getProject(), FileEditorManager.class, myManager, getTestRootDisposable());
    ((FileEditorProviderManagerImpl)FileEditorProviderManager.getInstance()).clearSelectedProviders();
  }

  @Override
  protected void tearDown() throws Exception {
    Project project = getProject();
    RunAll.runAll(
      () -> myManager.closeAllFiles(),
      () -> { if (project != null) EditorHistoryManager.getInstance(project).removeAllFiles(); },
      () -> ((FileEditorProviderManagerImpl)FileEditorProviderManager.getInstance()).clearSelectedProviders(),
      () -> Disposer.dispose(myManager),
      () -> {
        myManager = null;
        if (project != null) {
          DockManager dockManager = project.getServiceIfCreated(DockManager.class);
          Set<DockContainer> containers = dockManager == null ? Collections.emptySet() : dockManager.getContainers();
          assertEmpty(containers);
        }
      },
      () -> super.tearDown()
    );
  }

  @NotNull
  protected VirtualFile getFile(@NotNull String path) {
    String fullPath = getTestDataPath() + path;
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(fullPath);
    assertNotNull("Can't find " + fullPath, file);
    return file;
  }
  @NotNull
  protected VirtualFile createFile(@NotNull String path, byte @NotNull [] content) {
    File io = new File(getTestDataPath() + path);
    try {
      FileUtil.writeToFile(io, content);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(io);
    assertNotNull("Can't find " + io, file);
    return file;
  }

  protected void openFiles(@NotNull String femSerialisedText) throws IOException, JDOMException, InterruptedException, ExecutionException {
    Element rootElement = JDOMUtil.load(femSerialisedText);
    ExpandMacroToPathMap map = new ExpandMacroToPathMap();
    map.addMacroExpand(PathMacroUtil.PROJECT_DIR_MACRO_NAME, getTestDataPath());
    map.substitute(rootElement, true, true);

    myManager.loadState(rootElement);

    Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() -> myManager.getMainSplitters().openFiles());
    while (true) {
      try {
        future.get(100, TimeUnit.MILLISECONDS);
        return;
      }
      catch (TimeoutException e) {
        UIUtil.dispatchAllInvocationEvents();
      }
    }
  }
}

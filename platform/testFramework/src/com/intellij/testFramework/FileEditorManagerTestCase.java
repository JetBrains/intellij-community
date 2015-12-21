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
package com.intellij.testFramework;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.fileEditor.impl.FileEditorProviderManagerImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.ui.docking.DockContainer;
import com.intellij.ui.docking.DockManager;
import com.intellij.util.ui.UIUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Dmitry Avdeev
 *         Date: 4/25/13
 */
public abstract class FileEditorManagerTestCase extends LightPlatformCodeInsightFixtureTestCase {

  protected FileEditorManagerImpl myManager;
  private FileEditorManager myOldManager;
  private Set<DockContainer> myOldDockContainers;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    DockManager dockManager = DockManager.getInstance(getProject());
    myOldDockContainers = dockManager.getContainers();
    myManager = new FileEditorManagerImpl(getProject(), dockManager);
    myOldManager = ((ComponentManagerImpl)getProject()).registerComponentInstance(FileEditorManager.class, myManager);
    ((FileEditorProviderManagerImpl)FileEditorProviderManager.getInstance()).clearSelectedProviders();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      for (DockContainer container : DockManager.getInstance(getProject()).getContainers()) {
        if (!myOldDockContainers.contains(container)) {
          Disposer.dispose(container);
        }
      }
      myOldDockContainers = null;
      ((ComponentManagerImpl)getProject()).registerComponentInstance(FileEditorManager.class, myOldManager);
      myManager.closeAllFiles();
      ((FileEditorProviderManagerImpl)FileEditorProviderManager.getInstance()).clearSelectedProviders();
    }
    finally {
      super.tearDown();
    }
  }

  @Override
  protected boolean isWriteActionRequired() {
    return false;
  }

  protected VirtualFile getFile(String path) {
    String fullPath = getTestDataPath() + path;
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(fullPath);
    assertNotNull("Can't find " + fullPath, file);
    return file;
  }

  protected void openFiles(@NotNull String femSerialisedText) throws IOException, JDOMException, InterruptedException, ExecutionException {
    Document document = JDOMUtil.loadDocument(femSerialisedText);
    Element rootElement = document.getRootElement();
    ExpandMacroToPathMap map = new ExpandMacroToPathMap();
    map.addMacroExpand(PathMacroUtil.PROJECT_DIR_MACRO_NAME, getTestDataPath());
    map.substitute(rootElement, true, true);

    myManager.loadState(rootElement);

    Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        myManager.getMainSplitters().openFiles();
      }
    });
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

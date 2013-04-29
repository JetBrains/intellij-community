package com.intellij.openapi.fileEditor;

import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.fileEditor.impl.FileEditorProviderManagerImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.ui.docking.DockManager;

/**
 * @author Dmitry Avdeev
 *         Date: 4/25/13
 */
public abstract class FileEditorManagerTestCase extends LightPlatformCodeInsightFixtureTestCase {

  protected FileEditorManagerImpl myManager;
  private FileEditorManager myOldManager;

  public void setUp() throws Exception {
    super.setUp();
    myManager = new FileEditorManagerImpl(getProject(), DockManager.getInstance(getProject()));
    myOldManager = ((ComponentManagerImpl)getProject()).registerComponentInstance(FileEditorManager.class, myManager);
  }

  @Override
  protected void tearDown() throws Exception {
    ((ComponentManagerImpl)getProject()).registerComponentInstance(FileEditorManager.class, myOldManager);
    myManager.closeAllFiles();
    ((FileEditorProviderManagerImpl)FileEditorProviderManager.getInstance()).clearSelectedProviders();
    super.tearDown();
  }

  @Override
  protected boolean isWriteActionRequired() {
    return false;
  }

  protected VirtualFile getFile(String path) {
    return LocalFileSystem.getInstance().refreshAndFindFileByPath(getTestDataPath() + path);
  }
}

/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.testFramework.fixtures.impl;

import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.idea.IdeaTestApplication;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.testFramework.EditorListenerTracker;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.ThreadTracker;
import com.intellij.testFramework.builders.ModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.HeavyIdeaTestFixture;
import com.intellij.util.PathUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;

/**
 * @author mike
 */
class HeavyIdeaTestFixtureImpl extends BaseFixture implements HeavyIdeaTestFixture {

  @NonNls private static final String PROJECT_FILE_PREFIX = "temp";
  @NonNls private static final String PROJECT_FILE_SUFFIX = ProjectFileType.DOT_DEFAULT_EXTENSION;

  private Project myProject;
  private final Set<File> myFilesToDelete = new HashSet<File>();
  private IdeaTestApplication myApplication;
  private final Set<ModuleFixtureBuilder> myModuleFixtureBuilders = new THashSet<ModuleFixtureBuilder>();
  private EditorListenerTracker myEditorListenerTracker;
  private ThreadTracker myThreadTracker;

  protected void addModuleFixtureBuilder(ModuleFixtureBuilder builder) {
    myModuleFixtureBuilders.add(builder);
  }

  public void setUp() throws Exception {
    super.setUp();

    initApplication();
    setUpProject();

    myEditorListenerTracker = new EditorListenerTracker();
    myThreadTracker = new ThreadTracker();
  }

  public void tearDown() throws Exception {
    LightPlatformTestCase.doTearDown(getProject(), myApplication, false);

    for (ModuleFixtureBuilder moduleFixtureBuilder : myModuleFixtureBuilders) {
      moduleFixtureBuilder.getFixture().tearDown();
    }

    Runnable runnable = new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            Disposer.dispose(myProject);
          }
        });
      }
    };
    if (ApplicationManager.getApplication().isDispatchThread()) {
      runnable.run();
    }
    else {
      SwingUtilities.invokeAndWait(runnable);
    }

    for (final File fileToDelete : myFilesToDelete) {
      boolean deleted = FileUtil.delete(fileToDelete);
      assert deleted : "Can't delete " + fileToDelete;
    }

    super.tearDown();

    myEditorListenerTracker.checkListenersLeak();
    myThreadTracker.checkLeak();
    LightPlatformTestCase.checkEditorsReleased();
  }


  private void setUpProject() throws Exception {
    File projectFile = File.createTempFile(PROJECT_FILE_PREFIX, PROJECT_FILE_SUFFIX);
    myFilesToDelete.add(projectFile);

    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(projectFile);
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    new Throwable(projectFile.getPath()).printStackTrace(new PrintStream(buffer));
    myProject = PlatformTestCase.createProject(projectFile, buffer.toString());

    for (ModuleFixtureBuilder moduleFixtureBuilder: myModuleFixtureBuilders) {
      moduleFixtureBuilder.getFixture().setUp();
    }

    //PropertiesReferenceManager.getInstance(myProject).projectOpened();

    StartupManagerImpl sm = (StartupManagerImpl)StartupManager.getInstance(myProject);
    sm.runStartupActivities();
    sm.runPostStartupActivities();

    ProjectManagerEx.getInstanceEx().setCurrentTestProject(myProject);
    ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(getProject())).clearUncommitedDocuments();
  }

  private void initApplication() throws Exception {
    myApplication = IdeaTestApplication.getInstance(null);
    myApplication.setDataProvider(new MyDataProvider());
  }

  public Project getProject() {
    assert myProject != null : "setUp() should be called first";
    return myProject;
  }

  public Module getModule() {
    Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    return modules.length == 0 ? null : modules[0];
  }

  private class MyDataProvider implements DataProvider {
    @Nullable
    public Object getData(@NonNls String dataId) {
      if (PlatformDataKeys.PROJECT.is(dataId)) {
        return myProject;
      }
      else if (PlatformDataKeys.EDITOR.is(dataId) || OpenFileDescriptor.NAVIGATE_IN_EDITOR.is(dataId)) {
        return FileEditorManager.getInstance(myProject).getSelectedTextEditor();
      }
      else {
        return null;
      }
    }
  }

  public PsiFile addFileToProject(@NonNls String rootPath, @NonNls final String relativePath, @NonNls final String fileText) throws IOException {
    final VirtualFile dir = VfsUtil.createDirectories(rootPath + "/" + PathUtil.getParentPath(relativePath));

    final VirtualFile[] virtualFile = new VirtualFile[1];
    new WriteCommandAction.Simple(getProject()) {
      protected void run() throws Throwable {
        virtualFile[0] = dir.createChildData(this, StringUtil.getShortName(relativePath, '/'));
        VfsUtil.saveText(virtualFile[0], fileText);
      }
    }.execute();
    return PsiManager.getInstance(getProject()).findFile(virtualFile[0]);
  }
}

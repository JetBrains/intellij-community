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

package com.intellij.testFramework.fixtures.impl;

import com.intellij.ide.IdeView;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.idea.IdeaTestApplication;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageManagerImpl;
import com.intellij.testFramework.EditorListenerTracker;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.ThreadTracker;
import com.intellij.testFramework.builders.ModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.HeavyIdeaTestFixture;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Creates new project for each test.
 * @author mike
 */
class HeavyIdeaTestFixtureImpl extends BaseFixture implements HeavyIdeaTestFixture {

  @NonNls private static final String PROJECT_FILE_SUFFIX = ProjectFileType.DOT_DEFAULT_EXTENSION;

  private Project myProject;
  private final Set<File> myFilesToDelete = new HashSet<File>();
  private IdeaTestApplication myApplication;
  private final Set<ModuleFixtureBuilder> myModuleFixtureBuilders = new LinkedHashSet<ModuleFixtureBuilder>();
  private EditorListenerTracker myEditorListenerTracker;
  private ThreadTracker myThreadTracker;
  private final String myName;

  public HeavyIdeaTestFixtureImpl(@NotNull String name) {
    myName = name;
  }

  protected void addModuleFixtureBuilder(ModuleFixtureBuilder builder) {
    myModuleFixtureBuilders.add(builder);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    initApplication();
    setUpProject();

    EncodingManager.getInstance(); // adds listeners
    myEditorListenerTracker = new EditorListenerTracker();
    myThreadTracker = new ThreadTracker();
    InjectedLanguageManagerImpl.pushInjectors(getProject());
  }

  @Override
  public void tearDown() throws Exception {
    Project project = getProject();
    LightPlatformTestCase.doTearDown(project, myApplication, false);

    for (ModuleFixtureBuilder moduleFixtureBuilder : myModuleFixtureBuilders) {
      moduleFixtureBuilder.getFixture().tearDown();
    }

    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            Disposer.dispose(myProject);
            myProject = null;
          }
        });
      }
    });

    for (final File fileToDelete : myFilesToDelete) {
      boolean deleted = FileUtil.delete(fileToDelete);
      Assert.assertTrue("Can't delete " + fileToDelete, deleted);
    }

    super.tearDown();

    myEditorListenerTracker.checkListenersLeak();
    myThreadTracker.checkLeak();
    LightPlatformTestCase.checkEditorsReleased();
    PlatformTestCase.cleanupApplicationCaches(project);
    InjectedLanguageManagerImpl.checkInjectorsAreDisposed(project);
  }


  private void setUpProject() throws Exception {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          File tempDirectory = FileUtil.createTempDirectory(myName, "");
          PlatformTestCase.synchronizeTempDirVfs(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDirectory));
          myFilesToDelete.add(tempDirectory);

          File projectFile = new File(tempDirectory, myName + PROJECT_FILE_SUFFIX);

          LocalFileSystem.getInstance().refreshAndFindFileByIoFile(projectFile);
          ByteArrayOutputStream buffer = new ByteArrayOutputStream();
          new Throwable(projectFile.getPath()).printStackTrace(new PrintStream(buffer));
          myProject = PlatformTestCase.createProject(projectFile, buffer.toString());
          ProjectManagerEx.getInstanceEx().openTestProject(myProject);

          for (ModuleFixtureBuilder moduleFixtureBuilder: myModuleFixtureBuilders) {
            moduleFixtureBuilder.getFixture().setUp();
          }

          LightPlatformTestCase.clearUncommittedDocuments(myProject);
          ((FileTypeManagerImpl)FileTypeManager.getInstance()).drainReDetectQueue();
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  private void initApplication() throws Exception {
    myApplication = IdeaTestApplication.getInstance(null);
    myApplication.setDataProvider(new MyDataProvider());
  }

  @Override
  public Project getProject() {
    Assert.assertNotNull("setUp() should be called first", myProject);
    return myProject;
  }

  @Override
  public Module getModule() {
    Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    return modules.length == 0 ? null : modules[0];
  }

  private class MyDataProvider implements DataProvider {
    @Override
    @Nullable
    public Object getData(@NonNls String dataId) {
      if (CommonDataKeys.PROJECT.is(dataId)) {
        return myProject;
      }
      else if (CommonDataKeys.EDITOR.is(dataId) || OpenFileDescriptor.NAVIGATE_IN_EDITOR.is(dataId)) {
        if (myProject == null) return null;
        return FileEditorManager.getInstance(myProject).getSelectedTextEditor();
      }
      else {
        Editor editor = (Editor)getData(CommonDataKeys.EDITOR.getName());
        if (editor != null) {
          FileEditorManagerEx manager = FileEditorManagerEx.getInstanceEx(myProject);
          return manager.getData(dataId, editor, editor.getCaretModel().getCurrentCaret());
        }
        else if (LangDataKeys.IDE_VIEW.is(dataId)) {
          VirtualFile[] contentRoots = ProjectRootManager.getInstance(myProject).getContentRoots();
          final PsiDirectory psiDirectory = PsiManager.getInstance(myProject).findDirectory(contentRoots[0]);
          if (contentRoots.length > 0) {
            return new IdeView() {
              @Override
              public void selectElement(PsiElement element) {

              }

              @NotNull
              @Override
              public PsiDirectory[] getDirectories() {
                return new PsiDirectory[] {psiDirectory};
              }

              @Override
              public PsiDirectory getOrChooseDirectory() {
                return psiDirectory;
              }
            };
          }
        }
        return null;
      }
    }
  }

  @Override
  public PsiFile addFileToProject(@NotNull @NonNls String rootPath, @NotNull @NonNls final String relativePath, @NotNull @NonNls final String fileText) throws IOException {
    final VirtualFile dir = VfsUtil.createDirectories(rootPath + "/" + PathUtil.getParentPath(relativePath));

    final VirtualFile[] virtualFile = new VirtualFile[1];
    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        virtualFile[0] = dir.createChildData(this, StringUtil.getShortName(relativePath, '/'));
        VfsUtil.saveText(virtualFile[0], fileText);
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      }
    }.execute();
    return ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
            @Override
            public PsiFile compute() {
              return PsiManager.getInstance(getProject()).findFile(virtualFile[0]);
            }
          });
  }
}

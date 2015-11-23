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
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessExtension;
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider;
import com.intellij.openapi.module.EmptyModuleType;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.WritingAccessProvider;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.EditorNotificationsImpl;
import com.intellij.util.NullableFunction;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class NonProjectFileAccessTest extends HeavyFileEditorManagerTestCase {

  private Set<VirtualFile> myOpenedFiles = new THashSet<>();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    EditorNotifications notifications = new EditorNotificationsImpl(getProject());
    ((ComponentManagerImpl)getProject()).registerComponentInstance(EditorNotifications.class, notifications);
    NonProjectFileWritingAccessProvider.enableChecksInTests(getProject(), true);
    ProjectManagerEx.getInstanceEx().blockReloadingProjectOnExternalChanges();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      NonProjectFileWritingAccessProvider.setCustomUnlocker(null);
      NonProjectFileWritingAccessProvider.enableChecksInTests(getProject(), false);
      FileEditorManager editorManager = FileEditorManager.getInstance(getProject());
      for (VirtualFile file : myOpenedFiles) {
        editorManager.closeFile(file);
      }
    }
    finally {
      super.tearDown();
      ProjectManagerEx.getInstanceEx().unblockReloadingProjectOnExternalChanges(); // unblock only after project is disposed
    }
  }

  public void testBasicAccessCheck() throws Exception {
    VirtualFile projectFile = createProjectFile();
    typeAndCheck(projectFile, true);
    typeAndCheck(projectFile, true); // still allowed

    VirtualFile nonProjectFile = createNonProjectFile();
    typeAndCheck(nonProjectFile, false);
    typeAndCheck(nonProjectFile, false);// still not allowed

    typeAndCheck(nonProjectFile, NonProjectFileWritingAccessProvider.UnlockOption.UNLOCK, true);
    typeAndCheck(nonProjectFile, null, true); // still allowed after previous Unlock
  }

  public void testAccessToProjectSystemFiles() throws Exception {
    PlatformTestUtil.saveProject(getProject());
    VirtualFile fileUnderProjectDir = new WriteAction<VirtualFile>() {
      @Override
      protected void run(@NotNull Result<VirtualFile> result) throws Throwable {
        result.setResult(getProject().getBaseDir().createChildData(this, "fileUnderProjectDir.txt"));
      }
    }.execute().getResultObject();
    
    assertFalse(ProjectFileIndex.SERVICE.getInstance(getProject()).isInContent(fileUnderProjectDir));

    typeAndCheck(getProject().getProjectFile(), true);
    typeAndCheck(getProject().getWorkspaceFile(), true);
    typeAndCheck(fileUnderProjectDir, false);
  }

  public void testAccessToModuleSystemFiles() throws Exception {
    final Module moduleWithoutContentRoot = new WriteCommandAction<Module>(getProject()) {
      @Override
      protected void run(@NotNull Result<Module> result) throws Throwable {
        String moduleName;
        ModifiableModuleModel moduleModel = ModuleManager.getInstance(getProject()).getModifiableModel();
        try {
          VirtualFile moduleDir = getProject().getBaseDir().createChildDirectory(this, "moduleWithoutContentRoot");
          moduleName = moduleModel.newModule(moduleDir.getPath() + "/moduleWithoutContentRoot.iml", EmptyModuleType.EMPTY_MODULE).getName();
          moduleModel.commit();
        }
        catch (Throwable t) {
          moduleModel.dispose();
          throw t;
        }

        result.setResult(ModuleManager.getInstance(getProject()).findModuleByName(moduleName));
      }
    }.execute().getResultObject();
    PlatformTestUtil.saveProject(getProject());

    VirtualFile fileUnderModuleDir = new WriteAction<VirtualFile>() {
      @Override
      protected void run(@NotNull Result<VirtualFile> result) throws Throwable {
        result.setResult(moduleWithoutContentRoot.getModuleFile().getParent().createChildData(this, "fileUnderModuleDir.txt"));
      }
    }.execute().getResultObject();
    
    assertFalse(ProjectFileIndex.SERVICE.getInstance(getProject()).isInContent(fileUnderModuleDir));

    typeAndCheck(moduleWithoutContentRoot.getModuleFile(), true);
    typeAndCheck(myModule.getModuleFile(), true);
    typeAndCheck(fileUnderModuleDir, false);
  }

  public void testAllowEditingInOneFileOnly() throws Exception {
    VirtualFile nonProjectFile1 = createNonProjectFile();
    VirtualFile nonProjectFile2 = createNonProjectFile();

    typeAndCheck(nonProjectFile1, NonProjectFileWritingAccessProvider.UnlockOption.UNLOCK, true);
    typeAndCheck(nonProjectFile2, null, false);

    typeAndCheck(nonProjectFile1, null, true);
    typeAndCheck(nonProjectFile2, NonProjectFileWritingAccessProvider.UnlockOption.UNLOCK, true);

    // let's check both files one more time to make sure unlock option doesn't have eny unexpected effect
    typeAndCheck(nonProjectFile1, null, true);
    typeAndCheck(nonProjectFile2, null, true);
  }

  public void testAllowEditingInAllFiles() throws Exception {
    VirtualFile nonProjectFile1 = createNonProjectFile();
    VirtualFile nonProjectFile2 = createNonProjectFile();
    VirtualFile nonProjectFile3 = createNonProjectFile();

    typeAndCheck(nonProjectFile1, false);
    typeAndCheck(nonProjectFile2, false);
    typeAndCheck(nonProjectFile3, false);

    typeAndCheck(nonProjectFile1, NonProjectFileWritingAccessProvider.UnlockOption.UNLOCK_ALL, true);
    // affects other files
    typeAndCheck(nonProjectFile2, true);
    typeAndCheck(nonProjectFile3, true);
  }

  public void testCheckingOtherWriteAccessProvidersOnUnlock() throws Exception {
    final VirtualFile nonProjectFile1 = createNonProjectFile();
    final VirtualFile nonProjectFile2 = createNonProjectFile();

    final Set<VirtualFile> requested = registerWriteAccessProvider(nonProjectFile1);

    typeAndCheck(nonProjectFile1, false);
    assertSameElements(requested); // not called since non-project file access is denied

    typeAndCheck(nonProjectFile1, NonProjectFileWritingAccessProvider.UnlockOption.UNLOCK, false);
    assertSameElements(requested, nonProjectFile1);
    
    typeAndCheck(nonProjectFile1, false); // leave file locked if other provides denied access 
    requested.clear();

    typeAndCheck(nonProjectFile2, NonProjectFileWritingAccessProvider.UnlockOption.UNLOCK, true);
    assertSameElements(requested, nonProjectFile2);
  }

  public void testCheckingOtherWriteAccessProvidersOnUnlockAll() throws Exception {
    final VirtualFile nonProjectFile1 = createNonProjectFile();
    final VirtualFile nonProjectFile2 = createNonProjectFile();

    registerWriteAccessProvider(nonProjectFile1);

    typeAndCheck(nonProjectFile1, NonProjectFileWritingAccessProvider.UnlockOption.UNLOCK_ALL, 
                 false); // can't write since denied by another write-access provider  
    typeAndCheck(nonProjectFile2, true);
  }

  public void testClearingInfoForDeletedFiles() throws Exception {
    final VirtualFile nonProjectFile1 = createNonProjectFile();
    final VirtualFile nonProjectFile2 = createNonProjectFile();

    typeAndCheck(nonProjectFile1, false);
    typeAndCheck(nonProjectFile2, false);

    assertNotNull(NonProjectFileWritingAccessProvider.getAccessStatus(getProject(), nonProjectFile1));
    assertNotNull(NonProjectFileWritingAccessProvider.getAccessStatus(getProject(), nonProjectFile2));

    new WriteAction<Object>() {
      @Override
      protected void run(@NotNull Result<Object> result) throws Throwable {
        nonProjectFile1.delete(this);
      }
    }.execute();

    assertNull(NonProjectFileWritingAccessProvider.getAccessStatus(getProject(), nonProjectFile1));
    assertNotNull(NonProjectFileWritingAccessProvider.getAccessStatus(getProject(), nonProjectFile2));
  }

  public void testUpdatingNotificationsOnRootChanges() throws Exception {
    VirtualFile nonProjectFile1 = createNonProjectFile();
    VirtualFile nonProjectFile2 = createNonProjectFile();

    typeAndCheck(nonProjectFile1, false);
    typeAndCheck(nonProjectFile2, false);

    assertNotNull(NonProjectFileWritingAccessProvider.getAccessStatus(getProject(), nonProjectFile1));
    assertNotNull(NonProjectFileWritingAccessProvider.getAccessStatus(getProject(), nonProjectFile2));

    ContentEntry contextRoot = PsiTestUtil.addContentRoot(myModule, nonProjectFile2.getParent());
    
    // removing notification panel for newly added files
    assertNotNull(NonProjectFileWritingAccessProvider.getAccessStatus(getProject(), nonProjectFile1));
    assertNull(NonProjectFileWritingAccessProvider.getAccessStatus(getProject(), nonProjectFile2));

    PsiTestUtil.removeContentEntry(myModule, contextRoot.getFile());

    // do not add notification panel until access is requested
    assertNotNull(NonProjectFileWritingAccessProvider.getAccessStatus(getProject(), nonProjectFile1));
    assertNull(NonProjectFileWritingAccessProvider.getAccessStatus(getProject(), nonProjectFile2));

    // but files are still not writable
    typeAndCheck(nonProjectFile1, false);
    typeAndCheck(nonProjectFile2, false);
    assertNotNull(NonProjectFileWritingAccessProvider.getAccessStatus(getProject(), nonProjectFile1));
    assertNotNull(NonProjectFileWritingAccessProvider.getAccessStatus(getProject(), nonProjectFile2));
  }

  public void testCheckingExtensions() throws Exception {
    VirtualFile nonProjectFile1 = createNonProjectFile();
    VirtualFile nonProjectFile2 = createNonProjectFile();

    typeAndCheck(nonProjectFile1, false);
    typeAndCheck(nonProjectFile2, false);
    
    List<VirtualFile> allowed = new ArrayList<VirtualFile>();
    registerAccessCheckExtension(allowed);

    typeAndCheck(nonProjectFile1, false);
    typeAndCheck(nonProjectFile2, false);

    allowed.add(nonProjectFile1);
    typeAndCheck(nonProjectFile1, true);
    typeAndCheck(nonProjectFile2, false);

    allowed.clear();
    allowed.add(nonProjectFile2);
    typeAndCheck(nonProjectFile1, false);
    typeAndCheck(nonProjectFile2, true);

    allowed.clear();
    typeAndCheck(nonProjectFile1, false);
    typeAndCheck(nonProjectFile2, false);
  }

  private Set<VirtualFile> registerWriteAccessProvider(final VirtualFile... filesToDeny) {
    final Set<VirtualFile> requested = new LinkedHashSet<VirtualFile>();
    PlatformTestUtil.registerExtension(Extensions.getArea(getProject()), WritingAccessProvider.EP_NAME, new WritingAccessProvider() {
      @NotNull
      @Override
      public Collection<VirtualFile> requestWriting(VirtualFile... files) {
        Collections.addAll(requested, files);
        HashSet<VirtualFile> denied = new HashSet<VirtualFile>(Arrays.asList(filesToDeny));
        denied.retainAll(Arrays.asList(files));
        return denied;
      }

      @Override
      public boolean isPotentiallyWritable(@NotNull VirtualFile file) {
        return true;
      }
    }, myTestRootDisposable);
    return requested;
  }

  private void registerAccessCheckExtension(final Collection<VirtualFile> filesToAllow) {
    PlatformTestUtil.registerExtension(Extensions.getArea(getProject()), NonProjectFileWritingAccessExtension.EP_NAME,
                                       new NonProjectFileWritingAccessExtension() {
                                         @Override
                                         public boolean isWritable(@NotNull VirtualFile file) {
                                           return filesToAllow.contains(file);
                                         }
                                       },
                                       myTestRootDisposable);
  }

  @NotNull
  private VirtualFile createProjectFile() {
    return new WriteAction<VirtualFile>() {
      @Override
      protected void run(@NotNull Result<VirtualFile> result) throws Throwable {
        result.setResult(ModuleRootManager.getInstance(myModule).getContentRoots()[0].createChildData(this, "projectFile.txt"));
      }
    }.execute().getResultObject();
  }

  @NotNull
  private VirtualFile createNonProjectFile() {
    return new WriteAction<VirtualFile>() {
      @Override
      protected void run(@NotNull Result<VirtualFile> result) throws Throwable {
        VirtualFile tmp = LocalFileSystem.getInstance().findFileByIoFile(FileUtil.createTempDirectory("tmp", null));
        result.setResult(tmp.createChildData(this, "nonProjectFile.txt"));
      }
    }.execute().getResultObject();
  }

  private void typeAndCheck(VirtualFile file, boolean fileHasBeenChanged) {
    typeAndCheck(file, null, fileHasBeenChanged);
  }

  private void typeAndCheck(VirtualFile file,
                            @Nullable final NonProjectFileWritingAccessProvider.UnlockOption option,
                            boolean fileHasBeenChanged) {
    Editor editor = getEditor(file);

    NullableFunction<List<VirtualFile>, NonProjectFileWritingAccessProvider.UnlockOption> unlocker =
      new NullableFunction<List<VirtualFile>, NonProjectFileWritingAccessProvider.UnlockOption>() {
        @Nullable
        @Override
        public NonProjectFileWritingAccessProvider.UnlockOption fun(List<VirtualFile> files) {
          return option;
        }
      };
    NonProjectFileWritingAccessProvider.setCustomUnlocker(unlocker);

    String before = editor.getDocument().getText();
    typeInChar(editor, 'a');

    if (fileHasBeenChanged) {
      assertEquals("Text should be changed", 'a' + before, editor.getDocument().getText());
    }
    else {
      assertEquals("Text should not be changed", before, editor.getDocument().getText());
    }
  }

  private Editor getEditor(VirtualFile file) {
    myOpenedFiles.add(file);
    return FileEditorManager.getInstance(getProject()).openTextEditor(new OpenFileDescriptor(getProject(), file, 0), false);
  }

  protected void typeInChar(Editor e, char c) {
    getActionManager().getTypedAction().actionPerformed(e, c, createDataContextFor(e));
  }

  private DataContext createDataContextFor(final Editor editor) {
    return new DataContext() {
      @Override
      public Object getData(String dataId) {
        if (dataId.equals(DataConstants.EDITOR)) return editor;
        if (dataId.equals(DataConstants.PROJECT)) return getProject();
        return null;
      }
    };
  }

  private static EditorActionManager getActionManager() {
    return EditorActionManager.getInstance();
  }
}

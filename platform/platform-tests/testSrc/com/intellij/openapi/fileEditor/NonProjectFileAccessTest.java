/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessExtension;
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider;
import com.intellij.openapi.module.EmptyModuleType;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.WritingAccessProvider;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.EditorNotificationsImpl;
import com.intellij.util.NullableFunction;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class NonProjectFileAccessTest extends HeavyFileEditorManagerTestCase {
  private final Set<VirtualFile> myCreatedFiles = new THashSet<>();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    EditorNotifications notifications = new EditorNotificationsImpl(getProject());
    ((ComponentManagerImpl)getProject()).registerComponentInstance(EditorNotifications.class, notifications);
    NonProjectFileWritingAccessProvider.enableChecksInTests(getProject());
    ProjectManagerEx.getInstanceEx().blockReloadingProjectOnExternalChanges();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      NonProjectFileWritingAccessProvider.setCustomUnlocker(null);
      FileEditorManagerEx.getInstanceEx(getProject()).closeAllFiles();
      ApplicationManager.getApplication().runWriteAction(() -> {
        for (VirtualFile each : myCreatedFiles) {
          try {
            each.delete(this);
          }
          catch (IOException e) {
            e.printStackTrace();
          }
        }
      });
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

  public void testDoNotLockCreatedAndCopiedFiles() throws Exception {
    VirtualFile nonProjectFile = createNonProjectFile();
    typeAndCheck(nonProjectFile, false);

    final VirtualFile[] createdNonProject = new VirtualFile[1];
    final VirtualFile[] copiedNonProject = new VirtualFile[1];

    new WriteAction<Object>() {
      @Override
      protected void run(@NotNull Result<Object> result) throws Throwable {
        createdNonProject[0] = nonProjectFile.getParent().createChildData(this, "createdNonProject.txt");
        copiedNonProject[0] = nonProjectFile.copy(this, nonProjectFile.getParent(), "copiedNonProject.txt");
        myCreatedFiles.add(createdNonProject[0]);
        myCreatedFiles.add(copiedNonProject[0]);
      }
    }.execute();

    typeAndCheck(createdNonProject[0], true); 
    typeAndCheck(copiedNonProject[0], true); 
    
    typeAndCheck(nonProjectFile, false); // original is still locked 
  }

  public void testAccessToProjectSystemFiles() {
    PlatformTestUtil.saveProject(getProject());
    VirtualFile fileUnderProjectDir = createFileExternally(new File(getProject().getBaseDir().getPath()));
    
    assertFalse(ProjectFileIndex.SERVICE.getInstance(getProject()).isInContent(fileUnderProjectDir));

    typeAndCheck(getProject().getProjectFile(), true);
    typeAndCheck(getProject().getWorkspaceFile(), true);
    typeAndCheck(fileUnderProjectDir, false);
  }

  public void testAccessToModuleSystemFiles() {
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

    VirtualFile fileUnderNonProjectModuleDir 
      = createFileExternally(new File(moduleWithoutContentRoot.getModuleFile().getParent().getPath()));
    
    assertFalse(ProjectFileIndex.SERVICE.getInstance(getProject()).isInContent(fileUnderNonProjectModuleDir));

    typeAndCheck(moduleWithoutContentRoot.getModuleFile(), true);
    typeAndCheck(myModule.getModuleFile(), true);
    typeAndCheck(fileUnderNonProjectModuleDir, false);
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

  public void testAllowEditingFileInDirectory() throws Exception {
    VirtualFile nonProjectFileDir11 = createNonProjectFile();
    File dir = new File(nonProjectFileDir11.getParent().getPath());
    VirtualFile nonProjectFileDir12 = createFileExternally(dir);

    File subDir = new File(dir, "subdir");
    assertTrue(subDir.mkdirs());
    VirtualFile nonProjectFileDirSubdir1 = createFileExternally(subDir);
    
    VirtualFile nonProjectFileDir2 = createNonProjectFile();

    typeAndCheck(nonProjectFileDir11, false);
    typeAndCheck(nonProjectFileDir12, false);
    typeAndCheck(nonProjectFileDir2, false);

    typeAndCheck(nonProjectFileDir11, NonProjectFileWritingAccessProvider.UnlockOption.UNLOCK_DIR, true);
    
    // affects other files in dir
    typeAndCheck(nonProjectFileDir12, true);
    typeAndCheck(nonProjectFileDirSubdir1, true);
    
    // doesn't affect files in other dirs
    typeAndCheck(nonProjectFileDir2, false);
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
  
  public void testCheckingExtensionsForWritableFiles() throws Exception {
    VirtualFile nonProjectFile1 = createNonProjectFile();
    VirtualFile nonProjectFile2 = createNonProjectFile();

    typeAndCheck(nonProjectFile1, false);
    typeAndCheck(nonProjectFile2, false);
    
    List<VirtualFile> allowed = new ArrayList<>();
    registerAccessCheckExtension(allowed, Collections.emptyList());

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
  
  public void testCheckingExtensionsForNonWritableFiles() {
    VirtualFile nonProjectFile1 = createProjectFile();
    VirtualFile nonProjectFile2 = createProjectFile();

    typeAndCheck(nonProjectFile1, true);
    typeAndCheck(nonProjectFile2, true);
    
    List<VirtualFile> denied = new ArrayList<>();
    registerAccessCheckExtension(Collections.emptyList(), denied);

    typeAndCheck(nonProjectFile1, true);
    typeAndCheck(nonProjectFile2, true);

    denied.add(nonProjectFile1);
    typeAndCheck(nonProjectFile1, false);
    typeAndCheck(nonProjectFile2, true);

    denied.clear();
    denied.add(nonProjectFile2);
    typeAndCheck(nonProjectFile1, true);
    typeAndCheck(nonProjectFile2, false);

    denied.clear();
    typeAndCheck(nonProjectFile1, true);
    typeAndCheck(nonProjectFile2, true);
  }

  private Set<VirtualFile> registerWriteAccessProvider(final VirtualFile... filesToDeny) {
    final Set<VirtualFile> requested = new LinkedHashSet<>();
    PlatformTestUtil.registerExtension(Extensions.getArea(getProject()), WritingAccessProvider.EP_NAME, new WritingAccessProvider() {
      @NotNull
      @Override
      public Collection<VirtualFile> requestWriting(VirtualFile... files) {
        Collections.addAll(requested, files);
        HashSet<VirtualFile> denied = new HashSet<>(Arrays.asList(filesToDeny));
        denied.retainAll(Arrays.asList(files));
        return denied;
      }

      @Override
      public boolean isPotentiallyWritable(@NotNull VirtualFile file) {
        return true;
      }
    }, getProject());
    return requested;
  }

  private void registerAccessCheckExtension(Collection<VirtualFile> filesToAllow, Collection<VirtualFile> filesToDeny) {
    PlatformTestUtil.registerExtension(Extensions.getArea(getProject()), NonProjectFileWritingAccessExtension.EP_NAME,
                                       new NonProjectFileWritingAccessExtension() {
                                         @Override
                                         public boolean isWritable(@NotNull VirtualFile file) {
                                           return filesToAllow.contains(file);
                                         }

                                         @Override
                                         public boolean isNotWritable(@NotNull VirtualFile file) {
                                           return filesToDeny.contains(file);
                                         }
                                       },
                                       getProject());
  }

  @NotNull
  private VirtualFile createProjectFile() {
    return createFileExternally(new File(ModuleRootManager.getInstance(myModule).getContentRoots()[0].getPath()));
  }

  @NotNull
  private VirtualFile createNonProjectFile() throws IOException {
    return createFileExternally(FileUtil.createTempDirectory("tmp", null));
  }
  
  @NotNull
  private VirtualFile createFileExternally(File dir) {
    VirtualFile result = new WriteAction<VirtualFile>() {
      @Override
      protected void run(@NotNull Result<VirtualFile> result) throws Throwable {
        // create externally, since files created via VFS are marked for editing automatically
        File file = new File(dir, FileUtil.createSequentFileName(dir, "extfile", "txt"));
        assertTrue(file.createNewFile());
        result.setResult(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file));
      }
    }.execute().getResultObject();
    myCreatedFiles.add(result);
    return result;
  }

  private void typeAndCheck(VirtualFile file, boolean fileHasBeenChanged) {
    typeAndCheck(file, null, fileHasBeenChanged);
  }

  private void typeAndCheck(VirtualFile file,
                            @Nullable final NonProjectFileWritingAccessProvider.UnlockOption option,
                            boolean fileHasBeenChanged) {
    Editor editor = getEditor(file);

    NullableFunction<List<VirtualFile>, NonProjectFileWritingAccessProvider.UnlockOption> unlocker =
      files -> option;
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
    Editor editor = FileEditorManager.getInstance(getProject()).openTextEditor(new OpenFileDescriptor(getProject(), file, 0), false);
    EditorTestUtil.waitForLoading(editor);
    return editor;
  }

  private void typeInChar(Editor e, char c) {
    getActionManager().getTypedAction().actionPerformed(e, c, createDataContextFor(e));
  }

  private DataContext createDataContextFor(final Editor editor) {
    return dataId -> {
      if (dataId.equals(CommonDataKeys.EDITOR.getName())) return editor;
      if (dataId.equals(CommonDataKeys.PROJECT.getName())) return getProject();
      return null;
    };
  }

  private static EditorActionManager getActionManager() {
    return EditorActionManager.getInstance();
  }
}

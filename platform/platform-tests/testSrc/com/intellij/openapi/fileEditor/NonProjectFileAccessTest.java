// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor;

import com.intellij.configurationStore.StoreReloadManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessExtension;
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider;
import com.intellij.openapi.module.EmptyModuleType;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.WritingAccessProvider;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.EditorNotificationsImpl;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class NonProjectFileAccessTest extends HeavyFileEditorManagerTestCase {
  private final Set<VirtualFile> myCreatedFiles = new HashSet<>();

  @Override
  public void setUp() throws Exception {
    super.setUp();

    EditorNotifications notifications = new EditorNotificationsImpl(getProject());
    ServiceContainerUtil.replaceService(getProject(), EditorNotifications.class, notifications, getTestRootDisposable());
    NonProjectFileWritingAccessProvider.enableChecksInTests(getProject());
    StoreReloadManager.getInstance().blockReloadingProjectOnExternalChanges();
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
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
      StoreReloadManager.getInstance().unblockReloadingProjectOnExternalChanges(); // unblock only after project is disposed;
    }
  }

  public void testBasicAccessCheck() throws Exception {
    VirtualFile projectFile = createProjectFile();
    typeAndCheck(projectFile, true);
    typeAndCheck(projectFile, true); // still allowed

    VirtualFile nonProjectFile = createNonProjectFile();
    typeAndCheck(nonProjectFile, false);
    typeAndCheck(nonProjectFile, false);// still not allowed

    typeAndCheck(nonProjectFile, UnlockOption.UNLOCK, true);
    typeAndCheck(nonProjectFile, null, true); // still allowed after previous Unlock
  }

  public void testDoNotLockCreatedAndCopiedFiles() throws Exception {
    VirtualFile nonProjectFile = createNonProjectFile();
    typeAndCheck(nonProjectFile, false);

    final VirtualFile[] createdNonProject = new VirtualFile[1];
    final VirtualFile[] copiedNonProject = new VirtualFile[1];

    WriteAction.runAndWait(() -> {
      createdNonProject[0] = nonProjectFile.getParent().createChildData(this, "createdNonProject.txt");
      copiedNonProject[0] = nonProjectFile.copy(this, nonProjectFile.getParent(), "copiedNonProject.txt");
      myCreatedFiles.add(createdNonProject[0]);
      myCreatedFiles.add(copiedNonProject[0]);
    });

    typeAndCheck(createdNonProject[0], true);
    typeAndCheck(copiedNonProject[0], true);

    typeAndCheck(nonProjectFile, false); // original is still locked
  }

  public void testDoNotLockExcludedFiles() throws Exception {
    VirtualFile excludedFile = WriteAction.computeAndWait(() -> {
      VirtualFile excludedDir = ModuleRootManager.getInstance(myModule).getContentRoots()[0].createChildDirectory(this, "excluded");
      PsiTestUtil.addExcludedRoot(myModule, excludedDir);

      return createFileExternally(new File(excludedDir.getPath()));
    });

    typeAndCheck(excludedFile, true);
  }

  public void testAccessToProjectSystemFiles() {
    PlatformTestUtil.saveProject(getProject(), true);
    VirtualFile fileUnderProjectDir = createFileExternally(new File(getProject().getBasePath()));

    assertFalse(ProjectFileIndex.getInstance(getProject()).isInContent(fileUnderProjectDir));

    typeAndCheck(getProject().getProjectFile(), true);
    typeAndCheck(getProject().getWorkspaceFile(), true);
    typeAndCheck(fileUnderProjectDir, false);
  }

  public void testAccessToModuleSystemFiles() throws IOException {
    final Module moduleWithoutContentRoot = WriteCommandAction.writeCommandAction(getProject()).compute(() -> {
      String moduleName;
      ModifiableModuleModel moduleModel = ModuleManager.getInstance(getProject()).getModifiableModel();
      try {
        VirtualFile moduleDir = VfsUtil.createDirectoryIfMissing(getProject().getBasePath() + "/moduleWithoutContentRoot");
        moduleName = moduleModel.newModule(moduleDir.toNioPath().resolve("moduleWithoutContentRoot.iml"), EmptyModuleType.EMPTY_MODULE).getName();
        moduleModel.commit();
      }
      catch (Throwable t) {
        moduleModel.dispose();
        throw t;
      }
      return ModuleManager.getInstance(getProject()).findModuleByName(moduleName);
    });
    PlatformTestUtil.saveProject(getProject());

    VirtualFile fileUnderNonProjectModuleDir = createFileExternally(moduleWithoutContentRoot.getModuleNioFile().getParent().toFile());

    assertFalse(ProjectFileIndex.getInstance(getProject()).isInContent(fileUnderNonProjectModuleDir));

    typeAndCheck(moduleWithoutContentRoot.getModuleFile(), true);
    typeAndCheck(myModule.getModuleFile(), true);
    typeAndCheck(fileUnderNonProjectModuleDir, false);
  }

  public void testAllowEditingInOneFileOnly() throws Exception {
    VirtualFile nonProjectFile1 = createNonProjectFile();
    VirtualFile nonProjectFile2 = createNonProjectFile();

    typeAndCheck(nonProjectFile1, UnlockOption.UNLOCK, true);
    typeAndCheck(nonProjectFile2, null, false);

    typeAndCheck(nonProjectFile1, null, true);
    typeAndCheck(nonProjectFile2, UnlockOption.UNLOCK, true);

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

    typeAndCheck(nonProjectFileDir11, UnlockOption.UNLOCK_DIR, true);

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

    typeAndCheck(nonProjectFile1, UnlockOption.UNLOCK_ALL, true);
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

    typeAndCheck(nonProjectFile1, UnlockOption.UNLOCK, false);
    assertSameElements(requested, nonProjectFile1);

    typeAndCheck(nonProjectFile1, false); // leave file locked if other provides denied access
    requested.clear();

    typeAndCheck(nonProjectFile2, UnlockOption.UNLOCK, true);
    assertSameElements(requested, nonProjectFile2);
  }

  public void testCheckingOtherWriteAccessProvidersOnUnlockAll() throws Exception {
    final VirtualFile nonProjectFile1 = createNonProjectFile();
    final VirtualFile nonProjectFile2 = createNonProjectFile();

    registerWriteAccessProvider(nonProjectFile1);

    typeAndCheck(nonProjectFile1, UnlockOption.UNLOCK_ALL,
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

  public void testEditingRecentFilesRegardlessExtensions() {
    NonProjectFileWritingAccessProvider.enableChecksInTests(true, getProject());

    VirtualFile nonProjectFile = createProjectFile();

    List<VirtualFile> denied = new ArrayList<>();
    registerAccessCheckExtension(Collections.emptyList(), denied);

    denied.add(nonProjectFile);
    typeAndCheck(nonProjectFile, false);

    denied.clear();
    typeAndCheck(nonProjectFile, true);

    denied.add(nonProjectFile);
    typeAndCheck(nonProjectFile, true); // still can edit since it's a recently edited file
  }

  private Set<VirtualFile> registerWriteAccessProvider(final VirtualFile... filesToDeny) {
    final Set<VirtualFile> requested = new LinkedHashSet<>();
    ServiceContainerUtil.registerExtension(getProject(), WritingAccessProvider.EP, new WritingAccessProvider() {
      @NotNull
      @Override
      public Collection<VirtualFile> requestWriting(@NotNull Collection<? extends VirtualFile> files) {
        requested.addAll(files);
        Set<VirtualFile> denied = ContainerUtil.newHashSet(filesToDeny);
        denied.retainAll(files);
        return denied;
      }
    }, getProject());
    return requested;
  }

  private void registerAccessCheckExtension(Collection<VirtualFile> filesToAllow, Collection<VirtualFile> filesToDeny) {
    ServiceContainerUtil.registerExtension(getProject(), NonProjectFileWritingAccessExtension.EP_NAME,
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
    VirtualFile result;
    try {
      result = WriteAction.computeAndWait(() -> {
        // create externally, since files created via VFS are marked for editing automatically
        File file = new File(dir, FileUtil.createSequentFileName(dir, "extfile", "txt"));
        assertTrue(file.createNewFile());
        return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
      });
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    myCreatedFiles.add(result);
    return result;
  }

  private void typeAndCheck(VirtualFile file, boolean fileHasBeenChanged) {
    typeAndCheck(file, null, fileHasBeenChanged);
  }

  private void typeAndCheck(VirtualFile file,
                            @Nullable final UnlockOption option,
                            boolean fileHasBeenChanged) {
    Editor editor = getEditor(file);

    NullableFunction<List<VirtualFile>, UnlockOption> unlocker =
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
    getActionManager();
    TypedAction.getInstance().actionPerformed(e, c, createDataContextFor(e));
  }

  private DataContext createDataContextFor(final Editor editor) {
    return dataId -> {
      if (CommonDataKeys.EDITOR.is(dataId)) return editor;
      if (CommonDataKeys.PROJECT.is(dataId)) return getProject();
      return null;
    };
  }

  private static EditorActionManager getActionManager() {
    return EditorActionManager.getInstance();
  }
}

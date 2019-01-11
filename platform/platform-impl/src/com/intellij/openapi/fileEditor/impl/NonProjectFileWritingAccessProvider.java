// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.project.ProjectKt;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class NonProjectFileWritingAccessProvider extends WritingAccessProvider {
  private static final Key<Boolean> ENABLE_IN_TESTS = Key.create("NON_PROJECT_FILE_ACCESS_ENABLE_IN_TESTS");
  private static final Key<Boolean> HONOUR_RECENT_FILES_IN_TESTS = Key.create("NON_PROJECT_FILE_ACCESS_HONOUR_RECENT_FILES_IN_TESTS");

  private static final NotNullLazyKey<AtomicInteger, UserDataHolder> ACCESS_ALLOWED
    = NotNullLazyKey.create("NON_PROJECT_FILE_ACCESS", holder -> new AtomicInteger());

  private static final AtomicBoolean myInitialized = new AtomicBoolean();

  @NotNull private final Project myProject;
  @Nullable private static NullableFunction<? super List<VirtualFile>, UnlockOption> ourCustomUnlocker;

  @TestOnly
  public static void setCustomUnlocker(@Nullable NullableFunction<? super List<VirtualFile>, UnlockOption> unlocker) {
    ourCustomUnlocker = unlocker;
  }

  public NonProjectFileWritingAccessProvider(@NotNull Project project) {
    myProject = project;

    if (myInitialized.compareAndSet(false, true)) {
      VirtualFileManager.getInstance().addVirtualFileListener(new OurVirtualFileListener());
    }
  }

  @NotNull
  @Override
  public Collection<VirtualFile> requestWriting(@NotNull Collection<? extends VirtualFile> files) {
    if (isAllAccessAllowed()) return Collections.emptyList();

    List<VirtualFile> deniedFiles = new ArrayList<>();
    for (VirtualFile o : files) {
      if (!isWriteAccessAllowed(o, myProject)) {
        deniedFiles.add(o);
      }
    }
    if (deniedFiles.isEmpty()) {
      return Collections.emptyList();
    }

    UnlockOption unlockOption = askToUnlock(deniedFiles);

    if (unlockOption == null) return deniedFiles;

    switch (unlockOption) {
      case UNLOCK:
        allowWriting(deniedFiles);
        break;
      case UNLOCK_DIR:
        allowWriting(ContainerUtil.map(deniedFiles, VirtualFile::getParent));
        break;
      case UNLOCK_ALL:
        ACCESS_ALLOWED.getValue(getApp()).incrementAndGet();
        break;
    }

    return Collections.emptyList();
  }

  @Nullable
  private UnlockOption askToUnlock(@NotNull List<VirtualFile> files) {
    if (ourCustomUnlocker != null) return ourCustomUnlocker.fun(files);

    NonProjectFileWritingAccessDialog dialog = new NonProjectFileWritingAccessDialog(myProject, files);
    if (!dialog.showAndGet()) return null;
    return dialog.getUnlockOption();
  }

  public static boolean isWriteAccessAllowed(@NotNull VirtualFile file, @NotNull Project project) {
    if (isAllAccessAllowed()) return true;
    if (file.isDirectory()) return true;

    if (!(file.getFileSystem() instanceof LocalFileSystem)) return true; // do not block e.g., HttpFileSystem, LightFileSystem etc.
    if (file.getFileSystem() instanceof TempFileSystem) return true;

    Application application = getApp();
    boolean unitTestMode = application.isUnitTestMode();

    if (!unitTestMode || HONOUR_RECENT_FILES_IN_TESTS.get(application) == Boolean.TRUE) {
      IdeDocumentHistoryImpl documentHistory = (IdeDocumentHistoryImpl)IdeDocumentHistory.getInstance(project);
      if (documentHistory.isRecentlyChanged(file)) return true;
    }

    if (!unitTestMode) {
      if (FileUtil.isAncestor(new File(FileUtil.getTempDirectory()), VfsUtilCore.virtualToIoFile(file), true)) {
        return true;
      }
    }

    VirtualFile each = file;
    while (each != null) {
      if (ACCESS_ALLOWED.getValue(each).get() > 0) return true;
      each = each.getParent();
    }

    return isProjectFile(file, project);
  }

  private static boolean isProjectFile(@NotNull VirtualFile file, @NotNull Project project) {
    for (NonProjectFileWritingAccessExtension each : NonProjectFileWritingAccessExtension.EP_NAME.getExtensions(project)) {
      if(each.isWritable(file)) return true;
      if(each.isNotWritable(file)) return false;
    }

    ProjectFileIndex fileIndex = ProjectFileIndex.SERVICE.getInstance(project);
    if (fileIndex.isInContent(file)) return true;
    if (!Registry.is("ide.hide.excluded.files") && fileIndex.isExcluded(file) && !fileIndex.isUnderIgnored(file)) return true;

    if (project instanceof ProjectEx && !project.isDefault()) {
      if (ProjectKt.getStateStore(project).isProjectFile(file)) {
        return true;
      }

      String filePath = file.getPath();
      for (Module module : ModuleManager.getInstance(project).getModules()) {
        if (FileUtil.namesEqual(filePath, module.getModuleFilePath())) {
          return true;
        }
      }
    }
    return false;
  }

  @Deprecated
  public static void allowWriting(VirtualFile... allowedFiles) {
    allowWriting(Arrays.asList(allowedFiles));
  }

  public static void allowWriting(Iterable<? extends VirtualFile> allowedFiles) {
    for (VirtualFile eachAllowed : allowedFiles) {
      ACCESS_ALLOWED.getValue(eachAllowed).incrementAndGet();
    }
  }

  public static void disableChecksDuring(@NotNull Runnable runnable) {
    Application app = getApp();
    ACCESS_ALLOWED.getValue(app).incrementAndGet();
    try {
      runnable.run();
    }
    finally {
      ACCESS_ALLOWED.getValue(app).decrementAndGet();
    }
  }

  @TestOnly
  public static void enableChecksInTests(@NotNull Disposable disposable) {
    enableChecksInTests(false, disposable);
  }

  @TestOnly
  public static void enableChecksInTests(@Nullable Boolean honourRecentFiles, @NotNull Disposable disposable) {
    getApp().putUserData(ENABLE_IN_TESTS, Boolean.TRUE);
    getApp().putUserData(HONOUR_RECENT_FILES_IN_TESTS, honourRecentFiles);
    getApp().putUserData(ACCESS_ALLOWED, null);

    Disposer.register(disposable, () -> {
      getApp().putUserData(ENABLE_IN_TESTS, null);
      getApp().putUserData(HONOUR_RECENT_FILES_IN_TESTS, null);
      getApp().putUserData(ACCESS_ALLOWED, null);
    });
  }

  private static boolean isAllAccessAllowed() {
    Application app = getApp();

    // disable checks in tests, if not asked
    if (app.isUnitTestMode() && app.getUserData(ENABLE_IN_TESTS) != Boolean.TRUE) {
      return true;
    }
    return ACCESS_ALLOWED.getValue(app).get() > 0;
  }

  private static Application getApp() {
    return ApplicationManager.getApplication();
  }

  public enum UnlockOption {UNLOCK, UNLOCK_DIR, UNLOCK_ALL}

  private static class OurVirtualFileListener implements VirtualFileListener {
    @Override
    public void fileCreated(@NotNull VirtualFileEvent event) {
      unlock(event);
    }

    @Override
    public void fileCopied(@NotNull VirtualFileCopyEvent event) {
      unlock(event);
    }

    private static void unlock(@NotNull VirtualFileEvent event) {
      if (!event.isFromRefresh() && !event.getFile().isDirectory()) allowWriting(Collections.singletonList(event.getFile()));
    }
  }
}

// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ignore;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsApplicationSettings;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.IgnoredFileContentProvider;
import com.intellij.openapi.vcs.changes.IgnoredFileGenerator;
import com.intellij.openapi.vcs.changes.IgnoredFileProvider;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.vcsUtil.VcsImplUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static com.intellij.openapi.vcs.changes.ignore.IgnoreConfigurationProperty.ASKED_MANAGE_IGNORE_FILES_PROPERTY;
import static com.intellij.openapi.vcs.changes.ignore.IgnoreConfigurationProperty.MANAGE_IGNORE_FILES_PROPERTY;
import static java.lang.System.lineSeparator;

public class IgnoredFileGeneratorImpl implements IgnoredFileGenerator {

  private static final Logger LOG = Logger.getInstance(IgnoredFileGeneratorImpl.class);

  private final Project myProject;

  private final Object myWriteLock = new Object();

  private static final Object myNotificationLock = new Object();

  @Nullable
  private static Notification myNotification;

  @Nullable
  private static VirtualFile myIgnoreFileRootNotificationShowFor;

  protected IgnoredFileGeneratorImpl(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void generateFile(@NotNull VirtualFile ignoreFileRoot, @NotNull AbstractVcs vcs, boolean notify) {
    doGenerate(ignoreFileRoot, vcs, notify);
  }

  private void doGenerate(@NotNull VirtualFile ignoreFileRoot, @NotNull AbstractVcs vcs, boolean notify) {
    if (!needGenerateIgnoreFile(myProject, ignoreFileRoot)) {
      LOG.debug("Skip VCS ignore file generation");
      return;
    }

    IgnoredFileContentProvider ignoredFileContentProvider = VcsImplUtil.getIgnoredFileContentProvider(myProject, vcs);
    if (ignoredFileContentProvider == null) {
      LOG.debug("Cannot find content provider for vcs " + vcs.getName());
      return;
    }

    String ignoreFileName = ignoredFileContentProvider.getFileName();

    synchronized (myWriteLock) {
      String ignoreFileContent =
        ignoredFileContentProvider.buildIgnoreFileContent(ignoreFileRoot, IgnoredFileProvider.IGNORE_FILE.getExtensions());
      if (StringUtil.isEmptyOrSpaces(ignoreFileContent)) return;

      File ignoreFile = getIgnoreFile(ignoreFileRoot, ignoreFileName);

      if (notify && needAskToManageIgnoreFiles(myProject)) {
        notifyVcsIgnoreFileManage(myProject, ignoreFileRoot, ignoredFileContentProvider,
                                  () -> writeToFile(ignoreFileRoot, ignoreFile, ignoreFileContent, true));
      }
      else {
        writeToFile(ignoreFileRoot, ignoreFile, ignoreFileContent, false);
      }
    }
  }

  private void writeToFile(@NotNull VirtualFile ignoreFileRoot, @NotNull File ignoreFile, @NotNull String ignoreFileContent, boolean openFile) {
    boolean append = ignoreFile.exists();
    String projectCharsetName = EncodingProjectManager.getInstance(myProject).getDefaultCharsetName();
    try {
      if (append) {
        FileUtil.writeToFile(ignoreFile, (lineSeparator() + ignoreFileContent).getBytes(projectCharsetName), true);
      }
      else {
        //create ignore file with VFS to prevent externally added files detection
        WriteAction.runAndWait(() -> {
          VirtualFile newIgnoreFile = ignoreFileRoot.createChildData(ignoreFileRoot, ignoreFile.getName());
          VfsUtil.saveText(newIgnoreFile, ignoreFileContent);
        });
      }
    }
    catch (IOException e) {
      LOG.warn("Cannot write to file " + ignoreFile.getPath());
    }
    IgnoredFileRootStore.getInstance(myProject).addRoot(ignoreFile.getParent());
    LocalFileSystem.getInstance().refreshIoFiles(Collections.singleton(ignoreFile));
    if (openFile) {
      openFile(ignoreFile);
    }
  }

  private void openFile(@NotNull File file) {
    ApplicationManager.getApplication().invokeLater(() -> {
      VirtualFile vFile = VfsUtil.findFileByIoFile(file, true);
      if (vFile == null) return;
      new OpenFileDescriptor(myProject, vFile).navigate(true);
    });
  }

  private static void notifyVcsIgnoreFileManage(@NotNull Project project,
                                                @NotNull VirtualFile ignoreFileRoot,
                                                @NotNull IgnoredFileContentProvider ignoredFileContentProvider,
                                                @NotNull Runnable writeToIgnoreFile) {
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(project);
    VcsApplicationSettings applicationSettings = VcsApplicationSettings.getInstance();

    synchronized (myNotificationLock) {
      if (myNotification != null &&
          myIgnoreFileRootNotificationShowFor != null &&
          !myNotification.isExpired() &&
          myIgnoreFileRootNotificationShowFor.equals(ignoreFileRoot)) {
        return;
      }

      myIgnoreFileRootNotificationShowFor = ignoreFileRoot;
      myNotification = VcsNotifier.getInstance(project).notifyMinorInfo(
        true,
        "",
        VcsBundle.message("ignored.file.manage.message",
                          ApplicationNamesInfo.getInstance().getFullProductName(), ignoredFileContentProvider.getFileName()),
        NotificationAction.create(VcsBundle.message("ignored.file.manage.this.project"), (event, notification) -> {
          writeToIgnoreFile.run();
          propertiesComponent.setValue(MANAGE_IGNORE_FILES_PROPERTY, true);
          propertiesComponent.setValue(ASKED_MANAGE_IGNORE_FILES_PROPERTY, true);
          synchronized (myNotificationLock) {
            notification.expire();
            myIgnoreFileRootNotificationShowFor = null;
          }
        }),
        NotificationAction.create(VcsBundle.message("ignored.file.manage.all.project"), (event, notification) -> {
          writeToIgnoreFile.run();
          applicationSettings.MANAGE_IGNORE_FILES = true;
          propertiesComponent.setValue(ASKED_MANAGE_IGNORE_FILES_PROPERTY, true);
          synchronized (myNotificationLock) {
            notification.expire();
            myIgnoreFileRootNotificationShowFor = null;
          }
        }),
        NotificationAction.create(VcsBundle.message("ignored.file.manage.notmanage"), (event, notification) -> {
          propertiesComponent.setValue(ASKED_MANAGE_IGNORE_FILES_PROPERTY, true);
          synchronized (myNotificationLock) {
            notification.expire();
            myIgnoreFileRootNotificationShowFor = null;
          }
        }));
    }
  }


  @NotNull
  private static File getIgnoreFile(@NotNull VirtualFile ignoreFileRoot, @NotNull String ignoreFileName) {
    File vcsRootFile = VfsUtilCore.virtualToIoFile(ignoreFileRoot);
    return new File(vcsRootFile.getPath(), ignoreFileName);
  }

  private static boolean needGenerateIgnoreFile(@NotNull Project project, @NotNull VirtualFile ignoreFileRoot) {
    VcsApplicationSettings vcsApplicationSettings = VcsApplicationSettings.getInstance();
    if (vcsApplicationSettings.DISABLE_MANAGE_IGNORE_FILES) return false;

    boolean wasGeneratedPreviously = IgnoredFileRootStore.getInstance(project).containsRoot(ignoreFileRoot.getPath());
    if (wasGeneratedPreviously) {
      LOG.debug("Ignore file generated previously for root " + ignoreFileRoot.getPath());
      return false;
    }

    boolean needGenerateRegistryFlag = ApplicationManager.getApplication().isInternal() || Registry.is("vcs.ignorefile.generation", true);
    if (!needGenerateRegistryFlag) {
      return false;
    }

    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(project);
    boolean askedToManageIgnores = propertiesComponent.getBoolean(ASKED_MANAGE_IGNORE_FILES_PROPERTY, false);

    return isManageIgnoreTurnOn(project) || !askedToManageIgnores;
  }

  private static boolean isManageIgnoreTurnOn(@NotNull Project project){
    boolean globalManageIgnores = VcsApplicationSettings.getInstance().MANAGE_IGNORE_FILES;
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(project);
    boolean manageIgnoresInProject = propertiesComponent.getBoolean(MANAGE_IGNORE_FILES_PROPERTY, false);

    return globalManageIgnores || manageIgnoresInProject;
  }

  private static boolean needAskToManageIgnoreFiles(@NotNull Project project) {
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(project);
    boolean askedToManageIgnores = propertiesComponent.getBoolean(ASKED_MANAGE_IGNORE_FILES_PROPERTY, false);

    return !askedToManageIgnores && !isManageIgnoreTurnOn(project);
  }

  @State(name = "IgnoredFileRootStore", storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)})
  static class IgnoredFileRootStore implements PersistentStateComponent<IgnoredFileRootStore.State> {

    static class State {
      public Set<String> generatedRoots = new HashSet<>();
    }

    State myState = new State();

    static IgnoredFileRootStore getInstance(Project project) {
      return ServiceManager.getService(project, IgnoredFileRootStore.class);
    }

    boolean containsRoot(@NotNull String root) {
      return myState.generatedRoots.contains(root);
    }

    void addRoot(@NotNull String root) {
      myState.generatedRoots.add(root);
    }

    @Nullable
    @Override
    public State getState() {
      return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
      myState = state;
    }
  }
}

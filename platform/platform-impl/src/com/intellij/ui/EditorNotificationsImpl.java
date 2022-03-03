// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.AdditionalLibraryRootsListener;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.KeyWithDefaultValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.listeners.RefactoringElementAdapter;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

public final class EditorNotificationsImpl extends EditorNotifications {

  /**
   * @deprecated Please use {@link EditorNotificationProvider#EP_NAME} instead.
   */
  @Deprecated
  public static final ProjectExtensionPointName<EditorNotificationProvider> EP_PROJECT = EditorNotificationProvider.EP_NAME;

  private static final Key<Map<Class<? extends EditorNotificationProvider>, JComponent>> EDITOR_NOTIFICATION_PROVIDER =
    KeyWithDefaultValue.create("editor.notification.provider", WeakHashMap::new);
  private static final Key<Boolean> PENDING_UPDATE = Key.create("pending.notification.update");

  private final @NotNull MergingUpdateQueue myUpdateMerger;
  private final @NotNull Project myProject;

  public EditorNotificationsImpl(@NotNull Project project) {
    myUpdateMerger = new MergingUpdateQueue("EditorNotifications update merger", 100, true, null, project)
      .usePassThroughInUnitTestMode();
    myProject = project;
    MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        updateNotifications(file);
      }

      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        VirtualFile file = event.getNewFile();
        FileEditor editor = event.getNewEditor();
        if (file != null && editor != null && Boolean.TRUE.equals(editor.getUserData(PENDING_UPDATE))) {
          editor.putUserData(PENDING_UPDATE, null);
          updateEditor(file, editor);
        }
      }
    });
    connection.subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      @Override
      public void enteredDumbMode() {
        updateAllNotifications();
      }

      @Override
      public void exitDumbMode() {
        updateAllNotifications();
      }
    });
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(@NotNull ModuleRootEvent event) {
        updateAllNotifications();
      }
    });
    connection.subscribe(AdditionalLibraryRootsListener.TOPIC,
                         ((presentableLibraryName, oldRoots, newRoots, libraryNameForDebug) -> updateAllNotifications()));

    EditorNotificationProvider.EP_NAME
      .getPoint(project)
      .addExtensionPointListener(new ExtensionPointListener<>() {
        @Override
        public void extensionAdded(@NotNull EditorNotificationProvider extension,
                                   @NotNull PluginDescriptor descriptor) {
          updateAllNotifications();
        }

        @Override
        public void extensionRemoved(@NotNull EditorNotificationProvider extension,
                                     @NotNull PluginDescriptor descriptor) {
          updateNotifications(extension);
        }
      }, false, null);
  }

  @Override
  public void updateNotifications(@NotNull EditorNotificationProvider provider) {
    for (VirtualFile file : FileEditorManager.getInstance(myProject).getOpenFilesWithRemotes()) {
      List<FileEditor> editors = getEditors(file);

      for (FileEditor editor : editors) {
        updateNotification(editor, provider, null);
      }
    }
  }

  @Override
  public void updateNotifications(@NotNull VirtualFile file) {
    UIUtil.invokeLaterIfNeeded(() -> {
      if (myProject.isDisposed() || !file.isValid()) {
        return;
      }

      List<FileEditor> editors = getEditors(file);

      if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
        editors = ContainerUtil.filter(editors, fileEditor -> {
          boolean visible = UIUtil.isShowing(fileEditor.getComponent());
          if (!visible) {
            fileEditor.putUserData(PENDING_UPDATE, Boolean.TRUE);
          }
          return visible;
        });
      }

      for (FileEditor editor : editors) {
        updateEditor(file, editor);
      }
    });
  }

  private @NotNull List<FileEditor> getEditors(@NotNull VirtualFile file) {
    return ContainerUtil.filter(
      FileEditorManager.getInstance(myProject).getAllEditors(file),
      editor -> !(editor instanceof TextEditor) || AsyncEditorLoader.isEditorLoaded(((TextEditor)editor).getEditor()));
  }

  private void updateEditor(@NotNull VirtualFile file,
                            @NotNull FileEditor fileEditor) {
    // light project is not disposed in tests
    if (myProject.isDisposed()) {
      return;
    }

    for (EditorNotificationProvider provider : EditorNotificationProvider.EP_NAME.getExtensions(myProject)) {
      ReadAction.nonBlocking(() -> provider.collectNotificationData(myProject, file))
        .expireWith(myProject)
        .expireWhen(() -> !file.isValid() || DumbService.isDumb(myProject) && !DumbService.isDumbAware(provider))
        .coalesceBy(this, provider, file)
        .finishOnUiThread(ModalityState.any(), componentProvider -> {
          JComponent component = componentProvider.apply(fileEditor);
          updateNotification(fileEditor, provider, component);
        }).submit(NonUrgentExecutor.getInstance());
    }
  }

  private void updateNotification(@NotNull FileEditor editor,
                                  @NotNull EditorNotificationProvider provider,
                                  @Nullable JComponent component) {
    Map<Class<? extends EditorNotificationProvider>, JComponent> map = getNotificationPanels(editor);
    Class<? extends EditorNotificationProvider> providerClass = provider.getClass();

    JComponent old = map.get(providerClass);
    if (old != null) {
      FileEditorManager.getInstance(myProject).removeTopComponent(editor, old);
    }

    if (component != null) {
      if (component instanceof EditorNotificationPanel) {
        EditorNotificationPanel panel = (EditorNotificationPanel)component;
        panel.setProvider(provider);
        panel.setProject(myProject);
      }

      EditorNotificationUsagesCollectorKt.logNotificationShown(myProject, provider);
      FileEditorManager.getInstance(myProject).addTopComponent(editor, component);
    }

    map.put(providerClass, component);
  }

  @Override
  public void logNotificationActionInvocation(@NotNull EditorNotificationProvider provider,
                                              @NotNull Class<?> runnableClass) {
    EditorNotificationUsagesCollectorKt.logHandlerInvoked(myProject,
                                                          provider,
                                                          runnableClass);
  }

  @Override
  public void updateAllNotifications() {
    if (myProject.isDefault()) {
      throw new UnsupportedOperationException("Editor notifications aren't supported for default project");
    }
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
    if (fileEditorManager == null) {
      throw new IllegalStateException("No FileEditorManager for " + myProject);
    }
    myUpdateMerger.queue(new Update("update") {
      @Override
      public void run() {
        for (VirtualFile file : fileEditorManager.getOpenFilesWithRemotes()) {
          updateNotifications(file);
        }
      }
    });
  }

  static final class RefactoringListenerProvider implements RefactoringElementListenerProvider {

    @Override
    public @Nullable RefactoringElementListener getListener(final @NotNull PsiElement element) {
      if (element instanceof PsiFile) {
        return new RefactoringElementAdapter() {
          @Override
          protected void elementRenamedOrMoved(final @NotNull PsiElement newElement) {
            if (newElement instanceof PsiFile) {
              final VirtualFile vFile = newElement.getContainingFile().getVirtualFile();
              if (vFile != null) {
                EditorNotifications.getInstance(element.getProject()).updateNotifications(vFile);
              }
            }
          }

          @Override
          public void undoElementMovedOrRenamed(final @NotNull PsiElement newElement, final @NotNull String oldQualifiedName) {
            elementRenamedOrMoved(newElement);
          }
        };
      }
      return null;
    }
  }

  @VisibleForTesting
  public static @NotNull Map<Class<? extends EditorNotificationProvider>, JComponent> getNotificationPanels(@NotNull FileEditor editor) {
    return Objects.requireNonNull(editor.getUserData(EDITOR_NOTIFICATION_PROVIDER),
                                  () -> String.format("'%s' doesn't seem to support '%s'",
                                                      editor.getClass().getName(),
                                                      KeyWithDefaultValue.class.getName()));
  }

  @TestOnly
  public static void completeAsyncTasks() {
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
  }
}

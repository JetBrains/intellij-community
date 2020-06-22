// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ProjectTopics;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
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
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.listeners.RefactoringElementAdapter;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public final class EditorNotificationsImpl extends EditorNotifications {
  public static final ProjectExtensionPointName<Provider<?>> EP_PROJECT = new ProjectExtensionPointName<>("com.intellij.editorNotificationProvider");
  private static final Key<Boolean> PENDING_UPDATE = Key.create("pending.notification.update");

  private final MergingUpdateQueue myUpdateMerger;
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
          updateEditors(file, Collections.singletonList(editor));
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

    EP_PROJECT.getPoint(project).addExtensionPointListener(
      new ExtensionPointListener<Provider<?>>() {
        @Override
        public void extensionAdded(@NotNull Provider extension, @NotNull PluginDescriptor pluginDescriptor) {
          updateAllNotifications();
        }

        @Override
        public void extensionRemoved(@NotNull Provider extension, @NotNull PluginDescriptor pluginDescriptor) {
          updateNotifications(extension);
        }
      }, false, null);
  }

  @Override
  public void updateNotifications(@NotNull Provider<?> provider) {
    Key<? extends JComponent> key = provider.getKey();
    for (VirtualFile file : FileEditorManager.getInstance(myProject).getOpenFiles()) {
      List<FileEditor> editors = getEditors(file);

      for (FileEditor editor : editors) {
        updateNotification(editor, key, null, PluginInfoDetectorKt.getPluginInfo(provider.getClass()));
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
        Iterator<FileEditor> it = editors.iterator();
        while (it.hasNext()) {
          FileEditor e = it.next();
          if (!e.getComponent().isShowing()) {
            e.putUserData(PENDING_UPDATE, Boolean.TRUE);
            it.remove();
          }
        }
      }
      if (!editors.isEmpty()) updateEditors(file, editors);
    });
  }

  private @NotNull List<FileEditor> getEditors(@NotNull VirtualFile file) {
    return ContainerUtil.filter(
      FileEditorManager.getInstance(myProject).getAllEditors(file),
      editor -> !(editor instanceof TextEditor) || AsyncEditorLoader.isEditorLoaded(((TextEditor)editor).getEditor()));
  }

  private void updateEditors(@NotNull VirtualFile file, List<FileEditor> editors) {
    ReadAction
      .nonBlocking(() -> calcNotificationUpdates(file, editors))
      .expireWith(myProject)
      .expireWhen(() -> !file.isValid())
      .coalesceBy(this, file)
      .finishOnUiThread(ModalityState.any(), updates -> {
        for (Runnable update : updates) {
          update.run();
        }
      })
      .submit(NonUrgentExecutor.getInstance());
  }

  private @NotNull List<Runnable> calcNotificationUpdates(@NotNull VirtualFile file, @NotNull List<? extends FileEditor> editors) {
    List<Provider<?>> providers = DumbService.getDumbAwareExtensions(myProject, EP_PROJECT);
    List<Runnable> updates = null;
    for (FileEditor editor : editors) {
      for (Provider<?> provider : providers) {
        // light project is not disposed in tests
        if (myProject.isDisposed()) {
          return Collections.emptyList();
        }

        JComponent component = provider.createNotificationPanel(file, editor, myProject);
        if (component instanceof EditorNotificationPanel) {
          ((EditorNotificationPanel)component).setProviderKey(provider.getKey());
          ((EditorNotificationPanel)component).setProject(myProject);
        }
        if (updates == null) {
          updates = new SmartList<>();
        }
        updates.add(() -> {
          updateNotification(editor, provider.getKey(), component, PluginInfoDetectorKt.getPluginInfo(provider.getClass()));
        });
      }
    }
    return updates == null ? Collections.emptyList() : updates;
  }

  private void updateNotification(@NotNull FileEditor editor,
                                  @NotNull Key<? extends JComponent> key,
                                  @Nullable JComponent component,
                                  PluginInfo pluginInfo) {
    JComponent old = editor.getUserData(key);
    if (old != null) {
      FileEditorManager.getInstance(myProject).removeTopComponent(editor, old);
    }
    if (component != null) {
      FeatureUsageData data = new FeatureUsageData()
        .addData("key", key.toString())
        .addPluginInfo(pluginInfo);
      FUCounterUsageLogger.getInstance().logEvent(myProject, "editor.notification.panel", "shown", data);

      FileEditorManager.getInstance(myProject).addTopComponent(editor, component);
      @SuppressWarnings("unchecked") Key<JComponent> _key = (Key<JComponent>)key;
      editor.putUserData(_key, component);
    }
    else {
      editor.putUserData(key, null);
    }
  }

  @Override
  public void logNotificationActionInvocation(@Nullable Key<?> providerKey, @Nullable Class<?> runnableClass) {
    if (providerKey == null || runnableClass == null) return;

    FeatureUsageData data = new FeatureUsageData()
      .addData("key", providerKey.toString())
      .addData("class_name", runnableClass.getName())
      .addPluginInfo(PluginInfoDetectorKt.getPluginInfo(runnableClass));
    FUCounterUsageLogger.getInstance().logEvent(myProject, "editor.notification.panel", "actionInvoked", data);
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
        for (VirtualFile file : fileEditorManager.getOpenFiles()) {
          updateNotifications(file);
        }
      }
    });
  }

  public static class RefactoringListenerProvider implements RefactoringElementListenerProvider {
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

  @TestOnly
  public static void completeAsyncTasks() {
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
  }

}

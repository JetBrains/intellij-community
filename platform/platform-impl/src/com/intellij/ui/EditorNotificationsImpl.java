// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.TextEditor;
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
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.concurrency.CancellablePromise;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * @author peter
 */
public class EditorNotificationsImpl extends EditorNotifications {
  // do not use project level - use app level instead
  private static final ProjectExtensionPointName<Provider> EP_PROJECT = new ProjectExtensionPointName<>("com.intellij.editorNotificationProvider");

  private final Key<CancellablePromise<?>> CURRENT_UPDATE = Key.create("EditorNotifications update"); // non-static, per-project
  private static final ExecutorService ourExecutor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor(
    "EditorNotificationsImpl Pool");
  private final MergingUpdateQueue myUpdateMerger;
  @NotNull private final Project myProject;

  public EditorNotificationsImpl(@NotNull Project project) {
    myUpdateMerger = new MergingUpdateQueue("EditorNotifications update merger", 100, true, null, project);
    myProject = project;
    MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        updateNotifications(file);
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
  }

  @Override
  public void updateNotifications(@NotNull final VirtualFile file) {
    UIUtil.invokeLaterIfNeeded(() -> {
      if (myProject.isDisposed() || !file.isValid()) {
        return;
      }

      CancellablePromise<?> prev = file.getUserData(CURRENT_UPDATE);
      if (prev != null) {
        prev.cancel();
      }

      CancellablePromise<List<Runnable>> promise = ReadAction.
        nonBlocking(() -> calcNotificationUpdates(file)).
        expireWhen(() -> !file.isValid() || myProject.isDisposed()).
        finishOnUiThread(ModalityState.any(), updates -> {
          for (Runnable update : updates) {
            update.run();
          }
        }).
        submit(ourExecutor);
      file.putUserData(CURRENT_UPDATE, promise);
      promise.onProcessed(__ -> file.putUserData(CURRENT_UPDATE, null));
    });
  }

  @NotNull
  private List<Runnable> calcNotificationUpdates(@NotNull VirtualFile file) {
    List<FileEditor> editors = ContainerUtil.filter(FileEditorManager.getInstance(myProject).getAllEditors(file),
                                                    editor -> !(editor instanceof TextEditor) || AsyncEditorLoader.isEditorLoaded(((TextEditor)editor).getEditor()));
    List<Provider> providers = DumbService.getDumbAwareExtensions(myProject, EP_PROJECT);
    List<Runnable> updates = new SmartList<>();
    for (FileEditor editor : editors) {
      for (Provider<?> provider : providers) {
        JComponent component = provider.createNotificationPanel(file, editor, myProject);
        updates.add(() -> updateNotification(editor, provider.getKey(), component));
      }
    }
    return updates;
  }

  private void updateNotification(@NotNull FileEditor editor, @NotNull Key<? extends JComponent> key, @Nullable JComponent component) {
    JComponent old = editor.getUserData(key);
    if (old != null) {
      FileEditorManager.getInstance(myProject).removeTopComponent(editor, old);
    }
    if (component != null) {
      FileEditorManager.getInstance(myProject).addTopComponent(editor, component);
      @SuppressWarnings("unchecked") Key<JComponent> _key = (Key<JComponent>)key;
      editor.putUserData(_key, component);
    }
    else {
      editor.putUserData(key, null);
    }
  }

  @Override
  public void updateAllNotifications() {
    myUpdateMerger.queue(new Update("update") {
      @Override
      public void run() {
        for (VirtualFile file : FileEditorManager.getInstance(myProject).getOpenFiles()) {
          updateNotifications(file);
        }
      }
    });
  }

  public static class RefactoringListenerProvider implements RefactoringElementListenerProvider {
    @Nullable
    @Override
    public RefactoringElementListener getListener(@NotNull final PsiElement element) {
      if (element instanceof PsiFile) {
        return new RefactoringElementAdapter() {
          @Override
          protected void elementRenamedOrMoved(@NotNull final PsiElement newElement) {
            if (newElement instanceof PsiFile) {
              final VirtualFile vFile = newElement.getContainingFile().getVirtualFile();
              if (vFile != null) {
                EditorNotifications.getInstance(element.getProject()).updateNotifications(vFile);
              }
            }
          }

          @Override
          public void undoElementMovedOrRenamed(@NotNull final PsiElement newElement, @NotNull final String oldQualifiedName) {
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

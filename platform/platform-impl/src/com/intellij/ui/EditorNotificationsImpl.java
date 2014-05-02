/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ReadTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author peter
 */
public class EditorNotificationsImpl extends EditorNotifications {
  private static final ExtensionPointName<Provider> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.editorNotificationProvider");
  private final Map<VirtualFile, ProgressIndicator> myCurrentUpdates = new ConcurrentHashMap<VirtualFile, ProgressIndicator>();

  public EditorNotificationsImpl(Project project) {
    super(project);
    project.getMessageBus().connect(project).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerAdapter() {
      @Override
      public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        updateNotifications(file);
      }
    });
  }

  public void updateNotifications(final VirtualFile file) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      public void run() {
        ProgressIndicator indicator = myCurrentUpdates.remove(file);
        if (indicator != null) {
          indicator.cancel();
        }

        indicator = new ProgressIndicatorBase();
        myCurrentUpdates.put(file, indicator);

        ReadTask task = createTask(indicator, file);
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          task.computeInReadAction(indicator);
        } else {
          ProgressIndicatorUtils.scheduleWithWriteActionPriority(indicator, task);
        }
      }
    });
  }

  private ReadTask createTask(final ProgressIndicator indicator, final VirtualFile file) {
    return new ReadTask() {

      private boolean isOutdated() {
        return myProject.isDisposed() || indicator != myCurrentUpdates.get(file);
      }

      @Override
      public void computeInReadAction(@NotNull final ProgressIndicator indicator) {
        if (isOutdated()) return;

        final List<Runnable> updates = ContainerUtil.newArrayList();
        for (final FileEditor editor : FileEditorManager.getInstance(myProject).getAllEditors(file)) {
          for (final Provider<?> provider : Extensions.getExtensions(EXTENSION_POINT_NAME, myProject)) {
            final JComponent component = provider.createNotificationPanel(file, editor);
            updates.add(new Runnable() {
              @Override
              public void run() {
                updateNotification(editor, provider.getKey(), component);
              }
            });
          }
        }

        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            if (!isOutdated()) {
              myCurrentUpdates.remove(file);
              for (Runnable update : updates) {
                update.run();
              }
            }
          }
        });
      }

      @Override
      public void onCanceled(@NotNull ProgressIndicator indicator) {
        updateNotifications(file);
      }
    };
  }


  private void updateNotification(FileEditor editor, Key<? extends JComponent> key, @Nullable JComponent component) {
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

}

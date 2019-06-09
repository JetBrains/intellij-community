// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement.finder;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.editorconfig.configmanagement.EditorConfigActionUtil;
import org.editorconfig.language.messages.EditorConfigBundle;
import org.jetbrains.annotations.NotNull;

public class EditorConfigFilesNotifier implements EditorConfigFinder.Callback {
  private static final String NOTIFICATION_SHOWN_FLAG = "editor.config.files.notification.shown";

  private final Project myProject;
  private boolean myFoundFlag;

  public EditorConfigFilesNotifier(Project project) {
    myProject = project;
  }

  @Override
  public EditorConfigFinder.Callback.Result found(@NotNull VirtualFile editorConfigFile) {
    myFoundFlag = true;
    return Result.Stop;
  }

  @Override
  public void done() {
    if (myFoundFlag && isShowNotification(myProject)) {
      ApplicationManager.getApplication().invokeLater(() -> {
        EditorConfigsFoundNotification notification = new EditorConfigsFoundNotification(myProject);
        notification.notify(myProject);
        setNotificationShown();
      });
    }
  }

  private static class EditorConfigsFoundNotification extends Notification {
    private EditorConfigsFoundNotification(Project project) {
      super(EditorConfigActionUtil.NOTIFICATION_GROUP.getDisplayId(),
            EditorConfigBundle.message("editor.config.files.found.title"),
            EditorConfigBundle.message("editor.config.files.found.message"),
            NotificationType.INFORMATION);
      addAction(EditorConfigActionUtil.createDisableAction(project, EditorConfigBundle.message("editor.config.files.disable")));
      addAction(EditorConfigActionUtil.createShowEditorConfigFilesAction());
    }
  }

  public static boolean isShowNotification(@NotNull Project project) {
    final PropertiesComponent projectProperties = PropertiesComponent.getInstance(project);
    return !projectProperties.getBoolean(NOTIFICATION_SHOWN_FLAG);
  }

  private void setNotificationShown() {
    final PropertiesComponent projectProperties = PropertiesComponent.getInstance(myProject);
    projectProperties.setValue(NOTIFICATION_SHOWN_FLAG, true);
  }
}

/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Bas Leijdekkers
 */
public class ShelvedChangesNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> implements DumbAware {
  private static final Key<EditorNotificationPanel> KEY = Key.create("file.changes.present.on.shelf.notification.panel");
  final Project myProject;

  public ShelvedChangesNotificationProvider(Project project) {
    myProject = project;
    myProject.getMessageBus().connect(project).subscribe(ShelveChangesManager.SHELF_TOPIC, new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        EditorNotifications.getInstance(myProject).updateAllNotifications();
      }
    });
  }

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor) {
    final String path = file.getPath();
    final List<ShelvedChangeList> foundChangeLists = ShelveChangesManager.getInstance(myProject).getShelvedChangeLists()
      .stream().filter(l -> findChange(path, l).isPresent()).collect(Collectors.toList());
    if (foundChangeLists.isEmpty()) {
      return null;
    }
    final EditorNotificationPanel notificationPanel = new EditorNotificationPanel();
    final int size = foundChangeLists.size();
    notificationPanel.setText("Changes to this file are present in " + size + " shelved change list" + (size > 1 ? "s" : ""));
    final int nameLength = size == 1 ? 50 : size == 2 ? 35 : 20;
    foundChangeLists.stream().limit(3).forEach(
      l -> notificationPanel.createActionLabel("Show '" + StringUtil.shortenTextWithEllipsis(l.toString(), nameLength, 0, true) + '\'',
                                               () -> findChange(path, l).ifPresent(
                                                 c -> ShelvedChangesViewManager.getInstance(myProject).showChangeInView(c))));
    return notificationPanel;
  }

  private Optional<ShelvedChange> findChange(String path, ShelvedChangeList shelvedChangeList) {
    return shelvedChangeList.getChanges(myProject).stream().filter(c -> path.endsWith(c.getBeforePath())).findAny();
  }
}

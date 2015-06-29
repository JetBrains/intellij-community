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
package com.intellij.openapi.vcs.changes.conflicts;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class ChangelistConflictNotificationProvider extends EditorNotifications.Provider<ChangelistConflictNotificationPanel> implements
                                                                                                                              DumbAware {

  private static final Key<ChangelistConflictNotificationPanel> KEY = Key.create("changelistConflicts");

  private final ChangelistConflictTracker myConflictTracker;

  public ChangelistConflictNotificationProvider(ChangeListManagerImpl changeListManager) {
    myConflictTracker = changeListManager.getConflictTracker();
  }

  @Override
  @NotNull
  public Key<ChangelistConflictNotificationPanel> getKey() {
    return KEY;
  }

  @Override
  public ChangelistConflictNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor) {
    return myConflictTracker.hasConflict(file) ? ChangelistConflictNotificationPanel.create(myConflictTracker, file) : null;
  }
}

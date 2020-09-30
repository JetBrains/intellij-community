// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class FxmlPresenceListener implements BulkFileListener {
  private static final Key<ModificationTracker> KEY = Key.create("fxml.presence.modification.tracker");
  private final SimpleModificationTracker myModificationTracker;

  public FxmlPresenceListener(@NotNull Project project) {
    myModificationTracker = new SimpleModificationTracker();
    project.putUserData(KEY, myModificationTracker);
  }

  static ModificationTracker getModificationTracker(@NotNull Project project) {
    return () -> {
      final ModificationTracker tracker = project.getUserData(KEY);
      return tracker != null ? tracker.getModificationCount() + 1 : 0;
    };
  }

  @Override
  public void after(@NotNull List<? extends VFileEvent> events) {
    for (VFileEvent event : events) {
      checkEvent(event);
    }
  }

  private void checkEvent(@NotNull VFileEvent event) {
    if (event instanceof VFileContentChangeEvent) return;
    if (event instanceof VFilePropertyChangeEvent) {
      VFilePropertyChangeEvent propertyChangeEvent = (VFilePropertyChangeEvent)event;
      if (VirtualFile.PROP_NAME.equals(propertyChangeEvent.getPropertyName())) {
        final String oldName = (String)propertyChangeEvent.getOldValue();
        final String newName = (String)propertyChangeEvent.getNewValue();
        if (oldName != null && newName != null &&
            oldName.endsWith(JavaFxFileTypeFactory.DOT_FXML_EXTENSION) != newName.endsWith(JavaFxFileTypeFactory.DOT_FXML_EXTENSION)) {
          myModificationTracker.incModificationCount();
        }
      }
    }
    else {
      VirtualFile file = event.getFile();
      if (file != null && JavaFxFileTypeFactory.isFxml(file)) {
        myModificationTracker.incModificationCount();
      }
    }
  }
}

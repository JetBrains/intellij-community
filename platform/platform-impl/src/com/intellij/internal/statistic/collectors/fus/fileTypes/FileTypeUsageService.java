// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service(Service.Level.PROJECT)
public final class FileTypeUsageService {
  private final Map<String, Long> myStartOpenTime = new ConcurrentHashMap<>();

  public static FileTypeUsageService getInstance(@NotNull Project project) {
    return project.getService(FileTypeUsageService.class);
  }

  private void addStartOpenTime(@NotNull VirtualFile file) {
    myStartOpenTime.put(getKey(file), System.nanoTime());
  }

  private @Nullable Long getAndRemoveStartOpenTime(@NotNull VirtualFile file) {
    return myStartOpenTime.remove(getKey(file));
  }

  private static @NotNull String getKey(@NotNull VirtualFile file) {
    return file.getUrl();
  }

  static final class MyBeforeFileEditorManagerListener implements FileEditorManagerListener.Before {
    @Override
    public void beforeFileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
      FileTypeUsageService service = getInstance(source.getProject());
      if (service != null) {
        service.addStartOpenTime(file);
      }
    }
  }

  static final class MyFileEditorManagerListener implements FileEditorManagerListener {
    @Override
    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
      FileTypeUsageService service = getInstance(source.getProject());
      Long startOpen = service == null ? null : service.getAndRemoveStartOpenTime(file);
      FileTypeUsageCounterCollector.triggerOpen(source.getProject(), source, file, startOpen);
    }

    @Override
    public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
      FileTypeUsageCounterCollector.triggerClosed(source.getProject(), file);
    }

    @Override
    public void selectionChanged(@NotNull FileEditorManagerEvent event) {
      FileTypeUsageCounterCollector.triggerSelect(event.getManager().getProject(), event.getNewFile());
    }
  }
}

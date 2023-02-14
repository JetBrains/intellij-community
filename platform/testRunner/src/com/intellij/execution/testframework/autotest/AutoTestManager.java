// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.autotest;

import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

@Service(Service.Level.PROJECT)
@State(name = "AutoTestManager", storages = @Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE))
public final class AutoTestManager extends AbstractAutoTestManager {
  public static @NotNull AutoTestManager getInstance(Project project) {
    return project.getService(AutoTestManager.class);
  }

  public AutoTestManager(@NotNull Project project) {
    super(project);
  }

  @Override
  protected @NotNull AutoTestWatcher createWatcher(@NotNull Project project) {
    return new DelayedDocumentWatcher(project, myDelayMillis, this::restartAllAutoTests, (Predicate<? super VirtualFile>)file -> {
      if (ScratchUtil.isScratch(file)) {
        return false;
      }
      // Vladimir.Krivosheev - I don't know, why AutoTestManager checks it, but old behavior is preserved
      return FileEditorManager.getInstance(project).isFileOpen(file);
    });
  }
}

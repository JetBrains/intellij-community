// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.execution.Location;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.navigation.GotoRelatedProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.List;

final class TestCaseAsRelatedFileProvider extends GotoRelatedProvider {
  @Override
  public @NotNull @Unmodifiable List<? extends GotoRelatedItem> getItems(@NotNull DataContext context) {
    final Editor editor = context.getData(CommonDataKeys.EDITOR);
    final Project project = context.getData(CommonDataKeys.PROJECT);
    final VirtualFile file = context.getData(CommonDataKeys.VIRTUAL_FILE);
    if (editor == null || file == null || project == null) {
      return Collections.emptyList();
    }

    final List<Location> locations = TestLocationUtil.collectRelativeLocations(project, file);
    if (locations.isEmpty()) {
      return Collections.emptyList();
    }

    return ContainerUtil.map(locations, location -> new GotoRelatedItem(location.getPsiElement()));
  }
}

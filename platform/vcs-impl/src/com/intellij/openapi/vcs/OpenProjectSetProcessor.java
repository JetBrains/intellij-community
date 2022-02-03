// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.PlatformProjectOpenProcessor;
import com.intellij.projectImport.ProjectSetProcessor;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Paths;
import java.util.List;

final class OpenProjectSetProcessor extends ProjectSetProcessor {
  @Override
  public String getId() {
    return PROJECT;
  }

  @Override
  public void processEntries(@NotNull List<? extends Pair<String, String>> entries, @NotNull Context context, @NotNull Runnable runNext) {
    String root = context.directory == null || context.directoryName == null ? null : context.directory.getPath() + "/" + context.directoryName;
    for (Pair<String, String> entry : entries) {
      if ("project".equals(entry.getFirst())) {
        String path = root == null ? entry.getSecond() : (root + "/" + entry.getSecond());
        context.project = ProjectUtil.openProject(Paths.get(path), OpenProjectTask.build().withProjectToClose(null));
        if (context.project != null) {
          runNext.run();
        }
        return;
      }
    }

    // no "project" entry
    VirtualFile dir = LocalFileSystem.getInstance().refreshAndFindFileByPath(root);
    if (dir != null) {
      Project project = PlatformProjectOpenProcessor.getInstance().doOpenProject(dir, null, false);
      if (project != null) {
        runNext.run();
      }
    }
  }
}

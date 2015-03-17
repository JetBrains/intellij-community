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
package com.intellij.openapi.vcs;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.PlatformProjectOpenProcessor;
import com.intellij.projectImport.ProjectSetProcessor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class OpenProjectSetProcessor extends ProjectSetProcessor {

  @Override
  public String getId() {
    return PROJECT;
  }

  @Override
  public void processEntries(@NotNull List<Pair<String, String>> entries, @NotNull final Context context, @NotNull Runnable runNext) {
    final String root = context.directory.getPath() + "/" + context.directoryName;
    for (final Pair<String, String> entry : entries) {
      if ("project".equals(entry.getFirst())) {
        final Project[] projects = ProjectManager.getInstance().getOpenProjects();
        context.project = UIUtil.invokeAndWaitIfNeeded(new Computable<Project>() {
          @Override
          public Project compute() {
            return ProjectUtil.openProject(root + "/" + entry.getSecond(), ArrayUtil.getFirstElement(projects), false);
          }
        });
        if (context.project != null) {
          runNext.run();
        }
        return;
      }
    }
    // no "project" entry
    final VirtualFile dir = LocalFileSystem.getInstance().refreshAndFindFileByPath(root);
    if (dir != null) {
      Project project = PlatformProjectOpenProcessor.getInstance().doOpenProject(dir, null, false);
      if (project != null) runNext.run();
    }
  }
}

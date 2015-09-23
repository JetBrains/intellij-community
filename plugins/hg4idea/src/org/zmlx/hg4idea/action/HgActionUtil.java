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
package org.zmlx.hg4idea.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.repo.HgRepositoryManager;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.Collection;
import java.util.List;

public class HgActionUtil {

  @NotNull
  public static List<HgRepository> collectRepositoriesFromFiles(@NotNull final HgRepositoryManager repositoryManager,
                                                                @NotNull Collection<VirtualFile> files) {
    return ContainerUtil.mapNotNull(files, new Function<VirtualFile, HgRepository>() {
      @Override
      public HgRepository fun(VirtualFile file) {
        return repositoryManager.getRepositoryForFile(file);
      }
    });
  }

  @Nullable
  public static HgRepository getSelectedRepositoryFromEvent(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return null;
    }
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    HgRepositoryManager repositoryManager = HgUtil.getRepositoryManager(project);
    return file != null ? repositoryManager.getRepositoryForFile(file) : HgUtil.getCurrentRepository(project);
  }
}

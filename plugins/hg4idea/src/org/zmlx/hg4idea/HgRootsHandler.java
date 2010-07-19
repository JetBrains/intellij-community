/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.zmlx.hg4idea;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Kirill Likhodedov
 */
public class HgRootsHandler implements AbstractVcs.RootsConvertor {

  private final Project myProject;

  public HgRootsHandler(Project project) {
    myProject = project;
  }

  public static HgRootsHandler getInstance(Project project) {
    return ServiceManager.getService(project, HgRootsHandler.class);
  }

  @Override
  public List<VirtualFile> convertRoots(List<VirtualFile> original) {
    final Set<VirtualFile> result = new THashSet<VirtualFile>(original.size());
    for (VirtualFile vf : original) {
      final VirtualFile root = convertRoot(vf);
      if (root != null) {
        result.add(root);
      }
    }
    return new ArrayList<VirtualFile>(result);
  }

  @Nullable
  public VirtualFile getRootFor(VirtualFile file) {
    return convertRoot(VcsUtil.getVcsRootFor(myProject, file));
  }

  @Nullable
  public VirtualFile getRootFor(FilePath filepath) {
    return convertRoot(VcsUtil.getVcsRootFor(myProject, filepath));
  }

  @Nullable
  private VirtualFile convertRoot(@Nullable VirtualFile root) {
    return HgUtil.getHgRootOrNull(myProject, root);
  }

}


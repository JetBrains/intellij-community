/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.diff.impl.patch.BinaryFilePatch;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.formove.PatchApplier;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ObjectsConvertor;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.MultiMap;

import java.util.Collection;
import java.util.LinkedList;

/**
 * @author irengrig
 *         Date: 2/25/11
 *         Time: 5:58 PM
 */
public class ApplyPatchDefaultExecutor implements ApplyPatchExecutor {
  private final Project myProject;

  public ApplyPatchDefaultExecutor(Project project) {
    myProject = project;
  }

  @Override
  public String getName() {
    // not used
    return null;
  }

  @Override
  public void apply(MultiMap<VirtualFile, FilePatchInProgress> patchGroups, LocalChangeList localList, String fileName) {
    final Collection<PatchApplier> appliers = new LinkedList<PatchApplier>();
    for (VirtualFile base : patchGroups.keySet()) {
      final PatchApplier patchApplier =
        new PatchApplier<BinaryFilePatch>(myProject, base, ObjectsConvertor.convert(patchGroups.get(base),
                                                                                  new Convertor<FilePatchInProgress, FilePatch>() {
                                                                                    public FilePatch convert(FilePatchInProgress o) {
                                                                                      return o.getPatch();
                                                                                    }
                                                                                  }), localList, null);
      appliers.add(patchApplier);
    }
    PatchApplier.executePatchGroup(appliers);
  }
}

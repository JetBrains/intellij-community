/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.ApplyPatchStatus;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.PatchSyntaxException;
import com.intellij.openapi.diff.impl.patch.formove.PatchApplier;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.TransparentlyFailedValueI;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.vcs.changes.shelf.ShelvedBinaryFile;
import com.intellij.openapi.vcs.changes.shelf.ShelvedBinaryFilePatch;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class UnshelvePatchDefaultExecutor extends ApplyPatchDefaultExecutor {
  private static final Logger LOG = Logger.getInstance(UnshelvePatchDefaultExecutor.class);

  @NotNull private final ShelvedChangeList myCurrentShelveChangeList;
  @NotNull private final List<ShelvedBinaryFilePatch> myBinaryShelvedPatches;

  public UnshelvePatchDefaultExecutor(@NotNull Project project,
                                      @NotNull ShelvedChangeList changeList,
                                      @NotNull List<ShelvedBinaryFilePatch> binaryShelvedPatches) {
    super(project);
    myCurrentShelveChangeList = changeList;
    myBinaryShelvedPatches = binaryShelvedPatches;
  }

  @Override
  public void apply(@NotNull MultiMap<VirtualFile, AbstractFilePatchInProgress> patchGroups,
                    @Nullable LocalChangeList localList,
                    @Nullable String fileName,
                    @Nullable TransparentlyFailedValueI<Map<String, Map<String, CharSequence>>, PatchSyntaxException> additionalInfo) {
    final CommitContext commitContext = new CommitContext();
    applyAdditionalInfoBefore(myProject, additionalInfo, commitContext);
    final Collection<PatchApplier> appliers = getPatchAppliers(patchGroups, localList, commitContext);
    final ApplyPatchStatus patchStatus = executeAndApplyAdditionalInfo(localList, additionalInfo, commitContext, appliers);
    if (patchStatus != ApplyPatchStatus.ABORT && patchStatus != ApplyPatchStatus.FAILURE) {
      removeAppliedAndSaveRemainedIfNeeded(appliers, commitContext); // remove only if partly applied or successful
    }
  }

  private void removeAppliedAndSaveRemainedIfNeeded(@NotNull Collection<PatchApplier> appliers, @NotNull CommitContext commitContext) {
    ShelveChangesManager shelveChangesManager = ShelveChangesManager.getInstance(myProject);
    if (!shelveChangesManager.isRemoveFilesFromShelf()) return;
    try {
      List<FilePatch> textPatches =
        ContainerUtil.<FilePatch>newArrayList(ShelveChangesManager.loadPatches(myProject, myCurrentShelveChangeList.PATH, commitContext));
      List<FilePatch> remainingBinaries = ContainerUtil.<FilePatch>newArrayList(myBinaryShelvedPatches);
      for (PatchApplier applier : appliers) {
        textPatches.removeAll(applier.getPatches());
        textPatches.addAll(applier.getRemainingPatches());
        remainingBinaries.removeAll(applier.getBinaryPatches());
      }
      if (textPatches.isEmpty() && remainingBinaries.isEmpty()) {
        shelveChangesManager.recycleChangeList(myCurrentShelveChangeList);
      }
      else {
        shelveChangesManager.saveRemainingPatches(myCurrentShelveChangeList, textPatches,
                                                  ContainerUtil.mapNotNull(remainingBinaries, new Function<FilePatch, ShelvedBinaryFile>() {
                                                    @Override
                                                    public ShelvedBinaryFile fun(FilePatch patch) {
                                                      return patch instanceof ShelvedBinaryFilePatch
                                                             ? ((ShelvedBinaryFilePatch)patch)
                                                               .getShelvedBinaryFile()
                                                             : null;
                                                    }
                                                  }), commitContext);
      }
    }
    catch (Exception e) {
      LOG.error("Couldn't update and store remaining patches", e);
    }

  }
}

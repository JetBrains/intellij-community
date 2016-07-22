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

import com.intellij.openapi.diff.impl.patch.*;
import com.intellij.openapi.diff.impl.patch.formove.PatchApplier;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.TransparentlyFailedValueI;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ApplyPatchDefaultExecutor implements ApplyPatchExecutor<AbstractFilePatchInProgress> {
  protected final Project myProject;

  public ApplyPatchDefaultExecutor(Project project) {
    myProject = project;
  }

  @Override
  public String getName() {
    // not used
    return null;
  }

  @CalledInAwt
  @Override
  public void apply(@NotNull List<FilePatch> remaining, @NotNull MultiMap<VirtualFile, AbstractFilePatchInProgress> patchGroupsToApply,
                    @Nullable LocalChangeList localList,
                    @Nullable String fileName,
                    @Nullable TransparentlyFailedValueI<Map<String, Map<String, CharSequence>>, PatchSyntaxException> additionalInfo) {
    final CommitContext commitContext = new CommitContext();
    applyAdditionalInfoBefore(myProject, additionalInfo, commitContext);
    final Collection<PatchApplier> appliers = getPatchAppliers(patchGroupsToApply, localList, commitContext);
    executeAndApplyAdditionalInfo(localList, additionalInfo, commitContext, appliers);
  }

  protected ApplyPatchStatus executeAndApplyAdditionalInfo(@Nullable LocalChangeList localList,
                                                           @Nullable TransparentlyFailedValueI<Map<String, Map<String, CharSequence>>, PatchSyntaxException> additionalInfo,
                                                           @NotNull CommitContext commitContext,
                                                           @NotNull Collection<PatchApplier> appliers) {
    final ApplyPatchStatus applyPatchStatus = PatchApplier.executePatchGroup(appliers, localList);
    if (applyPatchStatus != ApplyPatchStatus.ABORT) {
      applyAdditionalInfo(myProject, additionalInfo, commitContext);
    }
    return applyPatchStatus;
  }

  @NotNull
  protected Collection<PatchApplier> getPatchAppliers(@NotNull MultiMap<VirtualFile, AbstractFilePatchInProgress> patchGroups,
                                                      @Nullable LocalChangeList localList,
                                                      @NotNull CommitContext commitContext) {
    final Collection<PatchApplier> appliers = new LinkedList<>();
    for (VirtualFile base : patchGroups.keySet()) {
      appliers.add(new PatchApplier<BinaryFilePatch>(myProject, base,
                                                     ContainerUtil
                                                       .map(patchGroups.get(base), new Function<AbstractFilePatchInProgress, FilePatch>() {
                                                         @Override
                                                         public FilePatch fun(AbstractFilePatchInProgress patchInProgress) {
                                                           return patchInProgress.getPatch();
                                                         }
                                                       }), localList, null, commitContext));
    }
    return appliers;
  }


  public static void applyAdditionalInfoBefore(final Project project,
                                         TransparentlyFailedValueI<Map<String, Map<String, CharSequence>>, PatchSyntaxException> additionalInfo,
                                         CommitContext commitContext) {
    applyAdditionalInfoImpl(project, additionalInfo, commitContext, new Consumer<InfoGroup>() {
      @Override
      public void consume(InfoGroup infoGroup) {
        infoGroup.myPatchEP.consumeContentBeforePatchApplied(infoGroup.myPath, infoGroup.myContent, infoGroup.myCommitContext);
      }
    });
  }

  private static void applyAdditionalInfo(final Project project,
                                         TransparentlyFailedValueI<Map<String, Map<String, CharSequence>>, PatchSyntaxException> additionalInfo,
                                         CommitContext commitContext) {
    applyAdditionalInfoImpl(project, additionalInfo, commitContext, new Consumer<InfoGroup>() {
      @Override
      public void consume(InfoGroup infoGroup) {
        infoGroup.myPatchEP.consumeContent(infoGroup.myPath, infoGroup.myContent, infoGroup.myCommitContext);
      }
    });
  }

  private static void applyAdditionalInfoImpl(final Project project,
                                              TransparentlyFailedValueI<Map<String, Map<String, CharSequence>>, PatchSyntaxException> additionalInfo,
                                              CommitContext commitContext, final Consumer<InfoGroup> worker) {
    final PatchEP[] extensions = Extensions.getExtensions(PatchEP.EP_NAME, project);
    if (extensions.length == 0) return;
    if (additionalInfo != null) {
      try {
        final Map<String, Map<String, CharSequence>> map = additionalInfo.get();
        for (Map.Entry<String, Map<String, CharSequence>> entry : map.entrySet()) {
          final String path = entry.getKey();
          final Map<String, CharSequence> innerMap = entry.getValue();

          for (PatchEP extension : extensions) {
            final CharSequence charSequence = innerMap.get(extension.getName());
            if (charSequence != null) {
              worker.consume(new InfoGroup(extension, path, charSequence, commitContext));
            }
          }
        }
      }
      catch (PatchSyntaxException e) {
        VcsBalloonProblemNotifier
          .showOverChangesView(project, "Can not apply additional patch info: " + e.getMessage(), MessageType.ERROR);
      }
    }
  }
  
  private static class InfoGroup {
    private final PatchEP myPatchEP;
    private final String myPath;
    private final CharSequence myContent;
    private final CommitContext myCommitContext;

    private InfoGroup(PatchEP patchEP, String path, CharSequence content, CommitContext commitContext) {
      myPatchEP = patchEP;
      myPath = path;
      myContent = content;
      myCommitContext = commitContext;
    }
  }

  public static Set<String> pathsFromGroups(MultiMap<VirtualFile, AbstractFilePatchInProgress> patchGroups) {
    final Set<String> selectedPaths = new HashSet<>();
    final Collection<? extends AbstractFilePatchInProgress> values = patchGroups.values();
    for (AbstractFilePatchInProgress value : values) {
      final String path = value.getPatch().getBeforeName() == null ? value.getPatch().getAfterName() : value.getPatch().getBeforeName();
      selectedPaths.add(path);
    }
    return selectedPaths;
  }
}

// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.diff.impl.patch.BinaryFilePatch;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.PatchEP;
import com.intellij.openapi.diff.impl.patch.PatchSyntaxException;
import com.intellij.openapi.diff.impl.patch.formove.PatchApplier;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
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
  public void apply(@NotNull List<? extends FilePatch> remaining, @NotNull MultiMap<VirtualFile, AbstractFilePatchInProgress> patchGroupsToApply,
                    @Nullable LocalChangeList localList,
                    @Nullable String fileName,
                    @Nullable ThrowableComputable<? extends Map<String, Map<String, CharSequence>>, PatchSyntaxException> additionalInfo) {
    final CommitContext commitContext = new CommitContext();
    applyAdditionalInfoBefore(myProject, additionalInfo, commitContext);
    final Collection<PatchApplier> appliers = getPatchAppliers(patchGroupsToApply, localList, commitContext);
    PatchApplier.executePatchGroup(appliers, localList);
  }

  @NotNull
  protected Collection<PatchApplier> getPatchAppliers(@NotNull MultiMap<VirtualFile, AbstractFilePatchInProgress> patchGroups,
                                                      @Nullable LocalChangeList localList,
                                                      @NotNull CommitContext commitContext) {
    final Collection<PatchApplier> appliers = new LinkedList<>();
    for (VirtualFile base : patchGroups.keySet()) {
      appliers.add(new PatchApplier<BinaryFilePatch>(myProject, base,
                                                     ContainerUtil
                                                       .map(patchGroups.get(base), patchInProgress -> patchInProgress.getPatch()), localList,
                                                     commitContext));
    }
    return appliers;
  }


  public static void applyAdditionalInfoBefore(final Project project,
                                               @Nullable ThrowableComputable<? extends Map<String, Map<String, CharSequence>>, ? extends PatchSyntaxException> additionalInfo,
                                               @Nullable CommitContext commitContext) {
    final List<PatchEP> extensions = PatchEP.EP_NAME.getExtensions(project);
    if (extensions.isEmpty() || additionalInfo == null) {
      return;
    }

    try {
      Map<String, Map<String, CharSequence>> additionalInfoMap = additionalInfo.compute();
      for (Map.Entry<String, Map<String, CharSequence>> entry : additionalInfoMap.entrySet()) {
        for (PatchEP extension : extensions) {
          final CharSequence charSequence = entry.getValue().get(extension.getName());
          if (charSequence != null) {
            extension.consumeContentBeforePatchApplied(entry.getKey(), charSequence, commitContext);
          }
        }
      }
    }
    catch (PatchSyntaxException e) {
      VcsBalloonProblemNotifier
        .showOverChangesView(project, "Can not apply additional patch info: " + e.getMessage(), MessageType.ERROR);
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

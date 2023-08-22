// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.PatchEP;
import com.intellij.openapi.diff.impl.patch.PatchSyntaxException;
import com.intellij.openapi.diff.impl.patch.formove.PatchApplier;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ApplyPatchDefaultExecutor implements ApplyPatchExecutor<AbstractFilePatchInProgress<?>> {
  protected final Project myProject;

  public ApplyPatchDefaultExecutor(Project project) {
    myProject = project;
  }

  @Override
  public String getName() {
    // not used
    return null;
  }

  @RequiresEdt
  @Override
  public void apply(@NotNull List<? extends FilePatch> remaining,
                    @NotNull MultiMap<VirtualFile, AbstractFilePatchInProgress<?>> patchGroupsToApply,
                    @Nullable LocalChangeList localList,
                    @Nullable String fileName,
                    @Nullable ThrowableComputable<Map<String, Map<String, CharSequence>>, PatchSyntaxException> additionalInfo) {
    CommitContext commitContext = new CommitContext();
    if (additionalInfo != null) {
      applyAdditionalInfoBefore(myProject, additionalInfo, commitContext);
    }
    Collection<PatchApplier> appliers = getPatchAppliers(patchGroupsToApply, localList, commitContext);
    new Task.Backgroundable(myProject, VcsBundle.message("patch.apply.progress.title")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        PatchApplier.executePatchGroup(appliers, localList);
      }
    }.queue();
  }

  @NotNull
  protected Collection<PatchApplier> getPatchAppliers(@NotNull MultiMap<VirtualFile, AbstractFilePatchInProgress<?>> patchGroups,
                                                      @Nullable LocalChangeList localList,
                                                      @NotNull CommitContext commitContext) {
    Collection<PatchApplier> appliers = new ArrayList<>();
    for (VirtualFile base : patchGroups.keySet()) {
      appliers.add(new PatchApplier(myProject, base, ContainerUtil.map(patchGroups.get(base), patchInProgress -> {
        return patchInProgress.getPatch();
      }), localList, commitContext));
    }
    return appliers;
  }


  public static void applyAdditionalInfoBefore(@NotNull Project project,
                                               @NotNull ThrowableComputable<? extends Map<String, Map<String, CharSequence>>, PatchSyntaxException> additionalInfo,
                                               @Nullable CommitContext commitContext) {
    List<PatchEP> extensions = PatchEP.EP_NAME.getExtensionList();
    if (extensions.isEmpty()) {
      return;
    }

    try {
      Map<String, Map<String, CharSequence>> additionalInfoMap = additionalInfo.compute();
      for (Map.Entry<String, Map<String, CharSequence>> entry : additionalInfoMap.entrySet()) {
        for (PatchEP extension : extensions) {
          CharSequence charSequence = entry.getValue().get(extension.getName());
          if (charSequence != null) {
            extension.consumeContentBeforePatchApplied(project, entry.getKey(), charSequence, commitContext);
          }
        }
      }
    }
    catch (PatchSyntaxException e) {
      VcsBalloonProblemNotifier
        .showOverChangesView(project, VcsBundle.message("patch.apply.can.not.apply.additional.info.error", e.getMessage()), MessageType.ERROR);
    }
  }

  public static Set<String> pathsFromGroups(@NotNull MultiMap<VirtualFile, AbstractFilePatchInProgress<?>> patchGroups) {
    final Set<String> selectedPaths = new HashSet<>();
    final Collection<? extends AbstractFilePatchInProgress<?>> values = patchGroups.values();
    for (AbstractFilePatchInProgress value : values) {
      final String path = value.getPatch().getBeforeName() == null ? value.getPatch().getAfterName() : value.getPatch().getBeforeName();
      selectedPaths.add(path);
    }
    return selectedPaths;
  }
}

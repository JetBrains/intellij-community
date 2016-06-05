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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.PatchSyntaxException;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.diff.impl.patch.UnifiedDiffWriter;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.TransparentlyFailedValueI;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcsUtil.VcsCatchingRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ImportToShelfExecutor implements ApplyPatchExecutor<TextFilePatchInProgress> {
  private static final Logger LOG = Logger.getInstance(ImportToShelfExecutor.class);

  private static final String IMPORT_TO_SHELF = "Import to Shelf";
  private final Project myProject;

  public ImportToShelfExecutor(Project project) {
    myProject = project;
  }

  @Override
  public String getName() {
    return IMPORT_TO_SHELF;
  }

  @Override
  public void apply(@NotNull List<FilePatch> remaining, @NotNull final MultiMap<VirtualFile, TextFilePatchInProgress> patchGroupsToApply,
                    @Nullable LocalChangeList localList,
                    @Nullable final String fileName,
                    @Nullable final TransparentlyFailedValueI<Map<String, Map<String, CharSequence>>, PatchSyntaxException> additionalInfo) {
    if (fileName == null) {
      LOG.error("Patch file name shouldn't be null");
      return;
    }
    final VcsCatchingRunnable vcsCatchingRunnable = new VcsCatchingRunnable() {
      @Override
      public void runImpl() throws VcsException {
        final VirtualFile baseDir = myProject.getBaseDir();
        final File ioBase = new File(baseDir.getPath());
        final List<FilePatch> allPatches = new ArrayList<FilePatch>();
        for (VirtualFile virtualFile : patchGroupsToApply.keySet()) {
          final File ioCurrentBase = new File(virtualFile.getPath());
          allPatches.addAll(ContainerUtil.map(patchGroupsToApply.get(virtualFile), patchInProgress -> {
            final TextFilePatch was = patchInProgress.getPatch();
            was.setBeforeName(
              PathUtil.toSystemIndependentName(FileUtil.getRelativePath(ioBase, new File(ioCurrentBase, was.getBeforeName()))));
            was.setAfterName(
              PathUtil.toSystemIndependentName(FileUtil.getRelativePath(ioBase, new File(ioCurrentBase, was.getAfterName()))));
            return was;
          }));
        }
        if (!allPatches.isEmpty()) {
          Map<String, Map<String, CharSequence>> additionalInfoMap = Collections.emptyMap();
          if (additionalInfo != null) {
            try {
              additionalInfoMap = additionalInfo.get();
            }
            catch (PatchSyntaxException e) {
              VcsBalloonProblemNotifier
                .showOverChangesView(myProject, "Can not import additional patch info: " + e.getMessage(), MessageType.ERROR);
            }
          }
          try {
            final ShelvedChangeList shelvedChangeList = ShelveChangesManager.getInstance(myProject).
              importFilePatches(fileName, allPatches, new FromPatchToShelfPatchWriter(myProject, additionalInfoMap));
            ShelvedChangesViewManager.getInstance(myProject).activateView(shelvedChangeList);
          }
          catch (IOException e) {
            throw new VcsException(e);
          }
        }
      }
    };
    ProgressManager.getInstance().runProcessWithProgressSynchronously(vcsCatchingRunnable, "Import Patch to Shelf", true, myProject);
    if (! vcsCatchingRunnable.get().isEmpty()) {
      AbstractVcsHelper.getInstance(myProject).showErrors(vcsCatchingRunnable.get(), IMPORT_TO_SHELF);
    }
  }

  private static class FromPatchToShelfPatchWriter extends UnifiedDiffWriter {
    private final Map<String, Map<String, CharSequence>> myMap;

    public FromPatchToShelfPatchWriter(@NotNull Project project,
                                       @NotNull Map<String, Map<String, CharSequence>> infoMap) {
      super(project);
      myMap = infoMap;
    }

    @NotNull
    @Override
    protected Map<String, CharSequence> constructAdditionalInfoMap(@Nullable CommitContext commitContext, String path, String basePath) {
    //todo should use ioBase, not path
      Map<String, CharSequence> additionalInfoPerFile = myMap.get(path);
      return additionalInfoPerFile != null ? additionalInfoPerFile : Collections.emptyMap();
    }
  }
}

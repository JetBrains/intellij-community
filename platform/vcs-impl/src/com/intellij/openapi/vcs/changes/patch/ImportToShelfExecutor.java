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
import com.intellij.openapi.diff.impl.patch.PatchEP;
import com.intellij.openapi.diff.impl.patch.PatchSyntaxException;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
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
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcsUtil.VcsCatchingRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

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
        final List<FilePatch> allPatches = new ArrayList<>();
        for (VirtualFile virtualFile : patchGroupsToApply.keySet()) {
          final File ioCurrentBase = new File(virtualFile.getPath());
          allPatches.addAll(ContainerUtil.map(patchGroupsToApply.get(virtualFile), new Function<TextFilePatchInProgress, TextFilePatch>() {
            public TextFilePatch fun(TextFilePatchInProgress patchInProgress) {
              final TextFilePatch was = patchInProgress.getPatch();
              was.setBeforeName(
                PathUtil.toSystemIndependentName(FileUtil.getRelativePath(ioBase, new File(ioCurrentBase, was.getBeforeName()))));
              was.setAfterName(
                PathUtil.toSystemIndependentName(FileUtil.getRelativePath(ioBase, new File(ioCurrentBase, was.getAfterName()))));
              return was;
            }
          }));
        }
        if (!allPatches.isEmpty()) {
          PatchEP[] patchTransitExtensions = null;
          if (additionalInfo != null) {
            try {
              final Map<String, PatchEP> extensions = new HashMap<>();
              for (Map.Entry<String, Map<String, CharSequence>> entry : additionalInfo.get().entrySet()) {
                final String filePath = entry.getKey();
                Map<String, CharSequence> extToValue = entry.getValue();
                for (Map.Entry<String, CharSequence> innerEntry : extToValue.entrySet()) {
                  TransitExtension patchEP = (TransitExtension)extensions.get(innerEntry.getKey());
                  if (patchEP == null) {
                    patchEP = new TransitExtension(innerEntry.getKey());
                    extensions.put(innerEntry.getKey(), patchEP);
                  }
                  patchEP.put(filePath, innerEntry.getValue());
                }
              }
              Collection<PatchEP> values = extensions.values();
              patchTransitExtensions = values.toArray(new PatchEP[values.size()]);
            }
            catch (PatchSyntaxException e) {
              VcsBalloonProblemNotifier
                .showOverChangesView(myProject, "Can not import additional patch info: " + e.getMessage(), MessageType.ERROR);
            }
          }
          try {
            final ShelvedChangeList shelvedChangeList = ShelveChangesManager.getInstance(myProject).
              importFilePatches(fileName, allPatches, patchTransitExtensions);
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

  private static class TransitExtension implements PatchEP {
    private final String myName;
    private final Map<String, CharSequence> myMap;

    private TransitExtension(String name) {
      myName = name;
      myMap = new HashMap<>();
    }

    @NotNull
    @Override
    public String getName() {
      return myName;
    }

    @Override
    public CharSequence provideContent(@NotNull String path, CommitContext commitContext) {
      return myMap.get(path);
    }

    @Override
    public void consumeContent(@NotNull String path, @NotNull CharSequence content, CommitContext commitContext) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void consumeContentBeforePatchApplied(@NotNull String path,
                                                 @NotNull CharSequence content,
                                                 CommitContext commitContext) {
      throw new UnsupportedOperationException();
    }

    public void put(String fileName, CharSequence value) {
      myMap.put(fileName, value);
    }
  }
}

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

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.diff.impl.patch.BinaryFilePatch;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.PatchEP;
import com.intellij.openapi.diff.impl.patch.UnifiedDiffWriter;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.StringSelection;
import java.io.*;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;

import static com.intellij.util.ObjectUtils.chooseNotNull;

public class PatchWriter {

  public static void writePatches(@NotNull final Project project,
                                  @NotNull String fileName,
                                  @NotNull String basePath,
                                  @NotNull List<FilePatch> patches,
                                  @Nullable CommitContext commitContext,
                                  @NotNull Charset charset) throws IOException {
    writePatches(project, fileName, basePath, patches, commitContext, charset, false);
  }

  public static void writePatches(@NotNull final Project project,
                                  @NotNull String fileName,
                                  @Nullable String basePath,
                                  @NotNull List<FilePatch> patches,
                                  @Nullable CommitContext commitContext,
                                  @NotNull Charset charset, boolean includeBinaries) throws IOException {
    try (Writer writer = new OutputStreamWriter(new FileOutputStream(fileName), charset)) {
      write(project, writer, basePath, patches, commitContext, includeBinaries);
    }
  }

  private static void write(@NotNull Project project,
                            @NotNull Writer writer,
                            @Nullable String basePath,
                            @NotNull List<FilePatch> patches,
                            @Nullable CommitContext commitContext, boolean includeBinaries) throws IOException {
    final String lineSeparator = CodeStyle.getSettings(project).getLineSeparator();
    UnifiedDiffWriter
      .write(project, basePath, patches, writer, lineSeparator, Extensions.getExtensions(PatchEP.EP_NAME, project), commitContext);
    if (includeBinaries) {
      BinaryPatchWriter.writeBinaries(basePath, ContainerUtil.findAll(patches, BinaryFilePatch.class), writer);
    }
  }

  public static void writeAsPatchToClipboard(@NotNull Project project,
                                             @NotNull List<FilePatch> patches,
                                             @NotNull String basePath,
                                             @Nullable CommitContext commitContext) throws IOException {
    StringWriter writer = new StringWriter();
    write(project, writer, basePath, patches, commitContext, true);
    CopyPasteManager.getInstance().setContents(new StringSelection(writer.toString()));
  }

  @NotNull
  public static VirtualFile calculateBaseForWritingPatch(@NotNull Project project, @NotNull Collection<Change> changes) {
    File commonAncestor = ChangesUtil.findCommonAncestor(changes);
    boolean multiVcs = ChangesUtil.getAffectedVcses(changes, project).size() != 1;
    if (multiVcs || commonAncestor == null) return project.getBaseDir();
    VirtualFile vcsRoot = VcsUtil.getVcsRootFor(project, VcsUtil.getFilePath(commonAncestor));
    return chooseNotNull(vcsRoot, project.getBaseDir());
  }
}

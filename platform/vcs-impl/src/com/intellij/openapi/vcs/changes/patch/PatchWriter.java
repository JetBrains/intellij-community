// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.diff.impl.patch.BinaryFilePatch;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.diff.impl.patch.UnifiedDiffWriter;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.ProjectKt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.StringSelection;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.vcs.VcsType.distributed;

public final class PatchWriter {
  public static void writePatches(@NotNull Project project,
                                  @NotNull Path file,
                                  @NotNull Path basePath,
                                  @NotNull List<? extends FilePatch> patches,
                                  @Nullable CommitContext commitContext) throws IOException {
    writePatches(project, file, basePath, patches, commitContext, StandardCharsets.UTF_8, false);
  }

  public static void writePatches(@NotNull Project project,
                                  @NotNull Path file,
                                  @Nullable Path basePath,
                                  @NotNull List<? extends FilePatch> patches,
                                  @Nullable CommitContext commitContext,
                                  @NotNull Charset charset, boolean includeBinaries) throws IOException {
    Files.createDirectories(file.getParent());
    try (Writer writer = new OutputStreamWriter(Files.newOutputStream(file), charset)) {
      write(project, writer, basePath, patches, commitContext, includeBinaries);
    }
  }

  private static void write(@NotNull Project project,
                            @NotNull Writer writer,
                            @Nullable Path basePath,
                            @NotNull List<? extends FilePatch> patches,
                            @Nullable CommitContext commitContext, boolean includeBinaries) throws IOException {
    String lineSeparator = CodeStyle.getSettings(project).getLineSeparator();
    UnifiedDiffWriter.write(project, basePath, patches, writer, lineSeparator, commitContext, null);
    if (includeBinaries) {
      BinaryPatchWriter.writeBinaries(basePath, ContainerUtil.findAll(patches, BinaryFilePatch.class), writer);
    }
  }

  /**
   * We used to generate patches in "svn style".
   * Patch files use two kinds of line separator: for header lines (that use system separators) and for content lines (that use separator from that line).
   * We assume that all lines in a file use same separators, see {@link TextFilePatch#getLineSeparator()}.
   * <p>
   * This style does not work for distributed version control (a.e. git, hg):
   * 1) They always use unix separators for header lines
   * 2) They might automatically convert separators for content lines (ex: core.autocrlf for git). If we create patch with CRLF content line separators, it will issue a warning for CR as "trailing space".
   * <p>
   * @see <a href=https://youtrack.jetbrains.com/issue/IDEA-40539>IDEA-40539</a>
   */
  public static boolean shouldForceUnixLineSeparator(@Nullable Project project) {
    if (project == null) return true;
    return ContainerUtil.exists(ProjectLevelVcsManager.getInstance(project).getAllActiveVcss(), vcs -> vcs.getType() == distributed);
  }

  public static void writeAsPatchToClipboard(@NotNull Project project,
                                             @NotNull List<? extends FilePatch> patches,
                                             @NotNull Path basePath,
                                             @Nullable CommitContext commitContext) throws IOException {
    StringWriter writer = new StringWriter();
    write(project, writer, basePath, patches, commitContext, true);
    CopyPasteManager.getInstance().setContents(new StringSelection(writer.toString()));
  }

  /**
   * @deprecated Use {@link #calculateBaseDirForWritingPatch}
   */
  @Deprecated(forRemoval = true)
  public static @NotNull VirtualFile calculateBaseForWritingPatch(@NotNull Project project, @NotNull Collection<? extends Change> changes) {
    File commonAncestor = ChangesUtil.findCommonAncestor(changes);
    if (commonAncestor == null || ChangesUtil.getAffectedVcses(changes, project).size() != 1) {
      return project.getBaseDir();
    }
    VirtualFile vcsRoot = VcsUtil.getVcsRootFor(project, VcsUtil.getFilePath(commonAncestor));
    return vcsRoot == null ? project.getBaseDir() : vcsRoot;
  }

  public static @NotNull Path calculateBaseDirForWritingPatch(@NotNull Project project, @NotNull Collection<? extends Change> changes) {
    File commonAncestor = ChangesUtil.findCommonAncestor(changes);
    VirtualFile vcsRoot;
    if (commonAncestor == null || ChangesUtil.getAffectedVcses(changes, project).size() != 1) {
      vcsRoot = null;
    }
    else {
      vcsRoot = VcsUtil.getVcsRootFor(project, VcsUtil.getFilePath(commonAncestor));
    }
    return vcsRoot == null ? ProjectKt.getStateStore(project).getProjectBasePath() : vcsRoot.toNioPath();
  }
}

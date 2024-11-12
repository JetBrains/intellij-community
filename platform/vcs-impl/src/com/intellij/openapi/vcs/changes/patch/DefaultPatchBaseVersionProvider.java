// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsRunnable;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.vcs.VcsBundle.message;

@ApiStatus.Internal
public final class DefaultPatchBaseVersionProvider {
  private static final Logger LOG = Logger.getInstance(DefaultPatchBaseVersionProvider.class);
  private static final String ourRevisionPatternTemplate = "\\(revision (%s)\\)"; // NON-NLS

  @CalledInAny
  public static void getBaseVersionContent(@NotNull Project project,
                                           @NotNull String versionId,
                                           @NotNull VirtualFile file,
                                           @NotNull FilePath pathBeforeRename,
                                           @NotNull Processor<? super @NotNull String> processor) throws VcsException {
    runWithModalProgressIfNeeded(project, message("progress.text.loading.patch.base.revision"), () -> {
      AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
      if (vcs == null) return;

      final VcsHistoryProvider historyProvider = vcs.getVcsHistoryProvider();
      if (historyProvider == null) return;

      String contentByRevisionId = loadContentByRevisionId(versionId, file, pathBeforeRename, vcs);
      String content = contentByRevisionId != null ? contentByRevisionId : findContentInFileHistory(versionId, file, pathBeforeRename, vcs);
      if (content != null) {
        processor.process(content);
      }
    });
  }

  @Nullable
  private static String loadContentByRevisionId(@NotNull String versionId,
                                                @NotNull VirtualFile file,
                                                @NotNull FilePath pathBeforeRename,
                                                @NotNull AbstractVcs vcs) throws VcsException {
    String vcsRevisionString = parseVersionAsRevision(versionId, vcs);

    VcsHistoryProvider historyProvider = vcs.getVcsHistoryProvider();
    if (historyProvider instanceof VcsBaseRevisionAdviser revisionAdviser) {
      return revisionAdviser.getBaseVersionContent(pathBeforeRename, vcsRevisionString != null ? vcsRevisionString : versionId);
    }

    if (vcsRevisionString == null) return null;

    DiffProvider diffProvider = vcs.getDiffProvider();
    if (diffProvider == null) return null;

    VcsRevisionNumber revision = vcs.parseRevisionNumber(vcsRevisionString, pathBeforeRename);
    if (revision == null) return null;

    ContentRevision contentRevision = diffProvider.createFileContent(revision, file);
    if (contentRevision == null) return null;

    return contentRevision.getContent();
  }

  @Nullable
  private static String findContentInFileHistory(@NotNull String versionId,
                                                 @NotNull VirtualFile file,
                                                 @NotNull FilePath pathBeforeRename,
                                                 @NotNull AbstractVcs vcs) throws VcsException {
    Date versionDate = PatchDateParser.parseVersionAsDate(versionId);
    String vcsRevisionString = parseVersionAsRevision(versionId, vcs);
    VcsRevisionNumber revision = vcsRevisionString != null ? vcs.parseRevisionNumber(vcsRevisionString, pathBeforeRename) : null;

    Condition<VcsFileRevision> condition;
    if (revision != null) {
      condition = fileRevision -> fileRevision.getRevisionNumber().compareTo(revision) <= 0;
    }
    else if (versionDate != null) {
      condition = fileRevision -> {
        Date date = fileRevision instanceof VcsFileRevisionEx fileRevisionEx ? fileRevisionEx.getAuthorDate()
                                                                             : fileRevision.getRevisionDate();
        return date != null && date.compareTo(versionDate) <= 0;
      };
    }
    else {
      return null;
    }

    ProgressManager.progress2(message("loading.text2.file.history.progress"));

    List<VcsFileRevision> list = getRevisions(pathBeforeRename, vcs);
    // TODO: try to download more than one version?
    VcsFileRevision foundRevision = ContainerUtil.find(list, condition);
    if (foundRevision == null) return null;

    try {
      byte[] byteContent = foundRevision.loadContent();
      if (byteContent == null) return null;

      return LoadTextUtil.getTextByBinaryPresentation(byteContent, file, false, false).toString();
    }
    catch (IOException e) {
      LOG.warn(e);
      return null;
    }
  }

  @NotNull
  private static List<VcsFileRevision> getRevisions(@NotNull FilePath pathBeforeRename, @NotNull AbstractVcs vcs) throws VcsException {
    VcsHistoryProvider historyProvider = vcs.getVcsHistoryProvider();
    VcsHistorySession historySession = historyProvider != null ? historyProvider.createSessionFor(pathBeforeRename) : null;
    return historySession == null ? Collections.emptyList() : historySession.getRevisionList();
  }

  @Nullable
  private static String parseVersionAsRevision(@NotNull String versionId, @NotNull AbstractVcs vcs) {
    String vcsPattern = vcs.getRevisionPattern();
    if (vcsPattern != null) {
      Pattern revisionPattern = Pattern.compile(String.format(ourRevisionPatternTemplate, vcsPattern));
      Matcher revisionMatcher = revisionPattern.matcher(versionId);
      if (revisionMatcher.find()) {
        return revisionMatcher.group(1);
      }
    }
    return null;
  }

  private static void runWithModalProgressIfNeeded(@Nullable Project project,
                                                   @NotNull @NlsContexts.ProgressTitle String title,
                                                   @NotNull VcsRunnable task)
    throws VcsException {
    VcsUtil.runVcsProcessWithProgress(task, title, true, project);
  }
}

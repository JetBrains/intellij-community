// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
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
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.vcs.VcsBundle.message;

public final class DefaultPatchBaseVersionProvider {
  private static final Logger LOG = Logger.getInstance(DefaultPatchBaseVersionProvider.class);
  /**
   * @see com.intellij.openapi.diff.impl.patch.TextPatchBuilder
   */
  private static final Pattern ourTsPattern = Pattern.compile("\\(date ([0-9]+)\\)");
  private static final String ourRevisionPatternTemplate = "\\(revision (%s)\\)";

  @CalledInAny
  public static void getBaseVersionContent(@NotNull Project project,
                                           @NotNull String versionId,
                                           @NotNull VirtualFile file,
                                           @NotNull FilePath pathBeforeRename,
                                           @NotNull Processor<? super String> processor) throws VcsException {
    AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
    if (vcs == null) return;

    final VcsHistoryProvider historyProvider = vcs.getVcsHistoryProvider();
    if (historyProvider == null) return;

    String vcsRevisionString = parseVersionAsRevision(versionId, vcs);
    Date versionDate = parseVersionAsDate(versionId);
    if (vcsRevisionString == null && versionDate == null) return;

    runWithModalProgressIfNeeded(project, message("progress.text.loading.patch.base.revision"), () -> {
      VcsRevisionNumber revision = vcsRevisionString != null ? vcs.parseRevisionNumber(vcsRevisionString, pathBeforeRename) : null;
      if (revision == null && versionDate == null) return;

      if (revision != null) {
        boolean loadedExactRevision = false;
        if (historyProvider instanceof VcsBaseRevisionAdviser) {
          VcsBaseRevisionAdviser revisionAdviser = (VcsBaseRevisionAdviser)historyProvider;
          loadedExactRevision = revisionAdviser.getBaseVersionContent(pathBeforeRename, processor, revision.asString());
        }
        else {
          DiffProvider diffProvider = vcs.getDiffProvider();
          if (diffProvider != null) {
            ContentRevision fileContent = diffProvider.createFileContent(revision, file);
            loadedExactRevision = fileContent != null && !processor.process(fileContent.getContent());
          }
        }
        if (loadedExactRevision) return;
      }

      ProgressManager.progress2(message("loading.text2.file.history.progress"));
      VcsHistorySession historySession = historyProvider.createSessionFor(pathBeforeRename);
      if (historySession == null) return; // not found or cancelled

      List<VcsFileRevision> list = historySession.getRevisionList();
      if (list == null) return;

      // TODO: try to download more than one version
      VcsFileRevision foundRevision = ContainerUtil.find(list, fileRevision -> {
        if (revision != null) {
          return fileRevision.getRevisionNumber().compareTo(revision) <= 0;
        }
        else {
          Date date = fileRevision instanceof VcsFileRevisionEx ?
                      ((VcsFileRevisionEx)fileRevision).getAuthorDate() : fileRevision.getRevisionDate();
          return date != null && (date.before(versionDate) || date.equals(versionDate));
        }
      });

      if (foundRevision != null) {
        try {
          byte[] byteContent = foundRevision.loadContent();
          if (byteContent == null) return;

          CharSequence content = LoadTextUtil.getTextByBinaryPresentation(byteContent, file, false, false);
          processor.process(content.toString());
        }
        catch (IOException e) {
          LOG.warn(e);
        }
      }
    });
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

  @Nullable
  private static Date parseVersionAsDate(@NotNull String versionId) {
    try {
      Matcher tsMatcher = ourTsPattern.matcher(versionId);
      if (tsMatcher.find()) {
        long fromTsPattern = Long.parseLong(tsMatcher.group(1));
        return new Date(fromTsPattern);
      }
      else {
        return new Date(versionId);
      }
    }
    catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static void runWithModalProgressIfNeeded(@Nullable Project project, @NotNull String title, @NotNull VcsRunnable task)
    throws VcsException {
    VcsUtil.runVcsProcessWithProgress(task, title, true, project);
  }
}

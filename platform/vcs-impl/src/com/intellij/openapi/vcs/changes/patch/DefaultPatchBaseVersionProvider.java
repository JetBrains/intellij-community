/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.util.ThrowableRunnable;
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

public class DefaultPatchBaseVersionProvider {
  private static final Logger LOG = Logger.getInstance(DefaultPatchBaseVersionProvider.class);
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
      VcsRevisionNumber revision = null;
      if (vcsRevisionString != null) {
        revision = vcs.parseRevisionNumber(vcsRevisionString, pathBeforeRename);
        if (revision != null) {
          if (historyProvider instanceof VcsBaseRevisionAdviser) {
            VcsBaseRevisionAdviser revisionAdviser = (VcsBaseRevisionAdviser)historyProvider;
            boolean loadedExactRevision = revisionAdviser.getBaseVersionContent(pathBeforeRename, processor, revision.asString());
            if (loadedExactRevision) return;
          }
          else {
            DiffProvider diffProvider = vcs.getDiffProvider();
            if (diffProvider != null && pathBeforeRename.getVirtualFile() != null) {
              ContentRevision fileContent = diffProvider.createFileContent(revision, pathBeforeRename.getVirtualFile());
              boolean loadedExactRevision = fileContent != null && !processor.process(fileContent.getContent());
              if (loadedExactRevision) return;
            }
          }
        }
      }
      if (revision == null && versionDate == null) return;

      ProgressManager.progress2(message("loading.text2.file.history.progress"));
      final VcsHistorySession historySession = historyProvider.createSessionFor(pathBeforeRename);

      //if not found or cancelled
      if (historySession == null) return;
      final List<VcsFileRevision> list = historySession.getRevisionList();
      if (list == null) return;
      for (VcsFileRevision fileRevision : list) {
        boolean found;
        if (revision != null) {
          found = fileRevision.getRevisionNumber().compareTo(revision) <= 0;
        }
        else {
          final Date date = fileRevision instanceof VcsFileRevisionEx ?
                            ((VcsFileRevisionEx)fileRevision).getAuthorDate() : fileRevision.getRevisionDate();
          found = (date != null) && (date.before(versionDate) || date.equals(versionDate));
        }

        if (found) {
          try {
            byte[] byteContent = fileRevision.loadContent();
            if (byteContent == null) return;
            CharSequence content = LoadTextUtil.getTextByBinaryPresentation(byteContent, file, false, false);
            processor.process(content.toString());
            // TODO: try to download more than one version
            break;
          }
          catch (IOException e) {
            LOG.warn(e);
          }
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

  private static void runWithModalProgressIfNeeded(@Nullable Project project,
                                                   @NotNull String title,
                                                   @NotNull ThrowableRunnable<? extends VcsException> task) throws VcsException {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      VcsUtil.computeWithModalProgress(project, title, true, indicator -> {
        task.run();
        return null;
      });
    }
    else {
      task.run();
    }
  }
}

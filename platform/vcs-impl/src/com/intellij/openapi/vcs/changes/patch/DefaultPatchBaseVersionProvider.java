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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.vcs.VcsBundle.message;

public class DefaultPatchBaseVersionProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.patch.DefaultPatchBaseVersionProvider");
  private final static Pattern ourTsPattern = Pattern.compile("\\(date ([0-9]+)\\)");

  private final Project myProject;
  private final VirtualFile myFile;
  private final String myVersionId;
  private final Pattern myRevisionPattern;

  private final AbstractVcs myVcs;

  public DefaultPatchBaseVersionProvider(final Project project, final VirtualFile file, final String versionId) {
    myProject = project;
    myFile = file;
    myVersionId = versionId;
    myVcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(myFile);
    if (myVcs != null) {
      final String vcsPattern = myVcs.getRevisionPattern();
      if (vcsPattern != null) {
        myRevisionPattern = Pattern.compile("\\(revision (" + vcsPattern + ")\\)");
        return;
      }
    }
    myRevisionPattern = null;
  }

  @CalledInAny
  public void getBaseVersionContent(final FilePath filePath,
                                    final Processor<? super String> processor) throws VcsException {
    if (myVcs == null) {
      return;
    }
    final VcsHistoryProvider historyProvider = myVcs.getVcsHistoryProvider();
    if (historyProvider == null) return;

    VcsRevisionNumber revision = null;
    if (myRevisionPattern != null) {
      final Matcher matcher = myRevisionPattern.matcher(myVersionId);
      if (matcher.find()) {
        revision = myVcs.parseRevisionNumber(matcher.group(1), filePath);
        final VcsRevisionNumber finalRevision = revision;
        try {
          if (finalRevision != null) {
            final boolean loadedExactRevision =
              computeInBackgroundTask(myProject, message("progress.text2.loading.revision", finalRevision.asString()), true, () -> {
                if (historyProvider instanceof VcsBaseRevisionAdviser) {
                  VcsBaseRevisionAdviser revisionAdviser = (VcsBaseRevisionAdviser)historyProvider;
                  return revisionAdviser.getBaseVersionContent(filePath, processor, finalRevision.asString());
                }
                else {
                  DiffProvider diffProvider = myVcs.getDiffProvider();
                  if (diffProvider == null || filePath.getVirtualFile() == null) return false;

                  ContentRevision fileContent = diffProvider.createFileContent(finalRevision, filePath.getVirtualFile());
                  return fileContent != null && !processor.process(fileContent.getContent());
                }
              });
            if (loadedExactRevision) return;
          }
        }
        catch (ProcessCanceledException pce) {
          return;
        }
      }
    }

    Date versionDate = null;
    if (revision == null) {
      try {
        final Matcher tsMatcher = ourTsPattern.matcher(myVersionId);
        if (tsMatcher.find()) {
          final Long fromTsPattern = getFromTsPattern();
          if (fromTsPattern == null) return;
          versionDate = new Date(fromTsPattern);
        } else {
          versionDate = new Date(myVersionId);
        }
      }
      catch (IllegalArgumentException ex) {
        return;
      }
    }

    final VcsHistorySession historySession;
    try {
      historySession = computeInBackgroundTask(myProject, message("loading.file.history.progress"), true,
                                               () -> historyProvider.createSessionFor(filePath));
    }
    catch (ProcessCanceledException e) {
      return;
    }
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
          CharSequence content = LoadTextUtil.getTextByBinaryPresentation(fileRevision.loadContent(), myFile, false, false);
          processor.process(content.toString());
          // TODO: try to download more than one version
          break;
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
  }

  public boolean canProvideContent() {
    if (myVcs == null) {
      return false;
    }
    if ((myRevisionPattern != null) && myRevisionPattern.matcher(myVersionId).matches()) {
      return true;
    }
    if (ourTsPattern.matcher(myVersionId).matches()) return true;
    try {
      Date.parse(myVersionId);
    }
    catch (IllegalArgumentException ex) {
      return false;
    }
    return true;
  }

  public boolean hasVcs() {
    return myVcs != null;
  }

  private Long getFromTsPattern() {
    final String trimmed = myVersionId.trim();
    final String startPattern = "(date";
    final int start = trimmed.indexOf(startPattern);
    if (start >= 0) {
      String number = trimmed.substring(startPattern.length() + start);
      number = number.endsWith(")") ? number.substring(0, number.length() - 1) : number;
      try {
        return Long.parseLong(number.trim());
      }
      catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  public static <T, E extends Exception> T computeInBackgroundTask(@Nullable Project project,
                                                                   @Nls(capitalization = Nls.Capitalization.Title) @NotNull String title,
                                                                   boolean canBeCancelled,
                                                                   @NotNull ThrowableComputable<T, E> computable) throws E {
    return ProgressManager.getInstance().run(new Task.WithResult<T, E>(project, title, canBeCancelled) {
      @Override
      protected T compute(@NotNull ProgressIndicator indicator) throws E {
        return computable.compute();
      }
    });
  }
}

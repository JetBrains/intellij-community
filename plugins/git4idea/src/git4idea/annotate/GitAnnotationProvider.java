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
package git4idea.annotate;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.vfs.VcsFileSystem;
import com.intellij.openapi.vcs.vfs.VcsVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.AnnotationProviderEx;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitFileRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.annotate.GitFileAnnotation.LineInfo;
import git4idea.commands.GitCommand;
import git4idea.commands.GitSimpleHandler;
import git4idea.history.GitHistoryUtils;
import git4idea.i18n.GitBundle;
import git4idea.util.StringScanner;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class GitAnnotationProvider implements AnnotationProviderEx {
  private final Project myProject;
  @NonNls private static final String AUTHOR_KEY = "author";
  @NonNls private static final String COMMITTER_TIME_KEY = "committer-time";
  private static final Logger LOG = Logger.getInstance(GitAnnotationProvider.class);

  public GitAnnotationProvider(@NotNull Project project) {
    myProject = project;
  }

  public FileAnnotation annotate(@NotNull VirtualFile file) throws VcsException {
    return annotate(file, null);
  }

  public FileAnnotation annotate(@NotNull final VirtualFile file, final VcsFileRevision revision) throws VcsException {
    if (file.isDirectory()) {
      throw new VcsException("Cannot annotate a directory");
    }

    final FilePath currentFilePath = VcsUtil.getFilePath(file.getPath());
    final FilePath realFilePath;
    setProgressIndicatorText(GitBundle.message("getting.history", file.getName()));
    final List<VcsFileRevision> revisions = GitHistoryUtils.history(myProject, currentFilePath);
    if (revision == null) {
      realFilePath = GitHistoryUtils.getLastCommitName(myProject, currentFilePath);
    }
    else {
      realFilePath = ((GitFileRevision)revision).getPath();
    }
    setProgressIndicatorText(GitBundle.message("computing.annotation", file.getName()));
    VcsRevisionNumber revisionNumber = revision != null ? revision.getRevisionNumber() : null;

    return annotate(realFilePath, revisionNumber, revisions, file);
  }

  @NotNull
  @Override
  public FileAnnotation annotate(@NotNull final FilePath path, @NotNull final VcsRevisionNumber revision) throws VcsException {
    setProgressIndicatorText(GitBundle.message("getting.history", path.getName()));
    List<VcsFileRevision> revisions = GitHistoryUtils.history(myProject, path, null, revision);

    GitFileRevision fileRevision = new GitFileRevision(myProject, path, (GitRevisionNumber)revision);
    VcsVirtualFile file = new VcsVirtualFile(path.getPath(), fileRevision, VcsFileSystem.getInstance());

    setProgressIndicatorText(GitBundle.message("computing.annotation", path.getName()));
    return annotate(path, revision, revisions, file);
  }

  private static void setProgressIndicatorText(@Nullable String text) {
    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if (progress != null) progress.setText(text);
  }

  private GitFileAnnotation annotate(@NotNull final FilePath repositoryFilePath,
                                     @Nullable final VcsRevisionNumber revision,
                                     @NotNull final List<VcsFileRevision> revisions,
                                     @NotNull final VirtualFile file) throws VcsException {
    GitSimpleHandler h = new GitSimpleHandler(myProject, GitUtil.getGitRoot(repositoryFilePath), GitCommand.BLAME);
    h.setStdoutSuppressed(true);
    h.setCharset(file.getCharset());
    h.addParameters("--porcelain", "-l", "-t", "-w");
    if (revision == null) {
      h.addParameters("HEAD");
    }
    else {
      h.addParameters(revision.asString());
    }
    h.endOptions();
    h.addRelativePaths(repositoryFilePath);
    String output = h.run();
    return parseAnnotations(revision, file, output, revisions);
  }

  @NotNull
  private GitFileAnnotation parseAnnotations(@Nullable VcsRevisionNumber revision,
                                             @NotNull VirtualFile file,
                                             @NotNull String output,
                                             @NotNull List<VcsFileRevision> revisions) throws VcsException {
    try {
      StringBuilder content = new StringBuilder();
      List<LineInfo> lines = new ArrayList<>();
      HashMap<String, LineInfo> commits = new HashMap<>();
      final Map<VcsRevisionNumber, VcsFileRevision> historyAsMap = getRevisionMap(revisions);
      for (StringScanner s = new StringScanner(output); s.hasMoreData(); ) {
        // parse header line
        String commitHash = s.spaceToken();
        if (commitHash.equals(GitRevisionNumber.NOT_COMMITTED_HASH)) {
          commitHash = null;
        }
        s.spaceToken(); // skip revision line number
        String s1 = s.spaceToken();
        int lineNum = Integer.parseInt(s1);
        s.nextLine();
        // parse commit information
        LineInfo commit = commits.get(commitHash);
        if (commit != null || commitHash == null) {
          while (s.hasMoreData() && !s.startsWith('\t')) {
            s.nextLine();
          }
        }
        else {
          GitRevisionNumber revisionNumber = null;
          Date committerDate = null;
          String author = null;

          while (s.hasMoreData() && !s.startsWith('\t')) {
            String key = s.spaceToken();
            String value = s.line();
            if (AUTHOR_KEY.equals(key)) {
              author = value;
            }
            if (COMMITTER_TIME_KEY.equals(key)) {
              committerDate = GitUtil.parseTimestamp(value);
              revisionNumber = new GitRevisionNumber(commitHash, committerDate);
            }
          }
          commit = new LineInfo(committerDate, revisionNumber, (GitFileRevision)historyAsMap.get(revisionNumber), author);
          commits.put(commitHash, commit);
        }
        // parse line
        if (!s.hasMoreData()) {
          // if the file is empty, the next line will not start with tab and it will be
          // empty.
          continue;
        }
        s.skipChars(1);

        int expectedLineNum = lines.size() + 1;
        if (lineNum != expectedLineNum) {
          throw new VcsException("Adding for info for line " + lineNum + " but we are expecting it to be for " + expectedLineNum);
        }

        content.append(s.line(true));
        lines.add(commit);
      }
      return new GitFileAnnotation(myProject, file, revision, content.toString(), lines, revisions);
    }
    catch (Exception e) {
      LOG.error("Couldn't parse annotation: " + e, new Attachment("output.txt", output));
      throw new VcsException(e);
    }
  }

  @NotNull
  private static Map<VcsRevisionNumber, VcsFileRevision> getRevisionMap(@NotNull List<VcsFileRevision> revisions) {
    return ContainerUtil.map2Map(revisions,
                                 new Function<VcsFileRevision, Pair<VcsRevisionNumber, VcsFileRevision>>() {
                                   @Override
                                   public Pair<VcsRevisionNumber, VcsFileRevision> fun(VcsFileRevision revision) {
                                     return Pair.create(revision.getRevisionNumber(), revision);
                                   }
                                 });
  }
}

/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.annotate.*;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionDescription;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.*;
import git4idea.config.GitVersionSpecialty;
import git4idea.history.GitHistoryUtils;
import git4idea.i18n.GitBundle;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class GitFileAnnotation extends FileAnnotation {

  private static final Logger LOG = Logger.getInstance(GitFileAnnotation.class);
  private final Project myProject;
  @NotNull private final VirtualFile myFile;
  @NotNull private final GitVcs myVcs;
  @Nullable private final VcsRevisionNumber myBaseRevision;

  @NotNull private final List<LineInfo> myLines;
  @NotNull private final List<VcsRevisionDescription> myRevisions;

  private final LineAnnotationAspect DATE_ASPECT = new GitAnnotationAspect(LineAnnotationAspect.DATE, true) {
    @Override
    public String doGetValue(LineInfo info) {
      final Date date = info.getRevisionDate();
      return date == null ? "" : DateFormatUtil.formatPrettyDate(date);
    }
  };

  private final LineAnnotationAspect REVISION_ASPECT = new GitAnnotationAspect(LineAnnotationAspect.REVISION, false) {
    @Override
    protected String doGetValue(LineInfo lineInfo) {
      final GitRevisionNumber revision = lineInfo.getRevisionNumber();
      return revision == null ? "" : String.valueOf(revision.getShortRev());
    }
  };

  private final LineAnnotationAspect AUTHOR_ASPECT = new GitAnnotationAspect(LineAnnotationAspect.AUTHOR, true) {
    @Override
    protected String doGetValue(LineInfo lineInfo) {
      final String author = lineInfo.getAuthor();
      return author == null ? "" : author;
    }
  };

  public GitFileAnnotation(@NotNull final Project project,
                           @NotNull VirtualFile file,
                           @Nullable final VcsRevisionNumber revision,
                           @NotNull List<LineInfo> lines) {
    super(project);
    myProject = project;
    myFile = file;
    myVcs = ObjectUtils.assertNotNull(GitVcs.getInstance(myProject));
    myBaseRevision = revision == null ? (myVcs.getDiffProvider().getCurrentRevision(file)) : revision;

    myLines = lines;
    THashSet<VcsRevisionDescription> descriptions = new THashSet<>(lines, new TObjectHashingStrategy<VcsRevisionDescription>() {
      @Override
      public int computeHashCode(VcsRevisionDescription object) {
        return object.getRevisionNumber().asString().hashCode();
      }

      @Override
      public boolean equals(VcsRevisionDescription o1, VcsRevisionDescription o2) {
        return o1.getRevisionNumber().compareTo(o2.getRevisionNumber()) == 0;
      }
    });
    myRevisions = new ArrayList<>(descriptions);
    myRevisions.sort((o1, o2) -> o2.getRevisionDate().compareTo(o1.getRevisionDate()));
  }

  @Override
  public void dispose() {
  }

  @Override
  public LineAnnotationAspect[] getAspects() {
    return new LineAnnotationAspect[]{REVISION_ASPECT, DATE_ASPECT, AUTHOR_ASPECT};
  }

  @Override
  public String getToolTip(final int lineNumber) {
    if (myLines.size() <= lineNumber || lineNumber < 0) {
      return "";
    }
    final LineInfo info = myLines.get(lineNumber);
    return GitBundle.message("annotation.tool.tip", info.getRevisionNumber().asString(), info.getAuthor(),
                             DateFormatUtil.formatDateTime(info.getRevisionDate()));
  }

  @Nullable
  @Override
  public Computable<String> getToolTipAsync(int lineNumber) {
    VcsRevisionNumber number = getLineRevisionNumber(lineNumber);
    if (number == null) return null;
    VirtualFile rootFor = VcsUtil.getVcsRootFor(myProject, myFile);
    if (rootFor == null) return null;
    String noWalk = GitVersionSpecialty.NO_WALK_UNSORTED.existsIn(myVcs.getVersion()) ? "--no-walk=unsorted" : "--no-walk";

    return () -> {
      try {
        List<GitCommit> commits = GitHistoryUtils.history(myProject, rootFor, noWalk, number.asString());
        GitCommit item = ContainerUtil.getLastItem(commits);
        return item == null ? "" : getToolTip(lineNumber) + "\n\n" + item.getFullMessage();
      }
      catch (VcsException e) {
        LOG.error(e);
        return null;
      }
    };
  }

  @NotNull
  @Override
  public String getAnnotatedContent() {
    ContentRevision revision = GitContentRevision.createRevision(myFile, myBaseRevision, myProject);
    try {
      return revision.getContent();
    }
    catch (VcsException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public List<VcsFileRevision> getRevisions() {
    return null;
  }

  @Override
  public List<? extends VcsRevisionDescription> getRevisionDescriptions() {
    return myRevisions;
  }

  @Override
  public VcsFileRevision getRevisionByDescription(VcsRevisionDescription description) {
    LineInfo lineInfo = (LineInfo)description;
    FilePath path = VcsContextFactory.SERVICE.getInstance().createFilePath(lineInfo.getPath(), false);
    return new GitFileRevision(myProject, path, (GitRevisionNumber)description.getRevisionNumber());
  }

  @Override
  public boolean revisionsNotEmpty() {
    return !myRevisions.isEmpty();
  }

  @Override
  public AnnotationSourceSwitcher getAnnotationSourceSwitcher() {
    return null;
  }

  @Override
  public int getLineCount() {
    return myLines.size();
  }

  @Override
  public VcsRevisionNumber getLineRevisionNumber(final int lineNumber) {
    if (lineNumberCheck(lineNumber)) {
      return null;
    }
    return myLines.get(lineNumber).getRevisionNumber();
  }

  private boolean lineNumberCheck(int lineNumber) {
    return myLines.size() <= lineNumber || lineNumber < 0;
  }

  @Override
  public Date getLineDate(int lineNumber) {
    if (lineNumberCheck(lineNumber)) {
      return null;
    }
    return myLines.get(lineNumber).getRevisionDate();
  }

  /**
   * Get revision number for the line.
   */
  @Override
  public VcsRevisionNumber originalRevision(int lineNumber) {
    return getLineRevisionNumber(lineNumber);
  }

  public int getNumLines() {
    return myLines.size();
  }

  /**
   * Revision annotation aspect implementation
   */
  private abstract class GitAnnotationAspect extends LineAnnotationAspectAdapter {
    public GitAnnotationAspect(String id, boolean showByDefault) {
      super(id, showByDefault);
    }

    @Override
    public String getValue(int lineNumber) {
      if (lineNumberCheck(lineNumber)) {
        return "";
      }
      else {
        return doGetValue(myLines.get(lineNumber));
      }
    }

    protected abstract String doGetValue(LineInfo lineInfo);

    @Override
    protected void showAffectedPaths(int lineNum) {
      if (lineNum >= 0 && lineNum < myLines.size()) {
        LineInfo info = myLines.get(lineNum);
        ShowAllAffectedGenericAction.showSubmittedFiles(myProject, info.getRevisionNumber(), myFile, GitVcs.getKey());
      }
    }
  }

  /**
   * Line information
   */
  static class LineInfo implements VcsRevisionDescription {
    private final Date myDate;
    private final GitRevisionNumber myRevision;
    private final String myAuthor;
    private final String myPath;

    public LineInfo(Date date, GitRevisionNumber revision, String author, String path) {
      myDate = date;
      myRevision = revision;
      myAuthor = author;
      myPath = path;
    }

    public Date getRevisionDate() {
      return myDate;
    }

    public GitRevisionNumber getRevisionNumber() {
      return myRevision;
    }

    public String getAuthor() {
      return myAuthor;
    }

    @Nullable
    @Override
    public String getCommitMessage() {
      return null;
    }

    public String getPath() {
      return myPath;
    }
  }

  @NotNull
  @Override
  public VirtualFile getFile() {
    return myFile;
  }

  @Nullable
  @Override
  public VcsRevisionNumber getCurrentRevision() {
    return myBaseRevision;
  }

  @Override
  public VcsKey getVcsKey() {
    return GitVcs.getKey();
  }

  @Override
  public boolean isBaseRevisionChanged(VcsRevisionNumber number) {
    final VcsRevisionNumber currentCurrentRevision = myVcs.getDiffProvider().getCurrentRevision(myFile);
    return myBaseRevision != null && ! myBaseRevision.equals(currentCurrentRevision);
  }
}

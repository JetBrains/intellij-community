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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.annotate.*;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.text.DateFormatUtil;
import git4idea.GitFileRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitVcs;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.List;

public class GitFileAnnotation extends FileAnnotation {
  private final Project myProject;
  @NotNull private final VirtualFile myFile;
  @NotNull private final GitVcs myVcs;
  @Nullable private final VcsRevisionNumber myBaseRevision;

  @NotNull private final String myAnnotatedContent;
  @NotNull private final List<LineInfo> myLines;
  @NotNull private final List<VcsFileRevision> myRevisions;

  private final LineAnnotationAspect DATE_ASPECT = new GitAnnotationAspect(LineAnnotationAspect.DATE, true) {
    @Override
    public String doGetValue(LineInfo info) {
      final Date date = info.getDate();
      return date == null ? "" : DateFormatUtil.formatPrettyDate(date);
    }
  };

  private final LineAnnotationAspect REVISION_ASPECT = new GitAnnotationAspect(LineAnnotationAspect.REVISION, false) {
    @Override
    protected String doGetValue(LineInfo lineInfo) {
      final GitRevisionNumber revision = lineInfo.getRevision();
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
                           @NotNull String annotatedContent,
                           @NotNull List<LineInfo> lines,
                           @NotNull List<VcsFileRevision> revisions) {
    super(project);
    myProject = project;
    myFile = file;
    myVcs = ObjectUtils.assertNotNull(GitVcs.getInstance(myProject));
    myBaseRevision = revision == null ? (myVcs.getDiffProvider().getCurrentRevision(file)) : revision;

    myAnnotatedContent = annotatedContent;
    myLines = lines;
    myRevisions = revisions;
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
    VcsFileRevision fileRevision = info.myFileRevision;
    if (fileRevision != null) {
      return GitBundle.message("annotation.tool.tip", info.getRevision().asString(), info.getAuthor(), info.getDate(),
                               fileRevision.getCommitMessage());
    }
    else {
      return "";
    }
  }

  @Override
  public String getAnnotatedContent() {
    return myAnnotatedContent;
  }

  @Override
  public List<VcsFileRevision> getRevisions() {
    return myRevisions;
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
    return myLines.get(lineNumber).getRevision();
  }

  private boolean lineNumberCheck(int lineNumber) {
    return myLines.size() <= lineNumber || lineNumber < 0;
  }

  @Override
  public Date getLineDate(int lineNumber) {
    if (lineNumberCheck(lineNumber)) {
      return null;
    }
    return myLines.get(lineNumber).getDate();
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
        ShowAllAffectedGenericAction.showSubmittedFiles(myProject, info.getRevision(), myFile, GitVcs.getKey());
      }
    }
  }

  /**
   * Line information
   */
  static class LineInfo {
    private final Date myDate;
    private final GitRevisionNumber myRevision;
    private final GitFileRevision myFileRevision;
    private final String myAuthor;

    public LineInfo(Date date, GitRevisionNumber revision, GitFileRevision fileRevision, String author) {
      myDate = date;
      myRevision = revision;
      myFileRevision = fileRevision;
      myAuthor = author;
    }

    public Date getDate() {
      return myDate;
    }

    public GitRevisionNumber getRevision() {
      return myRevision;
    }

    public String getAuthor() {
      return myAuthor;
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

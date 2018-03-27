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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.LineAnnotationAspect;
import com.intellij.openapi.vcs.annotate.LineAnnotationAspectAdapter;
import com.intellij.openapi.vcs.annotate.ShowAllAffectedGenericAction;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitContentRevision;
import git4idea.GitFileRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitVcs;
import git4idea.i18n.GitBundle;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class GitFileAnnotation extends FileAnnotation {
  private final Project myProject;
  @NotNull private final VirtualFile myFile;
  @NotNull private final GitVcs myVcs;
  @Nullable private final VcsRevisionNumber myBaseRevision;

  @NotNull private final List<LineInfo> myLines;
  @Nullable private List<VcsFileRevision> myRevisions;
  @Nullable private TObjectIntHashMap<VcsRevisionNumber> myRevisionMap;

  private final LineAnnotationAspect DATE_ASPECT = new GitAnnotationAspect(LineAnnotationAspect.DATE, true) {
    @Override
    public String doGetValue(LineInfo info) {
      return DateFormatUtil.formatPrettyDate(info.getAuthorDate());
    }
  };

  private final LineAnnotationAspect REVISION_ASPECT = new GitAnnotationAspect(LineAnnotationAspect.REVISION, false) {
    @Override
    protected String doGetValue(LineInfo lineInfo) {
      return String.valueOf(lineInfo.getRevisionNumber().getShortRev());
    }
  };

  private final LineAnnotationAspect AUTHOR_ASPECT = new GitAnnotationAspect(LineAnnotationAspect.AUTHOR, true) {
    @Override
    protected String doGetValue(LineInfo lineInfo) {
      return lineInfo.getAuthor();
    }
  };

  public GitFileAnnotation(@NotNull Project project,
                           @NotNull VirtualFile file,
                           @Nullable VcsRevisionNumber revision,
                           @NotNull List<LineInfo> lines) {
    super(project);
    myProject = project;
    myFile = file;
    myVcs = GitVcs.getInstance(myProject);
    myBaseRevision = revision;
    myLines = lines;
  }

  public GitFileAnnotation(@NotNull GitFileAnnotation annotation) {
    this(annotation.getProject(), annotation.getFile(), annotation.getCurrentRevision(), annotation.getLines());
  }

  @Override
  public void dispose() {
  }

  @Override
  public LineAnnotationAspect[] getAspects() {
    return new LineAnnotationAspect[]{REVISION_ASPECT, DATE_ASPECT, AUTHOR_ASPECT};
  }

  @Nullable
  @Override
  public String getAnnotatedContent() {
    try {
      ContentRevision revision = GitContentRevision.createRevision(myFile, myBaseRevision, myProject);
      return revision.getContent();
    }
    catch (VcsException e) {
      return null;
    }
  }

  @Override
  public List<VcsFileRevision> getRevisions() {
    return myRevisions;
  }

  public void setRevisions(@NotNull List<VcsFileRevision> revisions) {
    myRevisions = revisions;

    myRevisionMap = new TObjectIntHashMap<>();
    for (int i = 0; i < myRevisions.size(); i++) {
      myRevisionMap.put(myRevisions.get(i).getRevisionNumber(), i);
    }
  }

  @Override
  public int getLineCount() {
    return myLines.size();
  }

  @Nullable
  public LineInfo getLineInfo(int lineNumber) {
    if (lineNumberCheck(lineNumber)) return null;
    return myLines.get(lineNumber);
  }

  @Nullable
  @Override
  public String getToolTip(int lineNumber) {
    LineInfo lineInfo = getLineInfo(lineNumber);
    if (lineInfo == null) return null;

    GitRevisionNumber revisionNumber = lineInfo.getRevisionNumber();

    VcsFileRevision fileRevision = null;
    if (myRevisions != null && myRevisionMap != null &&
        myRevisionMap.contains(revisionNumber)) {
      fileRevision = myRevisions.get(myRevisionMap.get(revisionNumber));
    }

    String commitMessage = fileRevision != null ? fileRevision.getCommitMessage() : lineInfo.getSubject() + "\n...";
    return GitBundle.message("annotation.tool.tip", revisionNumber.asString(), lineInfo.getAuthor(),
                             DateFormatUtil.formatDateTime(lineInfo.getAuthorDate()), commitMessage);
  }

  @Nullable
  @Override
  public VcsRevisionNumber getLineRevisionNumber(int lineNumber) {
    LineInfo lineInfo = getLineInfo(lineNumber);
    return lineInfo != null ? lineInfo.getRevisionNumber() : null;
  }

  @Nullable
  @Override
  public Date getLineDate(int lineNumber) {
    LineInfo lineInfo = getLineInfo(lineNumber);
    return lineInfo != null ? lineInfo.getAuthorDate() : null;
  }

  private boolean lineNumberCheck(int lineNumber) {
    return myLines.size() <= lineNumber || lineNumber < 0;
  }

  @NotNull
  public List<LineInfo> getLines() {
    return myLines;
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

  static class LineInfo {
    @NotNull private final Project myProject;
    @NotNull private final GitRevisionNumber myRevision;
    @NotNull private final FilePath myFilePath;
    @Nullable private final GitRevisionNumber myPreviousRevision;
    @Nullable private final FilePath myPreviousFilePath;
    @NotNull private final Date myCommitterDate;
    @NotNull private final Date myAuthorDate;
    @NotNull private final VcsUser myAuthor;
    @NotNull private final String mySubject;

    public LineInfo(@NotNull Project project,
                    @NotNull GitRevisionNumber revision,
                    @NotNull FilePath path,
                    @NotNull Date committerDate,
                    @NotNull Date authorDate,
                    @NotNull VcsUser author,
                    @NotNull String subject,
                    @Nullable GitRevisionNumber previousRevision,
                    @Nullable FilePath previousPath) {
      myProject = project;
      myRevision = revision;
      myFilePath = path;
      myPreviousRevision = previousRevision;
      myPreviousFilePath = previousPath;
      myCommitterDate = committerDate;
      myAuthorDate = authorDate;
      myAuthor = author;
      mySubject = subject;
    }

    @NotNull
    public GitRevisionNumber getRevisionNumber() {
      return myRevision;
    }

    @NotNull
    public FilePath getFilePath() {
      return myFilePath;
    }

    @NotNull
    public VcsFileRevision getFileRevision() {
      return new GitFileRevision(myProject, myFilePath, myRevision);
    }

    @Nullable
    public VcsFileRevision getPreviousFileRevision() {
      if (myPreviousRevision == null || myPreviousFilePath == null) return null;
      return new GitFileRevision(myProject, myPreviousFilePath, myPreviousRevision);
    }

    @NotNull
    public Date getCommitterDate() {
      return myCommitterDate;
    }

    @NotNull
    public Date getAuthorDate() {
      return myAuthorDate;
    }

    @NotNull
    public String getAuthor() {
      return myAuthor.getName();
    }

    @NotNull
    public String getSubject() {
      return mySubject;
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


  @Nullable
  @Override
  public CurrentFileRevisionProvider getCurrentFileRevisionProvider() {
    return (lineNumber) -> {
      LineInfo lineInfo = getLineInfo(lineNumber);
      return lineInfo != null ? lineInfo.getFileRevision() : null;
    };
  }

  @Nullable
  @Override
  public PreviousFileRevisionProvider getPreviousFileRevisionProvider() {
    return new PreviousFileRevisionProvider() {
      @Nullable
      @Override
      public VcsFileRevision getPreviousRevision(int lineNumber) {
        LineInfo lineInfo = getLineInfo(lineNumber);
        if (lineInfo == null) return null;

        VcsFileRevision previousFileRevision = lineInfo.getPreviousFileRevision();
        if (previousFileRevision != null) return previousFileRevision;

        GitRevisionNumber revisionNumber = lineInfo.getRevisionNumber();
        if (myRevisions != null && myRevisionMap != null &&
            myRevisionMap.contains(revisionNumber)) {
          int index = myRevisionMap.get(revisionNumber);
          if (index + 1 < myRevisions.size()) {
            return myRevisions.get(index + 1);
          }
        }

        return null;
      }

      @Nullable
      @Override
      public VcsFileRevision getLastRevision() {
        if (myBaseRevision instanceof GitRevisionNumber) {
          return new GitFileRevision(myProject, VcsUtil.getFilePath(myFile), (GitRevisionNumber)myBaseRevision);
        }
        else {
          return ContainerUtil.getFirstItem(getRevisions());
        }
      }
    };
  }

  @Nullable
  @Override
  public AuthorsMappingProvider getAuthorsMappingProvider() {
    Map<VcsRevisionNumber, String> authorsMap = new HashMap<>();
    for (int i = 0; i < getLineCount(); i++) {
      LineInfo lineInfo = getLineInfo(i);
      if (lineInfo == null) continue;

      if (!authorsMap.containsKey(lineInfo.getRevisionNumber())) {
        authorsMap.put(lineInfo.getRevisionNumber(), lineInfo.getAuthor());
      }
    }

    return () -> authorsMap;
  }

  @Nullable
  @Override
  public RevisionsOrderProvider getRevisionsOrderProvider() {
    ContainerUtil.KeyOrderedMultiMap<Date, VcsRevisionNumber> dates = new ContainerUtil.KeyOrderedMultiMap<>();

    for (int i = 0; i < getLineCount(); i++) {
      LineInfo lineInfo = getLineInfo(i);
      if (lineInfo == null) continue;

      VcsRevisionNumber number = lineInfo.getRevisionNumber();
      Date date = lineInfo.getCommitterDate();

      dates.putValue(date, number);
    }

    List<List<VcsRevisionNumber>> orderedRevisions = new ArrayList<>();
    NavigableSet<Date> orderedDates = dates.navigableKeySet();
    for (Date date : orderedDates.descendingSet()) {
      Collection<VcsRevisionNumber> revisionNumbers = dates.get(date);
      orderedRevisions.add(new ArrayList<>(revisionNumbers));
    }

    return () -> orderedRevisions;
  }
}

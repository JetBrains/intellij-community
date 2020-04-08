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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.LineAnnotationAspect;
import com.intellij.openapi.vcs.annotate.LineAnnotationAspectAdapter;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.impl.AbstractVcsHelperImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.impl.CommonUiProperties;
import com.intellij.vcs.log.impl.VcsLogApplicationSettings;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitContentRevision;
import git4idea.GitFileRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitVcs;
import git4idea.changes.GitCommittedChangeList;
import git4idea.changes.GitCommittedChangeListProvider;
import git4idea.log.GitCommitTooltipLinkHandler;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class GitFileAnnotation extends FileAnnotation {
  private final Project myProject;
  @NotNull private final VirtualFile myFile;
  @NotNull private final FilePath myFilePath;
  @NotNull private final GitVcs myVcs;
  @Nullable private final VcsRevisionNumber myBaseRevision;

  @NotNull private final List<LineInfo> myLines;
  @Nullable private List<VcsFileRevision> myRevisions;
  @Nullable private TObjectIntHashMap<VcsRevisionNumber> myRevisionMap;
  @NotNull private final Map<VcsRevisionNumber, String> myCommitMessageMap = new HashMap<>();
  private final VcsLogApplicationSettings myLogSettings = ApplicationManager.getApplication().getService(VcsLogApplicationSettings.class);

  private final LineAnnotationAspect DATE_ASPECT = new GitAnnotationAspect(LineAnnotationAspect.DATE, true) {
    @Override
    public String doGetValue(LineInfo info) {
      Date date = getDate(info);
      return FileAnnotation.formatDate(date);
    }
  };

  private final LineAnnotationAspect REVISION_ASPECT = new GitAnnotationAspect(LineAnnotationAspect.REVISION, false) {
    @Override
    protected String doGetValue(LineInfo lineInfo) {
      return lineInfo.getRevisionNumber().getShortRev();
    }
  };

  private final LineAnnotationAspect AUTHOR_ASPECT = new GitAnnotationAspect(LineAnnotationAspect.AUTHOR, true) {
    @Override
    protected String doGetValue(LineInfo lineInfo) {
      return lineInfo.getAuthor();
    }
  };
  private final VcsLogUiProperties.PropertiesChangeListener myLogSettingChangeListener = this::onLogSettingChange;

  public GitFileAnnotation(@NotNull Project project,
                           @NotNull VirtualFile file,
                           @Nullable VcsRevisionNumber revision,
                           @NotNull List<LineInfo> lines) {
    super(project);
    myProject = project;
    myFile = file;
    myFilePath = VcsUtil.getFilePath(file);
    myVcs = GitVcs.getInstance(myProject);
    myBaseRevision = revision;
    myLines = lines;
    myLogSettings.addChangeListener(myLogSettingChangeListener);
  }

  public <T> void onLogSettingChange(@NotNull VcsLogUiProperties.VcsLogUiProperty<T> property) {
    if (property.equals(CommonUiProperties.PREFER_COMMIT_DATE)) {
      reload(null);
    }
  }

  public GitFileAnnotation(@NotNull GitFileAnnotation annotation) {
    this(annotation.getProject(), annotation.getFile(), annotation.getCurrentRevision(), annotation.getLines());
  }

  @Override
  public void dispose() {
    myLogSettings.removeChangeListener(myLogSettingChangeListener);
  }

  @Override
  public LineAnnotationAspect @NotNull [] getAspects() {
    return new LineAnnotationAspect[]{REVISION_ASPECT, DATE_ASPECT, AUTHOR_ASPECT};
  }

  @NotNull
  private Date getDate(LineInfo info) {
    return Boolean.TRUE.equals(myLogSettings.get(CommonUiProperties.PREFER_COMMIT_DATE)) ? info.getCommitterDate() : info.getAuthorDate();
  }

  @Nullable
  @Override
  public String getAnnotatedContent() {
    try {
      ContentRevision revision = GitContentRevision.createRevision(myFilePath, myBaseRevision, myProject);
      return revision.getContent();
    }
    catch (VcsException e) {
      return null;
    }
  }

  @Nullable
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

  public void setCommitMessage(@NotNull VcsRevisionNumber revisionNumber, @NotNull String message) {
    myCommitMessageMap.put(revisionNumber, message);
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
    return getToolTip(lineNumber, false);
  }

  @Nullable
  @Override
  public String getHtmlToolTip(int lineNumber) {
    return getToolTip(lineNumber, true);
  }

  @Nullable
  private String getToolTip(int lineNumber, boolean asHtml) {
    LineInfo lineInfo = getLineInfo(lineNumber);
    if (lineInfo == null) return null;

    AnnotationTooltipBuilder atb = new AnnotationTooltipBuilder(myProject, asHtml);
    GitRevisionNumber revisionNumber = lineInfo.getRevisionNumber();

    atb.appendRevisionLine(revisionNumber, it -> GitCommitTooltipLinkHandler.createLink(it.asString(), it));
    atb.appendLine(VcsBundle.message("commit.description.tooltip.author", lineInfo.getAuthor()));
    atb.appendLine(VcsBundle.message("commit.description.tooltip.date", DateFormatUtil.formatDateTime(getDate(lineInfo))));

    if (!myFilePath.equals(lineInfo.getFilePath())) {
      String path = FileUtil.getLocationRelativeToUserHome(lineInfo.getFilePath().getPresentableUrl());
      atb.appendLine(VcsBundle.message("commit.description.tooltip.path", path));
    }

    String commitMessage = getCommitMessage(revisionNumber);
    if (commitMessage == null) commitMessage = lineInfo.getSubject() + "\n...";
    atb.appendCommitMessageBlock(commitMessage);

    return atb.toString();
  }

  @Nullable
  public String getCommitMessage(@NotNull VcsRevisionNumber revisionNumber) {
    if (myRevisions != null && myRevisionMap != null &&
        myRevisionMap.contains(revisionNumber)) {
      VcsFileRevision fileRevision = myRevisions.get(myRevisionMap.get(revisionNumber));
      return fileRevision.getCommitMessage();
    }
    return myCommitMessageMap.get(revisionNumber);
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
    return lineInfo != null ? getDate(lineInfo) : null;
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
    GitAnnotationAspect(String id, boolean showByDefault) {
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

        AbstractVcsHelperImpl.loadAndShowCommittedChangesDetails(myProject, info.getRevisionNumber(), myFilePath, () -> getRevisionsChangesProvider().getChangesIn(lineNum));
      }
    }
  }

  static class CommitInfo {
    @NotNull private final Project myProject;
    @NotNull private final GitRevisionNumber myRevision;
    @NotNull private final FilePath myFilePath;
    @Nullable private final GitRevisionNumber myPreviousRevision;
    @Nullable private final FilePath myPreviousFilePath;
    @NotNull private final Date myCommitterDate;
    @NotNull private final Date myAuthorDate;
    @NotNull private final VcsUser myAuthor;
    @NotNull private final String mySubject;

    CommitInfo(@NotNull Project project,
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

  public static class LineInfo {
    @NotNull private final CommitInfo myCommitInfo;
    private final int myLineNumber;
    private final int myOriginalLineNumber;

    LineInfo(@NotNull CommitInfo commitInfo, int lineNumber, int originalLineNumber) {
      this.myCommitInfo = commitInfo;
      this.myLineNumber = lineNumber;
      this.myOriginalLineNumber = originalLineNumber;
    }

    public int getLineNumber() {
      return myLineNumber;
    }

    public int getOriginalLineNumber() {
      return myOriginalLineNumber;
    }

    @NotNull
    public GitRevisionNumber getRevisionNumber() {
      return myCommitInfo.getRevisionNumber();
    }

    @NotNull
    public FilePath getFilePath() {
      return myCommitInfo.getFilePath();
    }

    @NotNull
    public VcsFileRevision getFileRevision() {
      return myCommitInfo.getFileRevision();
    }

    @Nullable
    public VcsFileRevision getPreviousFileRevision() {
      return myCommitInfo.getPreviousFileRevision();
    }

    @NotNull
    public Date getCommitterDate() {
      return myCommitInfo.getCommitterDate();
    }

    @NotNull
    public Date getAuthorDate() {
      return myCommitInfo.getAuthorDate();
    }

    @NotNull
    public String getAuthor() {
      return myCommitInfo.getAuthor();
    }

    @NotNull
    public String getSubject() {
      return myCommitInfo.getSubject();
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
  public boolean isBaseRevisionChanged(@NotNull VcsRevisionNumber number) {
    if (!myFile.isInLocalFileSystem()) return false;
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
          return new GitFileRevision(myProject, myFilePath, (GitRevisionNumber)myBaseRevision);
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

  /**
   * Do not use {@link CommittedChangesProvider#getOneList} to avoid unnecessary rename detections (as we know FilePath already)
   */
  @NotNull
  @Override
  public RevisionChangesProvider getRevisionsChangesProvider() {
    return (lineNumber) -> {
      LineInfo lineInfo = getLineInfo(lineNumber);
      if (lineInfo == null) return null;

      GitRepository repository = GitRepositoryManager.getInstance(myProject).getRepositoryForFile(lineInfo.getFilePath());
      if (repository == null) return null;

      GitCommittedChangeList changeList =
        GitCommittedChangeListProvider.getCommittedChangeList(myProject, repository.getRoot(), lineInfo.getRevisionNumber());
      return Pair.create(changeList, lineInfo.getFilePath());
    };
  }
}

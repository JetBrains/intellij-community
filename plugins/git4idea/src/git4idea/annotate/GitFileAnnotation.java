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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.*;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.EventDispatcher;
import com.intellij.util.text.SyncDateFormat;
import git4idea.GitRevisionNumber;
import git4idea.actions.GitShowAllSubmittedFilesAction;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Git file annotation implementation
 * <p/>
 * Based on the JetBrains SVNAnnotationProvider.
 */
public class GitFileAnnotation implements FileAnnotation {
  private final static Logger LOG = Logger.getInstance("#git4idea.annotate.GitFileAnnotation");

  /**
   * the format of the date shown in annotations
   */
  private static final SyncDateFormat DATE_FORMAT = new SyncDateFormat(SimpleDateFormat.getDateInstance(SimpleDateFormat.SHORT));
  /**
   * annotated content
   */
  private final StringBuffer myContentBuffer = new StringBuffer();
  /**
   * The currently annotated lines
   */
  private final ArrayList<LineInfo> myLines = new ArrayList<LineInfo>();
  /**
   * The project reference
   */
  private final Project myProject;
  /**
   * Annotation change listeners
   */
  private final EventDispatcher<AnnotationListener> myListeners = EventDispatcher.create(AnnotationListener.class);
  /**
   * Map from revision numbers to revisions
   */
  private final Map<VcsRevisionNumber, VcsFileRevision> myRevisionMap = new HashMap<VcsRevisionNumber, VcsFileRevision>();
  /**
   * listener for file system events
   */
  private final VirtualFileAdapter myFileListener;

  private final MyFileStatusListener myFileStatusListener;

  /**
   * the virtual file for which annotations are generated
   */
  private final VirtualFile myFile;
  /**
   * If true, file system is monitored for changes
   */
  private final boolean myMonitorFlag;

  private final LineAnnotationAspect DATE_ASPECT = new GitAnnotationAspect() {
    public String doGetValue(LineInfo info) {
      final Date date = info.getDate();
      return date == null ? "" : DATE_FORMAT.format(date);
    }
  };

  private final LineAnnotationAspect REVISION_ASPECT = new GitAnnotationAspect() {
    @Override
    protected String doGetValue(LineInfo lineInfo) {
      final GitRevisionNumber revision = lineInfo.getRevision();
      return revision == null ? "" : String.valueOf(revision.getShortRev());
    }
  };

  private final LineAnnotationAspect AUTHOR_ASPECT = new GitAnnotationAspect() {
    @Override
    protected String doGetValue(LineInfo lineInfo) {
      final String author = lineInfo.getAuthor();
      return author == null ? "" : author;
    }
  };

  /**
   * A constructor
   *
   * @param project     the project of annotation provider
   * @param file        the git root
   * @param monitorFlag if false the file system will not be listened for changes (used for annotated files from the repository).
   */
  public GitFileAnnotation(@NotNull final Project project, @NotNull VirtualFile file, final boolean monitorFlag) {
    myProject = project;
    myFile = file;
    myMonitorFlag = monitorFlag;
    if (myMonitorFlag) {
      myFileListener = new VirtualFileAdapter() {
        @Override
        public void contentsChanged(final VirtualFileEvent event) {
          if (myFile != event.getFile()) return;
          if (!event.isFromRefresh()) return;
          fireAnnotationChanged();
        }
      };
      VirtualFileManager.getInstance().addVirtualFileListener(myFileListener);
      myFileStatusListener = new MyFileStatusListener();
      FileStatusManager.getInstance(myProject).addFileStatusListener(myFileStatusListener);
    }
    else {
      myFileListener = null;
      myFileStatusListener = null;
    }
  }

  /**
   * Add revisions to the list (from log)
   *
   * @param revisions revisions to add
   */
  public void addLogEntries(List<VcsFileRevision> revisions) {
    for (VcsFileRevision vcsFileRevision : revisions) {
      myRevisionMap.put(vcsFileRevision.getRevisionNumber(), vcsFileRevision);
    }
  }

  /**
   * Fire annotation changed event
   */
  private void fireAnnotationChanged() {
    myListeners.getMulticaster().onAnnotationChanged();
  }

  /**
   * {@inheritDoc}
   */
  public void addListener(AnnotationListener listener) {
    myListeners.addListener(listener);
  }

  /**
   * {@inheritDoc}
   */
  public void removeListener(AnnotationListener listener) {
    myListeners.removeListener(listener);
  }

  /**
   * {@inheritDoc}
   */
  public void dispose() {
    if (myMonitorFlag) {
      VirtualFileManager.getInstance().removeVirtualFileListener(myFileListener);
      FileStatusManager.getInstance(myProject).removeFileStatusListener(myFileStatusListener);
    }
  }

  /**
   * {@inheritDoc}
   */
  public LineAnnotationAspect[] getAspects() {
    return new LineAnnotationAspect[]{REVISION_ASPECT, DATE_ASPECT, AUTHOR_ASPECT};
  }

  /**
   * {@inheritDoc}
   */
  public String getToolTip(final int lineNumber) {
    if (myLines.size() <= lineNumber || lineNumber < 0) {
      return "";
    }
    final LineInfo info = myLines.get(lineNumber);
    if (info == null) {
      return "";
    }
    VcsFileRevision fileRevision = myRevisionMap.get(info.getRevision());
    if (fileRevision != null) {
      return GitBundle
        .message("annotation.tool.tip", info.getRevision().asString(), fileRevision.getAuthor(), fileRevision.getRevisionDate(),
                 fileRevision.getCommitMessage());
    }
    else {
      return "";
    }
  }

  /**
   * {@inheritDoc}
   */
  public String getAnnotatedContent() {
    return myContentBuffer.toString();
  }

  /**
   * {@inheritDoc}
   */
  public List<VcsFileRevision> getRevisions() {
    final List<VcsFileRevision> result = new ArrayList<VcsFileRevision>(myRevisionMap.values());
    Collections.sort(result, new Comparator<VcsFileRevision>() {
      public int compare(final VcsFileRevision o1, final VcsFileRevision o2) {
        return -1 * o1.getRevisionNumber().compareTo(o2.getRevisionNumber());
      }
    });
    return result;
  }

  public boolean revisionsNotEmpty() {
    return ! myRevisionMap.isEmpty();
  }

  public AnnotationSourceSwitcher getAnnotationSourceSwitcher() {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public VcsRevisionNumber getLineRevisionNumber(final int lineNumber) {
    if (myLines.size() <= lineNumber || lineNumber < 0 || myLines.get(lineNumber) == null) {
      return null;
    }
    final LineInfo lineInfo = myLines.get(lineNumber);
    return lineInfo == null ? null : lineInfo.getRevision();
  }

  /**
   * Get revision number for the line.
   */
  public VcsRevisionNumber originalRevision(int lineNumber) {
    return getLineRevisionNumber(lineNumber);
  }

  /**
   * Append line info
   *
   * @param date       the revision date
   * @param revision   the revision number
   * @param author     the author
   * @param line       the line content
   * @param lineNumber the line number for revision
   * @throws VcsException in case when line could not be processed
   */
  public void appendLineInfo(final Date date,
                             final GitRevisionNumber revision,
                             final String author,
                             final String line,
                             final long lineNumber) throws VcsException {
    int expectedLineNo = myLines.size() + 1;
    if (lineNumber != expectedLineNo) {
      throw new VcsException("Adding for info for line " + lineNumber + " but we are expecting it to be for " + expectedLineNo);
    }
    myLines.add(new LineInfo(date, revision, author));
    myContentBuffer.append(line);
  }

  /**
   * Revision annotation aspect implementation
   */
  private abstract class GitAnnotationAspect extends LineAnnotationAspectAdapter {
    public String getValue(int lineNumber) {
      if (myLines.size() <= lineNumber || lineNumber < 0 || myLines.get(lineNumber) == null) {
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
        final LineInfo info = myLines.get(lineNum);
        VcsFileRevision revision = myRevisionMap.get(info.getRevision());
        if (revision != null) {
          GitShowAllSubmittedFilesAction.showSubmittedFiles(myProject, revision, myFile);
        }
      }
    }
  }

  /**
   * Line information
   */
  static class LineInfo {
    /**
     * date of the change
     */
    private final Date myDate;
    /**
     * revision number
     */
    private final GitRevisionNumber myRevision;
    /**
     * the author of the change
     */
    private final String myAuthor;

    /**
     * A constructor
     *
     * @param date     date of the change
     * @param revision revision number
     * @param author   the author of the change
     */
    public LineInfo(final Date date, final GitRevisionNumber revision, final String author) {
      myDate = date;
      myRevision = revision;
      myAuthor = author;
    }

    /**
     * @return the revision date
     */
    public Date getDate() {
      return myDate;
    }

    /**
     * @return the revision number
     */
    public GitRevisionNumber getRevision() {
      return myRevision;
    }

    /**
     * @return the author of the change
     */
    public String getAuthor() {
      return myAuthor;
    }
  }

  private class MyFileStatusListener implements FileStatusListener {
    public void fileStatusesChanged() {
    }

    public void fileStatusChanged(@NotNull VirtualFile virtualFile) {
      if (myFile.equals(virtualFile)) {
        checkAndFire();
      }
    }

    private void checkAndFire() {
      // for the case of commit changes... remove annotation gutter
      if (FileStatus.NOT_CHANGED.equals(FileStatusManager.getInstance(myProject).getStatus(myFile))) {
        fireAnnotationChanged();
      }
    }
  }
}

package git4idea;
/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the License.
 *
 * Author: Anatol Pomozov (Copyright 2008)
 * Copyright 2008 JetBrains s.r.o.
 */

import com.intellij.openapi.editor.EditorGutterAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.AnnotationListener;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.LineAnnotationAspect;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.EventDispatcher;
import com.intellij.util.text.SyncDateFormat;
import git4idea.actions.GitShowAllSubmittedFilesAction;
import git4idea.config.GitVcsSettings;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * Git file annotation implementation
 * <p/>
 * Based on the JetBrains SVNAnnotationProvider.
 */
public class GitFileAnnotation implements FileAnnotation {
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
  private final List<LineInfo> myLineInfos = new ArrayList<LineInfo>();
  /**
   * The project reference
   */
  private final Project myProject;
  /**
   * Git settings for annotation
   */
  @NotNull private final GitVcsSettings mySettings;
  /**
   * Annotation change listeners
   */
  private final EventDispatcher<AnnotationListener> myListeners = EventDispatcher.create(AnnotationListener.class);
  /**
   * Map from revision numbers to revisions
   */
  private final Map<VcsRevisionNumber, VcsFileRevision> myRevisionMap = new HashMap<VcsRevisionNumber, VcsFileRevision>();
  /**
   * listnere for file system events
   */
  private final VirtualFileAdapter myFileListener;
  /**
   * the virtual file for which annotations are generated
   */
  private final VirtualFile myFile;

  /**
   * Date annotation aspect
   */
  private final LineAnnotationAspect DATE_ASPECT = new LineAnnotationAspect() {
    public String getValue(int lineNumber) {
      if (myLineInfos.size() <= lineNumber || lineNumber < 0) {
        return "";
      }
      else {
        return DATE_FORMAT.format(myLineInfos.get(lineNumber).getDate());
      }
    }
  };
  /**
   * revsion annotation aspect
   */
  private final LineAnnotationAspect REVISION_ASPECT = new RevisionAnnotationAspect();
  /**
   * author annotation aspect
   */
  private final LineAnnotationAspect AUTHOR_ASPECT = new LineAnnotationAspect() {
    public String getValue(int lineNumber) {
      if (myLineInfos.size() <= lineNumber || lineNumber < 0) {
        return "";
      }
      else {
        return myLineInfos.get(lineNumber).getAuthor();
      }
    }
  };

  /**
   * A constructor
   *
   * @param project the project of annotation provider
   */
  public GitFileAnnotation(@NotNull final Project project, @NotNull GitVcsSettings settings, @NotNull VirtualFile file) {
    myProject = project;
    myFile = file;
    mySettings = settings;
    myFileListener = new VirtualFileAdapter() {
      @Override
      public void contentsChanged(final VirtualFileEvent event) {
        if (myFile != event.getFile()) return;
        if (!event.isFromRefresh()) return;
        fireAnnotationChanged();
      }
    };
    VirtualFileManager.getInstance().addVirtualFileListener(myFileListener);
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
    VirtualFileManager.getInstance().removeVirtualFileListener(myFileListener);
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
    if (myLineInfos.size() <= lineNumber || lineNumber < 0) {
      return "";
    }
    final LineInfo info = myLineInfos.get(lineNumber);
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

  /**
   * {@inheritDoc}
   */
  public VcsRevisionNumber getLineRevisionNumber(final int lineNumber) {
    if (myLineInfos.size() <= lineNumber || lineNumber < 0) {
      return null;
    }
    final LineInfo lineInfo = myLineInfos.get(lineNumber);
    return lineInfo == null ? null : lineInfo.getRevision();
  }

  /**
   * Append line info
   *
   * @param date       the revision date
   * @param revision   the revision number
   * @param author     the author
   * @param line       the line content
   * @param lineNumber the line number
   * @throws VcsException in case when line could not be processed
   */
  public void appendLineInfo(final Date date,
                             final GitRevisionNumber revision,
                             final String author,
                             final String line,
                             final long lineNumber) throws VcsException {
    int expectedLineNo = myLineInfos.size() + 1;
    if (lineNumber != expectedLineNo) {
      throw new VcsException("Adding for info for line " + lineNumber + " but we are expecting it to be for " + expectedLineNo);
    }
    myLineInfos.add(new LineInfo(date, revision, author));
    myContentBuffer.append(line);
    myContentBuffer.append("\n");
  }

  /**
   * Revision annotation aspec implementation
   */
  private class RevisionAnnotationAspect implements LineAnnotationAspect, EditorGutterAction {
    /**
     * {@inheritDoc}
     */
    public String getValue(int lineNumber) {
      if (myLineInfos.size() <= lineNumber || lineNumber < 0) {
        return "";
      }
      else {
        return String.valueOf(myLineInfos.get(lineNumber).getRevision().getShortRev());
      }
    }

    /**
     * {@inheritDoc}
     */
    public Cursor getCursor(final int lineNum) {
      return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
    }

    /**
     * {@inheritDoc}
     */
    public void doAction(int lineNum) {
      if (lineNum >= 0 && lineNum < myLineInfos.size()) {
        final LineInfo info = myLineInfos.get(lineNum);
        VcsFileRevision revision = myRevisionMap.get(info.getRevision());
        if (revision != null) {
          GitShowAllSubmittedFilesAction.showSubmittedFiles(myProject, mySettings, revision, myFile);
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
}

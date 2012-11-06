// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.provider.annotate;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.*;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgFileRevision;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.HgVcsMessages;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class HgAnnotation implements FileAnnotation {

  private static final Logger LOG = Logger.getInstance(HgAnnotation.class.getName());

  enum FIELD {
    USER, REVISION, DATE, LINE, CONTENT
  }

  private final HgLineAnnotationAspect dateAnnotationAspect =
    new HgLineAnnotationAspect(FIELD.DATE);

  private final HgLineAnnotationAspect userAnnotationAspect =
    new HgLineAnnotationAspect(FIELD.USER);

  private final HgLineAnnotationAspect revisionAnnotationAspect =
    new HgLineAnnotationAspect(FIELD.REVISION);

  @NotNull private final Project myProject;
  private final List<HgAnnotationLine> lines;
  private final List<HgFileRevision> vcsFileRevisions;
  private final HgFile hgFile;

  public HgAnnotation(@NotNull Project project, HgFile hgFile, List<HgAnnotationLine> lines, List<HgFileRevision> vcsFileRevisions) {
    myProject = project;
    this.lines = lines;
    this.vcsFileRevisions = vcsFileRevisions;
    this.hgFile = hgFile;
  }

  public AnnotationSourceSwitcher getAnnotationSourceSwitcher() {
    return null;
  }

  @Override
  public int getLineCount() {
    return lines.size();
  }

  public VcsRevisionNumber originalRevision(int lineNumber) {
    return getLineRevisionNumber(lineNumber);
  }

  public void addListener(AnnotationListener listener) {
  }

  public void removeListener(AnnotationListener listener) {
  }

  public void dispose() {
  }

  public LineAnnotationAspect[] getAspects() {
    return new LineAnnotationAspect[] {
      revisionAnnotationAspect,
      dateAnnotationAspect,
      userAnnotationAspect
    };
  }

  @Override
  @Nullable
  public String getToolTip(int lineNumber) {
    if ( lines.size() <= lineNumber || lineNumber < 0 ) {
      return null;
    }
    HgAnnotationLine info = lines.get(lineNumber);
    if (info == null) {
      return null;
    }

    for (HgFileRevision revision : vcsFileRevisions) {
      if (revision.getRevisionNumber().equals(info.getVcsRevisionNumber())) {
        return HgVcsMessages.message("hg4idea.annotation.tool.tip", revision.getRevisionNumber().asString(),
                                      revision.getAuthor(), revision.getRevisionDate(), revision.getCommitMessage());
      }
    }

    return null;
  }

  public String getAnnotatedContent() {
    try {
      return CurrentContentRevision.create(hgFile.toFilePath()).getContent();
    } catch (VcsException e) {
      LOG.info(e);
      return "";
    }
  }

  public VcsRevisionNumber getLineRevisionNumber(int lineNumber) {
    if (lineNumber >= lines.size() || lineNumber < 0) {
      return null;
    }
    HgAnnotationLine annotationLine = lines.get(lineNumber);
    return annotationLine.getVcsRevisionNumber();
  }

  @Override
  public Date getLineDate(int lineNumber) {
    if (lineNumber >= lines.size() || lineNumber < 0) {
      return null;
    }
    //lines.get(lineNumber).get(HgAnnotation.FIELD.DATE)
    // todo : parse date
    return null;
  }

  public List<VcsFileRevision> getRevisions() {
    List<VcsFileRevision> result = new LinkedList<VcsFileRevision>();
    result.addAll(vcsFileRevisions);
    return result;
  }

  private static String id(FIELD field) {
    switch (field) {
      case USER: return LineAnnotationAspect.AUTHOR;
      case REVISION: return LineAnnotationAspect.REVISION;
      case DATE: return LineAnnotationAspect.DATE;
      default: return null;
    }
  }

  private static boolean isShowByDefault(FIELD aspectType) {
    return aspectType == FIELD.DATE || aspectType == FIELD.USER;
  }

  class HgLineAnnotationAspect extends LineAnnotationAspectAdapter {
    private final FIELD aspectType;

    public HgLineAnnotationAspect(FIELD aspectType) {
      super(id(aspectType), HgAnnotation.isShowByDefault(aspectType));
      this.aspectType = aspectType;
    }

    public String getValue(int lineNumber) {
      if (lineNumber >= lines.size() || lineNumber < 0) {
        return "";
      }
      HgAnnotationLine annotationLine = lines.get(lineNumber);
      return aspectType == FIELD.REVISION
        ? annotationLine.getVcsRevisionNumber().asString()
        : annotationLine.get(aspectType).toString();
    }

    @Override
    protected void showAffectedPaths(int lineNum) {
      if (lineNum >= 0 && lineNum < lines.size()) {
        HgAnnotationLine line = lines.get(lineNum);
        VirtualFile file = hgFile.toFilePath().getVirtualFile();
        if (line != null && file != null) {
          ShowAllAffectedGenericAction.showSubmittedFiles(myProject, line.getVcsRevisionNumber(), file, HgVcs.getKey());
        }
      }
    }
  }

  public boolean revisionsNotEmpty() {
    return true;
  }
}

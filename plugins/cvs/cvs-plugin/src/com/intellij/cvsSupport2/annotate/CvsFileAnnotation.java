// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.annotate;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.cvsoperations.cvsAnnotate.Annotation;
import com.intellij.cvsSupport2.cvsstatuses.CvsEntriesListener;
import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.LineAnnotationAspect;
import com.intellij.openapi.vcs.annotate.LineAnnotationAspectAdapter;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CvsFileAnnotation extends FileAnnotation{
  private final String myContent;
  private final Annotation[] myAnnotations;
  private final CvsEntriesListener myCvsEntriesListener;
  private final Map<String, String> myRevisionComments = new HashMap<>();
  @Nullable private final List<VcsFileRevision> myRevisions;
  private final VirtualFile myFile;
  private final String myCurrentRevision;

  private final LineAnnotationAspect USER = new CvsAnnotationAspect(CvsAnnotationAspect.AUTHOR, true) {
    @Override
    public String getValue(int lineNumber) {
      if (lineNumber < 0 || lineNumber >= myAnnotations.length)  {
        return "";
      }
      else {
        return myAnnotations[lineNumber].getUserName();
      }
    }
  };

  private final LineAnnotationAspect DATE = new CvsAnnotationAspect(CvsAnnotationAspect.DATE, true) {
    @Override
    public String getValue(int lineNumber) {
      if (lineNumber < 0 || lineNumber >= myAnnotations.length)  {
        return "";
      }
      else {
        return myAnnotations[lineNumber].getPresentableDateString();
      }
    }
  };

  private final LineAnnotationAspect REVISION = new CvsAnnotationAspect(CvsAnnotationAspect.REVISION, false) {
    @Override
    public String getValue(int lineNumber) {
      if (lineNumber < 0 || lineNumber >= myAnnotations.length)  {
        return "";
      }
      else {
        return myAnnotations[lineNumber].getRevision();
      }
    }
  };


  public CvsFileAnnotation(final String content, final Annotation[] annotations,
                           @Nullable final List<VcsFileRevision> revisions, VirtualFile file, String currentRevision, Project project) {
    super(project);
    myContent = content;
    myAnnotations = annotations;
    myRevisions = revisions;
    myFile = file;
    myCurrentRevision = currentRevision;
    if (revisions != null) {
      for(VcsFileRevision revision: revisions) {
        myRevisionComments.put(revision.getRevisionNumber().toString(), revision.getCommitMessage());
      }
      myRevisions.sort((o1, o2) -> -1 * o1.getRevisionNumber().compareTo(o2.getRevisionNumber()));
    }

    myCvsEntriesListener = new CvsEntriesListener() {
      @Override
      public void entriesChanged(VirtualFile parent) {
        /*if (myFile == null) return;
        fireAnnotationChanged();*/
      }

      @Override
      public void entryChanged(VirtualFile file) {
        if (myFile == null) return;
        CvsFileAnnotation.this.close();
      }
    };

    CvsEntriesManager.getInstance().addCvsEntriesListener(myCvsEntriesListener);

  }

  @Override
  public void dispose() {
    CvsEntriesManager.getInstance().removeCvsEntriesListener(myCvsEntriesListener);

  }

  @Override
  public LineAnnotationAspect[] getAspects() {
    return new LineAnnotationAspect[]{REVISION, DATE, USER};
  }

  @Override
  public String getToolTip(final int lineNumber) {
    if (lineNumber < 0 || lineNumber >= myAnnotations.length)  {
      return "";
    }
    final Annotation annotation = myAnnotations[lineNumber];
    final String revision = annotation.getRevision();
    final Date date = annotation.getDate();
    final String author = annotation.getUserName();
    final String comment = myRevisionComments.get(revision);
    if (comment == null) {
      return "";
    }
    return CvsBundle.message("annotation.tooltip", revision, date, author, comment);
  }

  @Override
  public String getAnnotatedContent() {
    return myContent;
  }

  @Override
  public VcsRevisionNumber getLineRevisionNumber(final int lineNumber) {
    if (lineNumber < 0 || lineNumber >= myAnnotations.length)  {
      return null;
    }
    final String revision = myAnnotations[lineNumber].getRevision();
    if (revision != null) {
      return new CvsRevisionNumber(revision);
    }
    return null;
  }

  @Override
  public Date getLineDate(int lineNumber) {
    if (lineNumber < 0 || lineNumber >= myAnnotations.length)  {
      return null;
    }
    return myAnnotations[lineNumber].getDate();
  }

  @Override
  @Nullable
  public List<VcsFileRevision> getRevisions() {
    return myRevisions;
  }

  @Override
  public int getLineCount() {
    return myAnnotations.length;
  }

  private abstract static class CvsAnnotationAspect extends LineAnnotationAspectAdapter {
    CvsAnnotationAspect(String id, boolean showByDefault) {
      super(id, showByDefault);
    }

    @Override
    protected void showAffectedPaths(int lineNum) {
      //todo
    }
  }

  @Nullable
  @Override
  public VcsRevisionNumber getCurrentRevision() {
    return new CvsRevisionNumber(myCurrentRevision);
  }

  @Override
  public VcsKey getVcsKey() {
    return CvsVcs2.getKey();
  }

  @Override
  public VirtualFile getFile() {
    return myFile;
  }
}

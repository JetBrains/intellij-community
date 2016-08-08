/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.annotate;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.cvsoperations.cvsAnnotate.Annotation;
import com.intellij.cvsSupport2.cvsstatuses.CvsEntriesListener;
import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.annotate.AnnotationSourceSwitcher;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.LineAnnotationAspect;
import com.intellij.openapi.vcs.annotate.LineAnnotationAspectAdapter;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CvsFileAnnotation extends FileAnnotation{
  private final String myContent;
  private final Annotation[] myAnnotations;
  private final CvsEntriesListener myCvsEntriesListener;
  private final Map<String, String> myRevisionComments = new HashMap<>();
  @Nullable private final List<VcsFileRevision> myRevisions;
  private final VirtualFile myFile;
  private final String myCurrentRevision;

  private final LineAnnotationAspect USER = new CvsAnnotationAspect(CvsAnnotationAspect.AUTHOR, true) {
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
      Collections.sort(myRevisions, (o1, o2) -> -1 * o1.getRevisionNumber().compareTo(o2.getRevisionNumber()));
    }

    myCvsEntriesListener = new CvsEntriesListener() {
      public void entriesChanged(VirtualFile parent) {
        /*if (myFile == null) return;
        fireAnnotationChanged();*/
      }

      public void entryChanged(VirtualFile file) {
        if (myFile == null) return;
        CvsFileAnnotation.this.close();
      }
    };

    CvsEntriesManager.getInstance().addCvsEntriesListener(myCvsEntriesListener);

  }

  public void dispose() {
    CvsEntriesManager.getInstance().removeCvsEntriesListener(myCvsEntriesListener);

  }

  public LineAnnotationAspect[] getAspects() {
    return new LineAnnotationAspect[]{REVISION, DATE, USER};
  }

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

  public String getAnnotatedContent() {
    return myContent;
  }

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

  public VcsRevisionNumber originalRevision(int lineNumber) {
    return getLineRevisionNumber(lineNumber);
  }

  @Nullable
  public List<VcsFileRevision> getRevisions() {
    return myRevisions;
  }

  public boolean revisionsNotEmpty() {
    return ! myRevisions.isEmpty();
  }

  public AnnotationSourceSwitcher getAnnotationSourceSwitcher() {
    return null;
  }

  @Override
  public int getLineCount() {
    return myAnnotations.length;
  }

  private abstract static class CvsAnnotationAspect extends LineAnnotationAspectAdapter {
    public CvsAnnotationAspect(String id, boolean showByDefault) {
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

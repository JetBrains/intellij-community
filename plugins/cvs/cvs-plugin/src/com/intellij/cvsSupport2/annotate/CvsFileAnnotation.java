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
package com.intellij.cvsSupport2.annotate;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.cvsoperations.cvsAnnotate.Annotation;
import com.intellij.cvsSupport2.cvsstatuses.CvsEntriesListener;
import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import com.intellij.openapi.vcs.annotate.*;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CvsFileAnnotation implements FileAnnotation{
  private final String myContent;
  private final Annotation[] myAnnotations;
  private final CvsEntriesListener myCvsEntriesListener;
  private final Map<String, String> myRevisionComments = new HashMap<String, String>();
  @Nullable private final List<VcsFileRevision> myRevisions;
  private final VirtualFile myFile;
  private final List<AnnotationListener> myListeners = new ArrayList<AnnotationListener>();

  private final LineAnnotationAspect USER = new CvsAnnotationAspect() {
    public String getValue(int lineNumber) {
      if (lineNumber < 0 || lineNumber >= myAnnotations.length)  {
        return "";
      }
      else {
        return myAnnotations[lineNumber].getUserName();
      }
    }
  };

  private final LineAnnotationAspect DATE = new CvsAnnotationAspect() {
    public String getValue(int lineNumber) {
      if (lineNumber < 0 || lineNumber >= myAnnotations.length)  {
        return "";
      }
      else {
        return myAnnotations[lineNumber].getPresentableDateString();
      }
    }
  };

  private final LineAnnotationAspect REVISION = new CvsAnnotationAspect() {
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
                           @Nullable final List<VcsFileRevision> revisions, VirtualFile file) {
    myContent = content;
    myAnnotations = annotations;
    myRevisions = revisions;
    myFile = file;
    if (revisions != null) {
      for(VcsFileRevision revision: revisions) {
        myRevisionComments.put(revision.getRevisionNumber().toString(), revision.getCommitMessage());
      }
      Collections.sort(myRevisions, new Comparator<VcsFileRevision>() {
        public int compare(final VcsFileRevision o1, final VcsFileRevision o2) {
          return -1 * o1.getRevisionNumber().compareTo(o2.getRevisionNumber());
        }
      });
    }

    myCvsEntriesListener = new CvsEntriesListener() {
      public void entriesChanged(VirtualFile parent) {
        /*if (myFile == null) return;
        fireAnnotationChanged();*/
      }

      private void fireAnnotationChanged() {
        final AnnotationListener[] listeners = myListeners.toArray(new AnnotationListener[myListeners.size()]);
        for (AnnotationListener listener : listeners) {
          listener.onAnnotationChanged();
        }
      }

      public void entryChanged(VirtualFile file) {
        if (myFile == null) return;
        fireAnnotationChanged();
      }
    };

    CvsEntriesManager.getInstance().addCvsEntriesListener(myCvsEntriesListener);

  }

  public void addListener(AnnotationListener listener) {
    myListeners.add(listener);
  }

  public void removeListener(AnnotationListener listener) {
    myListeners.remove(listener);
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
    final String revision = myAnnotations[lineNumber].getRevision();
    final String comment = myRevisionComments.get(revision);
    if (comment == null) {
      return "";
    }
    return CvsBundle.message("annotation.tooltip", revision, comment);
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

  private abstract class CvsAnnotationAspect extends LineAnnotationAspectAdapter {
    @Override
    protected void showAffectedPaths(int lineNum) {
      //todo
    }
  }
}

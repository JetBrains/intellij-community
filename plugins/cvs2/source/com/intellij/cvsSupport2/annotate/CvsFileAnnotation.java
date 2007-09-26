/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.cvsSupport2.annotate;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.cvsoperations.cvsAnnotate.Annotation;
import com.intellij.cvsSupport2.cvsstatuses.CvsEntriesListener;
import com.intellij.openapi.vcs.annotate.AnnotationListener;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.LineAnnotationAspect;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CvsFileAnnotation implements FileAnnotation{
  private final String myContent;
  private final Annotation[] myAnnotations;
  private final CvsEntriesListener myCvsEntriesListener;
  private final Map<String, String> myRevisionComments = new HashMap<String, String>();
  private final VirtualFile myFile;
  private final List<AnnotationListener> myListeners = new ArrayList<AnnotationListener>();

  private final LineAnnotationAspect USER = new LineAnnotationAspect() {
    public String getValue(int lineNumber) {
      if (lineNumber < 0 || lineNumber >= myAnnotations.length)  {
        return "";
      }
      else {
        return myAnnotations[lineNumber].getUserName();
      }
    }
  };

  private final LineAnnotationAspect DATE = new LineAnnotationAspect() {
    public String getValue(int lineNumber) {
      if (lineNumber < 0 || lineNumber >= myAnnotations.length)  {
        return "";
      }
      else {
        return myAnnotations[lineNumber].getPresentableDateString();
      }
    }
  };

  private final LineAnnotationAspect REVISION = new LineAnnotationAspect() {
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
    myFile = file;
    if (revisions != null) {
      for(VcsFileRevision revision: revisions) {
        myRevisionComments.put(revision.getRevisionNumber().toString(), revision.getCommitMessage());
      }
    }

    myCvsEntriesListener = new CvsEntriesListener() {
      public void entriesChanged(VirtualFile parent) {
        if (myFile == null) return;
        fireAnnotationChanged();
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
}

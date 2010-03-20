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
package com.intellij.openapi.vcs.annotate;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.actions.CompareWithSelectedRevisionAction;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class HighlightAnnotationsActions {
  private final HightlightAction myBefore;
  private final HightlightAction myAfter;
  private final RemoveHighlightingAction myRemove;
  private final EditorGutterComponentEx myGutter;

  public HighlightAnnotationsActions(final Project project, final VirtualFile virtualFile, final FileAnnotation fileAnnotation,
                                     final EditorGutterComponentEx gutter) {
    myGutter = gutter;
    myBefore = new HightlightAction(true, project, virtualFile, fileAnnotation, myGutter, null);
    final List<VcsFileRevision> fileRevisionList = fileAnnotation.getRevisions();
    final VcsFileRevision afterSelected = ((fileRevisionList != null) && (fileRevisionList.size() > 1)) ? fileRevisionList.get(0) : null;
    myAfter = new HightlightAction(false, project, virtualFile, fileAnnotation, myGutter, afterSelected);
    myRemove = new RemoveHighlightingAction();
  }

  public List<AnAction> getList() {
    return Arrays.asList(myBefore, myAfter, myRemove);
  }

  public boolean isLineBold(final int lineNumber) {
    if (turnedOn()) {
      if (myBefore.isTurnedOn() && (! myBefore.isBold(lineNumber))) {
        return false;
      }
      if (myAfter.isTurnedOn() && (! myAfter.isBold(lineNumber))) {
        return false;
      }
      return true;
    }
    return false;
  }

  private boolean turnedOn() {
    return myBefore.isTurnedOn() || myAfter.isTurnedOn();
  }

  private class RemoveHighlightingAction extends AnAction {
    @Override
    public void update(final AnActionEvent e) {
      e.getPresentation().setText("Remove highlighting");
      e.getPresentation().setEnabled(turnedOn());
    }

    public void actionPerformed(final AnActionEvent e) {
      myBefore.clear();
      myAfter.clear();
      myGutter.revalidateMarkup();
    }
  }

  private static class HightlightAction extends AnAction {
    private final Project myProject;
    private final VirtualFile myVirtualFile;
    private final FileAnnotation myFileAnnotation;
    private final EditorGutterComponentEx myGutter;
    private final boolean myBefore;
    private VcsFileRevision mySelectedRevision;
    private Boolean myShowComments;

    private HightlightAction(final boolean before, final Project project, final VirtualFile virtualFile, final FileAnnotation fileAnnotation,
                             final EditorGutterComponentEx gutter, @Nullable final VcsFileRevision selectedRevision) {
      myBefore = before;
      myProject = project;
      myVirtualFile = virtualFile;
      myFileAnnotation = fileAnnotation;
      myGutter = gutter;
      myShowComments = null;
      mySelectedRevision = selectedRevision;
    }

    @Override
    public void update(final AnActionEvent e) {
      super.update(e);
      final String text;
      final String description;
      if (myBefore) {
        text = (mySelectedRevision == null) ? VcsBundle.message("highlight.annotation.before.not.selected.text") :
               VcsBundle.message("highlight.annotation.before.selected.text", mySelectedRevision.getRevisionNumber().asString());
        description = VcsBundle.message("highlight.annotation.before.description");
      } else {
        text = (mySelectedRevision == null) ? VcsBundle.message("highlight.annotation.after.not.selected.text") :
               VcsBundle.message("highlight.annotation.after.selected.text", mySelectedRevision.getRevisionNumber().asString());
        description = VcsBundle.message("highlight.annotation.after.description");
      }
      e.getPresentation().setText(text);
      e.getPresentation().setDescription(description);
      e.getPresentation().setEnabled(myFileAnnotation.revisionsNotEmpty());
    }

    public void actionPerformed(final AnActionEvent e) {
      final List<VcsFileRevision> fileRevisionList = myFileAnnotation.getRevisions();
      if (fileRevisionList != null) {
        if (myShowComments == null) {
          initShowComments(fileRevisionList);
        }
        CompareWithSelectedRevisionAction.showListPopup(fileRevisionList, myProject,
                                                        new Consumer<VcsFileRevision>() {
                                                          public void consume(final VcsFileRevision vcsFileRevision) {
                                                            mySelectedRevision = vcsFileRevision;
                                                            myGutter.revalidateMarkup();
                                                          }
                                                        }, myShowComments.booleanValue());
      }
    }

    private void initShowComments(final List<VcsFileRevision> revisions) {
      for (VcsFileRevision revision : revisions) {
        if (revision.getCommitMessage() != null) {
          myShowComments = true;
          return;
        }
      }
      myShowComments = false;
    }

    public boolean isTurnedOn() {
      return mySelectedRevision != null;
    }

    public void clear() {
      mySelectedRevision = null;
    }

    public boolean isBold(final int line) {
      if (mySelectedRevision != null) {
        final VcsRevisionNumber number = myFileAnnotation.originalRevision(line);
        if (number != null) {
          final int compareResult = number.compareTo(mySelectedRevision.getRevisionNumber());
          return (myBefore && compareResult <= 0) || ((! myBefore) && (compareResult >= 0));
        }
      }
      return false;
    }
  }
}

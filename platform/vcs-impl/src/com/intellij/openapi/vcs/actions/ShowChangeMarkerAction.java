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
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ex.LineStatusTracker;
import com.intellij.openapi.vcs.ex.LineStatusTrackerDrawing;
import com.intellij.openapi.vcs.ex.Range;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager;
import org.jetbrains.annotations.NotNull;

/**
 * author: lesya
 */
public abstract class ShowChangeMarkerAction extends AbstractVcsAction {
  protected final ChangeMarkerContext myChangeMarkerContext;


  protected abstract Range extractRange(LineStatusTracker lineStatusTracker, int line, Editor editor);

  public ShowChangeMarkerAction(final Range range, final LineStatusTracker lineStatusTracker, final Editor editor) {
    myChangeMarkerContext = new ChangeMarkerContext() {
      @Override
      public Range getRange(VcsContext dataContext) {
        return range;
      }

      @Override
      public LineStatusTracker getLineStatusTracker(VcsContext dataContext) {
        return lineStatusTracker;
      }

      @Override
      public Editor getEditor(VcsContext dataContext) {
        return editor;
      }
    };
  }

  @Override
  protected boolean forceSyncUpdate(final AnActionEvent e) {
    return true;
  }

  public ShowChangeMarkerAction() {
    myChangeMarkerContext = new ChangeMarkerContext() {
      @Override
      public Range getRange(VcsContext context) {
        Editor editor = getEditor(context);
        if (editor == null) return null;

        LineStatusTracker lineStatusTracker = getLineStatusTracker(context);
        if (lineStatusTracker == null) return null;

        return extractRange(lineStatusTracker, editor.getCaretModel().getLogicalPosition().line, editor);
      }

      @Override
      public LineStatusTracker getLineStatusTracker(VcsContext dataContext) {
        Editor editor = getEditor(dataContext);
        if (editor == null) return null;
        Project project = dataContext.getProject();
        if (project == null) return null;
        return LineStatusTrackerManager.getInstance(project).getLineStatusTracker(editor.getDocument());
      }

      @Override
      public Editor getEditor(VcsContext dataContext) {
        return dataContext.getEditor();
      }
    };
  }

  private boolean isActive(VcsContext context) {
    Editor editor = myChangeMarkerContext.getEditor(context);
    return myChangeMarkerContext.getRange(context) != null && editor != null && !DiffUtil.isDiffEditor(editor);
  }

  @Override
  protected void update(VcsContext context, Presentation presentation) {
    LineStatusTracker tracker = myChangeMarkerContext.getLineStatusTracker(context);
    if (tracker == null || !tracker.isValid() || tracker.isSilentMode()) {
      presentation.setEnabledAndVisible(false);
      return;
    }

    boolean active = isActive(context);
    presentation.setEnabled(active);
    presentation.setVisible(myChangeMarkerContext.getEditor(context) != null || ActionPlaces.isToolbarPlace(context.getPlace()));
  }


  @Override
  protected void actionPerformed(@NotNull VcsContext context) {
    Editor editor = myChangeMarkerContext.getEditor(context);
    LineStatusTracker lineStatusTracker = myChangeMarkerContext.getLineStatusTracker(context);
    Range range = myChangeMarkerContext.getRange(context);


    LineStatusTrackerDrawing.moveToRange(range, editor, lineStatusTracker);
  }

  protected interface ChangeMarkerContext {
    Range getRange(VcsContext dataContext);

    LineStatusTracker getLineStatusTracker(VcsContext dataContext);

    Editor getEditor(VcsContext dataContext);
  }
}

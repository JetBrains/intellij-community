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
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ex.LineStatusTracker;
import com.intellij.openapi.vcs.ex.Range;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ShowChangeMarkerAction extends AbstractVcsAction {
  protected abstract Range extractRange(LineStatusTracker lineStatusTracker, int line, Editor editor);


  @Nullable
  protected Range getRange(VcsContext context) {
    Editor editor = getEditor(context);
    if (editor == null) return null;

    LineStatusTracker lineStatusTracker = getLineStatusTracker(context);
    if (lineStatusTracker == null) return null;

    return extractRange(lineStatusTracker, editor.getCaretModel().getLogicalPosition().line, editor);
  }

  @Nullable
  protected LineStatusTracker getLineStatusTracker(VcsContext dataContext) {
    Editor editor = getEditor(dataContext);
    if (editor == null) return null;
    Project project = dataContext.getProject();
    if (project == null) return null;
    return LineStatusTrackerManager.getInstance(project).getLineStatusTracker(editor.getDocument());
  }

  @Nullable
  protected Editor getEditor(VcsContext dataContext) {
    return dataContext.getEditor();
  }


  @Override
  protected void update(@NotNull VcsContext context, @NotNull Presentation presentation) {
    Editor editor = getEditor(context);
    LineStatusTracker tracker = getLineStatusTracker(context);

    boolean isAvailable = tracker != null && tracker.isValid() && editor != null && tracker.isAvailableAt(editor);

    presentation.setEnabled(isAvailable && getRange(context) != null);
    presentation.setVisible(editor != null || ActionPlaces.isToolbarPlace(context.getPlace()));
  }

  @Override
  protected void actionPerformed(@NotNull VcsContext context) {
    Editor editor = getEditor(context);
    LineStatusTracker lineStatusTracker = getLineStatusTracker(context);
    Range range = getRange(context);
    assert editor != null && lineStatusTracker != null && range != null;

    LineStatusTracker.moveToRange(range, editor, lineStatusTracker);
  }


  public static class Next extends ShowChangeMarkerAction {
    protected Range extractRange(LineStatusTracker tracker, int line, Editor editor) {
      return tracker.getNextRange(line);
    }
  }

  public static class Prev extends ShowChangeMarkerAction {
    protected Range extractRange(LineStatusTracker tracker, int line, Editor editor) {
      return tracker.getPrevRange(line);
    }
  }

  public static class Current extends ShowChangeMarkerAction {
    protected Range extractRange(LineStatusTracker lineStatusTracker, int line, Editor editor) {
      return lineStatusTracker.getRangeForLine(line);
    }

    @Override
    protected void actionPerformed(@NotNull VcsContext context) {
      Editor editor = getEditor(context);
      LineStatusTracker lineStatusTracker = getLineStatusTracker(context);
      Range range = getRange(context);
      assert editor != null && lineStatusTracker != null && range != null;

      LineStatusTracker.showHint(range, editor, lineStatusTracker);
    }
  }
}

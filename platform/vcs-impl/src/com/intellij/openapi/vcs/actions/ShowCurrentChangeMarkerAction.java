/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vcs.ex.LineStatusTracker;
import com.intellij.openapi.vcs.ex.LineStatusTrackerDrawing;
import com.intellij.openapi.vcs.ex.Range;
import org.jetbrains.annotations.NotNull;

public class ShowCurrentChangeMarkerAction extends ShowChangeMarkerAction {

  public ShowCurrentChangeMarkerAction(final Range range, final LineStatusTracker lineStatusTracker, final Editor editor) {
    super(range, lineStatusTracker, editor);
  }

  public ShowCurrentChangeMarkerAction() {
  }

  protected Range extractRange(LineStatusTracker lineStatusTracker, int line, Editor editor) {
    return lineStatusTracker.getRangeForLine(line);
  }

  @Override
  protected void actionPerformed(@NotNull VcsContext context) {
    Editor editor = myChangeMarkerContext.getEditor(context);
    LineStatusTracker lineStatusTracker = myChangeMarkerContext.getLineStatusTracker(context);
    Range range = myChangeMarkerContext.getRange(context);


    LineStatusTrackerDrawing.showHint(range, editor, lineStatusTracker);
  }
}

package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vcs.ex.LineStatusTracker;
import com.intellij.openapi.vcs.ex.Range;

/**
 * author: lesya
 */
public class ShowPrevChangeMarkerAction extends ShowChangeMarkerAction {
  public ShowPrevChangeMarkerAction(final Range range, final LineStatusTracker lineStatusTracker, final Editor editor) {
    super(range, lineStatusTracker, editor);
  }

  public ShowPrevChangeMarkerAction() {
  }

  protected Range extractRange(LineStatusTracker lineStatusTracker, int line, Editor editor) {
    return lineStatusTracker.getPrevRange(line);
  }
}

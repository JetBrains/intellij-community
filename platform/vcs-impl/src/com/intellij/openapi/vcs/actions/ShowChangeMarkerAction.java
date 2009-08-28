package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ex.LineStatusTracker;
import com.intellij.openapi.vcs.ex.Range;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager;

/**
 * author: lesya
 */
public abstract class ShowChangeMarkerAction extends AbstractVcsAction {
  protected final ChangeMarkerContext myChangeMarkerContext;
  

  protected abstract Range extractRange(LineStatusTracker lineStatusTracker, int line, Editor editor);

  public ShowChangeMarkerAction(final Range range, final LineStatusTracker lineStatusTracker, final Editor editor) {
    myChangeMarkerContext = new ChangeMarkerContext() {
      public Range getRange(VcsContext dataContext) {
        return range;
      }

      public LineStatusTracker getLineStatusTracker(VcsContext dataContext) {
        return lineStatusTracker;
      }

      public Editor getEditor(VcsContext dataContext) {
        return editor;
      }
    };
  }

  protected boolean forceSyncUpdate(final AnActionEvent e) {
    return true;
  }

  public ShowChangeMarkerAction() {
    myChangeMarkerContext = new ChangeMarkerContext() {
      public Range getRange(VcsContext context) {
        Editor editor = getEditor(context);
        if (editor == null) return null;

        LineStatusTracker lineStatusTracker = getLineStatusTracker(context);
        if (lineStatusTracker == null) return null;

        return extractRange(lineStatusTracker, editor.getCaretModel().getLogicalPosition().line, editor);
      }

      public LineStatusTracker getLineStatusTracker(VcsContext dataContext) {
        Editor editor = getEditor(dataContext);
        if (editor == null) return null;
        Project project = dataContext.getProject();
        if (project == null) return null;
        return LineStatusTrackerManager.getInstance(project).getLineStatusTracker(editor.getDocument());
      }

      public Editor getEditor(VcsContext dataContext) {
        return dataContext.getEditor();
      }
    };
  }

  private boolean isActive(VcsContext context) {
    return myChangeMarkerContext.getRange(context) != null;
  }

  protected void update(VcsContext context, Presentation presentation) {
    presentation.setEnabled(isActive(context));
  }


  protected void actionPerformed(VcsContext context) {
    Editor editor = myChangeMarkerContext.getEditor(context);
    LineStatusTracker lineStatusTracker = myChangeMarkerContext.getLineStatusTracker(context);
    Range range = myChangeMarkerContext.getRange(context);

    lineStatusTracker.moveToRange(range, editor);
  }

  protected interface ChangeMarkerContext {
    Range getRange(VcsContext dataContext);

    LineStatusTracker getLineStatusTracker(VcsContext dataContext);

    Editor getEditor(VcsContext dataContext);
  }
}

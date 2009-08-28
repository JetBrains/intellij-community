package com.intellij.openapi.diff.impl.highlighting;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.impl.ContentChangeListener;
import com.intellij.openapi.project.Project;

public class DiffPanelState extends SimpleDiffPanelState<EditorPlaceHolder> {
  public DiffPanelState(ContentChangeListener changeListener, Project project) {
    super(createEditorWrapper(project, changeListener, FragmentSide.SIDE1),
          createEditorWrapper(project, changeListener, FragmentSide.SIDE2), project);
  }

  private static EditorPlaceHolder createEditorWrapper(Project project, ContentChangeListener changeListener, FragmentSide side) {
    EditorPlaceHolder editorWrapper = new EditorPlaceHolder(side, project);
    editorWrapper.addListener(changeListener);
    return editorWrapper;
  }

  public void setContents(final DiffContent content1, final DiffContent content2) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        myAppender1.setContent(content1);
        myAppender2.setContent(content2);
      }
    });
  }

  public DiffContent getContent2() {
    return myAppender2.getContent();
  }

  public void removeActions() {
    myAppender1.removeActions();
    myAppender2.removeActions();
  }
}


/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 16, 2002
 * Time: 2:28:05 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;

public class ScrollToCenterAction extends InactiveEditorAction {
  public ScrollToCenterAction() {
    super(new Handler());
  }

  private static class Handler extends EditorActionHandler {
    public void execute(Editor editor, DataContext dataContext) {
      editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
    }
  }
}

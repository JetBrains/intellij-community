/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.intelliLang.inject.quickedit;

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.plaf.beg.BegBorders;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

// Adapted from QuickEditHotspot that came with the source of the first designer release ;)
public class QuickEditEditor {

  private final Project myProject;
  private final JComponent myPanel;
  private final QuickEditSaver mySaver;

  private EditorEx myEditor;
  private JBPopup myPopup;
  private Boolean myCancelFlag;

  public QuickEditEditor(Document document, Project project, FileType ft, @NotNull QuickEditSaver saver) {
    myProject = project;
    mySaver = saver;
    myEditor = (EditorImpl)EditorFactory.getInstance().createEditor(document, project);
    myEditor.setHighlighter(HighlighterFactory.createHighlighter(project, ft));
    myEditor.setEmbeddedIntoDialogWrapper(true);

    EditorSettings settings = myEditor.getSettings();
    settings.setFoldingOutlineShown(false);
    settings.setLineMarkerAreaShown(false);
    settings.setIndentGuidesShown(false);
    settings.setLineNumbersShown(false);
    settings.setVirtualSpace(false);
    settings.setAdditionalLinesCount(2);

    myPanel = new MyPanel();
  }

  public JComponent getPreferredFocusedComponent() {
    return myEditor.getContentComponent();
  }

  public Editor getEditor() {
    return myEditor;
  }

  private class MyPanel extends JPanel implements DataProvider {

    public MyPanel() {
      super(new BorderLayout());

      add(myEditor.getComponent(), BorderLayout.CENTER);

      setBorder(new BegBorders.FlatLineBorder());
      setPreferredSize(new Dimension(400, 100));
    }

    public Object getData(String s) {
      if (LangDataKeys.EDITOR.is(s)) {
        return myEditor;
      }
      return null;
    }
  }

  public void setCancel(boolean cancel) {
    if (myCancelFlag == null) myCancelFlag = cancel;
  }

  private String releaseEditor() {
    if (myEditor != null) {
      final Document document = myEditor.getDocument();
      final String text = document.getText();
      PsiDocumentManager.getInstance(myProject).commitDocument(document);
      EditorFactory.getInstance().releaseEditor(myEditor);
      myEditor = null;
      return text;
    }
    return "";
  }

  public void install(JBPopup popup) {
    myPopup = popup;
    final JComponent component = myEditor.getContentComponent();
    component.requestFocus();

    new EscAction(this).registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)), component);
    new SaveAction(this)
        .registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_MASK)), component);

    setStatusBarText("Press Ctrl+Enter to save, Escape to cancel.");
  }

  private void setStatusBarText(String text) {
    StatusBar.Info.set(text, myProject);
  }

  public void uninstall() {
    setStatusBarText("");

    final String text = releaseEditor();

    assert myCancelFlag != null;
    if (!myCancelFlag) {
      new WriteCommandAction(myProject) {
        protected void run(Result result) throws Throwable {
          mySaver.save(text);
        }
      }.execute();
    }
    myPopup = null;
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public interface QuickEditSaver {
    void save(String text);
  }

  private static class EscAction extends AnAction {
    private final QuickEditEditor myEditor;

    public EscAction(QuickEditEditor editor) {
      super("Esc");
      myEditor = editor;
    }

    public void actionPerformed(AnActionEvent event) {
      myEditor.setCancel(true);
      myEditor.myPopup.cancel();
    }
  }

  private static class SaveAction extends AnAction {
    private final QuickEditEditor myEditor;

    public SaveAction(QuickEditEditor editor) {
      super("Save");
      myEditor = editor;
    }

    public void actionPerformed(AnActionEvent event) {
      myEditor.setCancel(false);
      myEditor.myPopup.cancel();
    }
  }
}



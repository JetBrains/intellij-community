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
package com.intellij.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.MacUIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;

/**
 * @author max
 */
// TODO[pegov]: should extend ComboBox not JComboBox!
public class EditorComboBox extends JComboBox implements DocumentListener {
  public static TextComponentAccessor<EditorComboBox> COMPONENT_ACCESSOR = new TextComponentAccessor<EditorComboBox>() {
    public String getText(EditorComboBox component) {
      return component.getText();
    }

    public void setText(EditorComboBox component, String text) {
      component.setText(text);
    }
  };
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.EditorTextField");

  private Document myDocument;
  private Project myProject;
  private EditorTextField myEditorField = null;
  private final ArrayList<DocumentListener> myDocumentListeners = new ArrayList<DocumentListener>();
  private boolean myIsListenerInstalled = false;
  private boolean myInheritSwingFont = true;
  private final FileType myFileType;
  private final boolean myIsViewer;

  public EditorComboBox(String text) {
    this(EditorFactory.getInstance().createDocument(text), null, FileTypes.PLAIN_TEXT);
  }

  public EditorComboBox(String text, Project project, FileType fileType) {
    this(EditorFactory.getInstance().createDocument(text), project, fileType, false);
  }

  public EditorComboBox(Document document, Project project, FileType fileType) {
    this(document, project, fileType, false);
  }

  public EditorComboBox(Document document, Project project, FileType fileType, boolean isViewer) {
    myFileType = fileType;
    myIsViewer = isViewer;
    setDocument(document);
    myProject = project;
    enableEvents(AWTEvent.KEY_EVENT_MASK);

    addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final Editor editor = myEditorField.getEditor();
        if (editor != null) {
          editor.getSelectionModel().removeSelection();
        }
      }
    });
    setHistory(new String[]{""});
    setEditable(true);
  }

  public void setFontInheritedFromLAF(boolean b) {
    myInheritSwingFont = b;
    setDocument(myDocument); // reinit editor.
  }

  public String getText() {
    return myDocument.getText();
  }

  public void addDocumentListener(DocumentListener listener) {
    myDocumentListeners.add(listener);
    installDocumentListener();
  }

  public void removeDocumentListener(DocumentListener listener) {
    myDocumentListeners.remove(listener);
    uninstallDocumentListener(false);
  }

  public void beforeDocumentChange(DocumentEvent event) {
    for (DocumentListener documentListener : myDocumentListeners) {
      documentListener.beforeDocumentChange(event);
    }
  }

  public void documentChanged(DocumentEvent event) {
    for (DocumentListener documentListener : myDocumentListeners) {
      documentListener.documentChanged(event);
    }
  }

  @Override
  public void paint(Graphics g) {
    super.paint(g);

    MacUIUtil.drawComboboxFocusRing(this, g);
  }

  public Project getProject() {
    return myProject;
  }

  public Document getDocument() {
    return myDocument;
  }

  public void setDocument(Document document) {
    if (myDocument != null) {
      uninstallDocumentListener(true);
    }

    myDocument = document;
    installDocumentListener();
    if (myEditorField == null) return;

    myEditorField.setDocument(document);
  }

  private void installDocumentListener() {
    if (myDocument != null && myDocumentListeners.size() > 0 && !myIsListenerInstalled) {
      myIsListenerInstalled = true;
      myDocument.addDocumentListener(this);
    }
  }

  private void uninstallDocumentListener(boolean force) {
    if (myDocument != null && myIsListenerInstalled && (force || myDocumentListeners.size() == 0)) {
      myIsListenerInstalled = false;
      myDocument.removeDocumentListener(this);
    }
  }

  public void setText(final String text) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
          public void run() {
            myDocument.replaceString(0, myDocument.getTextLength(), text);
            if (myEditorField != null && myEditorField.getEditor() != null) {
              myEditorField.getCaretModel().moveToOffset(myDocument.getTextLength());
            }
          }
        }, null, myDocument);
      }
    });
  }

  public void removeSelection() {
    if (myEditorField != null) {
      final Editor editor = myEditorField.getEditor();
      if (editor != null) {
        editor.getSelectionModel().removeSelection();
      }
    }
    else {
    }
  }

  public CaretModel getCaretModel() {
    return myEditorField.getCaretModel();
  }

  public void setHistory(final String[] history) {
    setModel(new DefaultComboBoxModel(history));
  }

  public void prependItem(String item) {
    ArrayList<Object> objects = new ArrayList<Object>();
    objects.add(item);
    int count = getItemCount();
    for (int i = 0; i < count; i++) {
      final Object itemAt = getItemAt(i);
      if (!item.equals(itemAt)) {
        objects.add(itemAt);
      }
    }
    setModel(new DefaultComboBoxModel(ArrayUtil.toObjectArray(objects)));
  }

  private class MyEditor implements ComboBoxEditor {
    public void addActionListener(ActionListener l) {
    }

    public Component getEditorComponent() {
      return myEditorField;
    }

    public Object getItem() {
      return myDocument.getText();
    }

    public void removeActionListener(ActionListener l) {
    }

    public void selectAll() {
      if (myEditorField != null) {
        final Editor editor = myEditorField.getEditor();
        if (editor != null) {
          editor.getSelectionModel().setSelection(0, myDocument.getTextLength());
        }
      }
    }

    public void setItem(Object anObject) {
      if (anObject != null) {
        EditorComboBox.this.setText((String)anObject);
      } else {
        EditorComboBox.this.setText("");
      }
    }
  }

  public void addNotify() {
    releaseEditor();
    setEditor();

    super.addNotify();
  }

  private void setEditor() {
    myEditorField = new ComboboxEditorTextField(myDocument, myProject, myFileType, myIsViewer);
    final ComboBoxEditor editor = new MyEditor();
    setEditor(editor);
    setRenderer(new EditorComboBoxRenderer(editor));
  }

  public void removeNotify() {
    super.removeNotify();
    if (myEditorField != null) {
      releaseEditor();
      myEditorField = null;
    }
  }

  private void releaseEditor() {
    if (myEditorField != null) {
      final Editor editor = myEditorField.getEditor();
      if (editor != null) {
        myEditorField.releaseEditor(editor);
      }
    }
  }

  public void setFont(Font font) {
    super.setFont(font);
    if (myEditorField != null && myEditorField.getEditor() != null) {
      setupEditorFont((EditorEx)myEditorField.getEditor());
    }
  }

  private void setupEditorFont(final EditorEx editor) {
    if (myInheritSwingFont) {
      editor.getColorsScheme().setEditorFontName(getFont().getFontName());
      editor.getColorsScheme().setEditorFontSize(getFont().getSize());
    }
  }

  protected boolean shouldHaveBorder() {
    return true;
  }

  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    if (myEditorField == null) {
      return;
    }
    myEditorField.setEnabled(enabled);
  }

  public Dimension getPreferredSize() {
    if (myEditorField != null) {
      final Dimension preferredSize = new Dimension(myEditorField.getComponent().getPreferredSize());
      final Insets insets = getInsets();
      if (insets != null) {
        preferredSize.width += insets.left;
        preferredSize.width += insets.right;
        preferredSize.height += insets.top;
        preferredSize.height += insets.bottom;
      }
      return preferredSize;
    }
    return new Dimension(100, 20);
  }

  protected boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
    if (!((EditorEx)myEditorField.getEditor()).processKeyTyped(e)) {
      return super.processKeyBinding(ks, e, condition, pressed);
    }
    return true;
  }

  public EditorEx getEditorEx() {
    return myEditorField != null ? (EditorEx)myEditorField.getEditor() : null;
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
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
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

public class EditorComboBox extends ComboBox implements DocumentListener {
  public static TextComponentAccessor<EditorComboBox> COMPONENT_ACCESSOR = new TextComponentAccessor<>() {
    @Override
    public String getText(EditorComboBox component) {
      return component.getText();
    }

    @Override
    public void setText(EditorComboBox component, @NotNull String text) {
      component.setText(text);
    }
  };

  private Document myDocument;
  private final Project myProject;
  private EditorTextField myEditorField = null;
  private final List<DocumentListener> myDocumentListeners = ContainerUtil.createLockFreeCopyOnWriteList();
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
      @Override
      public void actionPerformed(ActionEvent e) {
        final Editor editor = myEditorField != null ? myEditorField.getEditor() : null;
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

  @Override
  public void beforeDocumentChange(@NotNull DocumentEvent event) {
    for (DocumentListener documentListener : myDocumentListeners) {
      documentListener.beforeDocumentChange(event);
    }
  }

  @Override
  public void documentChanged(@NotNull DocumentEvent event) {
    for (DocumentListener documentListener : myDocumentListeners) {
      documentListener.documentChanged(event);
    }
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
    if (myDocument != null && !myDocumentListeners.isEmpty() && !myIsListenerInstalled) {
      myIsListenerInstalled = true;
      myDocument.addDocumentListener(this);
    }
  }

  private void uninstallDocumentListener(boolean force) {
    if (myDocument != null && myIsListenerInstalled && (force || myDocumentListeners.isEmpty())) {
      myIsListenerInstalled = false;
      myDocument.removeDocumentListener(this);
    }
  }

  public void setText(final String text) {
    ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance().executeCommand(getProject(), () -> {
      myDocument.replaceString(0, myDocument.getTextLength(), text);
      if (myEditorField != null && myEditorField.getEditor() != null) {
        myEditorField.getCaretModel().moveToOffset(myDocument.getTextLength());
      }
    }, null, myDocument));
  }

  public void removeSelection() {
    if (myEditorField != null) {
      final Editor editor = myEditorField.getEditor();
      if (editor != null) {
        editor.getSelectionModel().removeSelection();
      }
    }
  }

  public CaretModel getCaretModel() {
    return myEditorField.getCaretModel();
  }

  public void setHistory(final String[] history) {
    setModel(new DefaultComboBoxModel(history));
  }

  public void prependItem(String item) {
    ArrayList<Object> objects = new ArrayList<>();
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

  public void appendItem(String item) {
    ArrayList<Object> objects = new ArrayList<>();

    int count = getItemCount();
    for (int i = 0; i < count; i++) {
      objects.add(getItemAt(i));
    }

    if (!objects.contains(item)) {
      objects.add(item);
    }
    setModel(new DefaultComboBoxModel(ArrayUtil.toObjectArray(objects)));
  }

  private class MyEditor implements ComboBoxEditor {
    @Override
    public void addActionListener(ActionListener l) {
    }

    @Override
    public Component getEditorComponent() {
      return myEditorField;
    }

    @Override
    public Object getItem() {
      return myDocument.getText();
    }

    @Override
    public void removeActionListener(ActionListener l) {
    }

    @Override
    public void selectAll() {
      if (myEditorField != null) {
        final Editor editor = myEditorField.getEditor();
        if (editor != null) {
          editor.getSelectionModel().setSelection(0, myDocument.getTextLength());
        }
      }
    }

    @Override
    public void setItem(Object anObject) {
      if (anObject != null) {
        EditorComboBox.this.setText(anObject.toString());
      } else {
        EditorComboBox.this.setText("");
      }
    }
  }

  @Override
  public void addNotify() {
    releaseEditor();
    setEditor();

    super.addNotify();
    myEditorField.getFocusTarget().addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        EditorComboBox.this.repaint();
      }

      @Override
      public void focusLost(FocusEvent e) {
        EditorComboBox.this.repaint();
      }
    });
  }

  private void setEditor() {
    myEditorField = createEditorTextField(myDocument, myProject, myFileType, myIsViewer);
    final ComboBoxEditor editor = new MyEditor();
    setEditor(editor);
    setRenderer(new EditorComboBoxRenderer(editor));
  }

  protected ComboboxEditorTextField createEditorTextField(Document document, Project project, FileType fileType, boolean isViewer) {
    return new ComboboxEditorTextField(document, project, fileType, isViewer);
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    releaseEditor();
    myEditorField = null;
  }

  private void releaseEditor() {
    if (myEditorField != null) {
      myEditorField.releaseEditorLater();
    }
  }

  @Override
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

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    if (myEditorField == null) {
      return;
    }
    myEditorField.setEnabled(enabled);
  }

  @Override
  public Dimension getPreferredSize() {
    if (UIUtil.isUnderIntelliJLaF() || StartupUiUtil.isUnderDarcula()) {
      return super.getPreferredSize();
    }
    if (myEditorField != null) {
      final Dimension preferredSize = new Dimension(myEditorField.getComponent().getPreferredSize());
      JBInsets.addTo(preferredSize, getInsets());
      return preferredSize;
    }

    return new Dimension(100, 20);
  }

  @Override
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

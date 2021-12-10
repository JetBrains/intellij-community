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

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.event.ActionListener;
import java.util.List;

/**
 * @author ven
 */
public class ReferenceEditorWithBrowseButton extends ComponentWithBrowseButton<EditorTextField> implements TextAccessor {
  private final Function<? super String, ? extends Document> myFactory;
  private final List<DocumentListener> myDocumentListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public ReferenceEditorWithBrowseButton(final ActionListener browseActionListener,
                                         final Project project,
                                         final Function<? super String, ? extends Document> factory,
                                         String text) {
    this(browseActionListener, new EditorTextField(factory.fun(text), project, StdFileTypes.JAVA), factory);
  }

  public ReferenceEditorWithBrowseButton(final ActionListener browseActionListener,
                                         final EditorTextField editorTextField,
                                         final Function<? super String, ? extends Document> factory) {
    super(editorTextField, browseActionListener);
    myFactory = factory;
  }

  public void addDocumentListener(DocumentListener listener) {
    myDocumentListeners.add(listener);
    getEditorTextField().getDocument().addDocumentListener(listener);
  }

  public void removeDocumentListener(DocumentListener listener) {
    myDocumentListeners.remove(listener);
    getEditorTextField().getDocument().removeDocumentListener(listener);
  }

  public EditorTextField getEditorTextField() {
    return getChildComponent();
  }

  @Override
  public String getText() {
    return getEditorTextField().getText().trim();
  }

  @Override
  public void setText(@NotNull String text) {
    Document oldDocument = getEditorTextField().getDocument();
    String oldText = oldDocument.getText();
    for (DocumentListener listener : myDocumentListeners) {
      oldDocument.removeDocumentListener(listener);
    }
    Document document = myFactory.fun(text);
    getEditorTextField().setDocument(document);
    for (DocumentListener listener : myDocumentListeners) {
      document.addDocumentListener(listener);
    }
    WriteAction.run(() -> document.setText(text));
  }

  public boolean isEditable() {
    return !getEditorTextField().getEditor().isViewer();
  }
}

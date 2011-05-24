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
package com.intellij.openapi.editor.impl;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityStateListener;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.impl.event.EditorEventMulticasterImpl;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EventDispatcher;
import com.intellij.util.SmartList;
import com.intellij.util.text.CharArrayCharSequence;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class EditorFactoryImpl extends EditorFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.EditorFactoryImpl");
  private final EditorEventMulticasterImpl myEditorEventMulticaster = new EditorEventMulticasterImpl();
  private final EventDispatcher<EditorFactoryListener> myEditorFactoryEventDispatcher = EventDispatcher.create(EditorFactoryListener.class);
  private final List<Editor> myEditors = new ArrayList<Editor>();
  private static final Key<String> EDITOR_CREATOR = new Key<String>("Editor creator");

  public EditorFactoryImpl(ProjectManager projectManager) {
    projectManager.addProjectManagerListener(new ProjectManagerAdapter() {
      public void projectClosed(final Project project) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            validateEditorsAreReleased(project);
          }
        });
      }
    });
  }

  @NotNull
  public String getComponentName() {
    return "EditorFactory";
  }

  public void initComponent() {
    ModalityStateListener myModalityStateListener = new ModalityStateListener() {
      public void beforeModalityStateChanged(boolean entering) {
        for (Editor editor : myEditors) {
          ((EditorImpl)editor).beforeModalityStateChanged();
        }
      }
    };
    LaterInvocator.addModalityStateListener(myModalityStateListener, ApplicationManager.getApplication());
  }

  public void validateEditorsAreReleased(Project project) {
    for (Editor editor : myEditors) {
      if (editor.getProject() == project || editor.getProject() == null) {
        try {
          LOG.error(notReleasedError(editor));
        }
        finally {
          releaseEditor(editor);
        }
      }
    }
  }

  @NonNls
  public static String notReleasedError(@NotNull Editor editor) {
    final String creator = editor.getUserData(EDITOR_CREATOR);
    if (creator == null) {
      return "Editor of " + editor.getClass() +
                " and the following text hasn't been released:\n" + editor.getDocument().getText();
    }
    else {
      return "Editor of " + editor.getClass() + " hasn't been released:\n" + creator;
    }
  }

  public void disposeComponent() {
  }

  @NotNull
  public Document createDocument(@NotNull char[] text) {
    return createDocument(new CharArrayCharSequence(text));
  }

  @NotNull
  public Document createDocument(@NotNull CharSequence text) {
    DocumentImpl document = new DocumentImpl(text);
    myEditorEventMulticaster.registerDocument(document);
    return document;
  }

  @NotNull
  public Document createDocument(boolean allowUpdatesWithoutWriteAction) {
    DocumentImpl document = new DocumentImpl(allowUpdatesWithoutWriteAction);
    myEditorEventMulticaster.registerDocument(document);
    return document;
  }

  public void refreshAllEditors() {
    for (Editor editor : myEditors) {
      ((EditorEx)editor).reinitSettings();
    }
  }

  public Editor createEditor(@NotNull Document document) {
    return createEditor(document, false, null);
  }

  public Editor createViewer(@NotNull Document document) {
    return createEditor(document, true, null);
  }

  public Editor createEditor(@NotNull Document document, Project project) {
    return createEditor(document, false, project);
  }

  public Editor createViewer(@NotNull Document document, Project project) {
    return createEditor(document, true, project);
  }

  public Editor createEditor(@NotNull final Document document, final Project project, @NotNull final FileType fileType, final boolean isViewer) {
    Editor editor = createEditor(document, isViewer, project);
    ((EditorEx)editor).setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(project, fileType));
    return editor;
  }

  public Editor createEditor(@NotNull Document document, Project project, @NotNull VirtualFile file, boolean isViewer) {
    Editor editor = createEditor(document, isViewer, project);
    ((EditorEx)editor).setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(project, file));
    return editor;
  }

  private Editor createEditor(@NotNull Document document, boolean isViewer, Project project) {
    Document hostDocument = document instanceof DocumentWindow ? ((DocumentWindow)document).getDelegate() : document;
    EditorImpl editor = new EditorImpl(hostDocument, isViewer, project);
    myEditors.add(editor);
    myEditorEventMulticaster.registerEditor(editor);
    myEditorFactoryEventDispatcher.getMulticaster().editorCreated(new EditorFactoryEvent(this, editor));

    if (LOG.isDebugEnabled()) {
      LOG.debug("number of Editor's:" + myEditors.size());
      //Thread.dumpStack();
    }

    String text = StringUtil.getThrowableText(new RuntimeException("Editor created"));
    editor.putUserData(EDITOR_CREATOR, text);

    return editor;
  }

  public void releaseEditor(@NotNull Editor editor) {
    editor.putUserData(EDITOR_CREATOR, null);
    ((EditorImpl)editor).release();
    myEditors.remove(editor);
    myEditorFactoryEventDispatcher.getMulticaster().editorReleased(new EditorFactoryEvent(this, editor));

    if (LOG.isDebugEnabled()) {
      LOG.debug("number of Editor's:" + myEditors.size());
      //Thread.dumpStack();
    }
  }

  @NotNull
  public Editor[] getEditors(@NotNull Document document, Project project) {
    List<Editor> list = null;
    for (Editor editor : myEditors) {
      Project project1 = editor.getProject();
      if (editor.getDocument().equals(document) && (project == null || project1 == null || project1.equals(project))) {
        if (list == null) list = new SmartList<Editor>();
        list.add(editor);
      }
    }
    return list == null ? Editor.EMPTY_ARRAY : list.toArray(new Editor[list.size()]);
  }

  @NotNull
  public Editor[] getEditors(@NotNull Document document) {
    return getEditors(document, null);
  }

  @NotNull
  public Editor[] getAllEditors() {
    return myEditors.toArray(new Editor[myEditors.size()]);
  }

  public void addEditorFactoryListener(@NotNull EditorFactoryListener listener) {
    myEditorFactoryEventDispatcher.addListener(listener);
  }

  @Override
  public void addEditorFactoryListener(@NotNull EditorFactoryListener listener, @NotNull Disposable parentDisposable) {
    myEditorFactoryEventDispatcher.addListener(listener,parentDisposable);
  }

  public void removeEditorFactoryListener(@NotNull EditorFactoryListener listener) {
    myEditorFactoryEventDispatcher.removeListener(listener);
  }

  @NotNull
  public EditorEventMulticaster getEventMulticaster() {
    return myEditorEventMulticaster;
  }
}

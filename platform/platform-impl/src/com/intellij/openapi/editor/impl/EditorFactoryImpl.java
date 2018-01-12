/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.editor.impl;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityStateListener;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.actionSystem.*;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.impl.event.EditorEventMulticasterImpl;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.impl.ProjectLifecycleListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EventDispatcher;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.text.CharArrayCharSequence;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class EditorFactoryImpl extends EditorFactory implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.EditorFactoryImpl");
  private final EditorEventMulticasterImpl myEditorEventMulticaster = new EditorEventMulticasterImpl();
  private final EventDispatcher<EditorFactoryListener> myEditorFactoryEventDispatcher = EventDispatcher.create(EditorFactoryListener.class);
  private final List<Editor> myEditors = ContainerUtil.createLockFreeCopyOnWriteList();

  public EditorFactoryImpl(EditorActionManager editorActionManager) {
    Application application = ApplicationManager.getApplication();
    MessageBus bus = application.getMessageBus();
    MessageBusConnection connect = bus.connect();
    connect.subscribe(ProjectLifecycleListener.TOPIC, new ProjectLifecycleListener() {
      @Override
      public void beforeProjectLoaded(@NotNull final Project project) {
        // validate all editors are disposed after fireProjectClosed() was called, because it's the place where editor should be released
        Disposer.register(project, () -> {
          final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
          final boolean isLastProjectClosed = openProjects.length == 0;
          validateEditorsAreReleased(project, isLastProjectClosed);
        });
      }
    });

    ApplicationManager.getApplication().getMessageBus().connect().subscribe(EditorColorsManager.TOPIC, new EditorColorsListener() {
      @Override
      public void globalSchemeChange(EditorColorsScheme scheme) {
        refreshAllEditors();
      }
    });
    TypedAction typedAction = editorActionManager.getTypedAction();
    TypedActionHandler originalHandler = typedAction.getRawHandler();
    typedAction.setupRawHandler(new MyTypedHandler(originalHandler));
  }

  @Override
  public void initComponent() {
    ModalityStateListener myModalityStateListener = entering -> {
      for (Editor editor : myEditors) {
        ((EditorImpl)editor).beforeModalityStateChanged();
      }
    };
    LaterInvocator.addModalityStateListener(myModalityStateListener, ApplicationManager.getApplication());
  }

  public void validateEditorsAreReleased(Project project, boolean isLastProjectClosed) {
    for (final Editor editor : myEditors) {
      if (editor.getProject() == project || editor.getProject() == null && isLastProjectClosed) {
        try {
          throwNotReleasedError(editor);
        }
        finally {
          releaseEditor(editor);
        }
      }
    }
  }

  @NonNls
  public static void throwNotReleasedError(@NotNull Editor editor) {
    if (editor instanceof EditorImpl) {
      ((EditorImpl)editor).throwEditorNotDisposedError("Editor of " + editor.getClass() + " hasn't been released:");
    }
    else {
      throw new RuntimeException("Editor of " + editor.getClass() +
                                 " and the following text hasn't been released:\n" + editor.getDocument().getText());
    }
  }

  @Override
  @NotNull
  public Document createDocument(@NotNull char[] text) {
    return createDocument(new CharArrayCharSequence(text));
  }

  @Override
  @NotNull
  public Document createDocument(@NotNull CharSequence text) {
    DocumentEx document = new DocumentImpl(text);
    myEditorEventMulticaster.registerDocument(document);
    return document;
  }

  @NotNull
  public Document createDocument(boolean allowUpdatesWithoutWriteAction) {
    DocumentEx document = new DocumentImpl("", allowUpdatesWithoutWriteAction);
    myEditorEventMulticaster.registerDocument(document);
    return document;
  }

  @NotNull
  public Document createDocument(@NotNull CharSequence text, boolean acceptsSlashR, boolean allowUpdatesWithoutWriteAction) {
    DocumentEx document = new DocumentImpl(text, acceptsSlashR, allowUpdatesWithoutWriteAction);
    myEditorEventMulticaster.registerDocument(document);
    return document;
  }

  @Override
  public void refreshAllEditors() {
    for (Editor editor : myEditors) {
      ((EditorEx)editor).reinitSettings();
    }
  }

  @Override
  public Editor createEditor(@NotNull Document document) {
    return createEditor(document, false, null, EditorKind.UNTYPED);
  }

  @Override
  public Editor createViewer(@NotNull Document document) {
    return createEditor(document, true, null, EditorKind.UNTYPED);
  }

  @Override
  public Editor createEditor(@NotNull Document document, Project project) {
    return createEditor(document, false, project, EditorKind.UNTYPED);
  }

  @Override
  public Editor createEditor(@NotNull Document document, @Nullable Project project, @NotNull EditorKind kind) {
    return createEditor(document, false, project, kind);
  }

  @Override
  public Editor createViewer(@NotNull Document document, Project project) {
    return createEditor(document, true, project, EditorKind.UNTYPED);
  }

  @Override
  public Editor createViewer(@NotNull Document document, @Nullable Project project, @NotNull EditorKind kind) {
    return createEditor(document, true, project, kind);
  }

  @Override
  public Editor createEditor(@NotNull final Document document, final Project project, @NotNull final FileType fileType, final boolean isViewer) {
    Editor editor = createEditor(document, isViewer, project, EditorKind.UNTYPED);
    ((EditorEx)editor).setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(project, fileType));
    return editor;
  }

  @Override
  public Editor createEditor(@NotNull Document document, Project project, @NotNull VirtualFile file, boolean isViewer) {
    Editor editor = createEditor(document, isViewer, project, EditorKind.UNTYPED);
    ((EditorEx)editor).setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(project, file));
    return editor;
  }

  @Override
  public Editor createEditor(@NotNull Document document,
                             Project project,
                             @NotNull VirtualFile file,
                             boolean isViewer,
                             @NotNull EditorKind kind) {
    Editor editor = createEditor(document, isViewer, project, kind);
    ((EditorEx)editor).setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(project, file));
    return editor;
  }

  private Editor createEditor(@NotNull Document document, boolean isViewer, Project project, @NotNull EditorKind kind) {
    Document hostDocument = document instanceof DocumentWindow ? ((DocumentWindow)document).getDelegate() : document;
    EditorImpl editor = new EditorImpl(hostDocument, isViewer, project, kind);
    myEditors.add(editor);
    myEditorEventMulticaster.registerEditor(editor);
    myEditorFactoryEventDispatcher.getMulticaster().editorCreated(new EditorFactoryEvent(this, editor));

    if (LOG.isDebugEnabled()) {
      LOG.debug("number of Editors after create: " + myEditors.size());
    }

    return editor;
  }

  @Override
  public void releaseEditor(@NotNull Editor editor) {
    try {
      myEditorFactoryEventDispatcher.getMulticaster().editorReleased(new EditorFactoryEvent(this, editor));
    }
    finally {
      try {
        ((EditorImpl)editor).release();
      }
      finally {
        myEditors.remove(editor);
        if (LOG.isDebugEnabled()) {
          LOG.debug("number of Editors after release: " + myEditors.size());
        }
      }
    }
  }

  @Override
  @NotNull
  public Editor[] getEditors(@NotNull Document document, Project project) {
    List<Editor> list = null;
    for (Editor editor : myEditors) {
      Project project1 = editor.getProject();
      if (editor.getDocument().equals(document) && (project == null || project1 == null || project1.equals(project))) {
        if (list == null) list = new SmartList<>();
        list.add(editor);
      }
    }
    return list == null ? Editor.EMPTY_ARRAY : list.toArray(new Editor[list.size()]);
  }

  @Override
  @NotNull
  public Editor[] getEditors(@NotNull Document document) {
    return getEditors(document, null);
  }

  @Override
  @NotNull
  public Editor[] getAllEditors() {
    return myEditors.toArray(Editor.EMPTY_ARRAY);
  }

  @Override
  @Deprecated
  public void addEditorFactoryListener(@NotNull EditorFactoryListener listener) {
    myEditorFactoryEventDispatcher.addListener(listener);
  }

  @Override
  public void addEditorFactoryListener(@NotNull EditorFactoryListener listener, @NotNull Disposable parentDisposable) {
    myEditorFactoryEventDispatcher.addListener(listener,parentDisposable);
  }

  @Override
  @Deprecated
  public void removeEditorFactoryListener(@NotNull EditorFactoryListener listener) {
    myEditorFactoryEventDispatcher.removeListener(listener);
  }

  @Override
  @NotNull
  public EditorEventMulticaster getEventMulticaster() {
    return myEditorEventMulticaster;
  }

  private static class MyTypedHandler implements TypedActionHandlerEx {
    private final TypedActionHandler myDelegate;

    private MyTypedHandler(TypedActionHandler delegate) {
      myDelegate = delegate;
    }

    @Override
    public void execute(@NotNull Editor editor, char charTyped, @NotNull DataContext dataContext) {
      editor.putUserData(EditorImpl.DISABLE_CARET_SHIFT_ON_WHITESPACE_INSERTION, Boolean.TRUE);
      try {
        myDelegate.execute(editor, charTyped, dataContext);
      }
      finally {
        editor.putUserData(EditorImpl.DISABLE_CARET_SHIFT_ON_WHITESPACE_INSERTION, null);
      }
    }

    @Override
    public void beforeExecute(@NotNull Editor editor, char c, @NotNull DataContext context, @NotNull ActionPlan plan) {
      if (myDelegate instanceof TypedActionHandlerEx) ((TypedActionHandlerEx)myDelegate).beforeExecute(editor, c, context, plan);
    }
  }
}

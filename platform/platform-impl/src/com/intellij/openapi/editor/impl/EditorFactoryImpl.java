// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityStateListener;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.actionSystem.ActionPlan;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.editor.actionSystem.TypedActionHandlerEx;
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
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.text.CharArrayCharSequence;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

public class EditorFactoryImpl extends EditorFactory {
  private static final ExtensionPointName<EditorFactoryListener> EP = new ExtensionPointName<>("com.intellij.editorFactoryListener");

  private static final Logger LOG = Logger.getInstance(EditorFactoryImpl.class);
  private final EditorEventMulticasterImpl myEditorEventMulticaster = new EditorEventMulticasterImpl();
  private final EventDispatcher<EditorFactoryListener> myEditorFactoryEventDispatcher = EventDispatcher.create(EditorFactoryListener.class);
  private final List<Editor> myEditors = ContainerUtil.createLockFreeCopyOnWriteList();

  public EditorFactoryImpl() {
    MessageBusConnection busConnection = ApplicationManager.getApplication().getMessageBus().connect();
    busConnection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectClosed(@NotNull Project project) {
        // validate all editors are disposed after fireProjectClosed() was called, because it's the place where editor should be released
        Disposer.register(project, () -> {
          Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
          boolean isLastProjectClosed = openProjects.length == 0;
          // EditorTextField.releaseEditorLater defer releasing its editor; invokeLater to avoid false positives about such editors.
          ApplicationManager.getApplication().invokeLater(() -> validateEditorsAreReleased(project, isLastProjectClosed));
        });
      }
    });
    busConnection.subscribe(EditorColorsManager.TOPIC, new EditorColorsListener() {
      @Override
      public void globalSchemeChange(@Nullable EditorColorsScheme scheme) {
        refreshAllEditors();
      }
    });

    LaterInvocator.addModalityStateListener(new ModalityStateListener() {
      @Override
      public void beforeModalityStateChanged(boolean entering, @NotNull Object modalEntity) {
        for (Editor editor : myEditors) {
          ((EditorImpl)editor).beforeModalityStateChanged();
        }
      }
    }, ApplicationManager.getApplication());
  }

  public void validateEditorsAreReleased(@NotNull Project project, boolean isLastProjectClosed) {
    for (Editor editor : myEditors) {
      if (editor.getProject() == project || (editor.getProject() == null && isLastProjectClosed)) {
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
  public @NotNull Document createDocument(char @NotNull [] text) {
    return createDocument(new CharArrayCharSequence(text));
  }

  @Override
  public @NotNull Document createDocument(@NotNull CharSequence text) {
    DocumentEx document = new DocumentImpl(text);
    myEditorEventMulticaster.registerDocument(document);
    return document;
  }

  public @NotNull Document createDocument(boolean allowUpdatesWithoutWriteAction) {
    DocumentEx document = new DocumentImpl("", allowUpdatesWithoutWriteAction);
    myEditorEventMulticaster.registerDocument(document);
    return document;
  }

  public @NotNull Document createDocument(@NotNull CharSequence text, boolean acceptsSlashR, boolean allowUpdatesWithoutWriteAction) {
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
  public Editor createEditor(final @NotNull Document document, final Project project, final @NotNull FileType fileType, final boolean isViewer) {
    EditorEx editor = createEditor(document, isViewer, project, EditorKind.UNTYPED);
    editor.setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(project, fileType));
    return editor;
  }

  @Override
  public Editor createEditor(@NotNull Document document, Project project, @NotNull VirtualFile file, boolean isViewer) {
    EditorEx editor = createEditor(document, isViewer, project, EditorKind.UNTYPED);
    editor.setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(project, file));
    return editor;
  }

  @Override
  public Editor createEditor(@NotNull Document document,
                             Project project,
                             @NotNull VirtualFile file,
                             boolean isViewer,
                             @NotNull EditorKind kind) {
    EditorEx editor = createEditor(document, isViewer, project, kind);
    editor.setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(project, file));
    return editor;
  }

  private @NotNull EditorEx createEditor(@NotNull Document document, boolean isViewer, Project project, @NotNull EditorKind kind) {
    Document hostDocument = document instanceof DocumentWindow ? ((DocumentWindow)document).getDelegate() : document;
    EditorImpl editor = new EditorImpl(hostDocument, isViewer, project, kind);
    myEditors.add(editor);
    myEditorEventMulticaster.registerEditor(editor);

    EditorFactoryEvent event = new EditorFactoryEvent(this, editor);
    myEditorFactoryEventDispatcher.getMulticaster().editorCreated(event);
    EP.forEachExtensionSafe(it -> it.editorCreated(event));

    if (LOG.isDebugEnabled()) {
      LOG.debug("number of Editors after create: " + myEditors.size());
    }

    return editor;
  }

  @Override
  public void releaseEditor(@NotNull Editor editor) {
    try {
      EditorFactoryEvent event = new EditorFactoryEvent(this, editor);
      myEditorFactoryEventDispatcher.getMulticaster().editorReleased(event);
      EP.forEachExtensionSafe(it -> it.editorReleased(event));
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
  public @NotNull Stream<Editor> editors(@NotNull Document document, @Nullable Project project) {
    return myEditors.stream().filter(editor -> editor.getDocument().equals(document) && (project == null || project.equals(editor.getProject())));
  }

  @Override
  public Editor @NotNull [] getAllEditors() {
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
  public @NotNull EditorEventMulticaster getEventMulticaster() {
    return myEditorEventMulticaster;
  }

  public static final class MyRawTypedHandler implements TypedActionHandlerEx {
    private final TypedActionHandler myDelegate;

    public MyRawTypedHandler(TypedActionHandler delegate) {
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
      if (myDelegate instanceof TypedActionHandlerEx) {
        ((TypedActionHandlerEx)myDelegate).beforeExecute(editor, c, context, plan);
      }
    }
  }
}

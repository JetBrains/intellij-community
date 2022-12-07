// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ModalityStateListener;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
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
import com.intellij.openapi.editor.impl.view.EditorPainter;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCloseListener;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EventDispatcher;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.text.CharArrayCharSequence;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

public class EditorFactoryImpl extends EditorFactory {
  private static final ExtensionPointName<EditorFactoryListener> EP = new ExtensionPointName<>("com.intellij.editorFactoryListener");

  private static final Logger LOG = Logger.getInstance(EditorFactoryImpl.class);
  private final EditorEventMulticasterImpl myEditorEventMulticaster = new EditorEventMulticasterImpl();
  private final EventDispatcher<EditorFactoryListener> myEditorFactoryEventDispatcher = EventDispatcher.create(EditorFactoryListener.class);

  public EditorFactoryImpl() {
    MessageBusConnection busConnection = ApplicationManager.getApplication().getMessageBus().connect();
    busConnection.subscribe(ProjectCloseListener.TOPIC, new ProjectCloseListener() {
      @Override
      public void projectClosed(@NotNull Project project) {
        // validate all editors are disposed after fireProjectClosed() was called, because it's the place where editor should be released
        Disposer.register(project, () -> {
          Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
          boolean isLastProjectClosed = openProjects.length == 0;
          // EditorTextField.releaseEditorLater defer releasing its editor; invokeLater to avoid false positives about such editors.
          ApplicationManager.getApplication().invokeLater(() -> validateEditorsAreReleased(project, isLastProjectClosed), ModalityState.any());
        });
      }
    });
    busConnection.subscribe(EditorColorsManager.TOPIC, new EditorColorsListener() {
      @Override
      public void globalSchemeChange(@Nullable EditorColorsScheme scheme) {
        refreshAllEditors();
      }
    });
    busConnection.subscribe(AdvancedSettingsChangeListener.TOPIC, new AdvancedSettingsChangeListener() {
      @Override
      public void advancedSettingChanged(@NotNull String id, @NotNull Object oldValue, @NotNull Object newValue) {
        if (id.equals(EditorGutterComponentImpl.DISTRACTION_FREE_MARGIN) ||
            id.equals(EditorPainter.EDITOR_TAB_PAINTING) ||
            id.equals(SettingsImpl.EDITOR_SHOW_SPECIAL_CHARS)) {
          refreshAllEditors();
        }
      }
    });

    LaterInvocator.addModalityStateListener(new ModalityStateListener() {
      @Override
      public void beforeModalityStateChanged(boolean entering, @NotNull Object modalEntity) {
        collectAllEditors().forEach(editor -> {
          ((EditorImpl)editor).beforeModalityStateChanged();
        });
      }
    }, ApplicationManager.getApplication());
  }

  public void validateEditorsAreReleased(@NotNull Project project, boolean isLastProjectClosed) {
    collectAllEditors().forEach(editor -> {
      if (editor.getProject() == project || (editor.getProject() == null && isLastProjectClosed)) {
        try {
          throwNotReleasedError(editor);
        }
        finally {
          releaseEditor(editor);
        }
      }
    });
  }

  @NonNls
  public static void throwNotReleasedError(@NotNull Editor editor) {
    if (editor instanceof EditorImpl) {
      ((EditorImpl)editor).throwDisposalError("Editor " + editor + " hasn't been released:");
    }
    throw new RuntimeException("Editor of " + editor.getClass() +
                               " and the following text hasn't been released:\n" + editor.getDocument().getText());
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
    collectAllEditors().forEach(editor -> {
      ((EditorEx)editor).reinitSettings();
    });
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

  private @NotNull EditorImpl createEditor(@NotNull Document document, boolean isViewer, Project project, @NotNull EditorKind kind) {
    Document hostDocument = document instanceof DocumentWindow ? ((DocumentWindow)document).getDelegate() : document;
    EditorImpl editor = new EditorImpl(hostDocument, isViewer, project, kind, null);
    ClientEditorManager editorManager = ClientEditorManager.getCurrentInstance();
    postEditorCreation(editor, editorManager);
    return editor;
  }

  @ApiStatus.Internal
  @ApiStatus.Experimental
  public @NotNull EditorImpl createMainEditor(@NotNull Document document, @NotNull Project project, @NotNull VirtualFile file) {
    assert !(document instanceof DocumentWindow);
    EditorImpl editor = new EditorImpl(document, false, project, EditorKind.MAIN_EDITOR, file);
    ClientEditorManager editorManager = ClientEditorManager.getCurrentInstance();
    postEditorCreation(editor, editorManager);
    return editor;
  }

  private void postEditorCreation(@NotNull EditorImpl editor, @NotNull ClientEditorManager editorManager) {
    editorManager.editorCreated(editor);
    myEditorEventMulticaster.registerEditor(editor);

    EditorFactoryEvent event = new EditorFactoryEvent(this, editor);
    myEditorFactoryEventDispatcher.getMulticaster().editorCreated(event);
    EP.forEachExtensionSafe(it -> it.editorCreated(event));

    if (LOG.isDebugEnabled()) {
      LOG.debug("number of editors after create: " + editorManager.editors().count());
    }
  }

  @Override
  public void releaseEditor(@NotNull Editor editor) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    try {
      EditorFactoryEvent event = new EditorFactoryEvent(this, editor);
      myEditorFactoryEventDispatcher.getMulticaster().editorReleased(event);
      EP.forEachExtensionSafe(it -> it.editorReleased(event));
    }
    finally {
      try {
        if (editor instanceof EditorImpl) {
          ((EditorImpl)editor).release();
        }
      }
      finally {
        for (ClientEditorManager clientEditors : ClientEditorManager.getAllInstances()) {
          if (clientEditors.editorReleased(editor)) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("number of Editors after release: " + clientEditors.editors().count());
            }
            if (clientEditors != ClientEditorManager.getCurrentInstance()) {
              LOG.warn("Released editor didn't belong to current session");
            }
            break;
          }
        }
      }
    }
  }

  @Override
  public @NotNull Stream<Editor> editors(@NotNull Document document, @Nullable Project project) {
    return collectAllEditors()
      .filter(editor -> editor.getDocument().equals(document) && (project == null || project.equals(editor.getProject())));
  }

  private static @NotNull Stream<Editor> collectAllEditors() {
    return ClientEditorManager.getAllInstances().stream().flatMap(ClientEditorManager::editors);
  }

  @Override
  public Editor @NotNull [] getAllEditors() {
    return collectAllEditors().toArray(Editor[]::new);
  }

  @Override
  @Deprecated
  public void addEditorFactoryListener(@NotNull EditorFactoryListener listener) {
    myEditorFactoryEventDispatcher.addListener(listener);
  }

  @Override
  public void addEditorFactoryListener(@NotNull EditorFactoryListener listener, @NotNull Disposable parentDisposable) {
    myEditorFactoryEventDispatcher.addListener(listener, parentDisposable);
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

  private static final class MyRawTypedHandler implements TypedActionHandlerEx {
    private final TypedActionHandler myDelegate;

    private MyRawTypedHandler(TypedActionHandler delegate) {
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

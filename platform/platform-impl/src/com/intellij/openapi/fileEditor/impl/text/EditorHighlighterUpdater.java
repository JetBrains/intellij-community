// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.cl.PluginAwareClassLoader;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EmptyEditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.KeyedFactoryEPBean;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class EditorHighlighterUpdater {
  protected final @NotNull Project project;
  protected final @NotNull EditorEx editor;
  private final @Nullable VirtualFile file;

  public EditorHighlighterUpdater(@NotNull Project project, @NotNull Disposable parentDisposable, @NotNull EditorEx editor, @Nullable VirtualFile file) {
    this.project = project;
    this.editor = editor;
    this.file = file;
    connect(parentDisposable, project.getMessageBus().connect(parentDisposable));
  }

  private void connect(@NotNull Disposable parentDisposable, @NotNull MessageBusConnection connection) {
    connection.subscribe(FileTypeManager.TOPIC, new MyFileTypeListener());
    connection.subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      @Override
      public void enteredDumbMode() {
        updateHighlighters();
      }

      @Override
      public void exitDumbMode() {
        updateHighlighters();
      }
    });

    updateHighlightersOnExtensionChange(parentDisposable, LanguageSyntaxHighlighters.EP_NAME);
    updateHighlightersOnExtensionChange(parentDisposable, SyntaxHighlighterLanguageFactory.EP_NAME);
    updateHighlightersOnExtensionChange(parentDisposable, FileTypeEditorHighlighterProviders.EP_NAME);

    SyntaxHighlighter.EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull KeyedFactoryEPBean extension, @NotNull PluginDescriptor pluginDescriptor) {
        checkUpdateHighlighters(extension.key, false);
      }

      @Override
      public void extensionRemoved(@NotNull KeyedFactoryEPBean extension, @NotNull PluginDescriptor pluginDescriptor) {
        checkUpdateHighlighters(extension.key, true);
      }
    }, parentDisposable);

    connection.subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        IdeaPluginDescriptor loadedPluginDescriptor = PluginManagerCore.getPlugin(pluginDescriptor.getPluginId());
        ClassLoader pluginClassLoader = loadedPluginDescriptor != null ? loadedPluginDescriptor.getPluginClassLoader() : null;
        if (EditorHighlighterUpdater.this.file != null && pluginClassLoader instanceof PluginAwareClassLoader) {
          FileType fileType = EditorHighlighterUpdater.this.file.getFileType();
          if (fileType.getClass().getClassLoader() == pluginClassLoader ||
              (fileType instanceof LanguageFileType && ((LanguageFileType) fileType).getClass().getClassLoader() == pluginClassLoader)) {
            setupHighlighter(createHighlighter(true));
          }
        }
      }
    });
  }

  private <T> void updateHighlightersOnExtensionChange(@NotNull Disposable parentDisposable, @NotNull ExtensionPointName<KeyedLazyInstance<T>> epName) {
    epName.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull KeyedLazyInstance<T> extension, @NotNull PluginDescriptor pluginDescriptor) {
        checkUpdateHighlighters(extension.getKey(), false);
      }

      @Override
      public void extensionRemoved(@NotNull KeyedLazyInstance<T> extension, @NotNull PluginDescriptor pluginDescriptor) {
        checkUpdateHighlighters(extension.getKey(), true);
      }
    }, parentDisposable);
  }

  private void checkUpdateHighlighters(String key, boolean updateSynchronously) {
    if (file != null) {
      FileType fileType = file.getFileType();
      boolean needUpdate = (fileType.getName().equals(key) ||
                            (fileType instanceof LanguageFileType && ((LanguageFileType)fileType).getLanguage().getID().equals(key)));
      if (!needUpdate) return;
    }

    if (updateSynchronously && ApplicationManager.getApplication().isDispatchThread()) {
      updateHighlightersSynchronously();
    }
    else {
      updateHighlighters();
    }
  }

  public void updateHighlightersAsync() {
    if (!AsyncEditorLoader.isEditorLoaded(editor)) {
      return;
    }

    ReadAction
      .nonBlocking(() -> createHighlighter(false))
      .expireWith(project)
      .expireWhen(() -> (file != null && !file.isValid()) || editor.isDisposed())
      .coalesceBy(EditorHighlighterUpdater.class, editor)
      .finishOnUiThread(ModalityState.any(), highlighter -> setupHighlighter(highlighter))
      .submit(NonUrgentExecutor.getInstance());
  }

  protected @NotNull EditorHighlighter createHighlighter(boolean forceEmpty) {
    EditorHighlighter highlighter = file != null && !forceEmpty
                                    ? EditorHighlighterFactory.getInstance().createEditorHighlighter(project, file)
                                    : new EmptyEditorHighlighter(EditorColorsManager.getInstance().getGlobalScheme(),
                                                                 HighlighterColors.TEXT);
    highlighter.setText(editor.getDocument().getImmutableCharSequence());
    return highlighter;
  }

  protected void setupHighlighter(@NotNull EditorHighlighter highlighter) {
    editor.setHighlighter(highlighter);
  }

  /**
   * Updates editors' highlighters. This should be done when the opened file changes its file type.
   */
  public void updateHighlighters() {
    if (!project.isDisposed() && !editor.isDisposed()) {
      updateHighlightersAsync();
    }
  }

  private void updateHighlightersSynchronously() {
    if (!project.isDisposed() && !editor.isDisposed()) {
      setupHighlighter(createHighlighter(false));
    }
  }

  @TestOnly
  public static void completeAsyncTasks() {
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
  }

  /**
   * Listen to changes of file types. When the type of the file changes, we need to also change highlighter.
   */
  private final class MyFileTypeListener implements FileTypeListener {
    @Override
    public void fileTypesChanged(final @NotNull FileTypeEvent event) {
      ThreadingAssertions.assertEventDispatchThread();
      // File can be invalid after file type changing. The editor should be removed by the FileEditorManager if it's invalid.
      FileType type = event.getRemovedFileType();
      if (type != null && !(type instanceof AbstractFileType)) {
        // Plugin is being unloaded, so we need to release plugin classes immediately
        updateHighlightersSynchronously();
      }
      else {
        updateHighlighters();
      }
    }
  }
}

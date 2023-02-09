// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement.editor;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileEditor.AsyncFileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorProvider;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import org.editorconfig.language.filetype.EditorConfigFileType;
import org.editorconfig.settings.EditorConfigSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class EditorConfigEditorProvider implements AsyncFileEditorProvider, DumbAware {
  private final static String EDITOR_TYPE_ID = "org.editorconfig.configmanagement.editor";

  final static int MAX_PREVIEW_LENGTH = 10000;

  private final static PsiAwareTextEditorProvider myMainEditorProvider = new PsiAwareTextEditorProvider();

  @NotNull
  @Override
  public Builder createEditorAsync(@NotNull Project project,
                                   @NotNull VirtualFile file) {
    return new MyEditorBuilder(project, file);
  }

  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    return FileTypeRegistry.getInstance().isFileOfType(file, EditorConfigFileType.INSTANCE);
  }

  @NotNull
  @Override
  public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    return new MyEditorBuilder(project, file).build();
  }

  @NotNull
  @Override
  public String getEditorTypeId() {
    return EDITOR_TYPE_ID;
  }

  @NotNull
  @Override
  public FileEditorPolicy getPolicy() {
    return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
  }

  private static final class MyEditorBuilder extends Builder {
    private final Project myProject;
    private final VirtualFile myFile;

    private MyEditorBuilder(Project project, VirtualFile file) {
      myProject = project;
      myFile = file;
    }

    @Override
    public FileEditor build() {
      VirtualFile contextFile = EditorConfigPreviewManager.getInstance(myProject).getAssociatedPreviewFile(myFile);
      EditorConfigStatusListener statusListener = new EditorConfigStatusListener(myProject, myFile);
      if (contextFile != null && CodeStyle.getSettings(myProject).getCustomSettings(EditorConfigSettings.class).ENABLED) {
        Document document =EditorFactory.getInstance().createDocument(getPreviewText(contextFile));
        Disposable previewDisposable = Disposer.newDisposable();
        final EditorConfigPreviewFile previewFile = new EditorConfigPreviewFile(myProject, contextFile, document, previewDisposable);
        FileEditor previewEditor = createPreviewEditor(document, previewFile);
        TextEditor ecTextEditor = (TextEditor)TextEditorProvider.getInstance().createEditor(myProject, myFile);
        final EditorConfigEditorWithPreview splitEditor = new EditorConfigEditorWithPreview(
          myFile, myProject, ecTextEditor, previewEditor);
        Disposer.register(splitEditor, previewDisposable);
        Disposer.register(splitEditor, statusListener);
        return splitEditor;
      }
      else {
        FileEditor fileEditor = myMainEditorProvider.createEditor(myProject, myFile);
        Disposer.register(fileEditor, statusListener);
        return fileEditor;
      }
    }

    private FileEditor createPreviewEditor(@NotNull Document document, @NotNull EditorConfigPreviewFile previewFile) {
      Editor previewEditor = EditorFactory.getInstance().createEditor(document, myProject);
      if (previewEditor instanceof EditorEx) {
        EditorHighlighter highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(myProject, previewFile);
        ((EditorEx)previewEditor).setHighlighter(highlighter);
      }
      return new EditorConfigPreviewFileEditor(previewEditor, previewFile);
    }

    @NotNull
    private static String getPreviewText(@NotNull VirtualFile file) {
      if (file.getLength() <= MAX_PREVIEW_LENGTH) {
        try {
          return StringUtil.convertLineSeparators(VfsUtilCore.loadText(file));
        }
        catch (IOException e) {
          // Ignore
        }
      }
      Language language = getLanguage(file);
      if (language != null) {
        LanguageCodeStyleSettingsProvider provider = LanguageCodeStyleSettingsProvider.forLanguage(language);
        if (provider != null) {
          String sample = provider.getCodeSample(LanguageCodeStyleSettingsProvider.SettingsType.INDENT_SETTINGS);
          if (sample != null) return sample;
        }
      }
      return "No preview";
    }
  }

  @Nullable
  static Language getLanguage(@NotNull VirtualFile virtualFile) {
    FileType fileType = virtualFile.getFileType();
    return fileType instanceof LanguageFileType ? ((LanguageFileType)fileType).getLanguage() : null;
  }
}

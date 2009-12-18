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
package com.intellij.openapi.editor.highlighter;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.LanguageSubstitutors;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class EditorHighlighterFactoryImpl extends EditorHighlighterFactory {
  public EditorHighlighter createEditorHighlighter(SyntaxHighlighter highlighter, final EditorColorsScheme colors) {
    if (highlighter == null) highlighter = new PlainSyntaxHighlighter();
    return new LexerEditorHighlighter(highlighter, colors);
  }

  public EditorHighlighter createEditorHighlighter(final FileType fileType, final EditorColorsScheme settings, final Project project) {
    if (fileType instanceof LanguageFileType) {
      return ((LanguageFileType)fileType).getEditorHighlighter(project, null, settings);
    }

    SyntaxHighlighter highlighter = SyntaxHighlighter.PROVIDER.create(fileType, project, null);
    return createEditorHighlighter(highlighter, settings);
  }

  public EditorHighlighter createEditorHighlighter(final Project project, final FileType fileType) {
    return createEditorHighlighter(fileType, EditorColorsManager.getInstance().getGlobalScheme(), project);
  }

  public EditorHighlighter createEditorHighlighter(final VirtualFile vFile, final EditorColorsScheme settings, final Project project) {
    final FileType fileType = vFile.getFileType();
    if (fileType instanceof LanguageFileType) {
      LanguageFileType substFileType = substituteFileType(((LanguageFileType)fileType).getLanguage(), vFile, project);
      if (substFileType != null) {
        final EditorHighlighter editorHighlighter = substFileType.getEditorHighlighter(project, vFile, settings);
        boolean isPlain = editorHighlighter.getClass() == LexerEditorHighlighter.class &&
                          ((LexerEditorHighlighter) editorHighlighter).isPlain();
        if (!isPlain) {
          return editorHighlighter;
        }
      }
      return ((LanguageFileType)fileType).getEditorHighlighter(project, vFile, settings);
    }

    final ContentBasedClassFileProcessor[] processors = Extensions.getExtensions(ContentBasedClassFileProcessor.EP_NAME);
    SyntaxHighlighter highlighter = null;
    for (ContentBasedClassFileProcessor processor : processors) {
      if (processor.isApplicable(project, vFile)) {
        highlighter = processor.createHighlighter(project, vFile);
      }
    }
    if (highlighter == null) {
      highlighter = SyntaxHighlighter.PROVIDER.create(fileType, project, vFile);
    }
    return createEditorHighlighter(highlighter, settings);
  }

  @Nullable
  private static LanguageFileType substituteFileType(Language language, VirtualFile vFile, Project project) {
    final Language substLanguage = LanguageSubstitutors.INSTANCE.substituteLanguage(language, vFile, project);
    if (substLanguage != language) {
      final FileType fileType = substLanguage.getAssociatedFileType();
      if (fileType instanceof LanguageFileType) {
        return (LanguageFileType) fileType;
      }
    }
    return null;
  }

  public EditorHighlighter createEditorHighlighter(final Project project, final VirtualFile file) {
    return createEditorHighlighter(file, EditorColorsManager.getInstance().getGlobalScheme(), project);
  }

  public EditorHighlighter createEditorHighlighter(final Project project, final String fileName) {
    return createEditorHighlighter(EditorColorsManager.getInstance().getGlobalScheme(), fileName, project);
  }

  public EditorHighlighter createEditorHighlighter(final EditorColorsScheme settings, final String fileName, final Project project) {
    return createEditorHighlighter(new LightVirtualFile(fileName), settings, project);
  }
}
/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui;

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.lang.Language;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LanguageTextField extends EditorTextField {
  private final @Nullable Language myLanguage;
  // Could be null to allow usage in UI designer, as EditorTextField permits
  private final @Nullable Project myProject;

  public LanguageTextField() {
    this(null, null, "");
  }

  public LanguageTextField(Language language, @Nullable Project project, @NotNull String value) {
    this(language, project, value, true);
  }

  public LanguageTextField(Language language, @Nullable Project project, @NotNull String value, boolean oneLineMode) {
    this(language, project, value, new SimpleDocumentCreator(), oneLineMode);
  }

  public LanguageTextField(@Nullable Language language,
                           @Nullable Project project,
                           @NotNull String value,
                           @NotNull DocumentCreator documentCreator) {
    this(language, project, value, documentCreator, true);
  }

  public LanguageTextField(@Nullable Language language,
                           @Nullable Project project,
                           @NotNull String value,
                           @NotNull DocumentCreator documentCreator,
                           boolean oneLineMode) {
    super(documentCreator.createDocument(value, language, project), project,
          language != null ? language.getAssociatedFileType() : FileTypes.PLAIN_TEXT, language == null, oneLineMode);

    myLanguage = language;
    myProject = project;

    setEnabled(language != null);
  }

  public interface DocumentCreator {
    Document createDocument(String value, @Nullable Language language, Project project);
  }

  public static class SimpleDocumentCreator implements DocumentCreator {
    @Override
    public Document createDocument(String value, @Nullable Language language, Project project) {
      return LanguageTextField.createDocument(value, language, project, this);
    }

    public void customizePsiFile(PsiFile file) {
    }
  }

  public static Document createDocument(String value, @Nullable Language language, @Nullable Project project,
                                        @NotNull SimpleDocumentCreator documentCreator) {
    final FileType fileType = language != null ? language.getAssociatedFileType() : null;
    if (fileType != null) {
      final Project notNullProject = project != null ? project : ProjectManager.getInstance().getDefaultProject();
      final PsiFileFactory factory = PsiFileFactory.getInstance(notNullProject);

      long stamp = LocalTimeCounter.currentTime();
      PsiFile psiFile = ReadAction.compute(
        () -> factory.createFileFromText("Dummy." + fileType.getDefaultExtension(), fileType, value, stamp, true, false));
      documentCreator.customizePsiFile(psiFile);

      // No need to guess project in getDocument - we already know it
      Document document;
      try (AccessToken ignored = ProjectLocator.withPreferredProject(psiFile.getVirtualFile(), notNullProject)) {
        document = ReadAction.compute(() -> PsiDocumentManager.getInstance(notNullProject).getDocument(psiFile));
      }
      assert document != null;
      return document;
    }
    else {
      return EditorFactory.getInstance().createDocument(value);
    }
  }

  @Override
  protected @NotNull EditorEx createEditor() {
    EditorEx editor = super.createEditor();
    if (myLanguage != null && (myProject == null || !myProject.isDisposed())) {
      FileType fileType = myLanguage.getAssociatedFileType();
      if (fileType != null) {
        editor.setHighlighter(HighlighterFactory.createHighlighter(myProject, fileType));
      }
    }
    editor.setEmbeddedIntoDialogWrapper(true);
    return editor;
  }
}

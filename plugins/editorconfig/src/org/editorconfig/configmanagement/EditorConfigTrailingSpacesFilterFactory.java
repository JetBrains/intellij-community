// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.StripTrailingSpacesFilter;
import com.intellij.openapi.editor.StripTrailingSpacesFilterFactory;
import com.intellij.openapi.fileEditor.TrailingSpacesOptionsProvider;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.SlowOperations;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EditorConfigTrailingSpacesFilterFactory extends StripTrailingSpacesFilterFactory {
  @Override
  public @NotNull StripTrailingSpacesFilter createFilter(@Nullable Project project,
                                                         @NotNull Document document) {
    if (project == null) return StripTrailingSpacesFilter.ALL_LINES;
    PsiFile file;
    try (AccessToken ignore = SlowOperations.knownIssue("IDEA-307607, EA-765267")) {
      file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    }
    if (file == null) {
      return StripTrailingSpacesFilter.ALL_LINES;
    }
    EditorConfigTrailingSpacesOptionsProvider optionsProvider =
      TrailingSpacesOptionsProvider.EP_NAME.findExtension(EditorConfigTrailingSpacesOptionsProvider.class);
    if (optionsProvider != null) {
      TrailingSpacesOptionsProvider.Options options = optionsProvider.getOptions(project, file.getVirtualFile());
      if (options != null && Boolean.TRUE.equals(options.getStripTrailingSpaces())) {
        return StripTrailingSpacesFilter.ENFORCED_REMOVAL;
      }
    }
    return StripTrailingSpacesFilter.ALL_LINES;
  }
}

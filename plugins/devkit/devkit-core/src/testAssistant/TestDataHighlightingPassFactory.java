// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactoryRegistrar;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

final class TestDataHighlightingPassFactory implements TextEditorHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar {
  public static final List<String> SUPPORTED_FILE_TYPES = Collections.singletonList(JavaFileType.INSTANCE.getDefaultExtension());
  public static final List<@NonNls String> SUPPORTED_IN_TEST_DATA_FILE_TYPES =
    List.of("js", "php", "css", "html", "xhtml", "jsp", "test", "py", "aj", "go", "mod", "work", "sum");
  private static final int MAX_HOPS = 3;

  @Override
  public void registerHighlightingPassFactory(@NotNull TextEditorHighlightingPassRegistrar registrar, @NotNull Project project) {
    registrar.registerTextEditorHighlightingPass(this, null, null, true, -1);
  }

  @Override
  public @Nullable TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile psiFile, final @NotNull Editor editor) {
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile != null) {
      Project project = psiFile.getProject();
      Document document = psiFile.getViewProvider().getDocument();
      if (isSupported(virtualFile, project) && document != null) {
        return new TestDataHighlightingPass(project, document);
      }
    }
    return null;
  }

  private static boolean isSupported(@NotNull VirtualFile file, @NotNull Project project) {
    if (ScratchUtil.isScratch(file)) {
      return false;
    }
    final String ext = file.getExtension();
    if (SUPPORTED_FILE_TYPES.contains(ext)) {
      return ProjectRootManager.getInstance(project).getFileIndex().getSourceRootForFile(file) == null;
    }

    if (ext != null && SUPPORTED_IN_TEST_DATA_FILE_TYPES.contains(ext)) {
      return TestDataDirectories.isNearTestDataDirectory(file, MAX_HOPS);
    }
    return false;
  }
}

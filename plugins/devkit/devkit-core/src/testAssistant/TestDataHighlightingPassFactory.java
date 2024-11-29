// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.util.text.StringUtil;
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
  private static final int MAX_HOPES = 3;
  private static final @NonNls String TEST_DATA = "testdata";

  @Override
  public void registerHighlightingPassFactory(@NotNull TextEditorHighlightingPassRegistrar registrar, @NotNull Project project) {
    registrar.registerTextEditorHighlightingPass(this, null, null, true, -1);
  }

  @Override
  @Nullable
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile psiFile, @NotNull final Editor editor) {
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
      int i = 0;
      VirtualFile parent = file.getParent();
      while (parent != null && i < MAX_HOPES) {
        if (StringUtil.toLowerCase(parent.getName()).contains(TEST_DATA)) {
          return true;
        }
        i++;
        parent = parent.getParent();
      }
    }
    return false;
  }
}

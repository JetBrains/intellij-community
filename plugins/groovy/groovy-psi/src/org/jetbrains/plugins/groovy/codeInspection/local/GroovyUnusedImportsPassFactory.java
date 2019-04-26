// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.local;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

final class GroovyUnusedImportsPassFactory implements TextEditorHighlightingPassFactory, StartupActivity, DumbAware {
  @Override
  public void runActivity(@NotNull Project project) {
    TextEditorHighlightingPassRegistrar.getInstance(project).registerTextEditorHighlightingPass(this, new int[]{Pass.UPDATE_ALL}, null, true, -1);
  }

  @Override
  @Nullable
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
    if (!(file instanceof GroovyFile)) return null;
    return new GroovyPostHighlightingPass((GroovyFile)file, editor);
  }
}

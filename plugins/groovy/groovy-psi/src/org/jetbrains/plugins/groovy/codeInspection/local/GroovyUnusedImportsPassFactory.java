// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.local;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

public class GroovyUnusedImportsPassFactory implements TextEditorHighlightingPassFactory {
  public GroovyUnusedImportsPassFactory(TextEditorHighlightingPassRegistrar registrar) {
    registrar.registerTextEditorHighlightingPass(this, new int[]{Pass.UPDATE_ALL},
                                                 null, true, -1);
  }

  @Override
  @Nullable
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
    if (!(file instanceof GroovyFile)) return null;
    return new GroovyPostHighlightingPass((GroovyFile)file, editor);
  }
}

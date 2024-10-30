// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactoryRegistrar;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptTypeDetector;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.util.GrFileIndexUtil;

final class GrReferenceHighlighterFactory implements TextEditorHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar {
  @Override
  public void registerHighlightingPassFactory(@NotNull TextEditorHighlightingPassRegistrar registrar, @NotNull Project project) {
    registrar.registerTextEditorHighlightingPass(this, null, null, false, -1);
  }

  @Override
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile psiFile, @NotNull Editor editor) {
    PsiFile groovyFile = psiFile.getViewProvider().getPsi(GroovyLanguage.INSTANCE);
    return groovyFile instanceof GroovyFileBase ? new GrReferenceHighlighter(psiFile, (GroovyFileBase)groovyFile, editor.getDocument()) : null;
  }

  static boolean shouldHighlight(@NotNull PsiFile file) {
    return file instanceof GroovyFileBase && shouldHighlight((GroovyFileBase)file);
  }

  static boolean shouldHighlight(@NotNull GroovyFileBase file) {
    return isSpecificScriptFile(file) ||
           GrFileIndexUtil.isGroovySourceFile(file) ||
           ScratchFileService.findRootType(file.getVirtualFile()) != null;
  }

  private static boolean isSpecificScriptFile(@NotNull GroovyFileBase file) {
    return file instanceof GroovyFile && GroovyScriptTypeDetector.isScriptFile((GroovyFile)file);
  }
}

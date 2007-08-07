/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.codeInspection.local;

import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

/**
 * @author ilyas
 */
public class GroovyUnusedImportsPassFactory implements TextEditorHighlightingPassFactory {
  @Nullable
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
    return new GroovyUnusedImportPass(file, editor);
  }

  public void projectOpened() {

  }

  public void projectClosed() {

  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "Groovy unused imports pass factory";
  }

  public void initComponent() {

  }

  public void disposeComponent() {

  }
}

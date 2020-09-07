// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.base;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public abstract class MutablyNamedIntention extends Intention {
  private @IntentionName String text = null;

  protected abstract @IntentionName String getTextForElement(PsiElement element);

  @Override
  @NotNull
  public String getText() {
    return text;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final PsiElement element = findMatchingElement(file, editor);
    if (element != null) {
      text = getTextForElement(element);
    }
    return element != null;
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath;

import com.intellij.jsonpath.psi.JsonPathFunctionCall;
import com.intellij.jsonpath.psi.JsonPathId;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

final class JsonPathFunctionCallAnnotator implements Annotator {
  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (element instanceof JsonPathId && element.getParent() instanceof JsonPathFunctionCall) {
      holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
        .range(element.getTextRange())
        .textAttributes(JsonPathSyntaxHighlighter.JSONPATH_FUNCTION_CALL)
        .create();
    }
  }
}

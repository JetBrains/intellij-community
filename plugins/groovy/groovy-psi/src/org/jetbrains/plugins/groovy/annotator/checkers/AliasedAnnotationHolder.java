/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.annotator.checkers;

import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

/**
* Created by Max Medvedev on 25/03/14
*/
class AliasedAnnotationHolder implements AnnotationHolder {
  private final AnnotationHolder myHolder;
  private final GrAnnotation myAlias;
  private final GrCodeReferenceElement myReference;

  public AliasedAnnotationHolder(@NotNull AnnotationHolder holder, @NotNull GrAnnotation alias) {
    myHolder = holder;
    myAlias = alias;
    myReference = myAlias.getClassReference();
  }

  @NotNull
  private PsiElement findCodeElement(@NotNull PsiElement elt) {
    if (PsiTreeUtil.isAncestor(myAlias, elt, true)) {
      return elt;
    }
    else {
      return myReference;
    }
  }

  @Override
  public Annotation createErrorAnnotation(@NotNull PsiElement elt, @Nullable String message) {
    PsiElement codeElement = findCodeElement(elt);
    return myHolder.createErrorAnnotation(codeElement, message);
  }

  @Override
  public Annotation createErrorAnnotation(@NotNull ASTNode node, @Nullable String message) {
    return createErrorAnnotation(node.getPsi(), message);
  }

  @Override
  public Annotation createErrorAnnotation(@NotNull TextRange range, @Nullable String message) {
    throw new UnsupportedOperationException("unsupported");
  }

  @Override
  public Annotation createWarningAnnotation(@NotNull PsiElement elt, @Nullable String message) {
    return myHolder.createWarningAnnotation(findCodeElement(elt), message);
  }

  @Override
  public Annotation createWarningAnnotation(@NotNull ASTNode node, @Nullable String message) {
    return myHolder.createWarningAnnotation(node.getPsi(), message);
  }

  @Override
  public Annotation createWarningAnnotation(@NotNull TextRange range, @Nullable String message) {
    throw new UnsupportedOperationException("unsupported");
  }

  @Override
  public Annotation createWeakWarningAnnotation(@NotNull PsiElement elt, @Nullable String message) {
    return myHolder.createWeakWarningAnnotation(findCodeElement(elt), message);
  }

  @Override
  public Annotation createWeakWarningAnnotation(@NotNull ASTNode node, @Nullable String message) {
    return myHolder.createWarningAnnotation(node.getPsi(), message);
  }

  @Override
  public Annotation createWeakWarningAnnotation(@NotNull TextRange range, @Nullable String message) {
    throw new UnsupportedOperationException("unsupported");
  }

  @Override
  public Annotation createInfoAnnotation(@NotNull PsiElement elt, @Nullable String message) {
    return myHolder.createInfoAnnotation(findCodeElement(elt), message);
  }

  @Override
  public Annotation createInfoAnnotation(@NotNull ASTNode node, @Nullable String message) {
    return myHolder.createInfoAnnotation(node.getPsi(), message);
  }

  @Override
  public Annotation createInfoAnnotation(@NotNull TextRange range, @Nullable String message) {
    throw new UnsupportedOperationException("unsupported");
  }

  @Override
  public Annotation createAnnotation(@NotNull HighlightSeverity severity, @NotNull TextRange range, @Nullable String message) {
    throw new UnsupportedOperationException("unsupported");
  }

  @Override
  public Annotation createAnnotation(@NotNull HighlightSeverity severity,
                                     @NotNull TextRange range,
                                     @Nullable String message,
                                     @Nullable String htmlTooltip) {
    throw new UnsupportedOperationException("unsupported");
  }

  @NotNull
  @Override
  public AnnotationSession getCurrentAnnotationSession() {
    return myHolder.getCurrentAnnotationSession();
  }

  @Override
  public boolean isBatchMode() {
    return myHolder.isBatchMode();
  }
}

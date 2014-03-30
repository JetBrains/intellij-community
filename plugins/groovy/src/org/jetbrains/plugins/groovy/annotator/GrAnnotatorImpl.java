/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.annotator;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author Max Medvedev
 */
public class GrAnnotatorImpl implements Annotator {
  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (element instanceof GroovyPsiElement) {
      final GroovyAnnotator annotator = new GroovyAnnotator(holder);
      ((GroovyPsiElement)element).accept(annotator);
      if (PsiUtil.isCompileStatic(element)) {
        GroovyAssignabilityCheckInspection.checkElement((GroovyPsiElement)element, holder);
      }
    }
    else if (element instanceof PsiComment) {
      String text = element.getText();
      if (text.startsWith("/*") && !(text.endsWith("*/"))) {
        TextRange range = element.getTextRange();
        holder.createErrorAnnotation(TextRange.create(range.getEndOffset() - 1, range.getEndOffset()), GroovyBundle.message("doc.end.expected"));
      }
    }
    else {
      final PsiElement parent = element.getParent();
      if (parent instanceof GrMethod) {
        if (element.equals(((GrMethod)parent).getNameIdentifierGroovy()) && ((GrMethod)parent).getReturnTypeElementGroovy() == null) {
          GroovyAnnotator.checkMethodReturnType((GrMethod)parent, element, holder);
        }
      }
      else if (parent instanceof GrField) {
        final GrField field = (GrField)parent;
        if (element.equals(field.getNameIdentifierGroovy())) {
          final GrAccessorMethod[] getters = field.getGetters();
          for (GrAccessorMethod getter : getters) {
            GroovyAnnotator.checkMethodReturnType(getter, field.getNameIdentifierGroovy(), holder);
          }

          final GrAccessorMethod setter = field.getSetter();
          if (setter != null) {
            GroovyAnnotator.checkMethodReturnType(setter, field.getNameIdentifierGroovy(), holder);
          }
        }
      }
    }
  }
}

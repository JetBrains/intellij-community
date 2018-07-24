// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.type.GroovyStaticTypeCheckVisitor;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author Max Medvedev
 */
public class GrAnnotatorImpl implements Annotator {

  private static final ThreadLocal<GroovyStaticTypeCheckVisitor> myTypeCheckVisitorThreadLocal =
    ThreadLocal.withInitial(() -> new GroovyStaticTypeCheckVisitor());

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (FileIndexFacade.getInstance(element.getProject()).isInLibrarySource(element.getContainingFile().getVirtualFile())) return;
    final GroovyConfigUtils groovyConfig = GroovyConfigUtils.getInstance();
    if (element instanceof GroovyPsiElement) {
      GroovyPsiElement grElement = (GroovyPsiElement)element;
      grElement.accept(new GroovyAnnotator(holder));

      if (groovyConfig.isVersionAtLeast(element, GroovyConfigUtils.GROOVY3_0)) {
        grElement.accept(new GroovyAnnotator30(holder));
      } else {
        grElement.accept(new GroovyAnnotatorPre30(holder));
      }

      if (PsiUtil.isCompileStatic(grElement)) {
        final GroovyStaticTypeCheckVisitor typeCheckVisitor = myTypeCheckVisitorThreadLocal.get();
        assert typeCheckVisitor != null;
        typeCheckVisitor.accept(grElement, holder);
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

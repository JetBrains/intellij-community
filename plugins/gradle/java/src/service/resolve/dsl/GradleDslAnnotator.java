// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve.dsl;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames;
import org.jetbrains.plugins.gradle.service.resolve.GradleNamedDomainCollectionContributor;
import org.jetbrains.plugins.gradle.service.resolve.GradleResolverUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import static org.jetbrains.plugins.gradle.service.resolve.GradleResolverUtil.canBeMethodOf;
import static org.jetbrains.plugins.groovy.highlighter.GroovySyntaxHighlighter.MAP_KEY;

/**
 * @author Vladislav.Soroka
 */
public class GradleDslAnnotator implements Annotator {
  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (element instanceof GrReferenceExpression) {
      GrReferenceExpression referenceExpression = (GrReferenceExpression)element;

      PsiElement resolved = referenceExpression.resolve();
      if (resolved instanceof OriginInfoAwareElement &&
          GradleNamedDomainCollectionContributor.NAMED_DOMAIN_DECLARATION.equals(((OriginInfoAwareElement)resolved).getOriginInfo())) {
        highlightElement(holder, referenceExpression.getReferenceNameElement());
      }

      final GrExpression qualifier = ResolveUtil.getSelfOrWithQualifier(referenceExpression);
      if (qualifier == null) return;
      if (qualifier instanceof GrReferenceExpression && ((GrReferenceExpression)qualifier).resolve() instanceof PsiClass) return;

      PsiType psiType = GradleResolverUtil.getTypeOf(qualifier);
      if (psiType == null) return;
      if (InheritanceUtil.isInheritor(psiType, GradleCommonClassNames.GRADLE_API_NAMED_DOMAIN_OBJECT_COLLECTION)) {
        JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(element.getProject());
        PsiClass defaultGroovyMethodsClass =
          javaPsiFacade.findClass(GroovyCommonClassNames.DEFAULT_GROOVY_METHODS, element.getResolveScope());
        if (canBeMethodOf(referenceExpression.getReferenceName(), defaultGroovyMethodsClass)) return;

        final String qualifiedName = TypesUtil.getQualifiedName(psiType);
        final PsiClass containerClass =
          qualifiedName != null ? javaPsiFacade.findClass(qualifiedName, element.getResolveScope()) : null;
        if (canBeMethodOf(referenceExpression.getReferenceName(), containerClass)) return;

        PsiElement nameElement = referenceExpression.getReferenceNameElement();
        highlightElement(holder, nameElement);
      }
    }
  }

  private static void highlightElement(@NotNull AnnotationHolder holder, PsiElement nameElement) {
    if (nameElement != null) {
      holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(nameElement).textAttributes(MAP_KEY).create();
    }
  }
}

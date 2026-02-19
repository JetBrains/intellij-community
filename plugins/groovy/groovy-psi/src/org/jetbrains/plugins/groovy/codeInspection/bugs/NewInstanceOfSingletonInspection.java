// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_SINGLETON;
import static org.jetbrains.plugins.groovy.transformations.singleton.ImplKt.getPropertyName;

public final class NewInstanceOfSingletonInspection extends BaseInspection {

  @Override
  protected @NotNull BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitNewExpression(@NotNull GrNewExpression newExpression) {
        if (newExpression.getArrayDeclaration() != null) return;

        GrCodeReferenceElement refElement = newExpression.getReferenceElement();
        if (refElement == null) return;

        PsiElement resolved = refElement.resolve();
        if (!(resolved instanceof GrTypeDefinition)) return;

        PsiAnnotation annotation = AnnotationUtil.findAnnotation((GrTypeDefinition)resolved, GROOVY_LANG_SINGLETON);
        if (annotation == null) return;

        registerError(
          newExpression,
          GroovyBundle.message("new.instance.of.singleton"),
          ContainerUtil.ar(new ReplaceWithInstanceAccessFix()),
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING
        );
      }
    };
  }

  private static class ReplaceWithInstanceAccessFix extends PsiUpdateModCommandQuickFix {

    @Override
    public @NotNull String getFamilyName() {
      return GroovyBundle.message("replace.new.expression.with.instance.access");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof GrNewExpression newExpression)) return;

      GrCodeReferenceElement refElement = newExpression.getReferenceElement();
      if (refElement == null) return;

      PsiElement resolved = refElement.resolve();
      if (!(resolved instanceof GrTypeDefinition singleton)) return;

      PsiAnnotation annotation = AnnotationUtil.findAnnotation(singleton, GROOVY_LANG_SINGLETON);
      if (annotation == null) return;

      String qualifiedName = singleton.getQualifiedName();
      if (qualifiedName == null) return;

      String propertyName = getPropertyName(annotation);
      GrExpression instanceRef = GroovyPsiElementFactory.getInstance(project).createExpressionFromText(qualifiedName + "." + propertyName);

      final GrExpression replaced = newExpression.replaceWithExpression(instanceRef, true);
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(replaced);
    }
  }
}

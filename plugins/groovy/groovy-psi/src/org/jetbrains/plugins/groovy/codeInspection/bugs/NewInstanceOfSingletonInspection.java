/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_SINGLETON;
import static org.jetbrains.plugins.groovy.transformations.singleton.ImplKt.getPropertyName;

public class NewInstanceOfSingletonInspection extends BaseInspection {

  @NotNull
  @Override
  protected BaseInspectionVisitor buildVisitor() {
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
          GroovyInspectionBundle.message("new.instance.of.singleton"),
          ContainerUtil.ar(new ReplaceWithInstanceAccessFix()),
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING
        );
      }
    };
  }

  private static class ReplaceWithInstanceAccessFix extends GroovyFix {

    @NotNull
    @Override
    public String getFamilyName() {
      return GroovyInspectionBundle.message("replace.new.expression.with.instance.access");
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) throws IncorrectOperationException {
      PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof GrNewExpression)) return;

      GrNewExpression newExpression = (GrNewExpression)element;

      GrCodeReferenceElement refElement = newExpression.getReferenceElement();
      if (refElement == null) return;

      PsiElement resolved = refElement.resolve();
      if (!(resolved instanceof GrTypeDefinition)) return;

      GrTypeDefinition singleton = (GrTypeDefinition)resolved;

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

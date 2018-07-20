// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFieldFromUsageHelper;
import com.intellij.codeInsight.daemon.impl.quickfix.EmptyExpression;
import com.intellij.codeInsight.daemon.impl.quickfix.GuessTypeParameters;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.template.expressions.ChooseTypeExpression;

/**
 * @author Max Medvedev
 */
public class GroovyCreateFieldFromUsageHelper extends CreateFieldFromUsageHelper {
  @Override
  public Template setupTemplateImpl(PsiField f,
                                    Object expectedTypes,
                                    PsiClass targetClass,
                                    Editor editor,
                                    PsiElement context,
                                    boolean createConstantField,
                                    @NotNull PsiSubstitutor substitutor) {
    GrVariableDeclaration fieldDecl = (GrVariableDeclaration)f.getParent();
    GrField field = (GrField)fieldDecl.getVariables()[0];
    Project project = field.getProject();
    fieldDecl = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(fieldDecl);
    TemplateBuilderImpl builder = new TemplateBuilderImpl(fieldDecl);

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);

    if (expectedTypes instanceof TypeConstraint[]) {
      GrTypeElement typeElement = fieldDecl.getTypeElementGroovy();
      assert typeElement != null;
      ChooseTypeExpression expr = new ChooseTypeExpression((TypeConstraint[])expectedTypes, PsiManager.getInstance(project),
                                                           typeElement.getResolveScope());
      builder.replaceElement(typeElement, expr);
    }
    else if (expectedTypes instanceof ExpectedTypeInfo[]) {
      new GuessTypeParameters(project, factory, builder, substitutor).setupTypeElement(field.getTypeElement(), (ExpectedTypeInfo[])expectedTypes,
                                                                                       context, targetClass);
    }
    GrExpression initializer = field.getInitializerGroovy();

    if (createConstantField && initializer != null) {
      builder.replaceElement(initializer, new EmptyExpression());
      PsiElement identifier = field.getNameIdentifierGroovy();
      builder.setEndVariableAfter(identifier);
    }

    fieldDecl = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(fieldDecl);
    Template template = builder.buildTemplate();

    TextRange range = fieldDecl.getTextRange();
    editor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());

    if (expectedTypes instanceof ExpectedTypeInfo[]) {
      if (!Registry.is("ide.create.field.enable.shortening") && ((ExpectedTypeInfo[])expectedTypes).length > 1) {
        template.setToShortenLongNames(false);
      }
    }
    return template;
  }

  @Override
  public GrField insertFieldImpl(@NotNull PsiClass targetClass, @NotNull PsiField field, @Nullable PsiElement place) {
    if (targetClass instanceof GroovyScriptClass) {
      PsiElement added = targetClass.getContainingFile().add(field.getParent());
      return (GrField)((GrVariableDeclaration)added).getVariables()[0];
    }
    else {
      return (GrField)targetClass.add(field);
    }
  }
}

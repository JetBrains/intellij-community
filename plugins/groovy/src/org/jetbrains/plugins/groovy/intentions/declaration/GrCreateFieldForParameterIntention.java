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
package org.jetbrains.plugins.groovy.intentions.declaration;

import com.intellij.codeInsight.intention.impl.CreateFieldFromParameterActionBase;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GrCreateFieldForParameterIntention extends CreateFieldFromParameterActionBase {

  @Override
  protected boolean isAvailable(PsiParameter parameter) {
    PsiElement scope = parameter.getDeclarationScope();
    if (!(scope instanceof GrMethod)) return false;
    if (((GrMethod)scope).getContainingClass() == null) return false;

    if (checkAssignmentToFieldExists(parameter)) return false;

    return true;
  }

  @Override
  protected PsiType getSubstitutedType(PsiParameter parameter) {
    return GroovyRefactoringUtil.getSubstitutedType((GrParameter)parameter);
  }

  private static boolean checkAssignmentToFieldExists(PsiParameter parameter) {
    for (PsiReference reference : ReferencesSearch.search(parameter).findAll()) {
      PsiElement element = reference.getElement();
      if (element instanceof GrReferenceExpression &&
          element.getParent() instanceof GrAssignmentExpression &&
          ((GrAssignmentExpression)element.getParent()).getRValue() == element) {
        GrAssignmentExpression parent = ((GrAssignmentExpression)element.getParent());
        GrExpression value = parent.getLValue();
        if (value instanceof GrReferenceExpression && ((GrReferenceExpression)value).resolve() instanceof PsiField) return true;
      }
    }
    return false;
  }

  @Override
  protected void performRefactoring(Project project,
                                    PsiClass targetClass,
                                    PsiMethod method,
                                    PsiParameter myParameter,
                                    PsiType type,
                                    String fieldName,
                                    boolean methodStatic,
                                    boolean isFinal) {
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);

    if (targetClass.findFieldByName(fieldName, false) == null) {
      String[] modifiers = getModifiers(methodStatic, isFinal);
      GrVariableDeclaration fieldDeclaration = factory.createFieldDeclaration(modifiers, fieldName, null, type);
      GrVariableDeclaration inserted = (GrVariableDeclaration)targetClass.add(fieldDeclaration);
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(inserted);
    }

    GrOpenBlock block = ((GrMethod)method).getBlock();
    if (block == null) return;

    GrAssignmentExpression assignment = createAssignment(targetClass, myParameter, fieldName, methodStatic, factory);
    GrStatement anchor = getAnchor(block);

    GrStatement statement = block.addStatementBefore(assignment, anchor);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(statement);
  }

  private static GrAssignmentExpression createAssignment(PsiClass targetClass,
                                                         PsiParameter myParameter,
                                                         String fieldName,
                                                         boolean methodStatic,
                                                         GroovyPsiElementFactory factory) {
    StringBuilder builder = new StringBuilder();
    if (methodStatic) {
      builder.append(targetClass.getQualifiedName());
      builder.append('.');
    }
    else {
      builder.append("this.");
    }
    builder.append(fieldName);
    builder.append("=").append(myParameter.getName());
    return (GrAssignmentExpression)factory.createStatementFromText(builder.toString());
  }

  @Nullable
  private static GrStatement getAnchor(GrOpenBlock block) {
    GrStatement[] statements = block.getStatements();
    GrStatement fist = ArrayUtil.getFirstElement(statements);
    if (fist instanceof GrConstructorInvocation) {
      return statements.length > 1 ? statements[1] : null;
    }
    else {
      return fist;
    }
  }

  private static String[] getModifiers(boolean aStatic, boolean aFinal) {
    List<String> list = new ArrayList<>();
    list.add(PsiModifier.PRIVATE);
    if (aStatic) list.add(PsiModifier.STATIC);
    if (aFinal) list.add(PsiModifier.FINAL);
    return ArrayUtil.toStringArray(list);
  }
}

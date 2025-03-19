// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.declaration;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
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
import java.util.Objects;

/**
 * @author Max Medvedev
 */
public final class GrCreateFieldForParameterIntention extends PsiUpdateModCommandAction<GrParameter> {
  
  public GrCreateFieldForParameterIntention() {
    super(GrParameter.class);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull GrParameter parameter) {
    if (!(parameter.getDeclarationScope() instanceof GrMethod grMethod) ||
        grMethod.getContainingClass() == null || checkAssignmentToFieldExists(parameter)) {
      return null;
    }
    return Presentation.of(JavaBundle.message("intention.create.field.from.parameter.text", parameter.getName()));
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("intention.create.field.from.parameter.family");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull GrParameter parameter, @NotNull ModPsiUpdater updater) {
    Project project = context.project();
    PsiType type = GroovyRefactoringUtil.getSubstitutedType(parameter);
    JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
    String parameterName = parameter.getName();
    String propertyName = styleManager.variableNameToPropertyName(parameterName, VariableKind.PARAMETER);

    GrMethod method = (GrMethod)parameter.getDeclarationScope();
    PsiClass targetClass = method.getContainingClass();
    if (targetClass == null) return;

    boolean isMethodStatic = method.hasModifierProperty(PsiModifier.STATIC);

    VariableKind kind = isMethodStatic ? VariableKind.STATIC_FIELD : VariableKind.FIELD;
    SuggestedNameInfo suggestedNameInfo = styleManager.suggestVariableName(kind, propertyName, null, type);
    SuggestedNameInfo uniqueNameInfo = styleManager.suggestUniqueVariableName(suggestedNameInfo, targetClass, true);

    boolean isFinal = !isMethodStatic && method.isConstructor();

    PsiVariable variable = createField(project, targetClass, method, parameter, type, uniqueNameInfo.names[0], isMethodStatic, isFinal);

    if (variable != null && variable.isValid()) {
      updater.rename(variable, List.of(Objects.requireNonNull(variable.getName())));
    }
  }

  private static boolean checkAssignmentToFieldExists(PsiParameter parameter) {
    for (PsiReference reference : ReferencesSearch.search(parameter).findAll()) {
      PsiElement element = reference.getElement();
      if (element instanceof GrReferenceExpression &&
          element.getParent() instanceof GrAssignmentExpression parent &&
          parent.getRValue() == element) {
        GrExpression value = parent.getLValue();
        if (value instanceof GrReferenceExpression ref && ref.resolve() instanceof PsiField) return true;
      }
    }
    return false;
  }

  private static PsiVariable createField(@NotNull Project project,
                                         @NotNull PsiClass targetClass,
                                         @NotNull GrMethod method,
                                         @NotNull PsiParameter myParameter,
                                         PsiType type,
                                         @NotNull String fieldName,
                                         boolean methodStatic,
                                         boolean isFinal) {
    GrOpenBlock block = method.getBlock();
    if (block == null) return null;

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
    GrAssignmentExpression assignment = createAssignment(targetClass, myParameter, fieldName, methodStatic, factory);
    GrStatement anchor = getAnchor(block);

    GrStatement statement = block.addStatementBefore(assignment, anchor);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(statement);

    if (targetClass.findFieldByName(fieldName, false) == null) {
      String[] modifiers = getModifiers(methodStatic, isFinal);
      GrVariableDeclaration fieldDeclaration = factory.createFieldDeclaration(modifiers, fieldName, null, type);
      GrVariableDeclaration inserted = (GrVariableDeclaration)targetClass.add(fieldDeclaration);
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(inserted);
      return inserted.getVariables()[0];
    }
    return null;
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

  private static @Nullable GrStatement getAnchor(GrOpenBlock block) {
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
    return ArrayUtilRt.toStringArray(list);
  }
}

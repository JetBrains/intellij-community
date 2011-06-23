/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.introduce.field;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.introduceField.IntroduceFieldHandler;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrQualifiedReference;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrConstructor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstantList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceDialog;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceRefactoringError;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.util.ArrayUtil.EMPTY_STRING_ARRAY;
import static org.jetbrains.plugins.groovy.refactoring.introduce.field.GrIntroduceFieldSettings.Init.*;

/**
 * @author Maxim.Medvedev
 */
public class GrIntroduceFieldHandler extends GrIntroduceHandlerBase<GrIntroduceFieldSettings> {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.introduce.field.GrIntroduceFieldHandler");

  @Override
  protected String getRefactoringName() {
    return IntroduceFieldHandler.REFACTORING_NAME;
  }

  @Override
  protected String getHelpID() {
    return HelpID.INTRODUCE_FIELD;
  }

  @NotNull
  @Override
  protected GrTypeDefinition findScope(GrExpression expression, GrVariable variable) {
    PsiElement place = expression == null ? variable : expression;
    final GrTypeDefinition scope = PsiTreeUtil.getParentOfType(place, GrTypeDefinition.class);
    LOG.assertTrue(scope != null);
    return scope;
  }

  @Override
  protected void checkExpression(GrExpression selectedExpr) {
    checkContainingClass(selectedExpr);
  }

  private static void checkContainingClass(PsiElement place) {
    final GrTypeDefinition containingClass = PsiTreeUtil.getParentOfType(place, GrTypeDefinition.class);
    if (containingClass == null) throw new GrIntroduceRefactoringError(GroovyRefactoringBundle.message("cannot.introduce.field.in.script"));
    if (containingClass.isInterface()) {
      throw new GrIntroduceRefactoringError(GroovyRefactoringBundle.message("cannot.introduce.field.in.interface"));
    }
    if (PsiUtil.skipParentheses(place, false) == null) {
      throw new GrIntroduceRefactoringError(GroovyRefactoringBundle.message("expression.contains.errors"));
    }
  }

  @Override
  protected void checkVariable(GrVariable variable) throws GrIntroduceRefactoringError {
    checkContainingClass(variable);
  }

  @Override
  protected void checkOccurrences(PsiElement[] occurrences) {
    //notning to do
  }

  @Override
  protected GrIntroduceDialog<GrIntroduceFieldSettings> getDialog(GrIntroduceContext context) {
    return new GrIntroduceFieldDialog(context);
  }

  @Override
  public GrField runRefactoring(GrIntroduceContext context, GrIntroduceFieldSettings settings) {
    final PsiClass targetClass = (PsiClass)context.scope;

    if (targetClass == null) return null;

    final GrVariableDeclaration declaration = createField(context, settings);

    final GrVariableDeclaration added;
    if (targetClass instanceof GrEnumTypeDefinition) {
      final GrEnumConstantList enumConstants = ((GrEnumTypeDefinition)targetClass).getEnumConstantList();
      added = (GrVariableDeclaration)targetClass.addAfter(declaration, enumConstants);
    }
    else {
      added = ((GrVariableDeclaration)targetClass.add(declaration));
    }

    final GrField field = (GrField)added.getVariables()[0];
    GrIntroduceFieldSettings.Init i = settings.initializeIn();
    if (i == CONSTRUCTOR) {
      initializeInConstructor(context, settings, field);
    }
    else if (i == CUR_METHOD) {
      initializeInMethod(context, settings, field);
    }

    PsiUtil.shortenReferences(added);
    if (settings.removeLocalVar()) {
      deleteLocalVar(context);
    }

    if (settings.replaceAllOccurrences()) {
      GroovyRefactoringUtil.sortOccurrences(context.occurrences);
      for (PsiElement occurrence : context.occurrences) {
        replaceOccurence(field, occurrence);
      }
    }
    else {
      replaceOccurence(field, context.expression);
    }
    return field;
  }

  @Override
  protected PsiElement[] findOccurences(GrExpression expression, PsiElement scope) {
    final PsiElement[] occurences = super.findOccurences(expression, scope);
    GrTypeDefinition clazz = (GrTypeDefinition)scope;
    if (shouldBeStatic(expression, clazz)) return occurences;

    List<PsiElement> filtered = new ArrayList<PsiElement>();
    for (PsiElement occurence : occurences) {
      if (!shouldBeStatic(occurence, clazz)) {
        filtered.add(occurence);
      }
    }
    return ContainerUtil.toArray(filtered, new PsiElement[filtered.size()]);
  }

  private static void initializeInMethod(GrIntroduceContext context, GrIntroduceFieldSettings settings, GrField field) {
    if (context.expression == null) return;
    final GrExpression expression = context.expression;
    final GrTypeDefinition scope = (GrTypeDefinition)context.scope;
    final GrMethod method = getContainingMethod(expression, scope);
    LOG.assertTrue(method != null);
    final GrOpenBlock block = method.getBlock();
    LOG.assertTrue(block != null);
    final GrStatement anchor;
    if (settings.removeLocalVar()) {
      final GrVariable variable = resolveLocalVar(context);
      anchor = PsiTreeUtil.getParentOfType(variable, GrStatement.class);
    }
    else {
      anchor = (GrStatement)findAnchor(context, settings, context.occurrences, block);
    }

    generateAssignment(context, settings, field, anchor, block);
  }

  private static void initializeInConstructor(GrIntroduceContext context, GrIntroduceFieldSettings settings, GrField field) {
    final GrTypeDefinition scope = (GrTypeDefinition)context.scope;
    PsiMethod[] constructors = scope.getConstructors();
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(context.project);
    if (constructors.length == 0) {
      final GrMethod constructor =
        factory.createConstructorFromText(scope.getName(), EMPTY_STRING_ARRAY, EMPTY_STRING_ARRAY, "{}", scope);
      final PsiElement added = scope.add(constructor);
      constructors = new PsiMethod[]{(PsiMethod)added};
    }
    for (PsiMethod constructor : constructors) {
      final GrConstructorInvocation invocation = ((GrConstructor)constructor).getChainingConstructorInvocation();
      if (invocation != null && invocation.isThisCall()) continue;
      final PsiElement anchor = findAnchor(context, settings, (GrConstructor)constructor);

      generateAssignment(context, settings, field, (GrStatement)anchor, ((GrConstructor)constructor).getBlock());
    }
  }

  private static void generateAssignment(GrIntroduceContext context,
                                         GrIntroduceFieldSettings settings,
                                         GrField field,
                                         GrStatement anchor,
                                         final GrOpenBlock block) {
    final GrExpression initializer;
    if (settings.removeLocalVar()) {
      initializer = extractVarInitializer(context);
    }
    else {
      initializer = context.expression;
    }
    GrAssignmentExpression init = ((GrAssignmentExpression)GroovyPsiElementFactory.getInstance(context.project)
      .createExpressionFromText(settings.getName() + " = " + initializer.getText()));
    init = (GrAssignmentExpression)block.addStatementBefore(init, anchor);
    replaceOccurence(field, init.getLValue());
  }

  private static GrExpression extractVarInitializer(GrIntroduceContext context) {
    final PsiElement resolved = resolveLocalVar(context);
    LOG.assertTrue(resolved instanceof GrVariable);
    GrExpression initializer = ((GrVariable)resolved).getInitializerGroovy();
    LOG.assertTrue(initializer != null);
    return initializer;
  }

  @Nullable
  private static PsiElement findAnchor(GrIntroduceContext context, GrIntroduceFieldSettings settings, final GrConstructor constructor) {
    final List<PsiElement> elements = ContainerUtil.findAll(context.occurrences, new Condition<PsiElement>() {
      @Override
      public boolean value(PsiElement element) {
        return PsiTreeUtil.isAncestor(constructor, element, true);
      }
    });
    if (elements.size() == 0) return null;
    return findAnchor(context, settings, ContainerUtil.toArray(elements, new PsiElement[elements.size()]), constructor.getBlock());
  }

  private static void replaceOccurence(GrField field, PsiElement occurence) {
    final GrReferenceExpression newExpr = createRefExpression(field, occurence);
    final PsiElement replaced;
    if (occurence instanceof GrExpression) {
      replaced = ((GrExpression)occurence).replaceWithExpression(newExpr, false);
    } else {
      replaced = occurence.replace(newExpr);
    }
    if (replaced instanceof GrQualifiedReference) {
      if (!PsiUtil.shortenReference((GrQualifiedReference)replaced)) {
        final PsiElement qualifier = ((GrQualifiedReference)replaced).getQualifier();
        if (qualifier instanceof GrQualifiedReference) {
          PsiUtil.shortenReference((GrQualifiedReference)qualifier);
        }
      }
    }
  }

  private static GrReferenceExpression createRefExpression(GrField field, PsiElement place) {
    final PsiClass containingClass = field.getContainingClass();
    LOG.assertTrue(containingClass != null);
    final String refText;
    if (field.hasModifierProperty(GrModifier.STATIC)) {
      refText = containingClass.getQualifiedName() + "." + field.getName();
    }
    else {
      refText = containingClass.getQualifiedName() + ".this." + field.getName();
    }
    return GroovyPsiElementFactory.getInstance(place.getProject()).createReferenceExpressionFromText(refText, place);
  }

  private static GrVariableDeclaration createField(GrIntroduceContext context, GrIntroduceFieldSettings settings) {
    final String name = settings.getName();
    final PsiType type = settings.getSelectedType();
    final String modifier = settings.getVisibilityModifier();

    final GrExpression initializer;
    if (settings.initializeIn() == FIELD_DECLARATION) {
      if (settings.removeLocalVar()) {
        initializer = extractVarInitializer(context);
      }
      else {
        initializer = context.expression;
      }
    }
    else {
      initializer = null;
    }

    final GrVariableDeclaration fieldDeclaration = GroovyPsiElementFactory.getInstance(context.project).createFieldDeclaration(
      EMPTY_STRING_ARRAY, name, initializer, type);

    fieldDeclaration.getModifierList().setModifierProperty(modifier, true);
    if (settings.isStatic()) {
      fieldDeclaration.getModifierList().setModifierProperty(GrModifier.STATIC, true);
    }
    if (settings.declareFinal()) {
      fieldDeclaration.getModifierList().setModifierProperty(GrModifier.FINAL, true);
    }
    return fieldDeclaration;
  }

  @Nullable
  static GrMethod getContainingMethod(PsiElement place, GrTypeDefinition clazz) {
    while (place != null && place != clazz) {
      place = place.getParent();
      if (place instanceof GrMethod) return (GrMethod)place;
    }
    return null;
  }

  @NotNull
  static GrMember getContainer(PsiElement place, GrTypeDefinition clazz) {
    while (place != null && place != clazz) {
      place = place.getParent();
      if (place instanceof GrMember) return (GrMember)place;
    }
    LOG.assertTrue(false, "container cannot be null");
    return null;
  }

  static boolean shouldBeStatic(PsiElement expr, GrTypeDefinition clazz) {
    final GrMember method = getContainer(expr, clazz);
    return method.hasModifierProperty(GrModifier.STATIC);
  }
}

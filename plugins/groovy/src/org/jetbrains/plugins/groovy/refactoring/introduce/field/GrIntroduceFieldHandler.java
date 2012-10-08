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
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.introduceField.IntroduceFieldHandler;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.GrReferenceAdjuster;
import org.jetbrains.plugins.groovy.lang.psi.GrQualifiedReference;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstantList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GrRefactoringError;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceDialog;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.util.ArrayUtil.EMPTY_STRING_ARRAY;
import static com.intellij.util.ArrayUtil.getFirstElement;
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
  protected PsiClass findScope(GrExpression expression, GrVariable variable) {
    PsiElement place = expression == null ? variable : expression;
    return ObjectUtils.assertNotNull(PsiUtil.getContextClass(place));
  }

  @Override
  protected void checkExpression(GrExpression selectedExpr) {
    checkContainingClass(selectedExpr);
  }

  private static void checkContainingClass(PsiElement place) {
    final PsiClass containingClass = PsiUtil.getContextClass(place);
    if (containingClass == null) throw new GrRefactoringError(GroovyRefactoringBundle.message("cannot.introduce.field.in.script"));
    if (containingClass.isInterface()) {
      throw new GrRefactoringError(GroovyRefactoringBundle.message("cannot.introduce.field.in.interface"));
    }
    if (PsiUtil.skipParentheses(place, false) == null) {
      throw new GrRefactoringError(GroovyRefactoringBundle.message("expression.contains.errors"));
    }
  }

  @Override
  protected void checkVariable(GrVariable variable) throws GrRefactoringError {
    checkContainingClass(variable);
  }

  @Override
  protected void checkOccurrences(PsiElement[] occurrences) {
    //nothing to do
  }

  @Override
  protected GrIntroduceDialog<GrIntroduceFieldSettings> getDialog(GrIntroduceContext context) {
    return new GrIntroduceFieldDialog(context);
  }

  @Override
  public GrVariable runRefactoring(GrIntroduceContext context, GrIntroduceFieldSettings settings) {
    final PsiClass targetClass = (PsiClass)context.getScope();

    if (targetClass == null) return null;

    final GrVariableDeclaration declaration = createField(context, settings);

    final GrVariableDeclaration added;
    if (targetClass instanceof GrEnumTypeDefinition) {
      final GrEnumConstantList enumConstants = ((GrEnumTypeDefinition)targetClass).getEnumConstantList();
      added = (GrVariableDeclaration)targetClass.addAfter(declaration, enumConstants);
    }
    else {
      if (targetClass instanceof GrTypeDefinition) {
        final PsiMethod[] methods = targetClass.getMethods();
        PsiElement anchor = getFirstElement(methods);
        added = (GrVariableDeclaration)targetClass.addBefore(declaration, anchor);
      }
      else {
        assert targetClass instanceof GroovyScriptClass;
        final GroovyFile file = (GroovyFile)targetClass.getContainingFile();
        PsiElement[] elements = file.getMethods();
        if (elements.length == 0) elements = file.getStatements();
        final PsiElement anchor = getFirstElement(elements);
        added = (GrVariableDeclaration)file.addBefore(declaration, anchor);
//        GrReferenceAdjuster.shortenReferences(added.getModifierList());
      }
    }

    final GrVariable field = added.getVariables()[0];
    GrIntroduceFieldSettings.Init i = settings.initializeIn();

    if (i == CONSTRUCTOR) {
      initializeInConstructor(context, settings, field);
    }
    else if (i == CUR_METHOD) {
      initializeInMethod(context, settings, field);
    }

    GrReferenceAdjuster.shortenReferences(added);

    //var can be invalid if it was removed while initialization
    if (settings.removeLocalVar()) {
      deleteLocalVar(context);
    }

    if (settings.replaceAllOccurrences()) {
      GroovyRefactoringUtil.sortOccurrences(context.getOccurrences());
      for (PsiElement occurrence : context.getOccurrences()) {
        replaceOccurrence(field, occurrence, targetClass);
      }
    }
    else {
      final GrExpression expression = context.getExpression();
      if (PsiUtil.isExpressionStatement(expression)) {
        expression.delete();
      }
      else {
        replaceOccurrence(field, expression, targetClass);
      }
    }
    return field;
  }

  @Override
  protected PsiElement[] findOccurrences(GrExpression expression, PsiElement scope) {
    if (scope instanceof GroovyScriptClass) {
      scope = scope.getContainingFile();
    }
    final PsiElement[] occurrences = super.findOccurrences(expression, scope);
    if (shouldBeStatic(expression, scope)) return occurrences;

    List<PsiElement> filtered = new ArrayList<PsiElement>();
    for (PsiElement occurrence : occurrences) {
      if (!shouldBeStatic(occurrence, scope)) {
        filtered.add(occurrence);
      }
    }
    return ContainerUtil.toArray(filtered, new PsiElement[filtered.size()]);
  }

  private static void initializeInMethod(GrIntroduceContext context, GrIntroduceFieldSettings settings, GrVariable field) {
    if (context.getExpression() == null) return;
    final GrExpression expression = context.getExpression();
    final PsiElement _scope = context.getScope();
    final PsiElement scope = _scope instanceof GroovyScriptClass? _scope.getContainingFile(): _scope;
    final GrMethod method = getContainingMethod(expression, scope);
    LOG.assertTrue(method != null);

    final GrStatement anchor;
    if (settings.removeLocalVar()) {
      GrVariable variable = resolveLocalVar(context);
      anchor = PsiTreeUtil.getParentOfType(variable, GrStatement.class);
    }
    else {
      anchor = (GrStatement)findAnchor(context, settings, context.getOccurrences(), method.getBlock());
    }

    generateAssignment(context, settings, field, anchor, method.getBlock());
  }

  private static void initializeInConstructor(GrIntroduceContext context, GrIntroduceFieldSettings settings, GrVariable field) {
    final PsiClass scope = (PsiClass)context.getScope();
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(context.getProject());

    if (scope instanceof GrAnonymousClassDefinition) {
      final GrClassInitializer[] initializers = ((GrAnonymousClassDefinition)scope).getInitializers();
      final GrClassInitializer initializer;
      if (initializers.length == 0) {
        initializer = (GrClassInitializer)scope.add(factory.createClassInitializer());
      }
      else {
        initializer = initializers[0];
      }

      final PsiElement anchor = findAnchor(context, settings, initializer.getBlock());
      generateAssignment(context, settings, field, (GrStatement)anchor, initializer.getBlock());
      return;
    }

    PsiMethod[] constructors = scope.getConstructors();
    if (constructors.length == 0) {
      final String name = scope.getName();
      LOG.assertTrue(name != null, scope.getText());
      final GrMethod constructor = factory.createConstructorFromText(name, EMPTY_STRING_ARRAY, EMPTY_STRING_ARRAY, "{}", scope);
      final PsiElement added = scope.add(constructor);
      constructors = new PsiMethod[]{(PsiMethod)added};
    }
    for (PsiMethod constructor : constructors) {
      final GrConstructorInvocation invocation = PsiImplUtil.getChainingConstructorInvocation((GrMethod)constructor);
      if (invocation != null && invocation.isThisCall()) continue;
      final PsiElement anchor = findAnchor(context, settings, ((GrMethod)constructor).getBlock());

      generateAssignment(context, settings, field, (GrStatement)anchor, ((GrMethod)constructor).getBlock());
    }
  }

  private static void generateAssignment(GrIntroduceContext context,
                                         GrIntroduceFieldSettings settings,
                                         GrVariable field,
                                         @Nullable GrStatement anchor,
                                         GrCodeBlock defaultContainer) {
    final GrExpression initializer;
    if (settings.removeLocalVar()) {
      initializer = extractVarInitializer(context);
    }
    else {
      initializer = context.getExpression();
    }
    GrAssignmentExpression init = (GrAssignmentExpression)GroovyPsiElementFactory.getInstance(context.getProject())
      .createExpressionFromText(settings.getName() + " = " + initializer.getText());

    GrCodeBlock block;
    if (anchor != null) {
      anchor = GroovyRefactoringUtil.addBlockIntoParent(anchor);
      LOG.assertTrue(anchor.getParent() instanceof GrCodeBlock);
      block = (GrCodeBlock)anchor.getParent();
    }
    else {
      block = defaultContainer;
    }
    init = (GrAssignmentExpression)block.addStatementBefore(init, anchor);
    replaceOccurrence(field, init.getLValue(), (PsiClass)context.getScope());
  }

  private static GrExpression extractVarInitializer(GrIntroduceContext context) {
    final PsiElement resolved = resolveLocalVar(context);
    LOG.assertTrue(resolved instanceof GrVariable);
    GrExpression initializer = ((GrVariable)resolved).getInitializerGroovy();
    LOG.assertTrue(initializer != null);
    return initializer;
  }

  @Nullable
  private static PsiElement findAnchor(GrIntroduceContext context, GrIntroduceFieldSettings settings, final GrCodeBlock block) {
    final List<PsiElement> elements = ContainerUtil.findAll(context.getOccurrences(), new Condition<PsiElement>() {
      @Override
      public boolean value(PsiElement element) {
        return PsiTreeUtil.isAncestor(block, element, true);
      }
    });
    if (elements.size() == 0) return null;
    return findAnchor(context, settings, ContainerUtil.toArray(elements, new PsiElement[elements.size()]), block);
  }

  private static void replaceOccurrence(GrVariable field, PsiElement occurrence, PsiClass containingClass) {
    final GrReferenceExpression newExpr = createRefExpression(field, occurrence, containingClass);
    final PsiElement replaced;
    if (occurrence instanceof GrExpression) {
      replaced = ((GrExpression)occurrence).replaceWithExpression(newExpr, false);
    } else {
      replaced = occurrence.replace(newExpr);
    }
    if (replaced instanceof GrQualifiedReference<?>) {
      GrReferenceAdjuster.shortenReference((GrQualifiedReference<?>)replaced);
    }
  }

  private static GrReferenceExpression createRefExpression(GrVariable field, PsiElement place, PsiClass containingClass) {
    final String qname = containingClass.getQualifiedName();
    final String prefix = qname != null ? qname + "." : "";
    final String refText;
    if (field.hasModifierProperty(PsiModifier.STATIC)) {
      refText = prefix + field.getName();
    }
    else {
      refText = prefix + "this." + field.getName();
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
        initializer = context.getExpression();
      }
    }
    else {
      initializer = null;
    }

    List<String> modifiers = new ArrayList<String>();
    if (context.getScope() instanceof GroovyScriptClass) modifiers.add("@"+GroovyCommonClassNames.GROOVY_TRANSFORM_FIELD);
    if (settings.isStatic()) modifiers.add(PsiModifier.STATIC);
    if (!PsiModifier.PACKAGE_LOCAL.equals(modifier)) modifiers.add(modifier);
    if (settings.declareFinal()) modifiers.add(PsiModifier.FINAL);

    final String[] arr_modifiers = ArrayUtil.toStringArray(modifiers);
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(context.getProject());
    if (context.getScope() instanceof GroovyScriptClass) {
      return factory.createVariableDeclaration(arr_modifiers, initializer, type, name);
    }
    else {
      return factory.createFieldDeclaration(arr_modifiers, name, initializer, type);
    }
  }

  @Nullable
  static GrMethod getContainingMethod(PsiElement place, PsiElement scope) {
    while (place != null && place != scope) {
      place = place.getParent();
      if (place instanceof GrMethod) return (GrMethod)place;
    }
    return null;
  }

  @Nullable
  static GrMember getContainer(PsiElement place, PsiElement clazz) {
    while (place != null && place != clazz) {
      place = place.getParent();
      if (place instanceof GrMember) return (GrMember)place;
    }
    return null;
  }

  static boolean shouldBeStatic(PsiElement expr, PsiElement clazz) {
    final GrMember method = getContainer(expr, clazz);
    if (method == null) return false;
    return method.hasModifierProperty(PsiModifier.STATIC);
  }
}

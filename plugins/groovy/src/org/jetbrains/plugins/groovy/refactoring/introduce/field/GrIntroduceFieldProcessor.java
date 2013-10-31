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
package org.jetbrains.plugins.groovy.refactoring.introduce.field;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeStyle.GrReferenceAdjuster;
import org.jetbrains.plugins.groovy.lang.psi.GrQualifiedReference;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstantList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GrIntroduceFieldProcessor {
  private static final Logger LOG = Logger.getInstance(GrIntroduceFieldProcessor.class);

  private final GrIntroduceContext context;
  private final GrIntroduceFieldSettings settings;

  public GrIntroduceFieldProcessor(@NotNull GrIntroduceContext context,
                                   @NotNull GrIntroduceFieldSettings settings) {
    this.context = context;
    this.settings = settings;
  }

  public GrVariable run() {
    PsiElement scope = context.getScope();
    final PsiClass targetClass = scope instanceof GroovyFileBase ? ((GroovyFileBase)scope).getScriptClass() : (PsiClass)scope;
    if (targetClass == null) return null;

    final GrVariableDeclaration declaration = insertField(targetClass);
    final GrVariable field = declaration.getVariables()[0];

    switch (settings.initializeIn()) {
      case CUR_METHOD:
        initializeInMethod(field);
        break;
      case FIELD_DECLARATION:
        field.setInitializerGroovy(getInitializer());
        break;
      case CONSTRUCTOR:
        initializeInConstructor(field);
        break;
      case SETUP_METHOD:
        initializeInSetup(field);
        break;
    }

    JavaCodeStyleManager.getInstance(declaration.getProject()).shortenClassReferences(declaration);

    if (settings.removeLocalVar()) {
      GrIntroduceHandlerBase.deleteLocalVar(context);
    }

    processOccurrences(targetClass, field);

    return field;
  }

  private void processOccurrences(@NotNull PsiClass targetClass, @NotNull GrVariable field) {
    if (context.getStringPart() != null) {
      final GrExpression expr = GrIntroduceHandlerBase.processLiteral(field.getName(), context.getStringPart(), context.getProject());
      final PsiElement occurrence = replaceOccurrence(field, expr, targetClass);
      updateCaretPosition(occurrence);
    }
    else {
      if (settings.replaceAllOccurrences()) {
        GroovyRefactoringUtil.sortOccurrences(context.getOccurrences());
        for (PsiElement occurrence : context.getOccurrences()) {
          replaceOccurrence(field, occurrence, targetClass);
        }
      }
      else {
        final GrExpression expression = context.getExpression();
        assert expression != null;
        if (PsiUtil.isExpressionStatement(expression)) {
          expression.delete();
        }
        else {
          replaceOccurrence(field, expression, targetClass);
        }
      }
    }
  }

  private void updateCaretPosition(PsiElement occurrence) {
    context.getEditor().getCaretModel().moveToOffset(occurrence.getTextRange().getEndOffset());
    context.getEditor().getSelectionModel().removeSelection();
  }

  @NotNull
  protected GrVariableDeclaration insertField(@NotNull PsiClass targetClass) {
    GrVariableDeclaration declaration = createField(targetClass);
    if (targetClass instanceof GrEnumTypeDefinition) {
      final GrEnumConstantList enumConstants = ((GrEnumTypeDefinition)targetClass).getEnumConstantList();
      return (GrVariableDeclaration)targetClass.addAfter(declaration, enumConstants);
    }

    if (targetClass instanceof GrTypeDefinition) {
      PsiElement anchor = getAnchorForDeclaration((GrTypeDefinition)targetClass);
      return (GrVariableDeclaration)targetClass.addAfter(declaration, anchor);
    }

    else {
      assert targetClass instanceof GroovyScriptClass;
      final GroovyFile file = ((GroovyScriptClass)targetClass).getContainingFile();
      PsiElement[] elements = file.getMethods();
      if (elements.length == 0) elements = file.getStatements();
      final PsiElement anchor = ArrayUtil.getFirstElement(elements);
      return (GrVariableDeclaration)file.addBefore(declaration, anchor);
    }
  }

  @Nullable
  private static PsiElement getAnchorForDeclaration(@NotNull GrTypeDefinition targetClass) {
    PsiElement anchor = targetClass.getBody().getLBrace();

    final GrMembersDeclaration[] declarations = targetClass.getMemberDeclarations();
    for (GrMembersDeclaration declaration : declarations) {
      if (declaration instanceof GrVariableDeclaration) anchor = declaration;
      if (!(declaration instanceof GrVariableDeclaration)) return anchor;
    }

    return anchor;
  }

  void initializeInSetup(GrVariable field) {
    final PsiMethod setUpMethod = TestFrameworks.getInstance().findOrCreateSetUpMethod(((PsiClass)context.getScope()));
    assert setUpMethod instanceof GrMethod;

    final GrOpenBlock body = ((GrMethod)setUpMethod).getBlock();
    final PsiElement anchor = findAnchorForAssignment(body);
    generateAssignment(field, (GrStatement)anchor, body);
  }

  void initializeInMethod(GrVariable field) {
    final PsiElement _scope = context.getScope();
    final PsiElement scope = _scope instanceof GroovyScriptClass ? ((GroovyScriptClass)_scope).getContainingFile() : _scope;

    final PsiElement place = context.getPlace();

    final GrMember member = GrIntroduceFieldHandler.getContainer(place, scope);
    GrStatementOwner container = member instanceof GrMethod ? ((GrMethod)member).getBlock() :
                                 member instanceof GrClassInitializer ? ((GrClassInitializer)member).getBlock() :
                                 place.getContainingFile() instanceof GroovyFile ? ((GroovyFile)place.getContainingFile()) :
                                 null;
    assert container != null;

    final PsiElement anchor;
    if (settings.removeLocalVar()) {
      GrVariable variable = GrIntroduceHandlerBase.resolveLocalVar(context);
      anchor = PsiTreeUtil.getParentOfType(variable, GrStatement.class);
    }
    else {
      anchor = GrIntroduceHandlerBase.findAnchor(context.getOccurrences(), container);
      GrIntroduceHandlerBase.assertStatement(anchor, context.getOccurrences(), context.getScope());
    }

    generateAssignment(field, (GrStatement)anchor, container);
  }


  void initializeInConstructor(@NotNull GrVariable field) {
    final PsiClass scope = (PsiClass)context.getScope();

    if (scope instanceof GrAnonymousClassDefinition) {
      initializeInAnonymousClassInitializer(field, (GrAnonymousClassDefinition)scope);
    }
    else {
      initializeInConstructor(field, scope);
    }
  }

  private void initializeInConstructor(@NotNull GrVariable field, @NotNull PsiClass scope) {
    PsiMethod[] constructors = scope.getConstructors();
    if (constructors.length == 0) {
      constructors = new PsiMethod[]{generateConstructor(scope)};
    }

    for (PsiMethod constructor : constructors) {
      final GrConstructorInvocation invocation = PsiImplUtil.getChainingConstructorInvocation((GrMethod)constructor);
      if (invocation != null && invocation.isThisCall()) continue;
      final PsiElement anchor = findAnchorForAssignment(((GrMethod)constructor).getBlock());

      generateAssignment(field, (GrStatement)anchor, ((GrMethod)constructor).getBlock());
    }
  }

  @NotNull
  private PsiMethod generateConstructor(@NotNull PsiClass scope) {
    final String name = scope.getName();
    LOG.assertTrue(name != null, scope.getText());
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(context.getProject());
    final GrMethod
      constructor = factory.createConstructorFromText(name, ArrayUtil.EMPTY_STRING_ARRAY, ArrayUtil.EMPTY_STRING_ARRAY, "{}", scope);
    if (scope instanceof GroovyScriptClass) constructor.getModifierList().setModifierProperty(GrModifier.DEF, true);
    return (PsiMethod)scope.add(constructor);
  }

  private void initializeInAnonymousClassInitializer(@NotNull GrVariable field, @NotNull GrAnonymousClassDefinition scope) {
    final GrClassInitializer[] initializers = scope.getInitializers();
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(context.getProject());
    final GrClassInitializer initializer = initializers.length == 0 ? (GrClassInitializer)scope.add(factory.createClassInitializer())
                                                                    : initializers[0];

    final PsiElement anchor = findAnchorForAssignment(initializer.getBlock());
    generateAssignment(field, (GrStatement)anchor, initializer.getBlock());
  }

  private void generateAssignment(GrVariable field,
                                  @Nullable GrStatement anchor,
                                  GrStatementOwner defaultContainer) {
    final GrExpression initializer = getInitializer();
    GrAssignmentExpression init = (GrAssignmentExpression)GroovyPsiElementFactory.getInstance(context.getProject())
      .createExpressionFromText(settings.getName() + " = " + initializer.getText());

    GrStatementOwner block;
    if (anchor != null) {
      anchor = GroovyRefactoringUtil.addBlockIntoParent(anchor);
      LOG.assertTrue(anchor.getParent() instanceof GrStatementOwner);
      block = (GrStatementOwner)anchor.getParent();
    }
    else {
      block = defaultContainer;
    }

    init = (GrAssignmentExpression)block.addStatementBefore(init, anchor);
    replaceOccurrence(field, init.getLValue(), (PsiClass)context.getScope());
  }

  private GrExpression extractVarInitializer() {
    final PsiElement resolved = GrIntroduceHandlerBase.resolveLocalVar(context);
    GrExpression initializer = ((GrVariable)resolved).getInitializerGroovy();
    LOG.assertTrue(initializer != null);
    return initializer;
  }

  @Nullable
  private PsiElement findAnchorForAssignment(final GrCodeBlock block) {
    final List<PsiElement> elements = ContainerUtil.findAll(context.getOccurrences(), new Condition<PsiElement>() {
      @Override
      public boolean value(PsiElement element) {
        return PsiTreeUtil.isAncestor(block, element, true);
      }
    });
    if (elements.size() == 0) return null;
    return GrIntroduceHandlerBase.findAnchor(ContainerUtil.toArray(elements, new PsiElement[elements.size()]), block);
  }

  private PsiElement replaceOccurrence(GrVariable field, PsiElement occurrence, PsiClass containingClass) {
    boolean isOriginal = occurrence == context.getExpression();
    final GrReferenceExpression newExpr = createRefExpression(field, occurrence, containingClass);
    final PsiElement replaced;
    if (occurrence instanceof GrExpression) {
      replaced = ((GrExpression)occurrence).replaceWithExpression(newExpr, false);
    }
    else {
      replaced = occurrence.replace(newExpr);
    }

    if (replaced instanceof GrQualifiedReference<?>) {
      GrReferenceAdjuster.shortenReference((GrQualifiedReference<?>)replaced);
    }
    if (isOriginal) {
      updateCaretPosition(replaced);
    }
    return replaced;
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

  private GrVariableDeclaration createField(PsiClass targetClass) {
    final String name = settings.getName();
    final PsiType type = settings.getSelectedType();
    final String modifier = settings.getVisibilityModifier();

    List<String> modifiers = new ArrayList<String>();
    if (targetClass instanceof GroovyScriptClass) {
      modifiers.add("@" + GroovyCommonClassNames.GROOVY_TRANSFORM_FIELD);
    }
    if (settings.isStatic()) modifiers.add(PsiModifier.STATIC);
    if (!PsiModifier.PACKAGE_LOCAL.equals(modifier)) modifiers.add(modifier);
    if (settings.declareFinal()) modifiers.add(PsiModifier.FINAL);

    final String[] arr_modifiers = ArrayUtil.toStringArray(modifiers);
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(context.getProject());
    if (targetClass instanceof GroovyScriptClass) {
      return factory.createVariableDeclaration(arr_modifiers, ((GrExpression)null), type, name);
    }
    else {
      return factory.createFieldDeclaration(arr_modifiers, name, null, type);
    }
  }

  @NotNull
  protected GrExpression getInitializer() {
    if (settings.removeLocalVar()) {
      return extractVarInitializer();
    }


    final GrExpression expression = context.getExpression();
    if (expression != null) {
      return expression;
    }


    return GrIntroduceHandlerBase.generateExpressionFromStringPart(context.getStringPart(), context.getProject());
  }
}

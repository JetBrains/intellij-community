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
import java.util.Collections;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GrIntroduceFieldProcessor {
  private static final Logger LOG = Logger.getInstance(GrIntroduceFieldProcessor.class);

  private final GrIntroduceContext myContext;
  private final GrIntroduceFieldSettings mySettings;
  private GrExpression myInitializer;
  private GrVariable myLocalVariable;

  public GrIntroduceFieldProcessor(@NotNull GrIntroduceContext context,
                                   @NotNull GrIntroduceFieldSettings settings) {
    this.myContext = context;
    this.mySettings = settings;
  }

  @Nullable
  public GrVariable run() {
    PsiElement scope = myContext.getScope();
    final PsiClass targetClass = scope instanceof GroovyFileBase ? ((GroovyFileBase)scope).getScriptClass() : (PsiClass)scope;
    if (targetClass == null) return null;

    final GrVariableDeclaration declaration = insertField(targetClass);
    final GrVariable field = declaration.getVariables()[0];

    if (mySettings.removeLocalVar()) {
      myLocalVariable = GrIntroduceHandlerBase.resolveLocalVar(myContext);
    }
    myInitializer = (GrExpression)getInitializer().copy();

    List<PsiElement> replaced = processOccurrences(targetClass, field);

    switch (mySettings.initializeIn()) {
      case CUR_METHOD:
        initializeInMethod(field, replaced);
        break;
      case FIELD_DECLARATION:
        field.setInitializerGroovy(myInitializer);
        break;
      case CONSTRUCTOR:
        initializeInConstructor(field);
        break;
      case SETUP_METHOD:
        initializeInSetup(field);
        break;
    }

    JavaCodeStyleManager.getInstance(declaration.getProject()).shortenClassReferences(declaration);

    if (mySettings.removeLocalVar()) {
      GrIntroduceHandlerBase.deleteLocalVar(myLocalVariable);
    }

    return field;
  }

  @NotNull
  private List<PsiElement> processOccurrences(@NotNull PsiClass targetClass, @NotNull GrVariable field) {
    if (myContext.getStringPart() != null) {
      final GrExpression expr = GrIntroduceHandlerBase.processLiteral(field.getName(), myContext.getStringPart(), myContext.getProject());
      final PsiElement occurrence = replaceOccurrence(field, expr, targetClass);
      updateCaretPosition(occurrence);
      return Collections.singletonList(occurrence);
    }
    else {
      if (mySettings.replaceAllOccurrences()) {
        GroovyRefactoringUtil.sortOccurrences(myContext.getOccurrences());
        ArrayList<PsiElement> result = ContainerUtil.newArrayList();
        for (PsiElement occurrence : myContext.getOccurrences()) {
          result.add(replaceOccurrence(field, occurrence, targetClass));
        }
        return result;
      }
      else {
        final GrExpression expression = myContext.getExpression();
        assert expression != null;
        if (PsiUtil.isExpressionStatement(expression)) {
          return Collections.<PsiElement>singletonList(expression);
        }
        else {
          return Collections.singletonList(replaceOccurrence(field, expression, targetClass));
        }
      }
    }
  }

  private void updateCaretPosition(@NotNull PsiElement occurrence) {
    myContext.getEditor().getCaretModel().moveToOffset(occurrence.getTextRange().getEndOffset());
    myContext.getEditor().getSelectionModel().removeSelection();
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

  void initializeInSetup(@NotNull GrVariable field) {
    final PsiMethod setUpMethod = TestFrameworks.getInstance().findOrCreateSetUpMethod(((PsiClass)myContext.getScope()));
    assert setUpMethod instanceof GrMethod;

    final GrOpenBlock body = ((GrMethod)setUpMethod).getBlock();
    final PsiElement anchor = findAnchorForAssignment(body);
    generateAssignment(field, (GrStatement)anchor, body, null);
  }

  void initializeInMethod(@NotNull GrVariable field, @NotNull List<PsiElement> replaced) {
    final PsiElement _scope = myContext.getScope();
    final PsiElement scope = _scope instanceof GroovyScriptClass ? ((GroovyScriptClass)_scope).getContainingFile() : _scope;

    final PsiElement place = replaced.get(0);

    final GrMember member = GrIntroduceFieldHandler.getContainer(place, scope);
    GrStatementOwner container = member instanceof GrMethod ? ((GrMethod)member).getBlock() :
                                 member instanceof GrClassInitializer ? ((GrClassInitializer)member).getBlock() :
                                 place.getContainingFile() instanceof GroovyFile ? ((GroovyFile)place.getContainingFile()) :
                                 null;
    assert container != null;

    final PsiElement anchor;
    if (mySettings.removeLocalVar()) {
      GrVariable variable = myLocalVariable;
      anchor = PsiTreeUtil.getParentOfType(variable, GrStatement.class);
    }
    else {
      anchor = GrIntroduceHandlerBase.findAnchor(replaced.toArray(new PsiElement[replaced.size()]), container);
      GrIntroduceHandlerBase.assertStatement(anchor, myContext.getScope());
    }

    PsiElement occurrence = replaced.get(0);
    if (!mySettings.replaceAllOccurrences() && !isRefToField(occurrence, field) && PsiUtil.isExpressionStatement(occurrence)) {
      generateAssignment(field, (GrStatement)anchor, container, occurrence);
    }
    else {
      generateAssignment(field, (GrStatement)anchor, container, null);
    }
  }

  private static boolean isRefToField(@NotNull PsiElement occurrence, @NotNull PsiElement field) {
    return occurrence instanceof GrReferenceExpression && ((GrReferenceExpression)occurrence).resolve() == field;
  }


  void initializeInConstructor(@NotNull GrVariable field) {
    final PsiClass scope = (PsiClass)myContext.getScope();

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

      generateAssignment(field, (GrStatement)anchor, ((GrMethod)constructor).getBlock(), null);
    }
  }

  @NotNull
  private PsiMethod generateConstructor(@NotNull PsiClass scope) {
    final String name = scope.getName();
    LOG.assertTrue(name != null, scope.getText());
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(myContext.getProject());
    final GrMethod
      constructor = factory.createConstructorFromText(name, ArrayUtil.EMPTY_STRING_ARRAY, ArrayUtil.EMPTY_STRING_ARRAY, "{}", scope);
    if (scope instanceof GroovyScriptClass) constructor.getModifierList().setModifierProperty(GrModifier.DEF, true);
    return (PsiMethod)scope.add(constructor);
  }

  private void initializeInAnonymousClassInitializer(@NotNull GrVariable field, @NotNull GrAnonymousClassDefinition scope) {
    final GrClassInitializer[] initializers = scope.getInitializers();
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(myContext.getProject());
    final GrClassInitializer initializer = initializers.length == 0 ? (GrClassInitializer)scope.add(factory.createClassInitializer())
                                                                    : initializers[0];

    final PsiElement anchor = findAnchorForAssignment(initializer.getBlock());
    generateAssignment(field, (GrStatement)anchor, initializer.getBlock(), null);
  }

  private void generateAssignment(@NotNull GrVariable field,
                                  @Nullable GrStatement anchor,
                                  @NotNull GrStatementOwner defaultContainer,
                                  @Nullable PsiElement occurrenceToDelete) {
    final GrExpression initializer = myInitializer;
    GrAssignmentExpression init = (GrAssignmentExpression)GroovyPsiElementFactory.getInstance(myContext.getProject())
      .createExpressionFromText(mySettings.getName() + " = " + initializer.getText());

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
    replaceOccurrence(field, init.getLValue(), (PsiClass)myContext.getScope());

    if (occurrenceToDelete != null) {
      occurrenceToDelete.delete();
    }
  }

  @NotNull
  private GrExpression extractVarInitializer() {
    assert myLocalVariable != null;
    GrExpression initializer = myLocalVariable.getInitializerGroovy();
    LOG.assertTrue(initializer != null);
    return initializer;
  }

  @Nullable
  private PsiElement findAnchorForAssignment(final GrCodeBlock block) {
    final List<PsiElement> elements = ContainerUtil.findAll(myContext.getOccurrences(), new Condition<PsiElement>() {
      @Override
      public boolean value(PsiElement element) {
        return PsiTreeUtil.isAncestor(block, element, true);
      }
    });
    if (elements.size() == 0) return null;
    return GrIntroduceHandlerBase.findAnchor(ContainerUtil.toArray(elements, new PsiElement[elements.size()]), block);
  }

  @NotNull
  private PsiElement replaceOccurrence(@NotNull GrVariable field, @NotNull PsiElement occurrence, @NotNull PsiClass containingClass) {
    boolean isOriginal = occurrence == myContext.getExpression();
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

  @NotNull
  private static GrReferenceExpression createRefExpression(@NotNull GrVariable field,
                                                           @NotNull PsiElement place,
                                                           @NotNull PsiClass containingClass) {
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

  @NotNull
  private GrVariableDeclaration createField(@NotNull PsiClass targetClass) {
    final String name = mySettings.getName();
    final PsiType type = mySettings.getSelectedType();
    final String modifier = mySettings.getVisibilityModifier();

    List<String> modifiers = new ArrayList<String>();
    if (targetClass instanceof GroovyScriptClass) {
      modifiers.add("@" + GroovyCommonClassNames.GROOVY_TRANSFORM_FIELD);
    }
    if (mySettings.isStatic()) modifiers.add(PsiModifier.STATIC);
    if (!PsiModifier.PACKAGE_LOCAL.equals(modifier)) modifiers.add(modifier);
    if (mySettings.declareFinal()) modifiers.add(PsiModifier.FINAL);

    final String[] arr_modifiers = ArrayUtil.toStringArray(modifiers);
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(myContext.getProject());
    if (targetClass instanceof GroovyScriptClass) {
      return factory.createVariableDeclaration(arr_modifiers, ((GrExpression)null), type, name);
    }
    else {
      return factory.createFieldDeclaration(arr_modifiers, name, null, type);
    }
  }

  @NotNull
  protected GrExpression getInitializer() {
    if (mySettings.removeLocalVar()) {
      return extractVarInitializer();
    }


    final GrExpression expression = myContext.getExpression();
    if (expression != null) {
      return expression;
    }


    return GrIntroduceHandlerBase.generateExpressionFromStringPart(myContext.getStringPart(), myContext.getProject());
  }
}

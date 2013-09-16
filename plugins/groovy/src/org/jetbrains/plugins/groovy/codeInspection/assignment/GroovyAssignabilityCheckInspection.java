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

package org.jetbrains.plugins.groovy.codeInspection.assignment;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiSubstitutorImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.GrHighlightUtil;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.extensions.GroovyNamedArgumentProvider;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.findUsages.LiteralConstructorReference;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrThrowStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.*;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrReferenceResolveUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.ClosureParameterEnhancer;
import org.jetbrains.plugins.groovy.lang.psi.util.*;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Maxim.Medvedev
 */
public class GroovyAssignabilityCheckInspection extends BaseInspection {
  private static final Logger LOG = Logger.getInstance(GroovyAssignabilityCheckInspection.class);

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return ASSIGNMENT_ISSUES;
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Incompatible type assignments";
  }

  @Override
  protected String buildErrorString(Object... args) {
    return (String)args[0];
  }

  @NotNull
  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new MyVisitor();
  }

  private static class MyVisitor extends BaseInspectionVisitor {
    private void checkAssignability(@NotNull PsiType expectedType, @NotNull GrExpression expression, PsiElement toHighlight) {
      if (PsiUtil.isRawClassMemberAccess(expression)) return;
      if (checkForImplicitEnumAssigning(expectedType, expression, expression)) return;
      final PsiType rType = expression.getType();
      if (rType == null) return;

      if (!TypesUtil.isAssignable(expectedType, rType, expression)) {
        final List<LocalQuickFix> fixes = ContainerUtil.newArrayList();
        fixes.add(new GrCastFix(expectedType));

        String varName = getLValueVarName(toHighlight);
        if (varName != null) {
          fixes.add(new GrChangeVariableType(rType, varName));
        }

        final String message = GroovyBundle.message("cannot.assign", rType.getPresentableText(), expectedType.getPresentableText());
        registerError(toHighlight, message, fixes.toArray(new LocalQuickFix[fixes.size()]), ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
      }
    }

    @Nullable
    private static String getLValueVarName(PsiElement highlight) {
      final PsiElement parent = highlight.getParent();
      if (parent instanceof GrVariable) {
        return ((GrVariable)parent).getName();
      }
      else if (highlight instanceof GrReferenceExpression &&
               parent instanceof GrAssignmentExpression &&
               ((GrAssignmentExpression)parent).getLValue() == highlight) {
        final PsiElement resolved = ((GrReferenceExpression)highlight).resolve();
        if (resolved instanceof GrVariable && GroovyRefactoringUtil.isLocalVariable(resolved)) {
          return ((GrVariable)resolved).getName();
        }
      }

      return null;
    }

    private void checkAssignability(@NotNull PsiType lType,
                                    @Nullable PsiType rType,
                                    @NotNull GroovyPsiElement context,
                                    @NotNull final PsiElement elementToHighlight) {
      if (rType == null) return;
      if (!TypesUtil.isAssignable(lType, rType, context)) {
        final String message = GroovyBundle.message("cannot.assign", rType.getPresentableText(), lType.getPresentableText());
        registerError(elementToHighlight, message, LocalQuickFix.EMPTY_ARRAY, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
      }
    }


    private boolean checkForImplicitEnumAssigning(PsiType expectedType, GrExpression expression, GroovyPsiElement element) {
      if (!(expectedType instanceof PsiClassType)) return false;

      if (!GroovyConfigUtils.getInstance().isVersionAtLeast(element, GroovyConfigUtils.GROOVY1_8)) return false;

      final PsiClass resolved = ((PsiClassType)expectedType).resolve();
      if (resolved == null || !resolved.isEnum()) return false;

      final PsiType type = expression.getType();
      if (type == null) return false;

      if (!type.equalsToText(GroovyCommonClassNames.GROOVY_LANG_GSTRING) && !type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        return false;
      }

      final Object result = GroovyConstantExpressionEvaluator.evaluate(expression);
      if (result == null || !(result instanceof String)) {
        registerError(element, ProblemHighlightType.WEAK_WARNING,
                      GroovyBundle.message("cannot.assign.string.to.enum.0", expectedType.getPresentableText()));
      }
      else {
        final PsiField field = resolved.findFieldByName((String)result, true);
        if (!(field instanceof PsiEnumConstant)) {
          registerError(element, GroovyBundle.message("cannot.find.enum.constant.0.in.enum.1", result, expectedType.getPresentableText()));
        }
      }
      return true;
    }

    @Override
    public void visitReturnStatement(GrReturnStatement returnStatement) {
      super.visitReturnStatement(returnStatement);
      final GrExpression value = returnStatement.getReturnValue();
      if (value == null || isNewInstanceInitialingByTuple(value)) return;

      final PsiType returnType = PsiImplUtil.inferReturnType(returnStatement);
      if (returnType != null) {
        checkAssignability(returnType, value, returnStatement.getReturnWord());
      }
    }

    @Override
    public void visitExpression(GrExpression expression) {
      super.visitExpression(expression);
      if (PsiUtil.isExpressionStatement(expression)) {
        final PsiType returnType = PsiImplUtil.inferReturnType(expression);
        final GrControlFlowOwner flowOwner = ControlFlowUtils.findControlFlowOwner(expression);
        if (flowOwner != null && returnType != null && returnType != PsiType.VOID) {
          if (ControlFlowUtils.isReturnValue(expression, flowOwner) &&
              !isNewInstanceInitialingByTuple(expression) &&
              expression.getType() != PsiType.VOID) {
            checkAssignability(returnType, expression, getExpressionPartToHighlight(expression));
          }
        }
      }
    }

    protected boolean shouldProcess(GrMember member) {
      return !GroovyPsiManager.getInstance(member.getProject()).isCompileStatic(member);
    }

    @Override
    public void visitAssignmentExpression(GrAssignmentExpression assignment) {
      super.visitAssignmentExpression(assignment);

      GrExpression lValue = assignment.getLValue();
      if (lValue instanceof GrIndexProperty) return;
      if (!PsiUtil.mightBeLValue(lValue)) return;

      IElementType opToken = assignment.getOperationTokenType();
      if (opToken != GroovyTokenTypes.mASSIGN) return;

      GrExpression rValue = assignment.getRValue();
      if (rValue == null) return;

      if (lValue instanceof GrTupleExpression) {
        checkTupleAssignment(((GrTupleExpression)lValue), rValue);
      }
      else {
        checkAssignment(lValue, rValue);
      }
    }

    private void checkTupleAssignment(GrTupleExpression tupleExpression, GrExpression initializer) {
      GrExpression[] lValues = tupleExpression.getExpressions();
      if (initializer instanceof GrListOrMap) {
        GrExpression[] initializers = ((GrListOrMap)initializer).getInitializers();
        for (int i = 0; i < lValues.length; i++) {
          GrExpression lValue = lValues[i];
          if (initializers.length >= i) break;
          GrExpression rValue = initializers[i];
          checkAssignment(lValue, rValue);
        }
      }
      else {
        PsiType type = initializer.getType();
        PsiType rType = com.intellij.psi.util.PsiUtil.extractIterableTypeParameter(type, false);

        for (GrExpression lValue : lValues) {
          PsiType lType = lValue.getNominalType();
          // For assignments with spread dot
          if (isListAssignment(lValue) && lType != null && lType instanceof PsiClassType) {
            final PsiClassType pct = (PsiClassType)lType;
            final PsiClass clazz = pct.resolve();
            if (clazz != null && CommonClassNames.JAVA_UTIL_LIST.equals(clazz.getQualifiedName())) {
              final PsiType[] types = pct.getParameters();
              if (types.length == 1 && types[0] != null && rType != null) {
                checkAssignability(types[0], rType, tupleExpression, getExpressionPartToHighlight(lValue));
              }
            }
            return;
          }
          if (lValue instanceof GrReferenceExpression && ((GrReferenceExpression)lValue).resolve() instanceof GrReferenceExpression) {
            //lvalue is not-declared variable
            return;
          }

          if (lType != null && rType != null) {
            checkAssignability(lType, rType, tupleExpression, getExpressionPartToHighlight(lValue));
          }
        }
      }
    }

    private void checkAssignment(GrExpression lValue, GrExpression rValue) {
      PsiType lType = lValue.getNominalType();
      PsiType rType = rValue.getType();
      // For assignments with spread dot
      if (isListAssignment(lValue) && lType != null && lType instanceof PsiClassType) {
        final PsiClassType pct = (PsiClassType)lType;
        final PsiClass clazz = pct.resolve();
        if (clazz != null && CommonClassNames.JAVA_UTIL_LIST.equals(clazz.getQualifiedName())) {
          final PsiType[] types = pct.getParameters();
          if (types.length == 1 && types[0] != null && rType != null) {
            checkAssignability(types[0], rValue, getExpressionPartToHighlight(lValue));
          }
        }
        return;
      }
      if (lValue instanceof GrReferenceExpression && ((GrReferenceExpression)lValue).resolve() instanceof GrReferenceExpression) {
        //lvalue is not-declared variable
        return;
      }

      if (isNewInstanceInitialingByTuple(rValue)) {
        // new instance initializing e.g.: X x; x = [1, 2]
        return;
      }

      if (lType != null && rType != null) {
        checkAssignability(lType, rValue, getExpressionPartToHighlight(lValue));
      }
    }

    @Override
    public void visitVariable(GrVariable variable) {
      super.visitVariable(variable);

      PsiType varType = variable.getType();

      PsiElement parent = variable.getParent();
      //check tuple assignment:  def (int x, int y) = foo()
      if (parent instanceof GrVariableDeclaration && ((GrVariableDeclaration)parent).isTuple()) {
        GrVariableDeclaration tuple = (GrVariableDeclaration)parent;
        GrExpression initializer = tuple.getTupleInitializer();
        if (initializer == null) return;
        if (!(initializer instanceof GrListOrMap)) {
          PsiType type = initializer.getType();
          if (type == null) return;
          PsiType valueType = com.intellij.psi.util.PsiUtil.extractIterableTypeParameter(type, false);
          checkAssignability(varType, valueType, tuple, variable.getNameIdentifierGroovy());
          return;
        }
      }
      else if (parent instanceof GrForInClause) {
        GrExpression iterated = ((GrForInClause)parent).getIteratedExpression();
        if (iterated == null) return;

        PsiType iteratedType = ClosureParameterEnhancer.findTypeForIteration(iterated, parent);
        if (iteratedType == null) return;

        checkAssignability(varType, iteratedType, iterated, variable.getNameIdentifierGroovy());
        return;
      }

      GrExpression initializer = variable.getInitializerGroovy();
      if (initializer == null) return;

      PsiType rType = initializer.getType();
      if (rType == null) return;
      if (isNewInstanceInitialingByTuple(initializer)) {
        return;
      }

      if (variable instanceof GrParameter && ((GrParameter)variable).getDeclarationScope() instanceof GrMethod) {
        final GrMethod method = (GrMethod)((GrParameter)variable).getDeclarationScope();
        final PsiTypeParameter[] parameters = method.getTypeParameters();

        Map<PsiTypeParameter, PsiType> map = ContainerUtil.newHashMap();
        for (PsiTypeParameter parameter : parameters) {
          final PsiClassType[] types = parameter.getSuperTypes();

          if (types.length == 1) {
            map.put(parameter, PsiWildcardType.createExtends(variable.getManager(), types[0]));
          }
          else {
            map.put(parameter, PsiWildcardType.createExtends(variable.getManager(), PsiIntersectionType.createIntersection(types)));
          }
        }
        PsiSubstitutor substitutor = PsiSubstitutorImpl.createSubstitutor(map);
        checkAssignability(substitutor.substitute(varType), initializer, variable.getNameIdentifierGroovy());
        return;
      }

      checkAssignability(varType, initializer, variable.getNameIdentifierGroovy());
    }

    private static boolean isNewInstanceInitialingByTuple(GrExpression initializer) {
      return initializer instanceof GrListOrMap && initializer.getReference() instanceof LiteralConstructorReference;
    }

    @Override
    public void visitNewExpression(GrNewExpression newExpression) {
      super.visitNewExpression(newExpression);
      if (newExpression.getArrayCount() > 0) return;

      GrCodeReferenceElement refElement = newExpression.getReferenceElement();
      if (refElement == null) return;

      checkConstructorCall(newExpression, refElement);
    }

    private void checkConstructorCall(GrConstructorCall constructorCall, GroovyPsiElement refElement) {
      final GrArgumentList argList = constructorCall.getArgumentList();
      if (hasErrorElements(argList)) return;

      if (!checkCannotInferArgumentTypes(refElement)) return;
      final GroovyResolveResult constructorResolveResult = constructorCall.advancedResolve();
      final PsiElement constructor = constructorResolveResult.getElement();

      if (constructor != null) {
        if (!checkConstructorApplicability(constructorResolveResult, refElement, true)) return;
      }
      else {
        final GroovyResolveResult[] results = constructorCall.multiResolve(false);
        if (results.length > 0) {
          for (GroovyResolveResult result : results) {
            PsiElement resolved = result.getElement();
            if (resolved instanceof PsiMethod) {
              if (!checkConstructorApplicability(result, refElement, false)) return;
            }
          }
          registerError(getElementToHighlight(refElement, argList), GroovyBundle.message("constructor.call.is.ambiguous"));
        }
        else {
          final GrExpression[] expressionArguments = constructorCall.getExpressionArguments();
          final boolean hasClosureArgs = PsiImplUtil.hasClosureArguments(constructorCall);
          final boolean hasNamedArgs = PsiImplUtil.hasNamedArguments(constructorCall.getArgumentList());
          if (hasClosureArgs ||
              hasNamedArgs && expressionArguments.length > 0 ||
              !hasNamedArgs && expressionArguments.length > 0 && !isOnlyOneMapParam(expressionArguments)) {
            final GroovyResolveResult[] resolveResults = constructorCall.multiResolveClass();
            if (resolveResults.length == 1) {
              final PsiElement element = resolveResults[0].getElement();
              if (element instanceof PsiClass) {
                registerError(getElementToHighlight(refElement, argList),
                              GroovyBundle.message("cannot.apply.default.constructor", ((PsiClass)element).getName()));
                return;
              }
            }
          }
        }
      }

      checkNamedArgumentsType(constructorCall);
    }

    private static boolean isOnlyOneMapParam(GrExpression[] exprs) {
      if (!(exprs.length == 1)) return false;

      final GrExpression e = exprs[0];
      return TypesUtil.isAssignableByMethodCallConversion(TypesUtil.createTypeByFQClassName(CommonClassNames.JAVA_UTIL_MAP, e), e.getType(),
                                                          e);
    }

    @NotNull
    private static PsiElement getElementToHighlight(@NotNull PsiElement refElement, @Nullable GrArgumentList argList) {
      PsiElement elementToHighlight = argList;
      if (elementToHighlight == null || elementToHighlight.getTextLength() == 0) elementToHighlight = refElement;
      return elementToHighlight;
    }

    @NotNull
    private static PsiElement getExpressionPartToHighlight(@NotNull GrExpression expr) {
      if (expr instanceof GrClosableBlock) {
        return ((GrClosableBlock)expr).getLBrace();
      }

      return expr;
    }

    @Override
    public void visitListOrMap(GrListOrMap listOrMap) {
      super.visitListOrMap(listOrMap);

      final PsiReference reference = listOrMap.getReference();
      if (!(reference instanceof LiteralConstructorReference)) return;

      final GroovyResolveResult[] results = ((LiteralConstructorReference)reference).multiResolve(false);
      if (results.length == 0) return;

      if (results.length == 1) {
        final GroovyResolveResult result = results[0];
        final PsiElement element = result.getElement();
        if (element instanceof PsiClass) {
          if (!listOrMap.isMap()) {
            registerError(listOrMap, GroovyBundle.message("cannot.apply.default.constructor", ((PsiClass)element).getName()));
          }
        }
        else if (element instanceof PsiMethod && ((PsiMethod)element).isConstructor()) {
          checkLiteralConstructorApplicability(result, listOrMap, true);
        }
      }
      else {
        for (GroovyResolveResult result : results) {
          PsiElement resolved = result.getElement();
          if (resolved instanceof PsiMethod) {
            if (!checkLiteralConstructorApplicability(result, listOrMap, false)) return;
          }
          registerError(listOrMap, GroovyBundle.message("constructor.call.is.ambiguous"));
        }
      }
    }

    @Override
    public void visitThrowStatement(GrThrowStatement throwStatement) {
      super.visitThrowStatement(throwStatement);

      final GrExpression exception = throwStatement.getException();
      if (exception != null) {
        final PsiElement throwWord = throwStatement.getFirstChild();
        checkAssignability(PsiType.getJavaLangThrowable(throwStatement.getManager(), throwStatement.getResolveScope()), exception, throwWord);
      }
    }

    private boolean checkLiteralConstructorApplicability(GroovyResolveResult result, GrListOrMap listOrMap, boolean checkUnknownArgs) {
      final PsiElement element = result.getElement();
      LOG.assertTrue(element instanceof PsiMethod && ((PsiMethod)element).isConstructor());
      final PsiMethod constructor = (PsiMethod)element;

      final GrExpression[] exprArgs;
      final GrNamedArgument[] namedArgs;
      if (listOrMap.isMap()) {
        exprArgs = GrExpression.EMPTY_ARRAY;
        namedArgs = listOrMap.getNamedArguments();
      }
      else {
        exprArgs = listOrMap.getInitializers();
        namedArgs = GrNamedArgument.EMPTY_ARRAY;
      }

      if (exprArgs.length == 0 && !PsiUtil.isConstructorHasRequiredParameters(constructor)) return true;

      PsiType[] argumentTypes = PsiUtil.getArgumentTypes(namedArgs, exprArgs, GrClosableBlock.EMPTY_ARRAY, false, null, false);
      if (listOrMap.isMap() && namedArgs.length == 0) {
        argumentTypes = new PsiType[]{listOrMap.getType()};
      }

      GrClosureSignatureUtil.ApplicabilityResult applicable =
        PsiUtil.isApplicableConcrete(argumentTypes, constructor, result.getSubstitutor(), listOrMap, false);
      switch (applicable) {
        case inapplicable:
          highlightInapplicableMethodUsage(result, listOrMap, constructor, argumentTypes);
          return false;
        case canBeApplicable:
          if (checkUnknownArgs) {
            highlightUnknownArgs(listOrMap);
          }
          return !checkUnknownArgs;
        default:
          return true;
      }
    }

    private boolean checkConstructorApplicability(GroovyResolveResult constructorResolveResult,
                                                  GroovyPsiElement place,
                                                  boolean checkUnknownArgs) {
      final PsiElement element = constructorResolveResult.getElement();
      LOG.assertTrue(element instanceof PsiMethod && ((PsiMethod)element).isConstructor());
      final PsiMethod constructor = (PsiMethod)element;

      final GrArgumentList argList = PsiUtil.getArgumentsList(place);
      if (argList != null) {
        final GrExpression[] exprArgs = argList.getExpressionArguments();

        if (exprArgs.length == 0 && !PsiUtil.isConstructorHasRequiredParameters(constructor)) return true;
      }

      PsiType[] types = PsiUtil.getArgumentTypes(place, true);
      PsiClass containingClass = constructor.getContainingClass();
      if (types != null && containingClass != null) {
        types = GrInnerClassConstructorUtil.addEnclosingArgIfNeeded(types, place, containingClass);
      }
      return checkMethodApplicability(constructorResolveResult, place, checkUnknownArgs, types);
    }

    @Override
    public void visitConstructorInvocation(GrConstructorInvocation invocation) {
      super.visitConstructorInvocation(invocation);
      checkConstructorCall(invocation, invocation.getInvokedExpression());
    }

    @Override
    public void visitIndexProperty(GrIndexProperty expression) {
      super.visitIndexProperty(expression);

      if (!checkCannotInferArgumentTypes(expression.getInvokedExpression())) return;

      final GrExpression invoked = expression.getInvokedExpression();

      if (hasErrorElements(expression.getArgumentList())) return;

      final PsiType type = invoked.getType();
      final PsiType[] types = PsiUtil.getArgumentTypes(expression.getArgumentList(), true);
      if (type instanceof PsiArrayType) {
        assert types != null;

        if (PsiUtil.isLValue(expression)) {
          if (types.length == 2 &&
              TypesUtil.isAssignable(PsiType.INT, types[0], expression) &&
              TypesUtil.isAssignable(((PsiArrayType)type).getComponentType(), types[1], expression)) {
            return;
          }
        }
        else {
          if (types.length == 1 && TypesUtil.isAssignable(PsiType.INT, types[0], expression)) {
            return;
          }
        }
      }

      final GroovyResolveResult[] results = expression.multiResolve(false);

      if (results.length == 1) {
        final GroovyResolveResult resolveResult = results[0];
        PsiElement resolved = resolveResult.getElement();

        if (resolved instanceof PsiMethod && !resolveResult.isInvokedOnProperty()) {
          checkMethodApplicability(resolveResult, invoked, true);
        }
        else if (resolved instanceof GrField) {
          checkCallApplicability(((GrField)resolved).getTypeGroovy(), invoked, true);
        }
        else if (resolved instanceof PsiField) {
          checkCallApplicability(((PsiField)resolved).getType(), invoked, true);
        }
      }
      else if (results.length > 0) {
        for (GroovyResolveResult resolveResult : results) {
          PsiElement resolved = resolveResult.getElement();
          if (resolved instanceof PsiMethod && !resolveResult.isInvokedOnProperty()) {
            if (!checkMethodApplicability(resolveResult, invoked, false)) return;
          }
          else if (resolved instanceof GrField) {
            if (!checkCallApplicability(((GrField)resolved).getTypeGroovy(), invoked, false)) return;
          }
          else if (resolved instanceof PsiField) {
            if (!checkCallApplicability(((PsiField)resolved).getType(), invoked, false)) return;
          }
        }

        registerError(getElementToHighlight(invoked, PsiUtil.getArgumentsList(invoked)), GroovyBundle.message("method.call.is.ambiguous"));
      }
      else {
        final String typesString = buildArgTypesList(types);
        registerError(PsiUtil.getArgumentsList(invoked), GroovyBundle.message("cannot.find.operator.overload.method", typesString));
      }
    }

    private boolean checkCannotInferArgumentTypes(PsiElement place) {
      if (PsiUtil.getArgumentTypes(place, true) != null) {
        return true;
      }
      else {
        highlightUnknownArgs(place);
        return false;
      }
    }

    @Override
    public void visitMethodCallExpression(GrMethodCallExpression methodCallExpression) {
      super.visitMethodCallExpression(methodCallExpression);
      checkMethodCall(methodCallExpression, methodCallExpression.getInvokedExpression());
    }

    @Override
    public void visitApplicationStatement(GrApplicationStatement applicationStatement) {
      super.visitApplicationStatement(applicationStatement);
      checkMethodCall(applicationStatement, applicationStatement.getInvokedExpression());
    }

    @Override
    public void visitEnumConstant(GrEnumConstant enumConstant) {
      super.visitEnumConstant(enumConstant);
      checkConstructorCall(enumConstant, enumConstant);
    }

    private void checkNamedArgumentsType(GrCall call) {
      GrNamedArgument[] namedArguments = PsiUtil.getFirstMapNamedArguments(call);

      if (namedArguments.length == 0) return;

      Map<String, NamedArgumentDescriptor> map = GroovyNamedArgumentProvider.getNamedArgumentsFromAllProviders(call, null, false);
      if (map == null) return;

      for (GrNamedArgument namedArgument : namedArguments) {
        String labelName = namedArgument.getLabelName();

        NamedArgumentDescriptor descriptor = map.get(labelName);

        if (descriptor == null) continue;

        GrExpression namedArgumentExpression = namedArgument.getExpression();
        if (namedArgumentExpression == null) continue;

        if (isNewInstanceInitialingByTuple(namedArgumentExpression)) continue;

        if (PsiUtil.isRawClassMemberAccess(namedArgumentExpression)) continue;

        PsiType expressionType = namedArgumentExpression.getType();
        if (expressionType == null) continue;

        expressionType = TypesUtil.boxPrimitiveType(expressionType, call.getManager(), call.getResolveScope());

        if (!descriptor.checkType(expressionType, call)) {
          registerError(namedArgumentExpression,
                        "Type of argument '" + labelName + "' can not be '" + expressionType.getPresentableText() + "'");
        }
      }
    }

    /**
     * checks only children of e
     */
    private static boolean hasErrorElements(@Nullable PsiElement e) {
      if (e == null) return false;

      for (PsiElement child = e.getFirstChild(); child != null; child = child.getNextSibling()) {
        if (child instanceof PsiErrorElement) return true;
      }
      return false;
    }

    private void checkMethodCall(GrCall call, GrExpression invoked) {
      if (hasErrorElements(call.getArgumentList())) return;

      if (invoked instanceof GrReferenceExpression) {
        final GrReferenceExpression referenceExpression = (GrReferenceExpression)invoked;
        GroovyResolveResult resolveResult = call.advancedResolve();
        GroovyResolveResult[] results = call.multiResolve(false); //cached

        PsiElement resolved = resolveResult.getElement();
        if (resolved == null) {
          GrExpression qualifier = referenceExpression.getQualifierExpression();
          if (qualifier == null && GrHighlightUtil.isDeclarationAssignment(referenceExpression)) return;
        }

        if (!checkCannotInferArgumentTypes(referenceExpression)) return;

        final PsiType type = referenceExpression.getType();
        if (resolved != null) {
          if (resolved instanceof PsiMethod && !resolveResult.isInvokedOnProperty()) {
            checkMethodApplicability(resolveResult, referenceExpression, true);
          }
          else {
            checkCallApplicability(type, referenceExpression, true);
          }
        }
        else if (results.length > 0) {
          for (GroovyResolveResult result : results) {
            resolved = result.getElement();
            if (resolved instanceof PsiMethod && !result.isInvokedOnProperty()) {
              if (!checkMethodApplicability(result, referenceExpression, false)) return;
            }
            else {
              if (!checkCallApplicability(type, referenceExpression, false)) return;
            }
          }

          registerError(getElementToHighlight(referenceExpression, PsiUtil.getArgumentsList(referenceExpression)),
                        GroovyBundle.message("method.call.is.ambiguous"));
        }
      }
      else if (invoked != null) { //it checks in visitRefExpr(...)
        final PsiType type = invoked.getType();
        checkCallApplicability(type, invoked, true);
      }

      checkNamedArgumentsType(call);
    }

    private void highlightInapplicableMethodUsage(GroovyResolveResult methodResolveResult,
                                                  GroovyPsiElement place,
                                                  PsiMethod method,
                                                  PsiType[] argumentTypes) {
      final PsiClass containingClass =
        method instanceof GrGdkMethod ? ((GrGdkMethod)method).getStaticMethod().getContainingClass() : method.getContainingClass();

      if (containingClass == null) {
        registerCannotApplyError(place, argumentTypes, method.getName());
        return;
      }
      final String typesString = buildArgTypesList(argumentTypes);
      final PsiElementFactory factory = JavaPsiFacade.getInstance(method.getProject()).getElementFactory();
      final PsiClassType containingType = factory.createType(containingClass, methodResolveResult.getSubstitutor());
      final String canonicalText = containingType.getInternalCanonicalText();
      String message;
      if (method.isConstructor()) {
        message = GroovyBundle.message("cannot.apply.constructor", method.getName(), canonicalText, typesString);
      }
      else {
        message = GroovyBundle.message("cannot.apply.method1", method.getName(), canonicalText, typesString);
      }

      final GrArgumentList argumentsList = PsiUtil.getArgumentsList(place);
      registerError(getElementToHighlight(place, argumentsList), message,
                    genCastFixes(GrClosureSignatureUtil.createSignature(methodResolveResult), argumentTypes, argumentsList),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    }

    private static LocalQuickFix[] genCastFixes(GrSignature signature, PsiType[] argumentTypes, @Nullable GrArgumentList argumentList) {
      if (argumentList == null) return LocalQuickFix.EMPTY_ARRAY;

      final List<GrClosureSignature> signatures = GrClosureSignatureUtil.generateSimpleSignature(signature);

      List<Pair<Integer, PsiType>> allErrors = new ArrayList<Pair<Integer, PsiType>>();
      for (GrClosureSignature closureSignature : signatures) {
        final GrClosureSignatureUtil.MapResultWithError<PsiType> map =
          GrClosureSignatureUtil.mapSimpleSignatureWithErrors(closureSignature, argumentTypes, Function.ID, argumentList, 1);
        if (map != null) {
          final List<Pair<Integer, PsiType>> errors = map.getErrors();
          for (Pair<Integer, PsiType> error : errors) {
            if (!(error.first == 0 && PsiImplUtil.hasNamedArguments(argumentList))) {
              allErrors.add(error);
            }
          }
        }
      }

      final ArrayList<LocalQuickFix> fixes = new ArrayList<LocalQuickFix>();
      for (Pair<Integer, PsiType> error : allErrors) {
        fixes.add(new ParameterCastFix(error.first, error.second));
      }

      return fixes.toArray(new LocalQuickFix[fixes.size()]);
    }

    private boolean checkCallApplicability(PsiType type, GroovyPsiElement invokedExpr, boolean checkUnknownArgs) {

      PsiType[] argumentTypes = PsiUtil.getArgumentTypes(invokedExpr, true);
      if (type instanceof GrClosureType) {
        if (argumentTypes == null) return true;

        GrClosureSignatureUtil.ApplicabilityResult result = PsiUtil.isApplicableConcrete(argumentTypes, (GrClosureType)type, invokedExpr);
        switch (result) {
          case inapplicable:
            registerCannotApplyError(invokedExpr, argumentTypes, invokedExpr.getText());
            return false;
          case canBeApplicable:
            if (checkUnknownArgs) {
              highlightUnknownArgs(invokedExpr);
            }
            return !checkUnknownArgs;
          default:
            return true;
        }
      }
      else if (type != null) {
        final GroovyResolveResult[] calls = ResolveUtil.getMethodCandidates(type, "call", invokedExpr, argumentTypes);
        for (GroovyResolveResult result : calls) {
          PsiElement resolved = result.getElement();
          if (resolved instanceof PsiMethod && !result.isInvokedOnProperty()) {
            if (!checkMethodApplicability(result, invokedExpr, checkUnknownArgs && calls.length == 1)) return false;
          }
          else if (resolved instanceof PsiField) {
            if (!checkCallApplicability(((PsiField)resolved).getType(), invokedExpr, checkUnknownArgs && calls.length == 1)) return false;
          }
        }
        if (calls.length == 0 && !(invokedExpr instanceof GrString)) {
          registerCannotApplyError(invokedExpr, argumentTypes, invokedExpr.getText());
        }
        return true;
      }
      return true;
    }

    private void registerCannotApplyError(PsiElement place, PsiType[] argumentTypes, String invokedText) {
      final String typesString = buildArgTypesList(argumentTypes);
      String message = GroovyBundle.message("cannot.apply.method.or.closure", invokedText, typesString);
      PsiElement elementToHighlight = PsiUtil.getArgumentsList(place);
      if (elementToHighlight == null || elementToHighlight.getTextRange().getLength() == 0) elementToHighlight = place;
      registerError(elementToHighlight, message);
    }

    private static String buildArgTypesList(PsiType[] argTypes) {
      StringBuilder builder = new StringBuilder();
      builder.append("(");
      for (int i = 0; i < argTypes.length; i++) {
        if (i > 0) {
          builder.append(", ");
        }
        PsiType argType = argTypes[i];
        builder.append(argType != null ? argType.getInternalCanonicalText() : "?");
      }
      builder.append(")");
      return builder.toString();
    }

    private boolean checkMethodApplicability(@NotNull GroovyResolveResult methodResolveResult,
                                             @NotNull GroovyPsiElement place,
                                             boolean checkUnknownArgs) {
      return checkMethodApplicability(methodResolveResult, place, checkUnknownArgs, PsiUtil.getArgumentTypes(place, true));
    }

    private boolean checkMethodApplicability(@NotNull GroovyResolveResult methodResolveResult,
                                             @NotNull GroovyPsiElement place,
                                             boolean checkUnknownArgs,
                                             @Nullable PsiType[] argumentTypes) {
      final PsiElement element = methodResolveResult.getElement();
      if (!(element instanceof PsiMethod)) return true;
      if (element instanceof GrBuilderMethod) return true;

      final PsiMethod method = (PsiMethod)element;
      if ("call".equals(method.getName()) && place instanceof GrReferenceExpression) {
        final GrExpression qualifierExpression = ((GrReferenceExpression)place).getQualifierExpression();
        if (qualifierExpression != null) {
          final PsiType type = qualifierExpression.getType();
          if (type instanceof GrClosureType) {
            GrClosureSignatureUtil.ApplicabilityResult result = PsiUtil.isApplicableConcrete(argumentTypes, (GrClosureType)type, place);
            switch (result) {
              case inapplicable:
                highlightInapplicableMethodUsage(methodResolveResult, place, method, argumentTypes);
                return false;
              case canBeApplicable:
                if (checkUnknownArgs) {
                  highlightUnknownArgs(place);
                }
                return !checkUnknownArgs;
              default:
                return true;
            }
          }
        }
      }

      if (method instanceof GrGdkMethod && place instanceof GrReferenceExpression) {
        final PsiMethod staticMethod = ((GrGdkMethod)method).getStaticMethod();
        final PsiType qualifierType = inferQualifierTypeByPlace((GrReferenceExpression)place);

        final GrExpression qualifier = PsiImplUtil.getRuntimeQualifier((GrReferenceExpression)place);

        //check methods processed by @Category(ClassWhichProcessMethod) annotation
        if (qualifierType != null &&
            !GdkMethodUtil.isCategoryMethod(staticMethod, qualifierType, qualifier, methodResolveResult.getSubstitutor()) &&
            !checkCategoryQualifier((GrReferenceExpression)place, qualifier, staticMethod, methodResolveResult.getSubstitutor())) {
          registerError(((GrReferenceExpression)place).getReferenceNameElement(), GroovyInspectionBundle
            .message("category.method.0.cannot.be.applied.to.1", method.getName(), qualifierType.getCanonicalText()));
          return false;
        }
      }

      if (argumentTypes == null) return true;

      GrClosureSignatureUtil.ApplicabilityResult applicable = PsiUtil.isApplicableConcrete(argumentTypes, method, methodResolveResult.getSubstitutor(), place, false);
      switch (applicable) {
        case inapplicable:
          highlightInapplicableMethodUsage(methodResolveResult, place, method, argumentTypes);
          return false;
        case canBeApplicable:
          if (checkUnknownArgs) {
            highlightUnknownArgs(place);
          }
          return !checkUnknownArgs;
        default:
          return true;
      }
    }

    private static boolean checkCategoryQualifier(GrReferenceExpression place,
                                                  GrExpression qualifier,
                                                  PsiMethod gdkMethod,
                                                  PsiSubstitutor substitutor) {
      PsiClass categoryAnnotationOwner = inferCategoryAnnotationOwner(place, qualifier);

      if (categoryAnnotationOwner != null) {
        PsiClassType categoryType = GdkMethodUtil.getCategoryType(categoryAnnotationOwner);
        if (categoryType != null) {
          return GdkMethodUtil.isCategoryMethod(gdkMethod, categoryType, qualifier, substitutor);
        }
      }

      return false;
    }

    private static PsiClass inferCategoryAnnotationOwner(GrReferenceExpression place, GrExpression qualifier) {
      if (qualifier == null) {
        GrMethod container = PsiTreeUtil.getParentOfType(place, GrMethod.class, true, GrMember.class);
        if (container != null && !container.hasModifierProperty(PsiModifier.STATIC)) { //only instance methods can be qualified by category class
          return container.getContainingClass();
        }
      }
      else if (PsiUtil.isThisReference(qualifier)) {
        PsiElement resolved = ((GrReferenceExpression)qualifier).resolve();
        if (resolved instanceof PsiClass) {
          return (PsiClass)resolved;
        }
      }
      return null;
    }

    private void highlightUnknownArgs(@NotNull PsiElement place) {
      final PsiElement toHighlight = getElementToHighlight(place, PsiUtil.getArgumentsList(place));
      registerError(toHighlight, GroovyBundle.message("cannot.infer.argument.types"), LocalQuickFix.EMPTY_ARRAY, ProblemHighlightType.WEAK_WARNING);
    }
  }

  private static boolean isListAssignment(GrExpression lValue) {
    if (lValue instanceof GrReferenceExpression) {
      GrReferenceExpression expression = (GrReferenceExpression)lValue;
      final PsiElement dot = expression.getDotToken();
      //noinspection ConstantConditions
      if (dot != null && dot.getNode().getElementType() == GroovyTokenTypes.mSPREAD_DOT) {
        return true;
      }
      else {
        final GrExpression qualifier = expression.getQualifierExpression();
        if (qualifier != null) return isListAssignment(qualifier);
      }
    }
    return false;
  }

  @Nullable
  private static PsiType inferQualifierTypeByPlace(GrReferenceExpression place) {
    if (place.getParent() instanceof GrIndexProperty) {
      return place.getType();
    }
    return GrReferenceResolveUtil.getQualifierType(place);
  }


  private static class AnnotatingVisitor extends MyVisitor {
    private AnnotationHolder myHolder;

    @Override
    protected boolean shouldProcess(GrMember member) {
      return true;
    }

    @Override
    protected void registerError(@NotNull final PsiElement location,
                                 @NotNull final String description,
                                 @Nullable final LocalQuickFix[] fixes,
                                 final ProblemHighlightType highlightType) {
      Annotation annotation = myHolder.createErrorAnnotation(location, description);
      if (fixes != null) {
        for (final LocalQuickFix fix : fixes) {
          annotation.registerFix(new IntentionAction() {
            @NotNull
            @Override
            public String getText() {
              return fix.getName();
            }

            @NotNull
            @Override
            public String getFamilyName() {
              return fix.getFamilyName();
            }

            @Override
            public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
              return true;
            }

            @Override
            public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
              InspectionManager manager = InspectionManager.getInstance(project);
              ProblemDescriptor descriptor = manager.createProblemDescriptor(location, description, fixes, highlightType, fixes.length == 1, false);
              fix.applyFix(project, descriptor);
            }

            @Override
            public boolean startInWriteAction() {
              return true;
            }
          });
        }
      }
    }

    protected void registerError(@NotNull PsiElement location,
                                 ProblemHighlightType highlightType,
                                 Object... args) {
      registerError(location, (String)args[0], LocalQuickFix.EMPTY_ARRAY, highlightType);
    }


    @Override
    public void visitElement(GroovyPsiElement element) {
      //do nothing
    }
  }

  private static final ThreadLocal<AnnotatingVisitor> visitor = new ThreadLocal<AnnotatingVisitor>() {
    @Override
    protected AnnotatingVisitor initialValue() {
      return new AnnotatingVisitor();
    }
  };

  public static void checkElement(GroovyPsiElement e, AnnotationHolder holder) {
    AnnotatingVisitor annotatingVisitor = visitor.get();

    AnnotationHolder oldHolder = annotatingVisitor.myHolder;
    try {
      annotatingVisitor.myHolder = holder;
      e.accept(annotatingVisitor);
    }
    finally {
      annotatingVisitor.myHolder = oldHolder;
    }
  }

  @Nullable
  public ProblemDescriptor[] checkFile(@NotNull PsiFile psiFile, @NotNull InspectionManager inspectionManager, boolean isOnTheFly) {
    if (!(psiFile instanceof GroovyFileBase)) {
      return super.checkFile(psiFile, inspectionManager, isOnTheFly);
    }


    final GroovyFileBase groovyFile = (GroovyFileBase)psiFile;
    final ProblemsHolder problemsHolder = new ProblemsHolder(inspectionManager, psiFile, isOnTheFly);
    final MyVisitor visitor = (MyVisitor)buildGroovyVisitor(problemsHolder, isOnTheFly);

    processElement(groovyFile, visitor);

    return problemsHolder.getResultsArray();
  }

  private static void processElement(@NotNull GroovyPsiElement element, @NotNull MyVisitor visitor) {
    if (element instanceof GrMember && !visitor.shouldProcess((GrMember)element)) {
      return;
    }

    final int count = visitor.getErrorCount();

    PsiElement child = element.getFirstChild();
    while (child != null) {
      if (child instanceof GroovyPsiElement) {
        processElement((GroovyPsiElement)child, visitor);
      }
      child = child.getNextSibling();
    }

    if (count == visitor.getErrorCount()) {
      element.accept(visitor);
    }
  }

}

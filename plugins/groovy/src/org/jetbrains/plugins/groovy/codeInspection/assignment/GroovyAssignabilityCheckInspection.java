/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrSpreadArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrThrowStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.*;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.ClosureParameterEnhancer;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.ClosureParamsEnhancer;
import org.jetbrains.plugins.groovy.lang.psi.util.*;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Maxim.Medvedev
 */
public class GroovyAssignabilityCheckInspection extends BaseInspection {
  private static final Logger LOG = Logger.getInstance(GroovyAssignabilityCheckInspection.class);

  private static final String SHORT_NAME = "GroovyAssignabilityCheck";

  public boolean myHighlightAssignmentsFromVoid = true;

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

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(GroovyInspectionBundle.message("highlight.assignments.from.void"), "myHighlightAssignmentsFromVoid");
    return optionsPanel;
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

      if (PsiUtil.isVoidMethodCall(expression)) {
        if (isHighlightAssignmentsFromVoid(expression)) {
          registerError(toHighlight, GroovyBundle.message("cannot.assign", PsiType.VOID.getPresentableText(),
                                                          expectedType.getPresentableText()));
        }
        return;
      }

      if (!TypesUtil.isAssignable(expectedType, rType, expression)) {
        final List<LocalQuickFix> fixes = ContainerUtil.newArrayList();
        fixes.add(new GrCastFix(expectedType, expression));

        String varName = getLValueVarName(toHighlight);
        if (varName != null) {
          fixes.add(new GrChangeVariableType(rType, varName));
        }

        final String message = GroovyBundle.message("cannot.assign", rType.getPresentableText(), expectedType.getPresentableText());
        registerError(toHighlight, message, fixes.toArray(new LocalQuickFix[fixes.size()]), ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
      }
    }

    private static boolean isHighlightAssignmentsFromVoid(PsiElement place) {
      final GroovyAssignabilityCheckInspection instance = getInspectionInstance(place.getContainingFile(), place.getProject());
      if (instance != null) {
        return instance.myHighlightAssignmentsFromVoid;
      }

      return false;
    }

    private static GroovyAssignabilityCheckInspection getInspectionInstance(PsiFile file, Project project) {
      final InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
      return (GroovyAssignabilityCheckInspection)profile.getUnwrappedTool(SHORT_NAME, file);
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
      final GrExpression value = returnStatement.getReturnValue();
      if (value == null || isNewInstanceInitialingByTuple(value)) return;

      final PsiType returnType = PsiImplUtil.inferReturnType(returnStatement);
      if (returnType != null) {
        checkAssignability(returnType, value, returnStatement.getReturnWord());
      }
    }

    @Override
    public void visitExpression(GrExpression expression) {
      if (PsiUtil.isExpressionStatement(expression)) {
        final PsiType returnType = PsiImplUtil.inferReturnType(expression);
        final GrControlFlowOwner flowOwner = ControlFlowUtils.findControlFlowOwner(expression);
        if (flowOwner != null && returnType != null && returnType != PsiType.VOID) {
          if (ControlFlowUtils.isReturnValue(expression, flowOwner) &&
              !isNewInstanceInitialingByTuple(expression) &&
              !PsiUtil.isVoidMethodCall(expression)) {
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
          if (GroovyRefactoringUtil.isSpreadAssignment(lValue)) {
            final PsiType argType = extractIterableArg(lType);
            if (argType != null && rType != null) {
              checkAssignability(argType, rType, tupleExpression, getExpressionPartToHighlight(lValue));
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
      if (GroovyRefactoringUtil.isSpreadAssignment(lValue)) {
        final PsiType argType = extractIterableArg(lType);
        if (argType != null && rValue != null) {
          checkAssignability(argType, rValue, getExpressionPartToHighlight(lValue));
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

    @Nullable
    private static PsiType extractIterableArg(@Nullable PsiType type) {
      return com.intellij.psi.util.PsiUtil.extractIterableTypeParameter(type, false);
    }

    @Override
    public void visitVariable(GrVariable variable) {
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
      return initializer instanceof GrListOrMap &&
             initializer.getReference() instanceof LiteralConstructorReference &&
             ((LiteralConstructorReference)initializer.getReference()).getConstructedClassType() != null;
    }

    @Override
    public void visitNewExpression(GrNewExpression newExpression) {
      if (newExpression.getArrayCount() > 0) return;

      GrCodeReferenceElement refElement = newExpression.getReferenceElement();
      if (refElement == null) return;

      GrNewExpressionInfo info = new GrNewExpressionInfo(newExpression);
      checkConstructorCall(info);
    }

    private void checkConstructorCall(ConstructorCallInfo<?> info) {
      if (hasErrorElements(info.getArgumentList())) return;

      if (!checkCannotInferArgumentTypes(info)) return;
      final GroovyResolveResult constructorResolveResult = info.advancedResolve();
      final PsiElement constructor = constructorResolveResult.getElement();

      if (constructor != null) {
        if (!checkConstructorApplicability(constructorResolveResult, info, true)) return;
      }
      else {
        final GroovyResolveResult[] results = info.multiResolve();
        if (results.length > 0) {
          for (GroovyResolveResult result : results) {
            PsiElement resolved = result.getElement();
            if (resolved instanceof PsiMethod) {
              if (!checkConstructorApplicability(result, info, false)) return;
            }
          }
          registerError(info.getElementToHighlight(), GroovyBundle.message("constructor.call.is.ambiguous"));
        }
        else {
          final GrExpression[] expressionArguments = info.getExpressionArguments();
          final boolean hasClosureArgs = info.getClosureArguments().length > 0;
          final boolean hasNamedArgs = info.getNamedArguments().length > 0;
          if (hasClosureArgs ||
              hasNamedArgs && expressionArguments.length > 0 ||
              !hasNamedArgs && expressionArguments.length > 0 && !isOnlyOneMapParam(expressionArguments)) {
            final GroovyResolveResult[] resolveResults = info.multiResolveClass();
            if (resolveResults.length == 1) {
              final PsiElement element = resolveResults[0].getElement();
              if (element instanceof PsiClass) {
                registerError(info.getElementToHighlight(),
                              GroovyBundle.message("cannot.apply.default.constructor", ((PsiClass)element).getName()));
                return;
              }
            }
          }
        }
      }

      checkNamedArgumentsType(info);
    }

    private static boolean isOnlyOneMapParam(GrExpression[] exprs) {
      if (!(exprs.length == 1)) return false;

      final GrExpression e = exprs[0];
      return TypesUtil.isAssignableByMethodCallConversion(TypesUtil.createTypeByFQClassName(CommonClassNames.JAVA_UTIL_MAP, e), e.getType(), e);
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

      final PsiReference reference = listOrMap.getReference();
      if (!(reference instanceof LiteralConstructorReference)) return;

      final GroovyResolveResult[] results = ((LiteralConstructorReference)reference).multiResolve(false);
      if (results.length == 0) return;

      checkConstructorCall(new GrListOrMapInfo(listOrMap));
    }

    @Override
    public void visitThrowStatement(GrThrowStatement throwStatement) {

      final GrExpression exception = throwStatement.getException();
      if (exception != null) {
        final PsiElement throwWord = throwStatement.getFirstChild();
        checkAssignability(PsiType.getJavaLangThrowable(throwStatement.getManager(), throwStatement.getResolveScope()), exception, throwWord);
      }
    }

    private boolean checkConstructorApplicability(GroovyResolveResult constructorResolveResult,
                                                  CallInfo<?> info,
                                                  boolean checkUnknownArgs) {
      final PsiElement element = constructorResolveResult.getElement();
      LOG.assertTrue(element instanceof PsiMethod && ((PsiMethod)element).isConstructor(), element);
      final PsiMethod constructor = (PsiMethod)element;

      final GrArgumentList argList = info.getArgumentList();
      if (argList != null) {
        final GrExpression[] exprArgs = argList.getExpressionArguments();
        if (exprArgs.length == 0 && !PsiUtil.isConstructorHasRequiredParameters(constructor)) return true;
      }

      PsiType[] types = info.getArgumentTypes();
      PsiClass containingClass = constructor.getContainingClass();
      if (types != null && containingClass != null) {
        final PsiType[] newTypes = GrInnerClassConstructorUtil.addEnclosingArgIfNeeded(types, info.getCall(), containingClass);
        if (newTypes.length != types.length) {
          return checkMethodApplicability(constructorResolveResult, checkUnknownArgs, new DelegatingCallInfo(info) {
            @Nullable
            @Override
            public PsiType[] getArgumentTypes() {
              return newTypes;
            }
          });
        }
      }

      return checkMethodApplicability(constructorResolveResult, checkUnknownArgs, info);
    }

    @Override
    public void visitConstructorInvocation(GrConstructorInvocation invocation) {
      GrConstructorInvocationInfo info = new GrConstructorInvocationInfo(invocation);
      checkConstructorCall(info);
      checkNamedArgumentsType(info);
    }

    @Override
    public void visitIndexProperty(GrIndexProperty expression) {
      checkIndexProperty(new GrIndexPropertyInfo(expression));
    }

    private void checkIndexProperty(CallInfo<? extends GrIndexProperty> info) {
      if (hasErrorElements(info.getArgumentList())) return;

      if (!checkCannotInferArgumentTypes(info)) return;

      final PsiType type = info.getQualifierInstanceType();
      final PsiType[] types = info.getArgumentTypes();

      if (checkSimpleArrayAccess(info, type, types)) return;

      final GroovyResolveResult[] results = info.multiResolve();
      final GroovyResolveResult resolveResult = info.advancedResolve();

      if (resolveResult.getElement() != null) {
        PsiElement resolved = resolveResult.getElement();

        if (resolved instanceof PsiMethod && !resolveResult.isInvokedOnProperty()) {
          checkMethodApplicability(resolveResult, true, info);
        }
        else if (resolved instanceof GrField) {
          checkCallApplicability(((GrField)resolved).getTypeGroovy(), true, info);
        }
        else if (resolved instanceof PsiField) {
          checkCallApplicability(((PsiField)resolved).getType(), true, info);
        }
      }
      else if (results.length > 0) {
        for (GroovyResolveResult result : results) {
          PsiElement resolved = result.getElement();
          if (resolved instanceof PsiMethod && !result.isInvokedOnProperty()) {
            if (!checkMethodApplicability(result, false, info)) return;
          }
          else if (resolved instanceof GrField) {
            if (!checkCallApplicability(((GrField)resolved).getTypeGroovy(), false, info)) return;
          }
          else if (resolved instanceof PsiField) {
            if (!checkCallApplicability(((PsiField)resolved).getType(), false, info)) return;
          }
        }

        registerError(info.getElementToHighlight(), GroovyBundle.message("method.call.is.ambiguous"));
      }
      else {
        final String typesString = buildArgTypesList(types);
        registerError(info.getElementToHighlight(), GroovyBundle.message("cannot.find.operator.overload.method", typesString));
      }
    }

    private static boolean checkSimpleArrayAccess(CallInfo<? extends GrIndexProperty> info, PsiType type, PsiType[] types) {
      if (!(type instanceof PsiArrayType)) return false;

      assert types != null;

      if (PsiUtil.isLValue(info.getCall())) {
        if (types.length == 2 &&
            TypesUtil.isAssignable(PsiType.INT, types[0], info.getCall()) &&
            TypesUtil.isAssignable(((PsiArrayType)type).getComponentType(), types[1], info.getCall())) {
          return true;
        }
      }
      else {
        if (types.length == 1 && TypesUtil.isAssignable(PsiType.INT, types[0], info.getCall())) {
          return true;
        }
      }

      return false;
    }

    private boolean checkCannotInferArgumentTypes(CallInfo info) {
      if (info.getArgumentTypes() != null) {
        return true;
      }
      else {
        highlightUnknownArgs(info);
        return false;
      }
    }

    @Override
    public void visitMethodCallExpression(GrMethodCallExpression methodCallExpression) {
      checkMethodCall(new GrMethodCallInfo(methodCallExpression));
    }

    @Override
    public void visitApplicationStatement(GrApplicationStatement applicationStatement) {
      checkMethodCall(new GrMethodCallInfo(applicationStatement));
    }

    @Override
    public void visitBinaryExpression(GrBinaryExpression binary) {
      checkOperator(new GrBinaryExprInfo(binary));
    }

    @Override
    public void visitEnumConstant(GrEnumConstant enumConstant) {
      GrEnumConstantInfo info = new GrEnumConstantInfo(enumConstant);
      checkConstructorCall(info);
      checkNamedArgumentsType(info);
    }

    private void checkNamedArgumentsType(CallInfo<?> info) {
      GroovyPsiElement rawCall = info.getCall();
      if (!(rawCall instanceof GrCall)) return;
      GrCall call = (GrCall)rawCall;

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

        PsiType expressionType = TypesUtil.boxPrimitiveType(namedArgumentExpression.getType(), call.getManager(), call.getResolveScope());
        if (expressionType == null) continue;

        if (!descriptor.checkType(expressionType, call)) {
          registerError(namedArgumentExpression, "Type of argument '" + labelName + "' can not be '" + expressionType.getPresentableText() + "'");
        }
      }
    }

    @Override
    public void visitParameterList(final GrParameterList parameterList) {
      PsiElement parent = parameterList.getParent();
      if (parent instanceof GrClosableBlock) {

        GrParameter[] parameters = parameterList.getParameters();
        if (parameters.length > 0) {
          List<PsiType[]> signatures = ClosureParamsEnhancer.findFittingSignatures((GrClosableBlock)parent);
          final List<PsiType> paramTypes = ContainerUtil.map(parameters, new Function<GrParameter, PsiType>() {
            @Override
            public PsiType fun(GrParameter parameter) {
              return parameter.getType();
            }
          });

          if (signatures.size() > 1) {
            PsiType[] fittingSignature = ContainerUtil.find(signatures, new Condition<PsiType[]>() {
              @Override
              public boolean value(PsiType[] types) {
                for (int i = 0; i < types.length; i++) {
                  if (!typesAreEqual(types[i], paramTypes.get(i), parameterList)) {
                    return false;
                  }
                }
                return true;
              }
            });

            if (fittingSignature == null) {
              registerError(parameterList, GroovyInspectionBundle.message("no.applicable.signature.found"));
            }
          }
          else if (signatures.size() == 1) {
            PsiType[] types = signatures.get(0);
            for (int i = 0; i < types.length; i++) {
              GrTypeElement typeElement = parameters[i].getTypeElementGroovy();
              if (typeElement == null) continue;
              PsiType expected = types[i];
              PsiType actual = paramTypes.get(i);
              if (!typesAreEqual(expected, actual, parameterList)) {
                registerError(typeElement, GroovyInspectionBundle.message("expected.type.0", expected.getPresentableText()));
              }
            }
          }
        }
      }
    }

    private static boolean typesAreEqual(@NotNull PsiType expected, @NotNull PsiType actual, @NotNull PsiElement context) {
      return TypesUtil.isAssignableByMethodCallConversion(expected, actual, context) &&
             TypesUtil.isAssignableByMethodCallConversion(actual, expected, context);
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

    private void checkOperator(CallInfo<? extends GrBinaryExpression> info) {
      if (hasErrorElements(info.getCall())) return;

      GroovyResolveResult[] results = info.multiResolve();
      GroovyResolveResult resolveResult = info.advancedResolve();

      if (isOperatorWithSimpleTypes(info.getCall(), resolveResult)) return;

      if (!checkCannotInferArgumentTypes(info)) return;

      if (resolveResult.getElement() != null) {
        checkMethodApplicability(resolveResult, true, info);
      }
      else if (results.length > 0) {
        for (GroovyResolveResult result : results) {
          if (!checkMethodApplicability(result, false, info)) return;
        }

        registerError(info.getElementToHighlight(), GroovyBundle.message("method.call.is.ambiguous"));
      }
    }

    private static boolean isOperatorWithSimpleTypes(GrBinaryExpression binary, GroovyResolveResult result) {
      if (result.getElement() != null && result.isApplicable()) {
        return false;
      }

      GrExpression left = binary.getLeftOperand();
      GrExpression right = binary.getRightOperand();

      PsiType ltype = left.getType();
      PsiType rtype = right != null ? right.getType() : null;

      return TypesUtil.isNumericType(ltype) && (rtype == null || TypesUtil.isNumericType(rtype));
    }

    private void checkMethodCall(CallInfo<? extends GrMethodCall> info) {
      if (hasErrorElements(info.getArgumentList())) return;

      if (info.getInvokedExpression() instanceof GrReferenceExpression) {
        final GrReferenceExpression referenceExpression = (GrReferenceExpression)info.getInvokedExpression();
        GroovyResolveResult resolveResult = info.advancedResolve();
        GroovyResolveResult[] results = info.multiResolve();

        PsiElement resolved = resolveResult.getElement();
        if (resolved == null) {
          GrExpression qualifier = referenceExpression.getQualifierExpression();
          if (qualifier == null && GrHighlightUtil.isDeclarationAssignment(referenceExpression)) return;
        }

        if (!checkCannotInferArgumentTypes(info)) return;

        final PsiType type = referenceExpression.getType();
        if (resolved != null) {
          if (resolved instanceof PsiMethod && !resolveResult.isInvokedOnProperty()) {
            checkMethodApplicability(resolveResult, true, info);
          }
          else {
            checkCallApplicability(type, true, info);
          }
        }
        else if (results.length > 0) {
          for (GroovyResolveResult result : results) {
            PsiElement current = result.getElement();
            if (current instanceof PsiMethod && !result.isInvokedOnProperty()) {
              if (!checkMethodApplicability(result, false, info)) return;
            }
            else {
              if (!checkCallApplicability(type, false, info)) return;
            }
          }

          registerError(info.getElementToHighlight(), GroovyBundle.message("method.call.is.ambiguous"));
        }
      }
      else if (info.getInvokedExpression() != null) { //it checks in visitRefExpr(...)
        final PsiType type = info.getInvokedExpression().getType();
        checkCallApplicability(type, true, info);
      }

      checkNamedArgumentsType(info);
    }

    private void highlightInapplicableMethodUsage(@NotNull GroovyResolveResult methodResolveResult,
                                                  @NotNull CallInfo info,
                                                  @NotNull PsiMethod method) {
      final PsiClass containingClass =
        method instanceof GrGdkMethod ? ((GrGdkMethod)method).getStaticMethod().getContainingClass() : method.getContainingClass();

      PsiType[] argumentTypes = info.getArgumentTypes();
      if (containingClass == null) {
        registerCannotApplyError(method.getName(), info);
        return;
      }
      final String typesString = buildArgTypesList(argumentTypes);
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(method.getProject());
      final PsiClassType containingType = factory.createType(containingClass, methodResolveResult.getSubstitutor());
      final String canonicalText = containingType.getInternalCanonicalText();
      String message = method.isConstructor() ? GroovyBundle.message("cannot.apply.constructor", method.getName(), canonicalText, typesString)
                                              : GroovyBundle.message("cannot.apply.method1", method.getName(), canonicalText, typesString);

      registerError(info.getElementToHighlight(), message,
                    genCastFixes(GrClosureSignatureUtil.createSignature(methodResolveResult), argumentTypes, info.getArgumentList()),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    }

    private static LocalQuickFix[] genCastFixes(GrSignature signature, PsiType[] argumentTypes, @Nullable GrArgumentList argumentList) {
      if (argumentList == null) return LocalQuickFix.EMPTY_ARRAY;
      final List<GrExpression> args = getExpressionArgumentsOfCall(argumentList);

      if (args == null) {
        return LocalQuickFix.EMPTY_ARRAY;
      }

      final List<GrClosureSignature> signatures = GrClosureSignatureUtil.generateSimpleSignatures(signature);

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
        fixes.add(new ParameterCastFix(error.first, error.second, args.get(error.first)));
      }

      return fixes.toArray(new LocalQuickFix[fixes.size()]);
    }

    private boolean checkCallApplicability(PsiType type, boolean checkUnknownArgs, CallInfo info) {

      PsiType[] argumentTypes = info.getArgumentTypes();
      GrExpression invoked = info.getInvokedExpression();
      if (invoked == null) return true;

      if (type instanceof GrClosureType) {
        if (argumentTypes == null) return true;

        GrClosureSignatureUtil.ApplicabilityResult result = PsiUtil.isApplicableConcrete(argumentTypes, (GrClosureType)type, info.getCall());
        switch (result) {
          case inapplicable:
            registerCannotApplyError(invoked.getText(), info);
            return false;
          case canBeApplicable:
            if (checkUnknownArgs) {
              highlightUnknownArgs(info);
            }
            return !checkUnknownArgs;
          default:
            return true;
        }
      }
      else if (type != null) {
        final GroovyResolveResult[] calls = ResolveUtil.getMethodCandidates(type, "call", invoked, argumentTypes);
        for (GroovyResolveResult result : calls) {
          PsiElement resolved = result.getElement();
          if (resolved instanceof PsiMethod && !result.isInvokedOnProperty()) {
            if (!checkMethodApplicability(result, checkUnknownArgs, info)) return false;
          }
          else if (resolved instanceof PsiField) {
            if (!checkCallApplicability(((PsiField)resolved).getType(), checkUnknownArgs && calls.length == 1, info)) return false;
          }
        }
        if (calls.length == 0 && !(invoked instanceof GrString)) {
          registerCannotApplyError(invoked.getText(), info);
        }
        return true;
      }
      return true;
    }

    private void registerCannotApplyError(String invokedText, CallInfo info) {
      final String typesString = buildArgTypesList(info.getArgumentTypes());
      registerError(info.getElementToHighlight(), GroovyBundle.message("cannot.apply.method.or.closure", invokedText, typesString));
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
                                             boolean checkUnknownArgs,
                                             @NotNull CallInfo info) {
      final PsiElement element = methodResolveResult.getElement();
      if (!(element instanceof PsiMethod)) return true;
      if (element instanceof GrBuilderMethod) return true;

      final PsiMethod method = (PsiMethod)element;
      if ("call".equals(method.getName()) && info.getInvokedExpression() instanceof GrReferenceExpression) {
        final GrExpression qualifierExpression = ((GrReferenceExpression)info.getInvokedExpression()).getQualifierExpression();
        if (qualifierExpression != null) {
          final PsiType type = qualifierExpression.getType();
          if (type instanceof GrClosureType) {
            GrClosureSignatureUtil.ApplicabilityResult result = PsiUtil.isApplicableConcrete(info.getArgumentTypes(), (GrClosureType)type, info.getInvokedExpression());
            switch (result) {
              case inapplicable:
                highlightInapplicableMethodUsage(methodResolveResult, info, method);
                return false;
              case canBeApplicable:
                if (checkUnknownArgs) {
                  highlightUnknownArgs(info);
                }
                return !checkUnknownArgs;
              default:
                return true;
            }
          }
        }
      }

      if (method instanceof GrGdkMethod && info.getInvokedExpression() instanceof GrReferenceExpression) {
        final PsiMethod staticMethod = ((GrGdkMethod)method).getStaticMethod();
        final PsiType qualifierType = info.getQualifierInstanceType();

        GrReferenceExpression invoked = (GrReferenceExpression)info.getInvokedExpression();
        final GrExpression qualifier = PsiImplUtil.getRuntimeQualifier(invoked);

        //check methods processed by @Category(ClassWhichProcessMethod) annotation
        if (qualifierType != null &&
            !GdkMethodUtil.isCategoryMethod(staticMethod, qualifierType, qualifier, methodResolveResult.getSubstitutor()) &&
            !checkCategoryQualifier(invoked, qualifier, staticMethod, methodResolveResult.getSubstitutor())) {
          registerError(info.getHighlightElementForCategoryQualifier(), GroovyInspectionBundle
            .message("category.method.0.cannot.be.applied.to.1", method.getName(), qualifierType.getCanonicalText()));
          return false;
        }
      }

      if (info.getArgumentTypes() == null) return true;

      GrClosureSignatureUtil.ApplicabilityResult applicable = PsiUtil.isApplicableConcrete(info.getArgumentTypes(), method, methodResolveResult.getSubstitutor(), info.getCall(), false);
      switch (applicable) {
        case inapplicable:
          highlightInapplicableMethodUsage(methodResolveResult, info, method);
          return false;
        case canBeApplicable:
          if (checkUnknownArgs) {
            highlightUnknownArgs(info);
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

    private void highlightUnknownArgs(@NotNull CallInfo info) {
      registerError(info.getElementToHighlight(), GroovyBundle.message("cannot.infer.argument.types"), LocalQuickFix.EMPTY_ARRAY, ProblemHighlightType.WEAK_WARNING);
    }

    @Override
    public void visitElement(GroovyPsiElement element) {
      //do nothing
    }
  }

  @Nullable
  private static List<GrExpression> getExpressionArgumentsOfCall(@NotNull GrArgumentList argumentList) {
    final GrExpression[] argArray = argumentList.getExpressionArguments();
    final ArrayList<GrExpression> args = ContainerUtil.newArrayList();

    for (GrExpression arg : argArray) {
      if (arg instanceof GrSpreadArgument) {
        GrExpression spreaded = ((GrSpreadArgument)arg).getArgument();
        if (spreaded instanceof GrListOrMap && !((GrListOrMap)spreaded).isMap()) {
          Collections.addAll(args, ((GrListOrMap)spreaded).getInitializers());
        }
        else {
          return null;
        }
      }
      else {
        args.add(arg);
      }
    }

    final PsiElement parent = argumentList.getParent();
    if (parent instanceof GrIndexProperty && PsiUtil.isLValue((GroovyPsiElement)parent)) {
      args.add(TypeInferenceHelper.getInitializerFor((GrExpression)parent));
    }
    else if (parent instanceof GrMethodCallExpression) {
      ContainerUtil.addAll(args, ((GrMethodCallExpression)parent).getClosureArguments());
    }
    return args;
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

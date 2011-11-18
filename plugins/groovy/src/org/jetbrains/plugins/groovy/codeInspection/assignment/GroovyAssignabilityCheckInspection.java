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

package org.jetbrains.plugins.groovy.codeInspection.assignment;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.GroovyAnnotator;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.extensions.GroovyNamedArgumentProvider;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.findUsages.LiteralConstructorReference;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.*;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyConstantExpressionEvaluator;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.Map;

/**
 * @author Maxim.Medvedev
 */
public class GroovyAssignabilityCheckInspection extends BaseInspection {
  private static final Logger LOG = Logger.getInstance(GroovyAssignabilityCheckInspection.class);

  @Override
  protected GroovyFix buildFix(PsiElement location) {
    return super.buildFix(location);    //To change body of overridden methods use File | Settings | File Templates.
  }

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

  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new MyVisitor();
  }

  private static class MyVisitor extends BaseInspectionVisitor {
    private void checkAssignability(@NotNull PsiType expectedType, @NotNull GrExpression expression, GroovyPsiElement element) {
      if (PsiUtil.isRawClassMemberAccess(expression)) return; //GRVY-2197
      if (checkForImplicitEnumAssigning(expectedType, expression, element)) return;
      final PsiType rType = expression.getType();
      if (rType == null || rType == PsiType.VOID) return;

      if (!TypesUtil.isAssignable(expectedType, rType, element)) {
        final LocalQuickFix[] fixes = {new GrCastFix(expression, expectedType)};
        final String message = GroovyBundle.message("cannot.assign", rType.getPresentableText(), expectedType.getPresentableText());
        registerError(element, message, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
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
    public void visitMethod(GrMethod method) {
      super.visitMethod(method);
      final GrOpenBlock block = method.getBlock();
      if (block == null) return;
      final PsiType expectedType = method.getReturnType();
      if (expectedType == null || PsiType.VOID.equals(expectedType)) return;

      ControlFlowUtils.visitAllExitPoints(block, new ControlFlowUtils.ExitPointVisitor() {
        @Override
        public boolean visitExitPoint(Instruction instruction, @Nullable GrExpression returnValue) {
          if (returnValue != null &&
              !(returnValue.getParent() instanceof GrReturnStatement) &&
              !isNewInstanceInitialingByTuple(returnValue)) {
            checkAssignability(expectedType, returnValue, returnValue);
          }
          return true;
        }
      });
    }

    @Override
    public void visitReturnStatement(GrReturnStatement returnStatement) {
      super.visitReturnStatement(returnStatement);

      final GrMethod method = PsiTreeUtil.getParentOfType(returnStatement, GrMethod.class, true, GrClosableBlock.class);
      if (method == null) return;

      final GrExpression value = returnStatement.getReturnValue();
      if (isNewInstanceInitialingByTuple(value)) return;
      
      final PsiType expectedType = method.getReturnType();
      if (value == null || expectedType == null) return;
      checkAssignability(expectedType, value, returnStatement);
    }

    @Override
    public void visitAssignmentExpression(GrAssignmentExpression assignment) {
      super.visitAssignmentExpression(assignment);

      GrExpression lValue = assignment.getLValue();
      if (lValue instanceof GrIndexProperty) return;
      if (!PsiUtil.mightBeLValue(lValue)) return;

      IElementType opToken = assignment.getOperationToken();
      if (opToken != GroovyTokenTypes.mASSIGN) return;

      GrExpression rValue = assignment.getRValue();
      if (rValue == null) return;

      PsiType lType = lValue.getNominalType();
      PsiType rType = rValue.getType();
      // For assignments with spread dot
      if (isListAssignment(lValue) && lType != null && lType instanceof PsiClassType) {
        final PsiClassType pct = (PsiClassType)lType;
        final PsiClass clazz = pct.resolve();
        if (clazz != null && CommonClassNames.JAVA_UTIL_LIST.equals(clazz.getQualifiedName())) {
          final PsiType[] types = pct.getParameters();
          if (types.length == 1 && types[0] != null && rType != null) {
            checkAssignability(types[0], rValue, rValue);
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
        checkAssignability(lType, rValue, rValue);
      }
    }

    @Override
    public void visitVariable(GrVariable variable) {
      super.visitVariable(variable);

      PsiType varType = variable.getType();
      GrExpression initializer = variable.getInitializerGroovy();
      if (initializer == null) return;

      PsiType rType = initializer.getType();
      if (rType == null) return;
      if (isNewInstanceInitialingByTuple(initializer)) {
        return;
      }

      checkAssignability(varType, initializer, initializer);
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
      if (checkCannotInferArgumentTypes(refElement)) return;
      final GroovyResolveResult constructorResolveResult = constructorCall.resolveConstructorGenerics();
      final PsiElement constructor = constructorResolveResult.getElement();

      if (constructor != null) {
        checkConstructorApplicability(constructorResolveResult, refElement);
      }
      else {
        final GroovyResolveResult[] results = constructorCall.multiResolveConstructor();
        if (results.length > 0) {
          for (GroovyResolveResult result : results) {
            PsiElement resolved = result.getElement();
            if (resolved instanceof PsiMethod) {
              if (!checkConstructorApplicability(result, refElement)) return;
            }
          }

          registerError(getElementToHighlight(refElement, argList), GroovyBundle.message("constructor.call.is.ambiguous"));
        }
        else {
          final GrExpression[] expressionArguments = constructorCall.getExpressionArguments();
          final GrClosableBlock[] closureArguments = constructorCall.getClosureArguments();
          if (expressionArguments.length + closureArguments.length > 0) {
            final GroovyResolveResult[] resolveResults = constructorCall.multiResolveClass();
            if (resolveResults.length == 1) {
              final PsiElement element = resolveResults[0].getElement();
              if (element instanceof PsiClass) {
                registerError(getElementToHighlight(refElement, argList),
                              GroovyBundle.message("cannot.apply.default.constructor", ((PsiClass)element).getName()));
              }
            }
          }
        }
      }

      checkNamedArgumentsType(constructorCall);
    }

    private static PsiElement getElementToHighlight(PsiElement refElement, GrArgumentList argList) {
      PsiElement elementToHighlight = argList;
      if (elementToHighlight == null || elementToHighlight.getTextLength() == 0) elementToHighlight = refElement;
      return elementToHighlight;
    }

    @Override
    public void visitListOrMap(GrListOrMap listOrMap) {
      super.visitListOrMap(listOrMap);

      final PsiReference reference = listOrMap.getReference();
      if (!(reference instanceof LiteralConstructorReference)) return;

      final GroovyResolveResult[] results = ((LiteralConstructorReference)reference).multiResolve(false);
      LOG.assertTrue(results.length > 0);

      if (results.length == 1) {
        final GroovyResolveResult result = results[0];
        final PsiElement element = result.getElement();
        if (element instanceof PsiClass) {
          if (!listOrMap.isMap()) {
            registerError(listOrMap, GroovyBundle.message("cannot.apply.default.constructor", ((PsiClass)element).getName()));
          }
        }
        else if (element instanceof PsiMethod && ((PsiMethod)element).isConstructor()){
          checkLiteralConstructorApplicability(result, listOrMap);
        }
      }
      else {
        for (GroovyResolveResult result : results) {
          PsiElement resolved = result.getElement();
          if (resolved instanceof PsiMethod) {
            if (!checkLiteralConstructorApplicability(result, listOrMap)) return;
          }
          registerError(listOrMap, GroovyBundle.message("constructor.call.is.ambiguous"));
        }
      }
    }

    private boolean checkLiteralConstructorApplicability(GroovyResolveResult result, GrListOrMap listOrMap) {
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

      PsiType[] argumentTypes = PsiUtil.getArgumentTypes(namedArgs, exprArgs, GrClosableBlock.EMPTY_ARRAY, false, null);
      if (listOrMap.isMap() && namedArgs.length == 0) {
        argumentTypes = new PsiType[]{listOrMap.getType()};
      }

      if (PsiUtil.isApplicable(argumentTypes, constructor, result.getSubstitutor(), listOrMap, false)) {
        return true;
      }

      highlightInapplicableMethodUsage(result, listOrMap, constructor, argumentTypes);
      return false;
    }

    private  boolean checkConstructorApplicability(GroovyResolveResult constructorResolveResult, GroovyPsiElement place) {
      final PsiElement element = constructorResolveResult.getElement();
      LOG.assertTrue(element instanceof PsiMethod && ((PsiMethod)element).isConstructor());
      final PsiMethod constructor = (PsiMethod)element;

      final GrArgumentList argList = PsiUtil.getArgumentsList(place);
      if (argList != null) {
        final GrExpression[] exprArgs = argList.getExpressionArguments();

        if (exprArgs.length == 0 &&  !PsiUtil.isConstructorHasRequiredParameters(constructor)) return true;
      }
      return checkMethodApplicability(constructorResolveResult, place);
    }

    @Override
    public void visitConstructorInvocation(GrConstructorInvocation invocation) {
      super.visitConstructorInvocation(invocation);
      checkConstructorCall(invocation, invocation.getThisOrSuperKeyword());
    }

    @Override
    public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
      super.visitReferenceExpression(referenceExpression);
      GroovyResolveResult resolveResult = referenceExpression.advancedResolve();
      GroovyResolveResult[] results = referenceExpression.multiResolve(false); //cached

      PsiElement resolved = resolveResult.getElement();
      final PsiElement parent = referenceExpression.getParent();
      if (resolved == null) {
        GrExpression qualifier = referenceExpression.getQualifierExpression();
        if (qualifier == null && GroovyAnnotator.isDeclarationAssignment(referenceExpression)) return;
      }

      if (parent instanceof GrCall) {
        if (checkCannotInferArgumentTypes(referenceExpression)) return;

        final PsiType type = referenceExpression.getType();
        if (resolved != null ) {
          if (resolved instanceof PsiMethod && !resolveResult.isInvokedOnProperty()) {
            checkMethodApplicability(resolveResult, referenceExpression);
          }
          else {
            checkCallApplicability(type, referenceExpression);
          }

        }
        else if (results.length > 0) {
          for (GroovyResolveResult result : results) {
            resolved = result.getElement();
            if (resolved instanceof PsiMethod && !resolveResult.isInvokedOnProperty()) {
              if (!checkMethodApplicability(result, referenceExpression)) return;
            }
            else {
              if (!checkCallApplicability(type, referenceExpression)) return;
            }
          }

          registerError(getElementToHighlight(referenceExpression, PsiUtil.getArgumentsList(referenceExpression)),
                        GroovyBundle.message("method.call.is.ambiguous"));
        }
      }
    }

    private boolean checkCannotInferArgumentTypes(PsiElement referenceExpression) {
      if (PsiUtil.getArgumentTypes(referenceExpression, true) != null) return false;

      registerError(getElementToHighlight(referenceExpression, PsiUtil.getArgumentsList(referenceExpression)),
                    GroovyBundle.message("cannot.infer.argument.types"), LocalQuickFix.EMPTY_ARRAY, ProblemHighlightType.WEAK_WARNING);
      return true;
    }

    @Override
    public void visitMethodCallExpression(GrMethodCallExpression methodCallExpression) {
      super.visitMethodCallExpression(methodCallExpression);
      checkMethodCall(methodCallExpression);
    }

    @Override
    public void visitApplicationStatement(GrApplicationStatement applicationStatement) {
      super.visitApplicationStatement(applicationStatement);
      checkMethodCall(applicationStatement);
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

      for (GrNamedArgument namedArgument : namedArguments) {
        String labelName = namedArgument.getLabelName();

        NamedArgumentDescriptor descriptor = map.get(labelName);

        if (descriptor == null) continue;

        GrExpression namedArgumentExpression = namedArgument.getExpression();
        if (namedArgumentExpression == null) continue;

        if (PsiUtil.isRawClassMemberAccess(namedArgumentExpression)) continue; //GRVY-2197

        PsiType expressionType = namedArgumentExpression.getType();
        if (expressionType == null) continue;

        expressionType = TypesUtil.boxPrimitiveType(expressionType, call.getManager(), call.getResolveScope());

        if (!descriptor.checkType(expressionType, call)) {
          registerError(namedArgumentExpression, "Type of argument '" + labelName + "' can not be '" + expressionType.getPresentableText() + "'");
        }
      }
    }

    private void checkMethodCall(GrMethodCall call) {
      final GrExpression expression = call.getInvokedExpression();
      if (!(expression instanceof GrReferenceExpression)) { //it checks in visitRefExpr(...)
        final PsiType type = expression.getType();
        checkCallApplicability(type, expression);
      }

      checkNamedArgumentsType(call);
    }

    private void highlightInapplicableMethodUsage(GroovyResolveResult methodResolveResult,
                                                  PsiElement place,
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
      registerError(getElementToHighlight(place, PsiUtil.getArgumentsList(place)), message);
    }


    private boolean checkCallApplicability(PsiType type, GroovyPsiElement invokedExpr) {

      PsiType[] argumentTypes = PsiUtil.getArgumentTypes(invokedExpr, true);
      if (type instanceof GrClosureType) {
        if (argumentTypes == null) return true;

        if (PsiUtil.isApplicable(argumentTypes, (GrClosureType)type, invokedExpr)) return true;

        registerCannotApplyError(invokedExpr, argumentTypes, invokedExpr.getText());
        return false;
      }
      else if (type != null) {
        final GroovyResolveResult[] calls = ResolveUtil.getMethodCandidates(type, "call", invokedExpr, argumentTypes);
        for (GroovyResolveResult result : calls) {
          PsiElement resolved = result.getElement();
          if (resolved instanceof PsiMethod && !result.isInvokedOnProperty()) {
            if (!checkMethodApplicability(result, invokedExpr)) return false;
          }
          else if (resolved instanceof PsiField) {
            if (!checkCallApplicability(((PsiField)resolved).getType(), invokedExpr)) return false;
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

    private boolean checkMethodApplicability(GroovyResolveResult methodResolveResult, GroovyPsiElement place) {
      final PsiElement element = methodResolveResult.getElement();
      if (!(element instanceof PsiMethod)) return true;
      if (element instanceof GrBuilderMethod) return true;

      final PsiMethod method = (PsiMethod)element;
      PsiType[] argumentTypes = PsiUtil.getArgumentTypes(place, true);
      if ("call".equals(method.getName()) && place instanceof GrReferenceExpression) {
        final GrExpression qualifierExpression = ((GrReferenceExpression)place).getQualifierExpression();
        if (qualifierExpression != null) {
          final PsiType type = qualifierExpression.getType();
          if (type instanceof GrClosureType) {
            if (!PsiUtil.isApplicable(argumentTypes, (GrClosureType)type, place)) {
              highlightInapplicableMethodUsage(methodResolveResult, place, method, argumentTypes);
              return false;
            }
          }
        }
      }
      if (argumentTypes != null &&
          !PsiUtil.isApplicable(argumentTypes, method, methodResolveResult.getSubstitutor(), place, false)) {

        //check for implicit use of property getter which returns closure
        if (GroovyPropertyUtils.isSimplePropertyGetter(method)) {
          if (method instanceof GrMethod || method instanceof GrAccessorMethod) {
            final PsiType returnType = PsiUtil.getSmartReturnType(method);
            if (returnType instanceof GrClosureType) {
              if (PsiUtil.isApplicable(argumentTypes, ((GrClosureType)returnType), place)) {
                return true;
              }
            }
          }

          PsiType returnType = method.getReturnType();
          if (returnType != null) {
            if (TypesUtil.isAssignable(TypesUtil.createType(GroovyCommonClassNames.GROOVY_LANG_CLOSURE, element), returnType, place)) {
              return true;
            }
          }
        }

        highlightInapplicableMethodUsage(methodResolveResult, place, method, argumentTypes);
        return false;
      }
      return true;
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
        final GrExpression qual = expression.getQualifierExpression();
        if (qual != null) return isListAssignment(qual);
      }
    }
    return false;
  }
}

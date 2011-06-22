/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.extensions.GroovyNamedArgumentProvider;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrBuilderMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.Map;

/**
 * @author Maxim.Medvedev
 */
public class GroovyAssignabilityCheckInspection extends BaseInspection {
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
      final PsiType rType = expression.getType();
      if (rType == null || rType == PsiType.VOID) return;
      if (!TypesUtil.isAssignable(expectedType, rType, element)) {
        registerError(element, GroovyBundle.message("cannot.assign", rType.getPresentableText(), expectedType.getPresentableText()));
      }
    }

    //isApplicable last expression on method body
    @Override
    public void visitOpenBlock(GrOpenBlock block) {
      super.visitOpenBlock(block);
      final PsiElement element = block.getParent();
      if (!(element instanceof GrMethod)) return;
      GrMethod method = (GrMethod)element;
      final PsiType expectedType = method.getReturnType();
      if (expectedType == null || PsiType.VOID.equals(expectedType)) return;

      ControlFlowUtils.visitAllExitPoints(block, new ControlFlowUtils.ExitPointVisitor() {
        @Override
        public boolean visitExitPoint(Instruction instruction, @Nullable GrExpression returnValue) {
          if (returnValue != null && !(returnValue.getParent() instanceof GrReturnStatement)) {
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
      final PsiType expectedType = method.getReturnType();
      if (value == null || expectedType == null) return;
      checkAssignability(expectedType, value, returnStatement);
    }

    @Override
    public void visitAssignmentExpression(GrAssignmentExpression assignment) {
      super.visitAssignmentExpression(assignment);

      GrExpression lValue = assignment.getLValue();
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
      if (lValue instanceof GrReferenceExpression &&
          ((GrReferenceExpression)lValue).resolve() instanceof GrReferenceExpression) { //lvalue is not-declared variable
        return;
      }
      if (lValue instanceof GrIndexProperty) return;
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

      checkAssignability(varType, initializer, initializer);
    }

    @Override
    public void visitNewExpression(GrNewExpression newExpression) {
      super.visitNewExpression(newExpression);
      if (newExpression.getArrayCount() > 0) return;

      GrCodeReferenceElement refElement = newExpression.getReferenceElement();
      if (refElement == null) return;

      final GrArgumentList argList = newExpression.getArgumentList();

      final GroovyResolveResult constructorResolveResult = newExpression.resolveConstructorGenerics();
      final PsiElement constructor = constructorResolveResult.getElement();
      if (constructor != null) {
        if (argList == null ||
            argList.getExpressionArguments().length != 0 ||
            ((PsiMethod)constructor).getParameterList().getParametersCount() != 0) {
          checkMethodApplicability(constructorResolveResult, refElement);
        }
      }
      else {
        final GroovyResolveResult[] results = newExpression.multiResolveConstructor();
        if (results.length > 0) {
          for (GroovyResolveResult result : results) {
            PsiElement resolved = result.getElement();
            if (resolved instanceof PsiMethod) {
              if (!checkMethodApplicability(result, refElement)) return;
            }
          }

          String message = GroovyBundle.message("method.call.is.ambiguous");
          PsiElement elementToHighlight = argList;
          if (elementToHighlight == null || elementToHighlight.getTextLength() == 0) elementToHighlight = refElement;
          registerError(elementToHighlight, message);
        }
      }

      checkNamedArgumentsType(newExpression);
    }

    @Override
    public void visitConstructorInvocation(GrConstructorInvocation invocation) {
      super.visitConstructorInvocation(invocation);
      final GroovyResolveResult resolveResult = invocation.resolveConstructorGenerics();
      if (resolveResult.getElement() != null) {
        checkMethodApplicability(resolveResult, invocation);
      }
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
        final PsiType type = referenceExpression.getType();
        if (resolved != null ) {
          if (resolved instanceof PsiMethod) {
            checkMethodApplicability(resolveResult, referenceExpression);
          }
          else {
            checkCallApplicability(type, referenceExpression);
          }

        }
        else if (results.length > 0) {
          for (GroovyResolveResult result : results) {
            resolved = result.getElement();
            if (resolved instanceof PsiMethod) {
              if (!checkMethodApplicability(result, referenceExpression)) return;
            }
            else {
              if (!checkCallApplicability(type, referenceExpression)) return;
            }
          }

          String message = GroovyBundle.message("method.call.is.ambiguous");
          PsiElement elementToHighlight = PsiUtil.getArgumentsList(referenceExpression);
          if (elementToHighlight == null || elementToHighlight.getTextLength() == 0) elementToHighlight = referenceExpression;
          registerError(elementToHighlight, message);
        }
      }
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

    private void checkNamedArgumentsType(GrCall call) {
      GrNamedArgument[] namedArguments = PsiUtil.getFirstMapNamedArguments(call);

      if (namedArguments.length == 0) return;

      Map<String, GroovyNamedArgumentProvider.ArgumentDescriptor> map = GroovyNamedArgumentProvider.getNamedArgumentsFromAllProviders(call, null, false);

      for (GrNamedArgument namedArgument : namedArguments) {
        String labelName = namedArgument.getLabelName();

        GroovyNamedArgumentProvider.ArgumentDescriptor descriptor = map.get(labelName);

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
      String message = GroovyBundle.message("cannot.apply.method1", method.getName(), canonicalText, typesString);
      PsiElement elementToHighlight = PsiUtil.getArgumentsList(place);
      if (elementToHighlight == null || elementToHighlight.getTextRange().getLength() == 0) {
        elementToHighlight = place;
      }
      registerError(elementToHighlight, message);
    }


    private boolean checkCallApplicability(PsiType type, GroovyPsiElement place) {

      PsiType[] argumentTypes = PsiUtil.getArgumentTypes(place, true);
      if (type instanceof GrClosureType) {
        if (argumentTypes == null) return true;

        if (PsiUtil.isApplicable(argumentTypes, (GrClosureType)type, place)) return true;

        registerCannotApplyError(place, argumentTypes, place.getText());
        return false;
      }
      else if (type != null) {
        final GroovyResolveResult[] calls = ResolveUtil.getMethodCandidates(type, "call", place, argumentTypes);
        for (GroovyResolveResult result : calls) {
          PsiElement resolved = result.getElement();
          if (resolved instanceof PsiMethod) {
            if (!checkMethodApplicability(result, place)) return false;
          }
          else if (resolved instanceof PsiField) {
            if (!checkCallApplicability(((PsiField)resolved).getType(), place)) return false;
          }
        }
        if (calls.length == 0) {
          registerCannotApplyError(place, argumentTypes, place.getText());
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
          !PsiUtil.isApplicable(argumentTypes, method, methodResolveResult.getSubstitutor(),
                                ResolveUtil.isInUseScope(methodResolveResult), place, false)) {

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

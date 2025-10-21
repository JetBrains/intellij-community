// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.type;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.FileTypeInspectionDisablerKt;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GrCastFix;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GrChangeVariableType;
import org.jetbrains.plugins.groovy.codeInspection.type.highlighting.*;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.extensions.GroovyNamedArgumentProvider;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentUtilKt;
import org.jetbrains.plugins.groovy.highlighting.HighlightSink;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GrArrayInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrThrowStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.ClosureParameterEnhancer;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.ClosureParamsEnhancer;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter.Position;
import org.jetbrains.plugins.groovy.lang.psi.util.*;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyCallReference;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyConstructorReference;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCallReference;
import org.jetbrains.plugins.groovy.lang.typing.MultiAssignmentTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.psi.util.PsiUtil.extractIterableTypeParameter;
import static org.jetbrains.plugins.groovy.codeInspection.type.GroovyTypeCheckVisitorHelper.*;
import static org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils.isImplicitReturnStatement;
import static org.jetbrains.plugins.groovy.lang.psi.util.PsiUtilKt.isFake;
import static org.jetbrains.plugins.groovy.lang.typing.TuplesKt.getMultiAssignmentTypes;

public class GroovyTypeCheckVisitor extends BaseInspectionVisitor {

  private final HighlightSink myHighlightSink = new HighlightSink() {
    @Override
    public void registerProblem(@NotNull PsiElement highlightElement,
                                @NotNull ProblemHighlightType highlightType,
                                @NotNull String message,
                                @NotNull LocalQuickFix @NotNull ... fixes) {
      GroovyTypeCheckVisitor.this.registerError(highlightElement, message, fixes, highlightType);
    }
  };

  private boolean checkForImplicitEnumAssigning(@Nullable PsiType expectedType,
                                                @NotNull GrExpression expression,
                                                @NotNull GroovyPsiElement elementToHighlight) {
    if (!(expectedType instanceof PsiClassType)) return false;

    if (!GroovyConfigUtils.getInstance().isVersionAtLeast(elementToHighlight, GroovyConfigUtils.GROOVY1_8)) return false;

    final PsiClass resolved = ((PsiClassType)expectedType).resolve();
    if (resolved == null || !resolved.isEnum()) return false;

    final PsiType type = expression.getType();
    if (type == null) return false;

    if (!type.equalsToText(GroovyCommonClassNames.GROOVY_LANG_GSTRING) &&
        !type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
      return false;
    }

    final Object result = GroovyConstantExpressionEvaluator.evaluate(expression);
    if (!(result instanceof String)) {
      registerError(
        elementToHighlight,
        ProblemHighlightType.WEAK_WARNING,
        GroovyBundle.message("cannot.assign.string.to.enum.0", expectedType.getPresentableText())
      );
    }
    else {
      final PsiField field = resolved.findFieldByName((String)result, true);
      if (!(field instanceof PsiEnumConstant)) {
        registerError(
          elementToHighlight,
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
          GroovyBundle.message("cannot.find.enum.constant.0.in.enum.1", result, expectedType.getPresentableText())
        );
      }
    }
    return true;
  }

  @Override
  public void visitMethodCall(@NotNull GrMethodCall call) {
    super.visitMethodCall(call); // visitExpression

    if (isFake(call)) return;
    GrArgumentList argumentList = call.getArgumentList();
    if (hasErrorElements(argumentList)) return;

    GroovyMethodCallReference callReference = call.getCallReference();
    if (callReference == null) {
      return;
    }

    PsiElement highlightElement;
    if (argumentList.getTextLength() == 0) {
      highlightElement = call;
    }
    else {
      highlightElement = argumentList;
    }

    CallReferenceHighlighter highlighter = new MethodCallReferenceHighlighter(callReference, highlightElement, myHighlightSink);
    if (highlighter.highlightMethodApplicability()) {
      return;
    }
    checkNamedArgumentsType(call);
  }

  private void checkNamedArgumentsType(@NotNull GrCall call) {
    GrNamedArgument[] namedArguments = PsiUtil.getFirstMapNamedArguments(call);

    if (namedArguments.length == 0) return;

    Map<String, NamedArgumentDescriptor> map = GroovyNamedArgumentProvider.getNamedArgumentsFromAllProviders(call, null, false);
    if (map == null) return;

    checkNamedArguments(call, namedArguments, map);
  }

  private void checkNamedArguments(GroovyPsiElement context, GrNamedArgument[] namedArguments, Map<String, NamedArgumentDescriptor> map) {
    for (GrNamedArgument namedArgument : namedArguments) {
      String labelName = namedArgument.getLabelName();

      NamedArgumentDescriptor descriptor = map.get(labelName);

      if (descriptor == null) continue;

      GrExpression namedArgumentExpression = namedArgument.getExpression();
      if (namedArgumentExpression == null) continue;

      if (hasTupleInitializer(namedArgumentExpression)) continue;

      if (PsiUtil.isRawClassMemberAccess(namedArgumentExpression)) continue;

      PsiType expressionType =
        TypesUtil.boxPrimitiveType(namedArgumentExpression.getType(), context.getManager(), context.getResolveScope());
      if (expressionType == null) continue;

      if (!descriptor.checkType(expressionType, context)) {
        registerError(
          namedArgumentExpression,
          ProblemHighlightType.GENERIC_ERROR,
          GroovyBundle.message("inspection.message.type.argument.0.can.not.be.1", labelName,expressionType.getPresentableText())
        );
      }
    }
  }

  private void processAssignment(@NotNull PsiType expectedType,
                                 @NotNull GrExpression expression,
                                 @NotNull PsiElement toHighlight,
                                 @NotNull PsiElement context) {
    checkPossibleLooseOfPrecision(expectedType, expression, toHighlight);

    processAssignment(expectedType, expression, toHighlight, "cannot.assign", context, Position.ASSIGNMENT);
  }

  private void processAssignment(@NotNull PsiType expectedType,
                                 @NotNull GrExpression expression,
                                 @NotNull PsiElement toHighlight,
                                 @NotNull @PropertyKey(resourceBundle = GroovyBundle.BUNDLE) String messageKey,
                                 @NotNull PsiElement context,
                                 @NotNull Position position) {
    if (hasTupleInitializer(expression)) {
      return;
    }

    if (PsiUtil.isRawClassMemberAccess(expression)) return;
    if (checkForImplicitEnumAssigning(expectedType, expression, expression)) return;

    final PsiType actualType = expression.getType();
    if (actualType == null) return;

    final ConversionResult result = TypesUtil.canAssign(expectedType, actualType, context, position);
    if (result == ConversionResult.OK) return;

    final List<LocalQuickFix> fixes = new ArrayList<>();
    {
      fixes.add(new GrCastFix(expectedType, expression));
      final String varName = getLValueVarName(toHighlight);
      if (varName != null) {
        fixes.add(new GrChangeVariableType(actualType, varName));
      }
    }

    final String message = GroovyBundle.message(messageKey, actualType.getPresentableText(), expectedType.getPresentableText());
    registerError(
      toHighlight,
      message,
      fixes.toArray(LocalQuickFix.EMPTY_ARRAY),
      result == ConversionResult.ERROR ? ProblemHighlightType.GENERIC_ERROR : ProblemHighlightType.GENERIC_ERROR_OR_WARNING
    );
  }


  private void processAssignment(@NotNull PsiType lType,
                                 @Nullable PsiType rType,
                                 @NotNull GroovyPsiElement context,
                                 @NotNull PsiElement elementToHighlight) {
    if (rType == null) return;
    final ConversionResult result = TypesUtil.canAssign(lType, rType, context, Position.ASSIGNMENT);
    processResult(result, elementToHighlight, lType, rType, LocalQuickFix.EMPTY_ARRAY);
  }

  protected void processAssignmentWithinMultipleAssignment(@Nullable PsiType targetType,
                                                           @Nullable PsiType actualType,
                                                           @NotNull PsiElement elementToHighlight) {
    if (targetType == null || actualType == null) return;
    final ConversionResult result = TypesUtil.canAssignWithinMultipleAssignment(targetType, actualType);
    if (result == ConversionResult.OK) return;
    registerError(
      elementToHighlight,
      GroovyBundle.message("cannot.assign", actualType.getPresentableText(), targetType.getPresentableText()),
      LocalQuickFix.EMPTY_ARRAY,
      result == ConversionResult.ERROR ? ProblemHighlightType.GENERIC_ERROR : ProblemHighlightType.GENERIC_ERROR_OR_WARNING
    );
  }

  @Override
  public void visitTupleAssignmentExpression(@NotNull GrTupleAssignmentExpression expression) {
    super.visitTupleAssignmentExpression(expression); // visitExpression

    GrExpression initializer = expression.getRValue();
    if (initializer == null) return;

    GrTuple tupleExpression = expression.getLValue();
    GrExpression[] lValues = tupleExpression.getExpressions();
    if (initializer instanceof GrListOrMap) {
      GrExpression[] initializers = ((GrListOrMap)initializer).getInitializers();
      for (int i = 0; i < lValues.length; i++) {
        GrExpression lValue = lValues[i];
        if (initializers.length <= i) break;
        GrExpression rValue = initializers[i];
        processAssignmentWithinMultipleAssignment(lValue.getType(), rValue.getType(), rValue);
      }
    }
    else {
      MultiAssignmentTypes multiAssignmentTypes = getMultiAssignmentTypes(initializer);
      if (multiAssignmentTypes == null) {
        return;
      }
      for (int position = 0; position < lValues.length; position++) {
        PsiType rType = multiAssignmentTypes.getComponentType(position);
        GrExpression lValue = lValues[position];
        // For assignments with spread dot
        if (PsiImplUtil.isSpreadAssignment(lValue)) {
          if (rType != null) {
            PsiType lType = lValue.getNominalType();
            final PsiType argType = extractIterableTypeParameter(lType, false);
            if (argType != null) {
              processAssignment(argType, rType, tupleExpression, getExpressionPartToHighlight(lValue));
            }
          }
          return;
        }
        if (lValue instanceof GrReferenceExpression && ((GrReferenceExpression)lValue).resolve() instanceof GrReferenceExpression) {
          //lvalue is not-declared variable
          return;
        }

        if (rType != null) {
          PsiType lType = lValue.getNominalType();
          if (lType != null) {
            processAssignment(lType, rType, tupleExpression, getExpressionPartToHighlight(lValue));
          }
        }
      }
    }
  }

  private void processResult(@NotNull ConversionResult result,
                             @NotNull PsiElement elementToHighlight,
                             @NotNull PsiType lType,
                             @NotNull PsiType rType,
                             LocalQuickFix @NotNull [] fixes) {
    if (result == ConversionResult.OK) return;
    registerError(
      elementToHighlight,
      GroovyBundle.message("cannot.assign", rType.getPresentableText(), lType.getPresentableText()),
      fixes,
      result == ConversionResult.ERROR ? ProblemHighlightType.GENERIC_ERROR : ProblemHighlightType.GENERIC_ERROR_OR_WARNING
    );
  }

  protected void processReturnValue(@NotNull GrExpression expression,
                                    @NotNull PsiElement context,
                                    @NotNull PsiElement elementToHighlight) {
    if (hasTupleInitializer(expression)) return;
    final PsiType returnType = PsiImplUtil.inferReturnType(expression);
    if (returnType == null || PsiTypes.voidType().equals(returnType)) return;
    processAssignment(returnType, expression, elementToHighlight, "cannot.return.type", context, Position.RETURN_VALUE);
  }

  @Override
  protected void registerError(@NotNull PsiElement location,
                               @InspectionMessage @NotNull String description,
                               @NotNull LocalQuickFix @Nullable [] fixes,
                               ProblemHighlightType highlightType) {
    if (FileTypeInspectionDisablerKt.isTypecheckingDisabled(location.getContainingFile())) {
      return;
    }
    if (CompileStaticUtil.isCompileStatic(location)) {
      // filter all errors here, error will be highlighted by annotator
      if (highlightType != ProblemHighlightType.GENERIC_ERROR) {
        super.registerError(location, description, fixes, highlightType);
      }
    }
    else {
      if (highlightType == ProblemHighlightType.GENERIC_ERROR) {
        // if this visitor works within non-static context we will highlight all errors as warnings
        super.registerError(location, description, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
      }
      else {
        // if this visitor works within static context errors will be highlighted as errors by annotator, warnings will be highlighted as warnings here
        super.registerError(location, description, fixes, highlightType);
      }
    }
  }

  @Override
  public void visitReturnStatement(@NotNull GrReturnStatement returnStatement) {
    final GrExpression value = returnStatement.getReturnValue();
    if (value != null) {
      processReturnValue(value, returnStatement, returnStatement.getReturnWord());
    }
  }

  @Override
  public void visitThrowStatement(@NotNull GrThrowStatement throwStatement) {
    final GrExpression exception = throwStatement.getException();
    if (exception == null) return;
    final PsiElement throwWord = throwStatement.getFirstChild();
    processAssignment(
      PsiType.getJavaLangThrowable(
        throwStatement.getManager(),
        throwStatement.getResolveScope()
      ),
      exception,
      throwWord,
      throwWord
    );
  }

  @Override
  public void visitExpression(@NotNull GrExpression expression) {
    if (isImplicitReturnStatement(expression)) {
      processReturnValue(expression, expression, expression);
    } else {
      processExpressionInsideArrayInitializer(expression);
    }
  }

  @Override
  public void visitArrayInitializer(@NotNull GrArrayInitializer arrayInitializer) {
    super.visitArrayInitializer(arrayInitializer);
    if (!GroovyConfigUtils.isAtLeastGroovy50(arrayInitializer)) return;
    processExpressionInsideArrayInitializer(arrayInitializer);
  }

  private void processExpressionInsideArrayInitializer(@NotNull GrExpression expression) {
    PsiElement parent = expression.getParent();
    if (!(parent instanceof GrArrayInitializer arrayInitializer)) return;

    PsiType initializerType = expression.getType();
    PsiType arrayInitializerType = arrayInitializer.getType();


    if (!(arrayInitializerType instanceof PsiArrayType arrayType)) return;

    PsiType componentType = arrayType.getComponentType();

    if (initializerType == null) {
      registerError(
        expression,
        GroovyBundle.message("illegal.array.initializer", componentType.getPresentableText()),
        LocalQuickFix.EMPTY_ARRAY,
        ProblemHighlightType.GENERIC_ERROR
      );
    }
    else {
      ConversionResult result = TypesUtil.canAssign(componentType, initializerType, expression, Position.ASSIGNMENT);
      processResult(result, expression, componentType, initializerType, LocalQuickFix.EMPTY_ARRAY);
    }
  }

  @Override
  public void visitNewExpression(@NotNull GrNewExpression newExpression) {
    super.visitNewExpression(newExpression); // visitExpression

    if (hasErrorElements(newExpression) || hasErrorElements(newExpression.getArgumentList())) return;

    final GroovyCallReference reference = newExpression.getConstructorReference();
    if (reference == null) return;
    if (new GrNewExpressionHighlighter(newExpression, reference, myHighlightSink).highlight()) {
      return;
    }

    checkNamedArgumentsType(newExpression);
  }

  @Override
  public void visitEnumConstant(@NotNull GrEnumConstant enumConstant) {
    if (hasErrorElements(enumConstant) || hasErrorElements(enumConstant.getArgumentList())) return;

    if (new GrEnumConstantHighlighter(enumConstant, myHighlightSink).highlight()) {
      return;
    }

    checkNamedArgumentsType(enumConstant);
  }

  @Override
  public void visitConstructorInvocation(@NotNull GrConstructorInvocation invocation) {
    if (hasErrorElements(invocation) || hasErrorElements(invocation.getArgumentList())) return;

    if (new GrConstructorInvocationHighlighter(invocation, myHighlightSink).highlight()) {
      return;
    }
    checkNamedArgumentsType(invocation);
  }

  @Override
  public void visitAssignmentExpression(@NotNull GrAssignmentExpression assignment) {
    super.visitAssignmentExpression(assignment); // visitExpression

    if (assignment.isOperatorAssignment()) return;

    final GrExpression lValue = assignment.getLValue();
    if (!PsiUtil.mightBeLValue(lValue)) return;

    final GrExpression rValue = assignment.getRValue();
    if (rValue == null) return;

    if (lValue instanceof GrReferenceExpression && ((GrReferenceExpression)lValue).resolve() instanceof GrReferenceExpression) {
      //lvalue is not-declared variable
      return;
    }

    PsiType lValueNominalType = lValue.getNominalType();
    final PsiType targetType = PsiImplUtil.isSpreadAssignment(lValue) ? extractIterableTypeParameter(lValueNominalType, false)
                                                                      : lValueNominalType;
    if (targetType == null) return;
    processAssignment(targetType, rValue, lValue, assignment);
  }

  @Override
  public void visitArrayDeclaration(@NotNull GrArrayDeclaration arrayDeclaration) {
    super.visitArrayDeclaration(arrayDeclaration);
    if (!GroovyConfigUtils.getInstance().isVersionAtLeast(arrayDeclaration, GroovyConfigUtils.GROOVY3_0)) return;

    for (GrExpression expr : arrayDeclaration.getBoundExpressions()) {
      PsiType type = expr.getType();
      if (type == null) continue;
      ConversionResult conversionResult = TypesUtil.isIntegralNumberType(type) ? ConversionResult.OK : ConversionResult.ERROR;
      processResult(conversionResult, expr, type, PsiTypes.intType(), new LocalQuickFix[]{new GrCastFix(PsiTypes.intType(), expr)});
    }
  }

  void checkPossibleLooseOfPrecision(@NotNull PsiType targetType, @NotNull GrExpression expression, @NotNull PsiElement toHighlight) {
    PsiType actualType = expression.getType();
    if (actualType == null) return;
    if (!PrecisionUtil.isPossibleLooseOfPrecision(targetType, actualType, expression)) return;
    registerError(
      toHighlight,
      GroovyBundle.message("loss.of.precision", actualType.getPresentableText(), targetType.getPresentableText()),
      new LocalQuickFix[]{new GrCastFix(targetType, expression, false)},
      ProblemHighlightType.GENERIC_ERROR
    );
  }

  @Override
  public void visitBinaryExpression(@NotNull GrBinaryExpression binary) {
    super.visitBinaryExpression(binary); // visitExpression

    GroovyCallReference reference = binary.getReference();
    if (reference == null) return;
    new BinaryExpressionHighlighter(binary, reference, myHighlightSink).highlight();
  }

  @Override
  public void visitCastExpression(@NotNull GrTypeCastExpression expression) {
    super.visitCastExpression(expression); // visitExpression

    final GrExpression operand = expression.getOperand();
    if (operand == null) return;
    final PsiType actualType = operand.getType();
    if (actualType == null) return;

    if (expression.getCastTypeElement() == null) return;
    final PsiType expectedType = expression.getCastTypeElement().getType();

    final ConversionResult result = TypesUtil.canCast(expectedType, actualType, expression);
    if (result == ConversionResult.OK) return;
    final ProblemHighlightType highlightType = result == ConversionResult.ERROR
                                               ? ProblemHighlightType.GENERIC_ERROR
                                               : ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
    final String message = GroovyBundle.message(
      "cannot.cast",
      actualType.getPresentableText(),
      expectedType.getPresentableText()
    );
    registerError(
      expression,
      highlightType,
      message
    );
  }

  @Override
  public void visitIndexProperty(@NotNull GrIndexProperty expression) {
    super.visitIndexProperty(expression); // visitExpression

    if (hasErrorElements(expression)) return;

    if (GroovyIndexPropertyUtil.isClassLiteral(expression)) return;
    if (GroovyIndexPropertyUtil.isSimpleArrayAccess(expression)) return;

    GrArgumentList argumentList = expression.getArgumentList();
    GroovyMethodCallReference rValueReference = expression.getRValueReference();
    if (rValueReference != null) {
      new MethodCallReferenceHighlighter(rValueReference, argumentList, myHighlightSink).highlightMethodApplicability();
    }
    GroovyMethodCallReference lValueReference = expression.getLValueReference();
    if (lValueReference != null) {
      new MethodCallReferenceHighlighter(lValueReference, argumentList, myHighlightSink).highlightMethodApplicability();
    }
  }

  /**
   * Handles method default values.
   */
  @Override
  public void visitMethod(@NotNull GrMethod method) {
    final PsiTypeParameter[] parameters = method.getTypeParameters();
    final Map<PsiTypeParameter, PsiType> map = new HashMap<>();
    for (PsiTypeParameter parameter : parameters) {
      final PsiClassType[] types = parameter.getSuperTypes();
      final PsiType bound = PsiIntersectionType.createIntersection(types);
      final PsiWildcardType wildcardType = PsiWildcardType.createExtends(method.getManager(), bound);
      map.put(parameter, wildcardType);
    }
    final PsiSubstitutor substitutor = PsiSubstitutor.createSubstitutor(map);

    for (GrParameter parameter : method.getParameterList().getParameters()) {
      final GrExpression initializer = parameter.getInitializerGroovy();
      if (initializer == null) continue;
      final PsiType targetType = parameter.getType();
      processAssignment(
        substitutor.substitute(targetType),
        initializer,
        parameter.getNameIdentifierGroovy(),
        parameter
      );
    }
  }

  @Override
  public void visitParameterList(final @NotNull GrParameterList parameterList) {
    PsiElement parent = parameterList.getParent();
    if (!(parent instanceof GrFunctionalExpression)) return;

    GrParameter[] parameters = parameterList.getParameters();
    if (parameters.length > 0) {
      List<PsiType[]> signatures = ClosureParamsEnhancer.findFittingSignatures((GrFunctionalExpression)parent); // TODO: suspicious method call
      final List<PsiType> paramTypes = ContainerUtil.map(parameters, parameter -> parameter.getType());

      if (signatures.size() > 1) {
        final PsiType[] fittingSignature = ContainerUtil.find(signatures, types -> {
          for (int i = 0; i < types.length; i++) {
            if (!TypesUtil.isAssignableByMethodCallConversion(paramTypes.get(i), types[i], parameterList)) {
              return false;
            }
          }
          return true;
        });
        if (fittingSignature == null) {
          registerError(
            parameterList,
            GroovyBundle.message("no.applicable.signature.found"),
            null,
            ProblemHighlightType.GENERIC_ERROR
          );
        }
      }
      else if (signatures.size() == 1) {
        PsiType[] types = signatures.get(0);
        for (int i = 0; i < types.length; i++) {
          GrTypeElement typeElement = parameters[i].getTypeElementGroovy();
          if (typeElement == null) continue;
          PsiType expected = types[i];
          PsiType actual = paramTypes.get(i);
          if (!TypesUtil.isAssignableByMethodCallConversion(actual, expected, parameterList)) {
            registerError(
              typeElement,
              GroovyBundle.message("expected.type.0", expected.getCanonicalText(false), actual.getCanonicalText(false)),
              null,
              ProblemHighlightType.GENERIC_ERROR
            );
          }
        }
      }
    }
  }

  @Override
  public void visitForInClause(@NotNull GrForInClause forInClause) {
    final GrVariable variable = forInClause.getDeclaredVariable();
    final GrExpression iterated = forInClause.getIteratedExpression();
    if (variable == null || iterated == null) return;

    final PsiType iteratedType = ClosureParameterEnhancer.findTypeForIteration(iterated, forInClause);
    if (iteratedType == null) return;
    final PsiType targetType = variable.getType();

    final ConversionResult result = TypesUtil.canAssign(targetType, iteratedType, forInClause, Position.ASSIGNMENT);
    LocalQuickFix[] fixes = {new GrCastFix(TypesUtil.createListType(iterated, targetType), iterated)};
    processResult(result, variable, targetType, iteratedType, fixes);
  }

  @Override
  public void visitVariable(@NotNull GrVariable variable) {
    final PsiType varType = variable.getType();
    final PsiElement parent = variable.getParent();

    if (variable instanceof GrParameter && ((GrParameter)variable).getDeclarationScope() instanceof GrMethod ||
        parent instanceof GrForInClause) {
      return;
    }
    GrExpression initializer = variable.getInitializerGroovy();
    if (initializer != null) {
      processAssignment(varType, initializer, variable.getNameIdentifierGroovy(), variable);
    }
    else {
      PsiType initializerType = variable.getInitializerType();
      processAssignment(varType, initializerType, variable, variable.getNameIdentifierGroovy());
    }
  }

  @Override
  public void visitListOrMap(@NotNull GrListOrMap listOrMap) {
    super.visitListOrMap(listOrMap); // visitExpression

    GroovyConstructorReference constructorReference = listOrMap.getConstructorReference();
    if (constructorReference != null) {
      CallReferenceHighlighter highlighter = new LiteralConstructorReferenceHighlighter(constructorReference, listOrMap, myHighlightSink);
      if (highlighter.highlightMethodApplicability()) {
        return;
      }
    }

    Map<String, NamedArgumentDescriptor> descriptors = NamedArgumentUtilKt.getDescriptors(listOrMap);
    if (descriptors.isEmpty()) return;

    GrNamedArgument[] namedArguments = listOrMap.getNamedArguments();
    if (namedArguments.length == 0) return;

    checkNamedArguments(listOrMap, namedArguments, descriptors);
  }

  @Override
  protected void registerError(@NotNull PsiElement location,
                               ProblemHighlightType highlightType,
                               @InspectionMessage Object... args) {
    registerError(location, (String)args[0], LocalQuickFix.EMPTY_ARRAY, highlightType);
  }
}

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class LambdaCanBeMethodReferenceInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(LambdaCanBeMethodReferenceInspection.class);


  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.language.level.specific.issues.and.migration.aids");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public String getShortName() {
    return "Convert2MethodRef";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    if (!HighlightingFeature.LAMBDA_EXPRESSIONS.isAvailable(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitLambdaExpression(PsiLambdaExpression expression) {
        super.visitLambdaExpression(expression);
        final PsiElement body = expression.getBody();
        MethodReferenceCandidate methodRefCandidate = extractMethodReferenceCandidateExpression(body);
        if (methodRefCandidate == null) return;
        final PsiExpression candidate = getLambdaToMethodReferenceConversionCandidate(expression, methodRefCandidate.myExpression);
        if (candidate == null) return;
        ProblemHighlightType type;
        if (methodRefCandidate.mySafeQualifier && methodRefCandidate.myConformsCodeStyle) {
          type = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
        }
        else {
          if (!isOnTheFly) return;
          type = ProblemHighlightType.INFORMATION;
        }
        PsiElement element =
          type == ProblemHighlightType.INFORMATION || InspectionProjectProfileManager.isInformationLevel(getShortName(), expression)
          ? expression
          : candidate;
        holder.registerProblem(holder.getManager().createProblemDescriptor(
          element,
          getDisplayName(),
          type != ProblemHighlightType.INFORMATION,
          type, true, new ReplaceWithMethodRefFix(methodRefCandidate.mySafeQualifier)));
      }
    };
  }

  @Nullable
  private static PsiExpression getLambdaToMethodReferenceConversionCandidate(@NotNull PsiLambdaExpression expression,
                                                                             @NotNull PsiExpression methodRefCandidate) {
    PsiParameter[] parameters = expression.getParameterList().getParameters();
    PsiType functionalInterfaceType = expression.getFunctionalInterfaceType();
    if (functionalInterfaceType == null) return null;
    final PsiExpression candidate = canBeMethodReferenceProblem(parameters, functionalInterfaceType, null, methodRefCandidate);
    if (candidate == null) return null;
    boolean safeLambdaReplacement = LambdaUtil.isSafeLambdaReplacement(expression, () -> {
      String text = createMethodReferenceText(methodRefCandidate, functionalInterfaceType, parameters);
      // We already did the same operation inside canBeMethodReferenceProblem
      LOG.assertTrue(text != null);
      return JavaPsiFacade.getElementFactory(expression.getProject()).createExpressionFromText(text, expression);
    });
    if (!safeLambdaReplacement) return null;
    return candidate;
  }

  @Nullable
  public static PsiExpression canBeMethodReferenceProblem(@Nullable final PsiElement body,
                                                          final PsiVariable[] parameters,
                                                          PsiType functionalInterfaceType,
                                                          @Nullable PsiElement context) {
    MethodReferenceCandidate methodRefCandidate = extractMethodReferenceCandidateExpression(body);
    if (methodRefCandidate == null || !methodRefCandidate.mySafeQualifier || !methodRefCandidate.myConformsCodeStyle) return null;
    return canBeMethodReferenceProblem(parameters, functionalInterfaceType, context, methodRefCandidate.myExpression);
  }

  @Nullable
  public static PsiExpression canBeMethodReferenceProblem(PsiVariable @NotNull [] parameters,
                                                          @Nullable PsiType functionalInterfaceType,
                                                          @Nullable PsiElement context,
                                                          final PsiExpression methodRefCandidate) {
    if (functionalInterfaceType == null) return null;
    // Do not suggest for annotated lambda, as annotation will be lost during the conversion
    if (ContainerUtil.or(parameters, LambdaCanBeMethodReferenceInspection::hasAnnotation)) return null;
    if (methodRefCandidate instanceof PsiNewExpression) {
      final PsiNewExpression newExpression = (PsiNewExpression)methodRefCandidate;
      if (newExpression.getAnonymousClass() != null || newExpression.getArrayInitializer() != null) {
        return null;
      }
    }

    final String methodReferenceText = createMethodReferenceText(methodRefCandidate, functionalInterfaceType, parameters);
    if (methodReferenceText != null) {
      LOG.assertTrue(methodRefCandidate != null);
      if (!(methodRefCandidate instanceof PsiCallExpression)) return methodRefCandidate;
      PsiCallExpression callExpression = (PsiCallExpression)methodRefCandidate;
      final PsiMethod method = callExpression.resolveMethod();
      if (method != null) {
        if (!isSimpleCall(parameters, callExpression, method)) {
          return null;
        }
      }
      else {
        LOG.assertTrue(callExpression instanceof PsiNewExpression);
        if (((PsiNewExpression)callExpression).getQualifier() != null) {
          return null;
        }

        final PsiExpression[] dims = ((PsiNewExpression)callExpression).getArrayDimensions();
        if (dims.length == 1 && parameters.length == 1){
          if (!ExpressionUtils.isReferenceTo(dims[0], parameters[0])) {
            return null;
          }
        }
        else if (dims.length > 0) {
          return null;
        }

        if (callExpression.getTypeArguments().length > 0) {
          return null;
        }
      }
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(callExpression.getProject());
      PsiMethodReferenceExpression methodReferenceExpression;
      try {
        methodReferenceExpression = (PsiMethodReferenceExpression)elementFactory.createExpressionFromText(methodReferenceText, context != null ? context : callExpression);
      }
      catch (IncorrectOperationException | ClassCastException e) {
        LOG.error(callExpression.getText(), e);
        return null;
      }
      return LambdaUtil.performWithTargetType(methodReferenceExpression, functionalInterfaceType, () -> {
        final JavaResolveResult result = methodReferenceExpression.advancedResolve(false);
        final PsiElement element = result.getElement();
        if (element != null && result.isAccessible() &&
            !(result instanceof MethodCandidateInfo && !((MethodCandidateInfo)result).isApplicable())) {
          if (!(element instanceof PsiMethod)) {
            return callExpression;
          }

          return method != null && MethodSignatureUtil.areSignaturesEqual((PsiMethod)element, method) ? callExpression : null;
        }
        return null;
      });
    }
    return null;
  }

  private static boolean isSimpleCall(final PsiVariable[] parameters, PsiCallExpression callExpression, PsiMethod psiMethod) {
    final PsiExpressionList argumentList = callExpression.getArgumentList();
    if (argumentList == null) {
      return false;
    }

    final int calledParametersCount = psiMethod.getParameterList().getParametersCount();
    final PsiExpression[] expressions = argumentList.getExpressions();

    final PsiExpression qualifier;
    if (callExpression instanceof PsiMethodCallExpression) {
      qualifier = ((PsiMethodCallExpression)callExpression).getMethodExpression().getQualifierExpression();
    }
    else if (callExpression instanceof PsiNewExpression) {
      qualifier = ((PsiNewExpression)callExpression).getQualifier();
    }
    else {
      qualifier = null;
    }

    if (expressions.length == 0 && parameters.length == 0) {
      return !(callExpression instanceof PsiNewExpression && qualifier != null);
    }

    final int offset = parameters.length > 0 && ExpressionUtils.isReferenceTo(qualifier, parameters[0]) ? 1 : 0;
    if (parameters.length != expressions.length + offset) return false;

    if (psiMethod.isVarArgs()) {
      if (expressions.length < calledParametersCount - 1) return false;
    }
    else {
      if (expressions.length != calledParametersCount) return false;
    }

    for (int i = 0; i < expressions.length; i++) {
      if (!ExpressionUtils.isReferenceTo(expressions[i], parameters[i + offset])) {
        return false;
      }
    }

    if (offset == 0 && qualifier != null) {
      return SyntaxTraverser.psiTraverser(qualifier)
        .filter(PsiReferenceExpression.class)
        .map(PsiReferenceExpression::resolve)
        .filter(target -> ArrayUtil.find(parameters, target) >= 0)
        .first() == null;
    }
    return true;
  }

  @Nullable
  static MethodReferenceCandidate extractMethodReferenceCandidateExpression(PsiElement body) {
    final PsiExpression expression = PsiUtil.skipParenthesizedExprDown(LambdaUtil.extractSingleExpressionFromBody(body));
    if (expression == null) {
      return null;
    }
    if (expression instanceof PsiNewExpression) {
      PsiNewExpression newExpression = (PsiNewExpression)expression;
      if (newExpression.getQualifier() != null ||
          newExpression.getAnonymousClass() != null ||
          newExpression.getArrayInitializer() != null) {
        return null;
      }
      PsiExpressionList argumentList = newExpression.getArgumentList();
      if (argumentList != null) {
        for (PsiExpression arg : argumentList.getExpressions()) {
          if (!isMethodReferenceArgCandidate(arg)) return null;
        }
      }
      return new MethodReferenceCandidate(expression, true, true);
    }
    else if (expression instanceof PsiMethodCallExpression) {
      for (PsiExpression arg : ((PsiMethodCallExpression)expression).getArgumentList().getExpressions()) {
        if (!isMethodReferenceArgCandidate(arg)) return null;
      }
      return new MethodReferenceCandidate(expression,
                                          checkQualifier(((PsiMethodCallExpression)expression).getMethodExpression().getQualifier()), true);
    }

    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(expression.getContainingFile());
    if (expression instanceof PsiInstanceOfExpression) {
      if (!isMethodReferenceArgCandidate(((PsiInstanceOfExpression)expression).getOperand())) return null;
      return new MethodReferenceCandidate(expression, true, javaSettings.REPLACE_INSTANCEOF_AND_CAST);
    }
    else if (expression instanceof PsiBinaryExpression) {
      PsiBinaryExpression binOp = (PsiBinaryExpression)expression;
      PsiExpression comparedWithNull = PsiUtil.skipParenthesizedExprDown(ExpressionUtils.getValueComparedWithNull(binOp));
      if (isMethodReferenceArgCandidate(comparedWithNull)) {
        return new MethodReferenceCandidate(expression, true, javaSettings.REPLACE_NULL_CHECK);
      }
      if (binOp.getOperationTokenType().equals(JavaTokenType.PLUS) &&
          isMethodReferenceArgCandidate(binOp.getLOperand()) && isMethodReferenceArgCandidate(binOp.getROperand())) {
        return new MethodReferenceCandidate(expression, true, javaSettings.REPLACE_SUM);
      }
    }
    else if (expression instanceof PsiTypeCastExpression) {
      if (!isMethodReferenceArgCandidate(((PsiTypeCastExpression)expression).getOperand())) return null;
      PsiTypeElement typeElement = ((PsiTypeCastExpression)expression).getCastType();
      if (typeElement != null) {
        PsiJavaCodeReferenceElement refs = typeElement.getInnermostComponentReferenceElement();
        if (refs != null && refs.getParameterList() != null && refs.getParameterList().getTypeParameterElements().length != 0) {
          return null;
        }
        PsiType type = typeElement.getType();
        if (type instanceof PsiPrimitiveType || PsiUtil.resolveClassInType(type) instanceof PsiTypeParameter) return null;
        if (type instanceof PsiIntersectionType) return null;
        return new MethodReferenceCandidate(expression, true, javaSettings.REPLACE_INSTANCEOF_AND_CAST);
      }
    }
    return null;
  }

  private static boolean isMethodReferenceArgCandidate(PsiExpression arg) {
    arg = PsiUtil.skipParenthesizedExprDown(arg);
    return arg instanceof PsiReferenceExpression && ((PsiReferenceExpression)arg).getQualifier() == null;
  }

  public static void replaceAllLambdasWithMethodReferences(PsiElement root) {
    Collection<PsiLambdaExpression> lambdas = PsiTreeUtil.findChildrenOfType(root, PsiLambdaExpression.class);
    if(!lambdas.isEmpty()) {
      for(PsiLambdaExpression lambda : lambdas) {
        replaceLambdaWithMethodReference(lambda);
      }
    }
  }

  @NotNull
  public static PsiExpression replaceLambdaWithMethodReference(@NotNull PsiLambdaExpression lambda) {
    MethodReferenceCandidate methodRefCandidate = extractMethodReferenceCandidateExpression(lambda.getBody());
    if (methodRefCandidate == null || !methodRefCandidate.mySafeQualifier || !methodRefCandidate.myConformsCodeStyle) return lambda;
    PsiExpression candidate = getLambdaToMethodReferenceConversionCandidate(lambda, methodRefCandidate.myExpression);
    return tryConvertToMethodReference(lambda, candidate);
  }

  public static boolean checkQualifier(@Nullable PsiElement qualifier) {
    if (qualifier == null) {
      return true;
    }
    final Condition<PsiElement> callExpressionCondition = Conditions.instanceOf(PsiCallExpression.class, PsiArrayAccessExpression.class, PsiTypeCastExpression.class);
    final Condition<PsiElement> nonFinalFieldRefCondition = expression -> {
      if (expression instanceof PsiReferenceExpression && !(expression.getParent() instanceof PsiCallExpression)) {
        PsiElement element = ((PsiReferenceExpression)expression).resolve();
        if (element instanceof PsiField && !((PsiField)element).hasModifierProperty(PsiModifier.FINAL)) {
          return true;
        }

        if (NullabilityUtil.getExpressionNullability((PsiExpression)expression, true) == Nullability.NULLABLE) {
          return true;
        }
      }
      return false;
    };
    return SyntaxTraverser
      .psiTraverser()
      .withRoot(qualifier)
      .filter(Conditions.or(callExpressionCondition, nonFinalFieldRefCondition)).toList().isEmpty();
  }

  @Nullable
  private static PsiMethod getNonAmbiguousReceiver(PsiVariable[] parameters, @NotNull PsiMethod psiMethod) {
    String methodName = psiMethod.getName();
    PsiClass containingClass = psiMethod.getContainingClass();
    if (containingClass == null) return null;

    final PsiMethod[] psiMethods = containingClass.findMethodsByName(methodName, false);
    if (psiMethods.length == 1) return psiMethod;

    final PsiType receiverType = parameters[0].getType();
    for (PsiMethod method : psiMethods) {
      if (isPairedNoReceiver(parameters, receiverType, method)) {
        final PsiMethod[] deepestSuperMethods = psiMethod.findDeepestSuperMethods();
        if (deepestSuperMethods.length > 0) {
          for (PsiMethod superMethod : deepestSuperMethods) {
            PsiMethod validSuperMethod = getNonAmbiguousReceiver(parameters, superMethod);
            if (validSuperMethod != null) return validSuperMethod;
          }
        }
        return null;
      }
    }
    return psiMethod;
  }

  private static boolean isPairedNoReceiver(PsiVariable[] parameters,
                                            PsiType receiverType,
                                            PsiMethod method) {
    final PsiParameter[] nonReceiverCandidateParams = method.getParameterList().getParameters();
    return nonReceiverCandidateParams.length == parameters.length &&
           method.hasModifierProperty(PsiModifier.STATIC) &&
           TypeConversionUtil.areTypesConvertible(nonReceiverCandidateParams[0].getType(), receiverType);
  }

  private static boolean isSoleParameter(PsiVariable @NotNull [] parameters, @Nullable PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    return parameters.length == 1 &&
           expression instanceof PsiReferenceExpression &&
           parameters[0] == ((PsiReferenceExpression)expression).resolve();
  }

  static @Nullable @NonNls String createMethodReferenceText(final PsiElement element,
                                                 final PsiType functionalInterfaceType,
                                                 final PsiVariable[] parameters) {
    if (element instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)element;

      JavaResolveResult result = methodCall.resolveMethodGenerics();
      final PsiMethod psiMethod = (PsiMethod)result.getElement();
      if (psiMethod == null) {
        return null;
      }

      final PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
      final String qualifierByMethodCall = getQualifierTextByMethodCall(methodCall, functionalInterfaceType, parameters, psiMethod, result.getSubstitutor());
      if (qualifierByMethodCall != null) {
        return qualifierByMethodCall + "::" + ((PsiMethodCallExpression)element).getTypeArgumentList().getText() + methodExpression.getReferenceName();
      }
    }
    else if (element instanceof PsiNewExpression) {
      final String qualifierByNew = getQualifierTextByNewExpression((PsiNewExpression)element);
      if (qualifierByNew != null) {
        return qualifierByNew + ((PsiNewExpression)element).getTypeArgumentList().getText() + "::new";
      }
    }
    else if (element instanceof PsiInstanceOfExpression) {
      if(isSoleParameter(parameters, ((PsiInstanceOfExpression)element).getOperand())) {
        PsiTypeElement type = ((PsiInstanceOfExpression)element).getCheckType();
        if(type != null && !PsiUtilCore.hasErrorElementChild(type)) {
          return type.getText() + ".class::isInstance";
        }
      }
    }
    else if (element instanceof PsiBinaryExpression) {
      PsiBinaryExpression binOp = (PsiBinaryExpression)element;
      PsiExpression operand = ExpressionUtils.getValueComparedWithNull(binOp);
      if(operand != null && isSoleParameter(parameters, operand)) {
        IElementType tokenType = binOp.getOperationTokenType();
        if(JavaTokenType.EQEQ.equals(tokenType)) {
          return "java.util.Objects::isNull";
        } else if(JavaTokenType.NE.equals(tokenType)) {
          return "java.util.Objects::nonNull";
        }
      }
      if(binOp.getOperationTokenType().equals(JavaTokenType.PLUS)) {
        PsiExpression left = binOp.getLOperand();
        PsiExpression right = binOp.getROperand();
        if (parameters.length == 2 && 
            ((ExpressionUtils.isReferenceTo(left, parameters[0]) && ExpressionUtils.isReferenceTo(right, parameters[1])) ||
            (ExpressionUtils.isReferenceTo(left, parameters[1]) && ExpressionUtils.isReferenceTo(right, parameters[0])))) {
          PsiType type = binOp.getType();
          if (type instanceof PsiPrimitiveType && TypeConversionUtil.isNumericType(type)) {
            // Can be only int/long/double/float as short/byte/char would be promoted to int
            return ((PsiPrimitiveType)type).getBoxedTypeName() + "::sum";
          }
        }
      }
    }
    else if (element instanceof PsiTypeCastExpression) {
      PsiTypeCastExpression castExpression = (PsiTypeCastExpression)element;
      if(isSoleParameter(parameters, castExpression.getOperand())) {
        PsiTypeElement type = castExpression.getCastType();
        if (type != null) {
          return type.getText() + ".class::cast";
        }
      }
    }
    return null;
  }

  private static String getQualifierTextByNewExpression(PsiNewExpression element) {
    final PsiType newExprType = element.getType();
    if (newExprType == null) {
      return null;
    }

    PsiClass containingClass = null;
    final PsiJavaCodeReferenceElement classReference = element.getClassOrAnonymousClassReference();
    if (classReference != null) {
      final JavaResolveResult resolve = classReference.advancedResolve(false);
      final PsiElement resolveElement = resolve.getElement();
      if (resolveElement instanceof PsiClass) {
        containingClass = (PsiClass)resolveElement;
      }
    }

    String classOrPrimitiveName = null;
    if (containingClass != null) {
      classOrPrimitiveName = getClassReferenceName(containingClass);
    }
    else if (newExprType instanceof PsiArrayType){
      final PsiType deepComponentType = newExprType.getDeepComponentType();
      if (deepComponentType instanceof PsiPrimitiveType) {
        classOrPrimitiveName = deepComponentType.getCanonicalText();
      }
    }

    if (classOrPrimitiveName == null) {
      return null;
    }

    return classOrPrimitiveName + StringUtil.repeat("[]", newExprType.getArrayDimensions());
  }

  private static @Nullable @NlsSafe String getQualifierTextByMethodCall(final PsiMethodCallExpression methodCall,
                                                                        final PsiType functionalInterfaceType,
                                                                        final PsiVariable[] parameters,
                                                                        final PsiMethod psiMethod,
                                                                        final PsiSubstitutor substitutor) {

    final PsiExpression qualifierExpression = methodCall.getMethodExpression().getQualifierExpression();

    final PsiClass containingClass = psiMethod.getContainingClass();
    LOG.assertTrue(containingClass != null);

    if (qualifierExpression != null) {
      boolean isReceiverType = false;
      if (ExpressionUtils.isReferenceTo(qualifierExpression, ArrayUtil.getFirstElement(parameters))) {
        PsiType type = PsiMethodReferenceUtil.getFirstParameterType(functionalInterfaceType, qualifierExpression);
        isReceiverType = PsiMethodReferenceUtil.isReceiverType(type, containingClass, substitutor);
      }
      return isReceiverType ? composeReceiverQualifierText(parameters, psiMethod, containingClass, qualifierExpression)
                            : qualifierExpression.getText();
    }
    else {
      if (psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
        return getClassReferenceName(containingClass);
      }
      else {
        PsiClass parentContainingClass = PsiTreeUtil.getParentOfType(methodCall, PsiClass.class);
        if (parentContainingClass instanceof PsiAnonymousClass) {
          parentContainingClass = PsiTreeUtil.getParentOfType(parentContainingClass, PsiClass.class, true);
        }
        PsiClass treeContainingClass = parentContainingClass;
        while (treeContainingClass != null && !InheritanceUtil.isInheritorOrSelf(treeContainingClass, containingClass, true)) {
          treeContainingClass = PsiTreeUtil.getParentOfType(treeContainingClass, PsiClass.class, true);
        }
        if (treeContainingClass != null && containingClass != parentContainingClass && treeContainingClass != parentContainingClass) {
          final String treeContainingClassName = treeContainingClass.getName();
          if (treeContainingClassName == null) {
            return null;
          }
          return treeContainingClassName + ".this";
        }
        else {
          return "this";
        }
      }
    }
  }

  @Nullable
  private static String composeReceiverQualifierText(PsiVariable[] parameters,
                                                     PsiMethod psiMethod,
                                                     PsiClass containingClass,
                                                     @NotNull PsiExpression qualifierExpression) {
    if (psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
      return null;
    }

    final PsiMethod nonAmbiguousMethod = getNonAmbiguousReceiver(parameters, psiMethod);
    if (nonAmbiguousMethod == null) {
      return null;
    }

    final PsiClass nonAmbiguousContainingClass = nonAmbiguousMethod.getContainingClass();
    if (nonAmbiguousContainingClass != null && !containingClass.equals(nonAmbiguousContainingClass)) {
      return getClassReferenceName(nonAmbiguousContainingClass);
    }

    if (containingClass.isPhysical() &&
        !PsiTypesUtil.isGetClass(psiMethod) &&
        ExpressionUtils.isReferenceTo(qualifierExpression, ArrayUtil.getFirstElement(parameters))) {
      return getClassReferenceName(containingClass);
    }

    final PsiType qualifierExpressionType = qualifierExpression.getType();
    if (qualifierExpressionType != null && !FunctionalInterfaceParameterizationUtil.isWildcardParameterized(qualifierExpressionType)) {
      try {
        final String canonicalText = qualifierExpressionType.getCanonicalText();
        JavaPsiFacade.getElementFactory(containingClass.getProject()).createExpressionFromText(canonicalText + "::foo", qualifierExpression);
        return canonicalText;
      }
      catch (IncorrectOperationException ignore){}
    }
    return getClassReferenceName(containingClass);
  }

  private static String getClassReferenceName(PsiClass containingClass) {
    final String qualifiedName = containingClass.getQualifiedName();
    if (qualifiedName != null) {
      return qualifiedName;
    }
    else {
      return containingClass.getName();
    }
  }

  /**
   * @param p variable to test
   * @return true if given variable is annotated or its type is annotated
   */
  private static boolean hasAnnotation(PsiVariable p) {
    if (p.getAnnotations().length > 0) return true;
    PsiTypeElement typeElement = p.getTypeElement();
    return typeElement != null && !typeElement.isInferredType() && PsiTypesUtil.hasTypeAnnotation(typeElement.getType());
  }

  private static class ReplaceWithMethodRefFix implements LocalQuickFix {
    private final boolean mySafeQualifier;

    ReplaceWithMethodRefFix(boolean mayChangeSemantics) {
      mySafeQualifier = mayChangeSemantics;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return mySafeQualifier ? getFamilyName() : InspectionGadgetsBundle.message("replace.with.method.ref.fix.name.may.change.semantics");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("replace.with.method.ref.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      if (element instanceof PsiLambdaExpression) {
        MethodReferenceCandidate methodReferenceCandidate = extractMethodReferenceCandidateExpression(((PsiLambdaExpression)element).getBody());
        if (methodReferenceCandidate == null) return;
        element = methodReferenceCandidate.myExpression;
      }
      final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(element, PsiLambdaExpression.class);
      if (lambdaExpression == null) return;
      tryConvertToMethodReference(lambdaExpression, element);
    }
  }

  @NotNull
  private static PsiExpression tryConvertToMethodReference(@NotNull PsiLambdaExpression lambda, PsiElement body) {
    Project project = lambda.getProject();
    PsiType functionalInterfaceType = lambda.getFunctionalInterfaceType();
    if (functionalInterfaceType == null || !functionalInterfaceType.isValid()) return lambda;
    final PsiType denotableFunctionalInterfaceType = RefactoringChangeUtil.getTypeByExpression(lambda);
    if (denotableFunctionalInterfaceType == null) return lambda;

    final String methodRefText = createMethodReferenceText(body, functionalInterfaceType, lambda.getParameterList().getParameters());

    if (methodRefText != null) {
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiExpression psiExpression = factory.createExpressionFromText(methodRefText, lambda);
      final SmartTypePointer typePointer = SmartTypePointerManager.getInstance(project).createSmartTypePointer(denotableFunctionalInterfaceType);
      PsiExpression replace = (PsiExpression)new CommentTracker().replaceAndRestoreComments(lambda, psiExpression);
      if (!(replace instanceof PsiMethodReferenceExpression)) {
        LOG.error("Generated code is not method reference: "+replace.getClass(), 
                  new Attachment("replacement.txt", replace.getText()),
                  new Attachment("origtext.txt", methodRefText));
        return lambda;
      }
      final PsiType functionalTypeAfterReplacement = GenericsUtil.getVariableTypeByExpressionType(((PsiMethodReferenceExpression)replace).getFunctionalInterfaceType());
      functionalInterfaceType = typePointer.getType();
      if (functionalInterfaceType != null && (functionalTypeAfterReplacement == null ||
          !functionalTypeAfterReplacement.equals(functionalInterfaceType))) { //ambiguity
        final PsiTypeCastExpression cast = (PsiTypeCastExpression)factory.createExpressionFromText("(A)a", replace);
        PsiTypeElement castType = cast.getCastType();
        LOG.assertTrue(castType != null);
        castType.replace(factory.createTypeElement(functionalInterfaceType));
        PsiExpression castOperand = cast.getOperand();
        LOG.assertTrue(castOperand != null);
        castOperand.replace(replace);
        replace = (PsiExpression)replace.replace(cast);
      }

      JavaCodeStyleManager.getInstance(project).shortenClassReferences(replace);
      return replace;
    }
    return lambda;
  }

  static class MethodReferenceCandidate {
    final PsiExpression myExpression;
    final boolean mySafeQualifier;
    final boolean myConformsCodeStyle;

    MethodReferenceCandidate(PsiExpression expression, boolean safeQualifier, boolean conformsCodeStyle) {
      myExpression = expression;
      mySafeQualifier = safeQualifier;
      myConformsCodeStyle = conformsCodeStyle;
    }
  }
}

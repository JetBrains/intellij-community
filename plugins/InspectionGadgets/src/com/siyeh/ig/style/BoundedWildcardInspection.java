// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.codeInspection.*;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.*;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.JavaChangeSignatureDialog;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

/**
 * {@code "void process(Processor<T> p)"  -> "void process(Processor<? super T> p)"}
 */
public class BoundedWildcardInspection extends AbstractBaseJavaLocalInspectionTool {
  @SuppressWarnings("WeakerAccess") public boolean REPORT_INVARIANT_CLASSES = true;
  private JBCheckBox myReportInvariantClassesCB;
  private JPanel myPanel;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("bounded.wildcard.display.name");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    return new JavaElementVisitor() {
      @Override
      public void visitTypeElement(PsiTypeElement typeElement) {
        VarianceCandidate candidate = VarianceCandidate.findVarianceCandidate(typeElement);
        if (candidate == null) return;
        PsiTypeParameterListOwner owner = candidate.typeParameter.getOwner();
        if (owner instanceof PsiClass && !REPORT_INVARIANT_CLASSES && getClassVariance((PsiClass)owner, candidate.typeParameter) == Variance.INVARIANT) {
          return; // Nikolay despises List<? extends T>
        }
        PsiClassReferenceType extendsT = suggestMethodParameterType(candidate, true);
        PsiClassReferenceType superT = suggestMethodParameterType(candidate, false);
        Variance variance = checkParameterVarianceInMethodBody(candidate.methodParameter, candidate.method, candidate.typeParameter,
                                                               extendsT, superT);
        if (variance == Variance.CONTRAVARIANT && makesSenseToSuper(candidate)) {
          holder.registerProblem(typeElement, InspectionGadgetsBundle.message("bounded.wildcard.contravariant.descriptor"),
                                 new ReplaceWithQuestionTFix(isOverriddenOrOverrides(candidate.method), false));
        }
        if (variance == Variance.COVARIANT && makesSenseToExtend(candidate)) {
          holder.registerProblem(typeElement, InspectionGadgetsBundle.message("bounded.wildcard.covariant.descriptor"),
                                 new ReplaceWithQuestionTFix(isOverriddenOrOverrides(candidate.method), true));
        }
      }
    };
  }

  private static boolean makesSenseToExtend(VarianceCandidate candidate) {
    if (!(candidate.type instanceof PsiClassType)) return false;
    PsiClass aClass = ((PsiClassType)candidate.type).resolve();
    if (aClass == null) return false;
    return !aClass.hasModifierProperty(PsiModifier.FINAL) && !CommonClassNames.JAVA_LANG_OBJECT.equals(aClass.getQualifiedName());
  }

  private static boolean makesSenseToSuper(VarianceCandidate candidate) {
    return !TypeUtils.isJavaLangObject(candidate.type);
  }

  // void doProcess(Processor<T> processor)
  private static class VarianceCandidate {
    private final PsiMethod method; // doProcess
    private final PsiParameter methodParameter; // processor
    private final int methodParameterIndex; // 0
    private final PsiTypeParameter typeParameter; // Processor.T
    private final PsiType type; // T
    private final int typeParameterIndex; // 0 - index in "Processor".getTypeParameters()

    private VarianceCandidate(PsiParameter methodParameter,
                              PsiMethod method,
                              int methodParameterIndex, PsiTypeParameter typeParameter,
                              PsiType type,
                              int typeParameterIndex) {
      this.methodParameter = methodParameter;
      this.method = method;
      this.methodParameterIndex = methodParameterIndex;
      this.typeParameter = typeParameter;
      this.type = type;
      this.typeParameterIndex = typeParameterIndex;
    }

    private static VarianceCandidate findVarianceCandidate(@NotNull PsiTypeElement innerTypeElement) {
      PsiType type = innerTypeElement.getType();
      if (type instanceof PsiWildcardType || type instanceof PsiCapturedWildcardType) return null;
      PsiElement parent = innerTypeElement.getParent();
      if (!(parent instanceof PsiReferenceParameterList)) return null;
      PsiElement pp = parent.getParent();
      if (!(pp instanceof PsiJavaCodeReferenceElement)) return null;
      PsiJavaCodeReferenceElement refElement = (PsiJavaCodeReferenceElement)pp;
      if (!parent.equals(refElement.getParameterList())) return null;
      JavaResolveResult result = refElement.advancedResolve(false);
      if (!(result.getElement() instanceof PsiClass)) return null;
      PsiClass resolved = (PsiClass)result.getElement();
      int index = ArrayUtil.indexOf(((PsiReferenceParameterList)parent).getTypeParameterElements(), innerTypeElement);

      PsiElement p3 = pp.getParent();
      if (!(p3 instanceof PsiTypeElement)) return null;
      PsiElement p4 = p3.getParent();
      if (!(p4 instanceof PsiParameter)) return null;
      PsiParameter parameter = (PsiParameter)p4;
      PsiElement scope = parameter.getDeclarationScope();
      if (!(scope instanceof PsiMethod)) return null;

      PsiTypeParameter[] typeParameters = resolved.getTypeParameters();
      if (typeParameters.length <= index) return null;
      PsiTypeParameter typeParameter = typeParameters[index];

      PsiMethod method = (PsiMethod)scope;
      PsiParameterList parameterList = method.getParameterList();
      int parameterIndex = parameterList.getParameterIndex(parameter);
      if (parameterIndex == -1) return null;
      PsiParameter[] methodParameters = parameterList.getParameters();

      // check that if there is a super method, then it's parameterized similarly.
      // otherwise, it would make no sense to wildcardize "new Function<List<T>, T>(){ T apply(List<T> param) {...} }"
      // Oh, and make sure super methods are all modifyable, or it wouldn't make sense to report them
      if (!
      SuperMethodsSearch.search(method, null, true, true).forEach((MethodSignatureBackedByPsiMethod superMethod)-> {
        ProgressManager.checkCanceled();
        if (superMethod.getMethod() instanceof PsiCompiledElement) return false;
        // check not substituted super parameters
        PsiParameter[] superMethodParameters = superMethod.getMethod().getParameterList().getParameters();
        if (superMethodParameters.length != methodParameters.length) return false;
        PsiType superParameterType = superMethodParameters[parameterIndex].getType();
        if (!(superParameterType instanceof PsiClassType)) return false;
        PsiType[] superTypeParameters = ((PsiClassType)superParameterType).getParameters();
        return superTypeParameters.length == typeParameters.length;
      })) return null;
      return new VarianceCandidate(parameter, method, parameterIndex, typeParameter, type, index);
    }
  }

  private static class ReplaceWithQuestionTFix implements LocalQuickFix {
    private final boolean isOverriddenOrOverrides;
    private final boolean isExtends;

    ReplaceWithQuestionTFix(boolean isOverriddenOrOverrides, boolean isExtends) {
      this.isOverriddenOrOverrides = isOverriddenOrOverrides;
      this.isExtends = isExtends;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with '? " + (isExtends ? "extends" : "super") + "'";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor)  {
      PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiTypeElement) || !element.isValid() || element.getParent() == null || !element.isPhysical()) return;
      PsiTypeElement typeElement = (PsiTypeElement)element;

      VarianceCandidate candidate = VarianceCandidate.findVarianceCandidate(typeElement);
      if (candidate == null) return;
      PsiMethod method = candidate.method;

      PsiClassReferenceType clone = suggestMethodParameterType(candidate, isExtends);

      if (!isOverriddenOrOverrides) {
        PsiField field = findFieldAssignedFromMethodParameter(candidate.methodParameter, method);
        if (field != null) {
          replaceType(project, field.getTypeElement(), suggestMethodParameterType(candidate, isExtends));
        }

        PsiTypeElement methodParameterTypeElement = candidate.methodParameter.getTypeElement();
        replaceType(project, methodParameterTypeElement, clone);
        return;
      }

      int[] i = {0};
      List<ParameterInfoImpl> parameterInfos = ContainerUtil.map(method.getParameterList().getParameters(), p -> new ParameterInfoImpl(i[0]++, p.getName(), p.getType()));
      int index = method.getParameterList().getParameterIndex(candidate.methodParameter);
      if (index == -1) return;

      PsiMethod superMethod = SuperMethodWarningUtil.checkSuperMethod(method, RefactoringBundle.message("to.refactor"));
      if (superMethod == null) return;
      if (superMethod != method) {
        method = superMethod;
        PsiParameter superMethodParameter = method.getParameterList().getParameters()[candidate.methodParameterIndex];
        PsiClass paraClass = ((PsiClassType)superMethodParameter.getType()).resolve();
        PsiTypeParameter superTypeParameter = paraClass.getTypeParameters()[candidate.typeParameterIndex];
        PsiJavaCodeReferenceElement ref = superMethodParameter.getTypeElement().getInnermostComponentReferenceElement();
        PsiTypeElement[] typeElements = ref.getParameterList().getTypeParameterElements();
        PsiType type = typeElements[candidate.typeParameterIndex].getType();

        candidate = new VarianceCandidate(superMethodParameter, superMethod, candidate.methodParameterIndex, superTypeParameter, type,
                                          candidate.typeParameterIndex);
        i[0] = 0;
        parameterInfos = ContainerUtil.map(superMethod.getParameterList().getParameters(), p -> new ParameterInfoImpl(i[0]++, p.getName(), p.getType()));
        clone = suggestMethodParameterType(candidate, isExtends);
      }
      parameterInfos.set(index, new ParameterInfoImpl(index, candidate.methodParameter.getName(), clone));

      JavaChangeSignatureDialog
        dialog = JavaChangeSignatureDialog.createAndPreselectNew(project, method, parameterInfos, false, null/*todo?*/);
      dialog.setParameterInfos(parameterInfos);
      dialog.show();
      if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
        PsiField field = findFieldAssignedFromMethodParameter(candidate.methodParameter, method);
        if (field != null) {
          replaceType(project, field.getTypeElement(), suggestMethodParameterType(candidate, isExtends));
        }
      }
    }

    private static void replaceType(@NotNull Project project, @NotNull PsiTypeElement typeElement, @NotNull PsiType withType) {
      PsiElementFactory pf = PsiElementFactory.SERVICE.getInstance(project);
      PsiTypeElement newTypeElement = pf.createTypeElement(withType);
      WriteCommandAction.runWriteCommandAction(project, (Runnable)() -> typeElement.replace(newTypeElement));
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }
  }

  @NotNull
  private static PsiClassReferenceType suggestMethodParameterType(@NotNull VarianceCandidate candidate, boolean isExtends) {
    PsiType type = candidate.type;

    PsiManager psiManager = candidate.method.getManager();
    PsiElementFactory pf = PsiElementFactory.SERVICE.getInstance(psiManager.getProject());
    PsiTypeElement newInnerTypeElement = pf.createTypeElement(isExtends ? PsiWildcardType
      .createExtends(psiManager, type) : PsiWildcardType.createSuper(psiManager, type));

    PsiClassReferenceType methodParamType = (PsiClassReferenceType)candidate.methodParameter.getType();
    PsiClassReferenceType clone = new PsiClassReferenceType((PsiJavaCodeReferenceElement)methodParamType.getReference().copy(), methodParamType.getLanguageLevel());
    PsiTypeElement innerTypeElement = clone.getReference().getParameterList().getTypeParameterElements()[candidate.typeParameterIndex];

    innerTypeElement.replace(newInnerTypeElement);
    return clone;
  }

  private static boolean isOverriddenOrOverrides(@NotNull PsiMethod method) {
    if (method.isConstructor() ||
        method.hasModifierProperty(PsiModifier.PRIVATE) ||
        method.hasModifierProperty(PsiModifier.STATIC)) return false;

    if (method.hasModifierProperty(PsiModifier.ABSTRACT)) return true;

    boolean overrides = SuperMethodsSearch.search(method, null, true, false).findFirst() != null;
    return overrides || OverridingMethodsSearch.search(method).findFirst() != null;
  }

  private enum Variance {
    NOVARIANT,// none
    COVARIANT, // return type only
    CONTRAVARIANT, // method params only
    INVARIANT; // both

    @NotNull
    Variance combine(@NotNull Variance other) {
      return Variance.values()[ordinal() | other.ordinal()];
    }

    static {
      // otherwise bitmasks in combine() won't work
      assert NOVARIANT.ordinal() == 0;
      assert COVARIANT.ordinal() == 1;
      assert CONTRAVARIANT.ordinal() == 2;
      assert INVARIANT.ordinal() == 3;
    }
  }

  private static PsiField findFieldAssignedFromMethodParameter(@NotNull PsiParameter methodParameter, @NotNull PsiMethod method) {
    PsiCodeBlock methodBody = method.getBody();
    if (methodBody == null) return null;
    PsiField[] v = {null};

    ReferencesSearch.search(methodParameter, new LocalSearchScope(methodBody)).forEach(ref -> {
      ProgressManager.checkCanceled();
      v[0] = isAssignedToField(ref);
      return v[0] == null;
    });

    return v[0];
  }

  @NotNull
  private static Variance checkParameterVarianceInMethodBody(@NotNull PsiParameter methodParameter,
                                                             @NotNull PsiMethod method,
                                                             @NotNull PsiTypeParameter typeParameter,
                                                             @NotNull PsiClassReferenceType extendsT,
                                                             @NotNull PsiClassReferenceType superT) {
    PsiCodeBlock methodBody = method.getBody();
    if (methodBody == null) return Variance.INVARIANT;
    return getVariance(methodParameter, new LocalSearchScope(methodBody), null, method, typeParameter, extendsT, superT);
  }

  @NotNull
  private static Variance getVariance(@NotNull PsiElement element,
                                      @NotNull LocalSearchScope searchScope,
                                      @Nullable PsiElement ignoreUsagesIn,
                                      @NotNull PsiMethod containingMethod,
                                      @NotNull PsiTypeParameter typeParameter,
                                      @NotNull PsiClassReferenceType extendsT,
                                      @NotNull PsiClassReferenceType superT) {
    Variance[] v = {Variance.NOVARIANT};
    ReferencesSearch.search(element, searchScope).forEach(ref -> {
      ProgressManager.checkCanceled();
      if (PsiTreeUtil.isAncestor(ignoreUsagesIn, ref.getElement(), false)) return true;
      PsiMethod calledMethod = getMethodCallOnReference(ref);
      if (calledMethod == null) {
        if (isInPolyadicExpression(ref)) return true; // ignore "x == y"
        PsiField field = isAssignedToField(ref);
        if (field != null) {
          // check if e.g. "Processor<String> field" is used in "field.process(xxx)" only
          PsiElement ignoreUsagesInAssignment = PsiUtil.skipParenthesizedExprUp(ref.getElement().getParent());
          PsiClass fieldClass = field.getContainingClass();
          Variance fv = fieldClass == null ? Variance.INVARIANT :
                        getVariance(field, new LocalSearchScope(fieldClass), ignoreUsagesInAssignment, containingMethod, typeParameter, extendsT, superT);
          v[0] = v[0].combine(fv);
        }
        else if (isIteratedValueInForeachExpression(ref)) {
          // heuristics: "for (e in List<T>)" can be replaced with "for (e in List<? extends T>)"
          v[0] = v[0].combine(Variance.COVARIANT);
        }
        else {
          boolean canBeSuperT = isPassedToMethodWhichAlreadyAcceptsQuestionT(ref, superT, containingMethod);
          boolean canBeExtendsT = isPassedToMethodWhichAlreadyAcceptsQuestionT(ref, extendsT, containingMethod);
          if (canBeExtendsT && canBeSuperT) {
            return true; // ignore e.g. recursive call
          }
          // otherwise it can be just "foo(Object)" to which anything can be passed - not very interesting
          if (canBeSuperT != canBeExtendsT || v[0] != Variance.NOVARIANT) {
            if (canBeSuperT && (v[0] == Variance.NOVARIANT || v[0] == Variance.CONTRAVARIANT)) {
              v[0] = Variance.CONTRAVARIANT;
              return true;
            }
            if (canBeExtendsT && (v[0] == Variance.NOVARIANT || v[0] == Variance.COVARIANT)) {
              v[0] = Variance.COVARIANT;
              return true;
            }
          }
          // some strange usage. do not highlight
          v[0] = Variance.INVARIANT;
        }
      }
      else {
        Variance mv = getMethodSignatureVariance(calledMethod, typeParameter);
        v[0] = v[0].combine(mv);
      }
      return v[0] != Variance.INVARIANT;
    });
    return v[0];
  }

  private static boolean isPassedToMethodWhichAlreadyAcceptsQuestionT(@NotNull PsiReference ref,
                                                                      @NotNull PsiClassReferenceType suggestedMethodParameterType,
                                                                      @NotNull PsiMethod myself) {
    PsiElement refElement = ref.getElement();
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(refElement.getParent());
    if (!(parent instanceof PsiExpressionList)) return false;
    List<PsiExpression> exprs = Arrays.asList(((PsiExpressionList)parent).getExpressions());
    int index = ContainerUtil.indexOf(exprs, (Condition<PsiExpression>)(PsiExpression e) -> PsiTreeUtil.isAncestor(e, refElement, false));
    if (index == -1) return false;
    PsiElement parent2 = parent.getParent();
    JavaResolveResult result;
    if (parent2 instanceof PsiAnonymousClass) {
      PsiElement newExpression = parent2.getParent();
      if (newExpression instanceof PsiCall) {
        result = ((PsiCall)newExpression).resolveMethodGenerics();
      }
      else {
        return false;
      }
    }
    else if (parent2 instanceof PsiCallExpression) {
      result = ((PsiCallExpression)parent2).resolveMethodGenerics();
    }
    else {
      return false;
    }
    PsiElement resolved = result.getElement();
    if (!(resolved instanceof PsiMethod)) return false;
    PsiMethod method = (PsiMethod)resolved;
    if (method.getManager().areElementsEquivalent(method, myself)
        || MethodSignatureUtil.isSuperMethod(method, myself)) return true; // recursive call or super.foo() call
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length == 0 || parameters.length <= index && !method.isVarArgs()) return false;
    PsiParameter parameter = parameters[Math.min(index, parameters.length - 1)];
    PsiType paramType = result.getSubstitutor().substitute(parameter.getType());

    PsiType capturedSuggested = PsiUtil.captureToplevelWildcards(suggestedMethodParameterType, refElement);
    return TypeConversionUtil.isAssignable(paramType, capturedSuggested);
  }

  private static PsiMethod getMethodCallOnReference(@NotNull PsiReference ref) {
    PsiElement refElement = ref.getElement();
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(refElement.getParent());
    if (!(parent instanceof PsiReferenceExpression)) return null;
    PsiReferenceExpression refExpression = (PsiReferenceExpression)parent;
    if (!refElement.equals(PsiUtil.skipParenthesizedExprDown(refExpression.getQualifierExpression()))) {
      return null;
    }
    // "foo(parameter::consume)" variance is equivalent to "parameter.consume(xxx)"
    if (refExpression instanceof PsiMethodReferenceExpression) {
      JavaResolveResult result = refExpression.advancedResolve(false);
      PsiElement resolved = result.getElement();
      if (resolved instanceof PsiMethod) {
        return (PsiMethod)resolved;
      }
    }
    PsiElement p = refExpression.getParent();
    if (p instanceof PsiMethodCallExpression) {
      JavaResolveResult result = ((PsiMethodCallExpression)p).resolveMethodGenerics();
      return (PsiMethod)result.getElement();
    }
    return null;
  }

  private static boolean isIteratedValueInForeachExpression(@NotNull PsiReference ref) {
    PsiElement refElement = ref.getElement();
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(refElement.getParent());
    if (!(parent instanceof PsiForeachStatement)) return false;
    PsiExpression iteratedValue = PsiUtil.skipParenthesizedExprDown(((PsiForeachStatement)parent).getIteratedValue());
    if (iteratedValue != ref) return false;

    PsiType type = iteratedValue.getType();
    if (!(type instanceof PsiClassType) || !InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_ITERABLE)) return false;
    PsiClassType.ClassResolveResult result = ((PsiClassType)type).resolveGenerics();
    PsiClass aClass = result.getElement();
    return aClass != null;
  }

  private static boolean isInPolyadicExpression(@NotNull PsiReference ref) {
    PsiElement refElement = ref.getElement();
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(refElement.getParent());
    return parent instanceof PsiPolyadicExpression;
  }

  private static PsiField isAssignedToField(@NotNull PsiReference ref) {
    PsiElement refElement = ref.getElement();
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(refElement.getParent());
    if (!(parent instanceof PsiAssignmentExpression) || ((PsiAssignmentExpression)parent).getOperationTokenType() != JavaTokenType.EQ) return null;
    PsiExpression r = ((PsiAssignmentExpression)parent).getRExpression();
    if (!PsiTreeUtil.isAncestor(r, refElement, false)) return null;
    PsiExpression l = PsiUtil.skipParenthesizedExprDown(((PsiAssignmentExpression)parent).getLExpression());
    if (!(l instanceof PsiReferenceExpression)) return null;
    PsiReferenceExpression lExpression = (PsiReferenceExpression)l;
    PsiExpression lQualifier = PsiUtil.skipParenthesizedExprDown(lExpression.getQualifierExpression());
    if (lQualifier != null && !(lQualifier instanceof PsiThisExpression)) return null;
    PsiElement field = lExpression.resolve();
    if (!(field instanceof PsiField)) return null;
    return (PsiField)field;
  }


  @NotNull
  private static Variance getMethodSignatureVariance(@NotNull PsiMethod method, @NotNull PsiTypeParameter typeParameter) {
    PsiTypeParameterListOwner owner = typeParameter.getOwner();
    PsiClass methodClass = method.getContainingClass();
    if (methodClass == null || !(owner instanceof PsiClass)) return Variance.INVARIANT;
    PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(methodClass, (PsiClass)owner, PsiSubstitutor.EMPTY);

    Variance r = Variance.NOVARIANT;
    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      PsiType type = parameter.getType();
      if (typeResolvesTo(type, typeParameter, superClassSubstitutor)) {
        r = Variance.CONTRAVARIANT;
      }
      else if (containsDeepIn(type, typeParameter, superClassSubstitutor)) {
        return Variance.INVARIANT;
      }
    }
    PsiType returnType = method.getReturnType();

    if (returnType != null && !TypeConversionUtil.isPrimitiveAndNotNullOrWrapper(returnType)) {
      if (typeResolvesTo(returnType, typeParameter, superClassSubstitutor)) {
        r = r.combine(Variance.COVARIANT);
      }
      else if (isComposeMethod(method, returnType, typeParameter, superClassSubstitutor)) {
        // ignore
      }
      else if (containsDeepIn(returnType, typeParameter, superClassSubstitutor)) {
        r = Variance.INVARIANT;
      }
    }
    return r;
  }

  // java.util.Function contains "<V> Function<T, V> andThen(Function<? super R, ? extends V> after)" which doesn't preclude it to be contravariant on T
  private static boolean isComposeMethod(@NotNull PsiMethod method,
                                         @NotNull PsiType returnType,
                                         @NotNull PsiTypeParameter typeParameter,
                                         @NotNull PsiSubstitutor superClassSubstitutor) {
    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null || !(returnType instanceof PsiClassType) || !containingClass.equals(((PsiClassType)returnType).resolve())) {
      return false;
    }
    PsiTypeParameterListOwner typeParameterOwner = typeParameter.getOwner();
    PsiTypeParameterList typeParameterList = typeParameterOwner == null ? null : typeParameterOwner.getTypeParameterList();
    int index = typeParameterList == null ? -1 : typeParameterList.getTypeParameterIndex(typeParameter);

    PsiType[] parameters = ((PsiClassType)returnType).getParameters();
    if (index == -1 || parameters.length <= index) return false;
    return typeResolvesTo(parameters[index], typeParameter, superClassSubstitutor);
  }

  private static boolean containsDeepIn(@NotNull PsiType rootType,
                                        @NotNull PsiTypeParameter parameter,
                                        @NotNull PsiSubstitutor superClassSubstitutor) {
    return rootType.accept(new PsiTypeVisitor<Boolean>() {
      boolean topLevel = true;
      @Nullable
      @Override
      public Boolean visitClassType(PsiClassType classType) {
        for (PsiType param : classType.getParameters()) {
          if (param.accept(this)) return true;
        }
        return super.visitClassType(classType);
      }

      @Nullable
      @Override
      public Boolean visitArrayType(PsiArrayType arrayType) {
        return arrayType.getComponentType().accept(this);
      }

      @Override
      public Boolean visitType(PsiType type) {
        return typeResolvesTo(type, parameter, superClassSubstitutor);
      }
    });
  }

  private static boolean typeResolvesTo(@NotNull PsiType type, @NotNull PsiTypeParameter typeParameter, @NotNull PsiSubstitutor superClassSubstitutor) {
    PsiType substituted = superClassSubstitutor.substitute(type);
    if (!(substituted instanceof PsiClassType))  return false;
    PsiClassType.ClassResolveResult result = ((PsiClassType)substituted).resolveGenerics();
    return typeParameter.equals(result.getElement()) && result.getSubstitutor().equals(PsiSubstitutor.EMPTY);
  }

  @NotNull
  private static Variance getClassVariance(@NotNull PsiClass aClass, @NotNull PsiTypeParameter typeParameter) {
    Variance result = Variance.NOVARIANT;
    for (PsiMethod method : aClass.getAllMethods()) {
      PsiClass containingClass = method.getContainingClass();
      if (containingClass == null ||
          CommonClassNames.JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName()) ||
          method.hasModifierProperty(PsiModifier.STATIC)) {
        continue;
      }
      result = result.combine(getMethodSignatureVariance(method, typeParameter));
      if (result == Variance.INVARIANT) break;
    }
    return result;
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    myReportInvariantClassesCB.setSelected(REPORT_INVARIANT_CLASSES);
    return myPanel;
  }

  @Override
  public void readSettings(@NotNull Element node) {
    super.readSettings(node);
    myReportInvariantClassesCB.addItemListener(__ -> REPORT_INVARIANT_CLASSES = myReportInvariantClassesCB.isSelected());
  }
}
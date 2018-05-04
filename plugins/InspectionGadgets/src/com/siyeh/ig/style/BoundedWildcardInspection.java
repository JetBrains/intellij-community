/*
 * Copyright 2006-2018 Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.codeInspection.*;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
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
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * {@code "void process(Processor<T> p)"  -> "void process(Processor<? super T> p)"}
 */
public class BoundedWildcardInspection extends AbstractBaseJavaLocalInspectionTool {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("bounded.wildcard.display.name");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    return new CoVarianceVisitor(holder, isOnTheFly);
  }

  private static class CoVarianceVisitor extends JavaElementVisitor {
    private final ProblemsHolder myHolder;
    private final boolean myIsOnTheFly;

    CoVarianceVisitor(ProblemsHolder holder, boolean isOnTheFly) {
      myHolder = holder;
      myIsOnTheFly = isOnTheFly;
    }

    @Override
    public void visitTypeElement(PsiTypeElement typeElement) {
      VarianceCandidate candidate = VarianceCandidate.findVarianceCandidate(typeElement);
      if (candidate == null) return;
      PsiClassReferenceType extendsT = suggestMethodParameterType(candidate, true);
      PsiClassReferenceType superT = suggestMethodParameterType(candidate, false);
      Variance variance = checkParameterVarianceInMethodBody(candidate.methodParameter, candidate.method, candidate.typeParameter,
                                                             extendsT, superT);
      if (variance == Variance.CONTRAVARIANT && makesSenseToSuper(candidate)) {
        myHolder.registerProblem(typeElement, InspectionGadgetsBundle.message("bounded.wildcard.contravariant.descriptor"), new ReplaceWithQuestionTFix(isOverriddenOrOverrides(candidate.method), false));
      }
      if (variance == Variance.COVARIANT && makesSenseToExtend(candidate)) {
        myHolder.registerProblem(typeElement, InspectionGadgetsBundle.message("bounded.wildcard.covariant.descriptor"), new ReplaceWithQuestionTFix(isOverriddenOrOverrides(candidate.method), true));
      }
    }
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
      if (!
      SuperMethodsSearch.search(method, null, true, true).forEach((MethodSignatureBackedByPsiMethod superMethod)-> {
        ProgressManager.checkCanceled();
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
        PsiElementFactory pf = PsiElementFactory.SERVICE.getInstance(project);
        PsiTypeElement methodParameterTypeElement = candidate.methodParameter.getTypeElement();
        PsiTypeElement newTypeElement = pf.createTypeElement(clone);
        WriteCommandAction.runWriteCommandAction(project, (Runnable)() -> methodParameterTypeElement.replace(newTypeElement));
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

  @NotNull
  private static Variance checkParameterVarianceInMethodBody(@NotNull PsiParameter methodParameter,
                                                             @NotNull PsiMethod method,
                                                             @NotNull PsiTypeParameter typeParameter,
                                                             PsiClassReferenceType extendsT,
                                                             PsiClassReferenceType superT) {
    PsiCodeBlock methodBody = method.getBody();
    if (methodBody == null) return Variance.INVARIANT;
    Variance[] v = {Variance.NOVARIANT};
    ReferencesSearch.search(methodParameter, new LocalSearchScope(methodBody)).forEach(ref -> {
      PsiMethod calledMethod = getMethodCallOnReference(ref);
      if (calledMethod == null) {
        if (isInComparison(ref)) return true; // ignore "x == y"
        if (isIteratedValueInForeachExpression(ref)) {
          // heuristics: "for (e in List<T>)" can be replaced with "for (e in List<? extends T>)"
          v[0] = v[0].combine(Variance.COVARIANT);
        }
        else {
          boolean canBeSuperT = isPassedToMethodWhichAlreadyAcceptsQuestionT(ref, superT, method);
          boolean canBeExtendsT = isPassedToMethodWhichAlreadyAcceptsQuestionT(ref, extendsT, method);
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
        Variance mv = checkVarianceInMethodSignature(calledMethod, typeParameter);
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
    if (!(parent2 instanceof PsiCallExpression)) return false;
    JavaResolveResult result = ((PsiCallExpression)parent2).resolveMethodGenerics();
    PsiMethod method = (PsiMethod)result.getElement();
    if (method == null) return false;
    if (method.getManager().areElementsEquivalent(method, myself)) return true; // recursive call
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length == 0 || parameters.length <= index && !method.isVarArgs()) return false;
    PsiParameter parameter = parameters[Math.min(index, parameters.length - 1)];
    PsiType paramType = result.getSubstitutor().substitute(parameter.getType());

    PsiType capturedSuggested = PsiUtil.captureToplevelWildcards(suggestedMethodParameterType, refElement);
    return TypeConversionUtil.isAssignable(paramType, capturedSuggested);
  }

  private static PsiMethod getMethodCallOnReference(PsiReference ref) {
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

  private static boolean isIteratedValueInForeachExpression(PsiReference ref) {
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

  private static boolean isInComparison(PsiReference ref) {
    PsiElement refElement = ref.getElement();
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(refElement.getParent());
    return parent instanceof PsiPolyadicExpression;
  }

  @NotNull
  private static Variance checkVarianceInMethodSignature(@NotNull PsiMethod method, @NotNull PsiTypeParameter typeParameter) {
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
    PsiType type = method.getReturnType();

    if (type != null && !TypeConversionUtil.isPrimitiveAndNotNullOrWrapper(type)) {
      if (typeResolvesTo(type, typeParameter, superClassSubstitutor)) {
        r = r.combine(Variance.COVARIANT);
      }
      else if (containsDeepIn(type, typeParameter, superClassSubstitutor)) {
        r = Variance.INVARIANT;
      }
    }
    return r;
  }

  private static boolean containsDeepIn(@NotNull PsiType type,
                                        @NotNull PsiTypeParameter parameter,
                                        @NotNull PsiSubstitutor superClassSubstitutor) {
    return type.accept(new PsiTypeVisitor<Boolean>() {
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
}
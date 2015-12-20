/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyMethodResult;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrReferenceExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.GrMethodComparator;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.*;

@SuppressWarnings("Duplicates")
class GroovyResolverProcessorImpl extends GroovyResolverProcessor implements GrMethodComparator.Context {

  private final boolean myIsPartOfFqn;

  GroovyResolverProcessorImpl(@NotNull final GrReferenceExpression ref) {
    super(ref, null);
    myIsPartOfFqn = ResolveUtil.isPartOfFQN(ref);
  }

  @NotNull
  public List<GroovyResolveResult> getCandidates() {
    Pair<Boolean, List<GroovyResolveResult>> candidates;

    // return package if whole ref text is valid class name
    if (myAcceptableKinds.contains(GroovyResolveKind.PACKAGE) && myIsPartOfFqn) {
      candidates = getCandidates(GroovyResolveKind.PACKAGE);
      if (candidates.first) {
        final GroovyResolveResult candidate = candidates.second.get(0);
        final PsiElement element = candidate.getElement();
        assert element instanceof PsiPackage;
        final GrReferenceExpressionImpl topRef = getContextReferenceExpression(myRef);
        if (topRef != null) {
          final String fqn = topRef.getTextSkipWhiteSpaceAndComments();
          if (JavaPsiFacade.getInstance(myRef.getProject()).findClass(fqn, myRef.getResolveScope()) != null) {
            return candidates.second;
          }
        }
      }
    }

    candidates = getCandidates(GroovyResolveKind.VARIABLE);
    if (candidates.first) {
      return candidates.second;
    }

    candidates = getCandidates(GroovyResolveKind.FIELD);
    if (candidates.first) {
      assert candidates.second.size() == 1;
      final GroovyResolveResult candidate = candidates.second.get(0);
      final PsiElement element = candidate.getElement();
      if (element instanceof PsiField) {
        final PsiClass containingClass = ((PsiField)element).getContainingClass();
        if (containingClass != null && PsiUtil.getContextClass(myRef) == containingClass) return candidates.second;
      }
      else if (!(element instanceof GrBindingVariable)) {
        return candidates.second;
      }
    }

    if (myIsPartOfFqn) {
      candidates = getCandidates(GroovyResolveKind.PACKAGE, GroovyResolveKind.CLASS);
      if (candidates.first) {
        return candidates.second;
      }
    }

    candidates = getCandidates(GroovyResolveKind.METHOD);
    if (candidates.first) {
      final List<GroovyResolveResult> results = filterMethodCandidates(candidates.second);
      return myRef.hasMemberPointer()? collapseReflectedMethods(results) : results;
    }

    candidates = getCandidates(GroovyResolveKind.PROPERTY);
    if (candidates.first) {
      final List<GroovyResolveResult> results = candidates.second;
      return results.size() <= 1 ? results : ContainerUtil.newSmartList(candidates.second.get(0));
    }

    candidates = getCandidates(GroovyResolveKind.FIELD);
    if (candidates.first) {
      return candidates.second;
    }

    candidates = getCandidates(GroovyResolveKind.PACKAGE, GroovyResolveKind.CLASS);
    if (candidates.first) {
      return candidates.second;
    }

    candidates = getCandidates(GroovyResolveKind.PROPERTY);
    if (candidates.first) {
      return candidates.second;
    }

    candidates = getCandidates(GroovyResolveKind.BINDING);
    if (candidates.first) {
      return candidates.second;
    }

    for (GroovyResolveKind kind : myAcceptableKinds) {
      Collection<GroovyResolveResult> results = myInapplicableCandidates.get(kind);
      if (!results.isEmpty()) {
        return ContainerUtil.newArrayList(ResolveUtil.filterSameSignatureCandidates(
          filterCorrectParameterCount(results)
        ));
      }
    }

    return Collections.emptyList();
  }

  private static GrReferenceExpressionImpl getContextReferenceExpression(GrReferenceExpression ref) {
    final PsiElement firstNonReferenceExprParent = PsiTreeUtil.skipParentsOfType(ref, GrReferenceExpressionImpl.class);
    return (GrReferenceExpressionImpl)PsiTreeUtil.findFirstParent(ref, new Condition<PsiElement>() {
      @Override
      public boolean value(PsiElement parent) {
        return parent.getParent() == firstNonReferenceExprParent && parent instanceof GrReferenceExpressionImpl;
      }
    });
  }

  private List<GroovyResolveResult> filterCorrectParameterCount(Collection<GroovyResolveResult> candidates) {
    if (myArgumentTypes == null) return ContainerUtil.newArrayList(candidates);
    final List<GroovyResolveResult> result = ContainerUtil.newSmartList();
    for (GroovyResolveResult candidate : candidates) {
      if (candidate instanceof GroovyMethodResult) {
        if (((GroovyMethodResult)candidate).getElement().getParameterList().getParametersCount() == myArgumentTypes.length) {
          result.add(candidate);
        }
      }
      else {
        result.add(candidate);
      }
    }
    if (!result.isEmpty()) return result;
    return ContainerUtil.newArrayList(candidates);
  }

  private List<GroovyResolveResult> filterMethodCandidates(List<GroovyResolveResult> candidates) {
    if (candidates.size() <= 1) return candidates;

    final List<GroovyResolveResult> results = ContainerUtil.newArrayList();
    final Iterator<GroovyResolveResult> itr = candidates.iterator();

    results.add(itr.next());

    Outer:
    while (itr.hasNext()) {
      final GroovyResolveResult resolveResult = itr.next();
      if (resolveResult instanceof GroovyMethodResult) {
        for (Iterator<GroovyResolveResult> iterator = results.iterator(); iterator.hasNext(); ) {
          final GroovyResolveResult otherResolveResult = iterator.next();
          if (otherResolveResult instanceof GroovyMethodResult) {
            int res = compareMethods((GroovyMethodResult)resolveResult, (GroovyMethodResult)otherResolveResult);
            if (res > 0) {
              continue Outer;
            }
            else if (res < 0) {
              iterator.remove();
            }
          }
        }
      }

      results.add(resolveResult);
    }

    return results;
  }

  /**
   * @return 1 if second is more preferable
   * 0 if methods are equal
   * -1 if first is more preferable
   */
  private int compareMethods(@NotNull GroovyMethodResult result1, @NotNull GroovyMethodResult result2) {
    final PsiMethod method1 = result1.getElement();
    final PsiMethod method2 = result2.getElement();

    if (!method1.getName().equals(method2.getName())) return 0;

    boolean firstIsPreferable = secondMethodIsPreferable(result2, result1);
    boolean secondIsPreferable = secondMethodIsPreferable(result1, result2);

    if (secondIsPreferable) {
      if (firstIsPreferable) {
        if (method2 instanceof GrGdkMethod && !(method1 instanceof GrGdkMethod)) {
          return -1;
        }
      }
      return 1;
    }

    if (firstIsPreferable) {
      return -1;
    }

    return 0;
  }

  //method1 has more general parameter types thn method2
  private boolean secondMethodIsPreferable(@NotNull GroovyMethodResult result1, @NotNull GroovyMethodResult result2) {
    PsiMethod method1 = result1.getElement();
    PsiSubstitutor substitutor1 = result1.getSubstitutor(false);
    PsiElement resolveContext1 = result1.getCurrentFileResolveContext();
    PsiMethod method2 = result2.getElement();
    PsiSubstitutor substitutor2 = result2.getSubstitutor(false);
    PsiElement resolveContext2 = result2.getCurrentFileResolveContext();

    final Boolean custom = GrMethodComparator.checkDominated(method1, substitutor1, method2, substitutor2, this);
    if (custom != null) return custom;

    PsiType[] argTypes = myArgumentTypes;
    if (method1 instanceof GrGdkMethod && method2 instanceof GrGdkMethod) {
      method1 = ((GrGdkMethod)method1).getStaticMethod();
      method2 = ((GrGdkMethod)method2).getStaticMethod();
      if (myArgumentTypes != null) {
        argTypes = PsiType.createArray(argTypes.length + 1);
        System.arraycopy(myArgumentTypes, 0, argTypes, 1, myArgumentTypes.length);
        argTypes[0] = myThisType;
      }
    }
    else if (method1 instanceof GrGdkMethod) {
      return true;
    }
    else if (method2 instanceof GrGdkMethod) {
      return false;
    }

    PsiParameter[] params1 = method1.getParameterList().getParameters();
    PsiParameter[] params2 = method2.getParameterList().getParameters();
    if (argTypes == null && params1.length != params2.length) return false;

    if (params1.length < params2.length) {
      if (params1.length == 0) return false;
      final PsiType lastType = params1[params1.length - 1].getType(); //varargs applicability
      return lastType instanceof PsiArrayType;
    }

    for (int i = 0; i < params2.length; i++) {
      final PsiType ptype1 = params1[i].getType();
      final PsiType ptype2 = params2[i].getType();
      PsiType type1 = substitutor1.substitute(ptype1);
      PsiType type2 = substitutor2.substitute(ptype2);

      if (argTypes != null && argTypes.length > i) {
        PsiType argType = argTypes[i];
        if (argType != null) {
          final boolean converts1 = TypesUtil.isAssignableWithoutConversions(TypeConversionUtil.erasure(type1), argType, myRef);
          final boolean converts2 = TypesUtil.isAssignableWithoutConversions(TypeConversionUtil.erasure(type2), argType, myRef);
          if (converts1 != converts2) {
            return converts2;
          }

          // see groovy.lang.GroovyCallable
          if (TypesUtil.resolvesTo(type1, CommonClassNames.JAVA_UTIL_CONCURRENT_CALLABLE) &&
              TypesUtil.resolvesTo(type2, CommonClassNames.JAVA_LANG_RUNNABLE)) {
            if (InheritanceUtil.isInheritor(argType, GroovyCommonClassNames.GROOVY_LANG_GROOVY_CALLABLE)) return true;
          }
        }
      }

      if (!typesAgree(TypeConversionUtil.erasure(ptype1), TypeConversionUtil.erasure(ptype2))) return false;

      if (resolveContext1 != null && resolveContext2 == null) {
        return !(TypesUtil.resolvesTo(type1, CommonClassNames.JAVA_LANG_OBJECT) &&
                 TypesUtil.resolvesTo(type2, CommonClassNames.JAVA_LANG_OBJECT));
      }

      if (resolveContext1 == null && resolveContext2 != null) {
        return true;
      }
    }

    if (!(method1 instanceof SyntheticElement) && !(method2 instanceof SyntheticElement)) {
      final PsiType returnType1 = substitutor1.substitute(method1.getReturnType());
      final PsiType returnType2 = substitutor2.substitute(method2.getReturnType());

      if (!TypesUtil.isAssignableWithoutConversions(returnType1, returnType2, myRef) &&
          TypesUtil.isAssignableWithoutConversions(returnType2, returnType1, myRef)) {
        return false;
      }
    }

    return true;
  }

  private boolean typesAgree(@NotNull PsiType type1, @NotNull PsiType type2) {
    if (argumentsSupplied() && type1 instanceof PsiArrayType && !(type2 instanceof PsiArrayType)) {
      type1 = ((PsiArrayType)type1).getComponentType();
    }
    return argumentsSupplied() ? //resolve, otherwise same_name_variants
           TypesUtil.isAssignableWithoutConversions(type1, type2, myRef) :
           type1.equals(type2);
  }

  @Contract(pure = true)
  private boolean argumentsSupplied() {
    return myArgumentTypes != null;
  }

  @Nullable
  @Override
  public PsiType[] getArgumentTypes() {
    return myArgumentTypes;
  }

  @Nullable
  @Override
  public PsiType[] getTypeArguments() {
    return myTypeArguments;
  }

  @Nullable
  @Override
  public PsiType getThisType() {
    return myThisType;
  }

  @NotNull
  @Override
  public PsiElement getPlace() {
    return myRef;
  }

  private static List<GroovyResolveResult> collapseReflectedMethods(Collection<GroovyResolveResult> candidates) {
    Set<GrMethod> visited = ContainerUtil.newHashSet();
    List<GroovyResolveResult> collapsed = ContainerUtil.newArrayList();
    for (GroovyResolveResult result : candidates) {
      PsiElement element = result.getElement();
      if (element instanceof GrReflectedMethod) {
        GrMethod baseMethod = ((GrReflectedMethod)element).getBaseMethod();
        if (visited.add(baseMethod)) {
          collapsed.add(PsiImplUtil.reflectedToBase(result, baseMethod, (GrReflectedMethod)element));
        }
      } else {
        collapsed.add(result);
      }
    }
    return collapsed;
  }
}

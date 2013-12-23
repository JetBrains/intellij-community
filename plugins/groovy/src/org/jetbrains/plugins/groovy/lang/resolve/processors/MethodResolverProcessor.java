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

package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.psi.*;
import com.intellij.psi.scope.JavaScopeProcessorEvent;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.gpp.GppClosureParameterTypeProvider;
import org.jetbrains.plugins.groovy.gpp.GppTypeConverter;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrGdkMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.GrMethodComparator;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.*;

/**
 * @author ven
 */
public class MethodResolverProcessor extends ResolverProcessor implements GrMethodComparator.Context {
  private final PsiType myThisType;

  @Nullable
  private final PsiType[] myArgumentTypes;

  private final boolean myAllVariants;

  protected final Set<GroovyResolveResult> myInapplicableCandidates = new LinkedHashSet<GroovyResolveResult>();

  private final boolean myIsConstructor;

  private boolean myStopExecuting = false;

  private final boolean myByShape;
  
  private final SubstitutorComputer mySubstitutorComputer;

  private final boolean myTypedContext;

  public MethodResolverProcessor(@Nullable String name,
                                 @NotNull PsiElement place,
                                 boolean isConstructor,
                                 @Nullable PsiType thisType,
                                 @Nullable PsiType[] argumentTypes,
                                 @Nullable PsiType[] typeArguments) {
    this(name, place, isConstructor, thisType, argumentTypes, typeArguments, false, false);
  }

  public MethodResolverProcessor(@Nullable String name,
                                 @NotNull PsiElement place,
                                 boolean isConstructor,
                                 @Nullable PsiType thisType,
                                 @Nullable PsiType[] argumentTypes,
                                 @Nullable PsiType[] typeArguments,
                                 boolean allVariants,
                                 final boolean byShape) {
    super(name, RESOLVE_KINDS_METHOD_PROPERTY, place, PsiType.EMPTY_ARRAY);
    myIsConstructor = isConstructor;
    myThisType = thisType;
    myArgumentTypes = argumentTypes;
    myAllVariants = allVariants;
    myByShape = byShape;

    mySubstitutorComputer = new SubstitutorComputer(thisType, argumentTypes, typeArguments, allVariants, place, myPlace.getParent());
    myTypedContext = GppTypeConverter.hasTypedContext(myPlace);
  }


  public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
    if (myStopExecuting) {
      return false;
    }
    PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);
    if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) element;

      if (method.isConstructor() != myIsConstructor) return true;
      if (substitutor == null) substitutor = PsiSubstitutor.EMPTY;
      if (!myByShape) {
        substitutor = mySubstitutorComputer.obtainSubstitutor(substitutor, method, state);
      }

      PsiElement resolveContext = state.get(RESOLVE_CONTEXT);
      final SpreadState spreadState = state.get(SpreadState.SPREAD_STATE);

      boolean isAccessible = isAccessible(method);
      boolean isStaticsOK = isStaticsOK(method, resolveContext, true);
      boolean isApplicable = PsiUtil.isApplicable(myArgumentTypes, method, substitutor, myPlace, myByShape);
      boolean isValidResult = isStaticsOK && isAccessible && isApplicable;

      GroovyResolveResultImpl candidate = new GroovyResolveResultImpl(method, resolveContext, spreadState, substitutor, isAccessible, isStaticsOK, false, isValidResult);

      if (!myAllVariants && isValidResult) {
        addCandidate(candidate);
      }
      else {
        myInapplicableCandidates.add(candidate);
      }

      return true;
    }

    return true;
  }

  @NotNull
  public GroovyResolveResult[] getCandidates() {
    if (!myAllVariants && super.hasCandidates()) {
      return filterCandidates();
    }
    if (!myInapplicableCandidates.isEmpty()) {
      final Set<GroovyResolveResult> resultSet =
        myAllVariants ? myInapplicableCandidates : filterCorrectParameterCount(myInapplicableCandidates);
      return ResolveUtil.filterSameSignatureCandidates(resultSet);
    }
    return GroovyResolveResult.EMPTY_ARRAY;
  }

  private Set<GroovyResolveResult> filterCorrectParameterCount(Set<GroovyResolveResult> candidates) {
    if (myArgumentTypes == null) return candidates;
    Set<GroovyResolveResult> result = new HashSet<GroovyResolveResult>();
    for (GroovyResolveResult candidate : candidates) {
      final PsiElement element = candidate.getElement();
      if (element instanceof PsiMethod && ((PsiMethod)element).getParameterList().getParametersCount() == myArgumentTypes.length) {
        result.add(candidate);
      }
    }
    if (result.size() > 0) return result;
    return candidates;
  }

  private GroovyResolveResult[] filterCandidates() {
    List<GroovyResolveResult> array = getCandidatesInternal();
    if (array.size() == 1) return array.toArray(new GroovyResolveResult[array.size()]);

    List<GroovyResolveResult> result = new ArrayList<GroovyResolveResult>();

    Iterator<GroovyResolveResult> itr = array.iterator();

    result.add(itr.next());

    Outer:
    while (itr.hasNext()) {
      GroovyResolveResult resolveResult = itr.next();
      PsiElement currentElement = resolveResult.getElement();
      if (currentElement instanceof PsiMethod) {
        PsiMethod currentMethod = (PsiMethod) currentElement;
        for (Iterator<GroovyResolveResult> iterator = result.iterator(); iterator.hasNext();) {
          final GroovyResolveResult otherResolveResult = iterator.next();
          PsiElement element = otherResolveResult.getElement();
          if (element instanceof PsiMethod) {
            PsiMethod method = (PsiMethod) element;
            final int res = compareMethods(currentMethod, resolveResult.getSubstitutor(), method, otherResolveResult.getSubstitutor());
            if (res > 0) {
              continue Outer;
            }
            else if (res < 0) {
              iterator.remove();
            }
          }
        }
      }

      result.add(resolveResult);
    }

    return result.toArray(new GroovyResolveResult[result.size()]);
  }

  private int compareMethods(PsiMethod method1,
                             PsiSubstitutor substitutor1,
                             PsiMethod method2,
                             PsiSubstitutor substitutor2) {
    if (!method1.getName().equals(method2.getName())) return 0;

    if (myTypedContext) {
      if (isMoreConcreteThan(method2, substitutor2, method1, substitutor1, (GroovyPsiElement)myPlace)) {
        return 1;
      }

      if (isMoreConcreteThan(method1, substitutor1, method2, substitutor2, (GroovyPsiElement)myPlace)) {
        return -1;
      }
    }

    if (dominated(method1, substitutor1, method2, substitutor2)) {
      if (dominated(method2, substitutor2, method1, substitutor1)) {
        if (method2 instanceof GrGdkMethod && !(method1 instanceof GrGdkMethod)) {
          return -1;
        }
      }
      return 1;
    }
    if (dominated(method2, substitutor2, method1, substitutor1)) {
      return -1;
    }

    return 0;
  }
  
  private static boolean isMoreConcreteThan(@NotNull PsiMethod method,
                                            @NotNull final PsiSubstitutor substitutor,
                                            @NotNull PsiMethod another,
                                            @NotNull PsiSubstitutor anotherSubstitutor,
                                            @NotNull GroovyPsiElement context) {
    if (another instanceof GrGdkMethodImpl && another.getName().equals(method.getName())) {
      final PsiParameter[] plusParameters = method.getParameterList().getParameters();
      final PsiParameter[] defParameters = another.getParameterList().getParameters();

      final PsiType[] paramTypes = new PsiType[plusParameters.length];
      for (int i = 0; i < paramTypes.length; i++) {
        paramTypes[i] = eliminateOneMethodInterfaces(plusParameters[i], defParameters, i);

      }

      final GrClosureSignature gdkSignature = GrClosureSignatureUtil.createSignature(another, anotherSubstitutor);
      if (GrClosureSignatureUtil.isSignatureApplicable(gdkSignature, paramTypes, context)) {
        return true;
      }
    }
    return false;
  }

  private static PsiType eliminateOneMethodInterfaces(PsiParameter plusParameter, PsiParameter[] gdkParameters, int i) {
    PsiType type = plusParameter.getType();
    if (i < gdkParameters.length &&
        gdkParameters[i].getType().equalsToText(GroovyCommonClassNames.GROOVY_LANG_CLOSURE) &&
        GppClosureParameterTypeProvider.findSingleAbstractMethodSignature(type) != null) {
      return gdkParameters[i].getType();
    }
    return type;
  }


  //method1 has more general parameter types thn method2
  private boolean dominated(PsiMethod method1,
                            PsiSubstitutor substitutor1,
                            PsiMethod method2,
                            PsiSubstitutor substitutor2) {
    if (!method1.getName().equals(method2.getName())) return false;

    final Boolean custom = GrMethodComparator.checkDominated(method1, substitutor1, method2, substitutor2, this);
    if (custom != null) return custom;

    PsiType[] argTypes = myArgumentTypes;
    if (method1 instanceof GrGdkMethod && method2 instanceof GrGdkMethod) {
      method1 = ((GrGdkMethod)method1).getStaticMethod();
      method2 = ((GrGdkMethod)method2).getStaticMethod();
      if (myArgumentTypes != null) {
        argTypes = new PsiType[argTypes.length + 1];
        System.arraycopy(myArgumentTypes, 0, argTypes, 1, myArgumentTypes.length);
        argTypes[0] = myThisType;
      }
    }

    if (myIsConstructor && argTypes != null && argTypes.length == 1) {
      if (method1.getParameterList().getParametersCount() == 0) return true;
      if (method2.getParameterList().getParametersCount() == 0) return false;
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
          final boolean converts1 = TypesUtil.isAssignableWithoutConversions(TypeConversionUtil.erasure(type1), argType, myPlace);
          final boolean converts2 = TypesUtil.isAssignableWithoutConversions(TypeConversionUtil.erasure(type2), argType, myPlace);
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
    }

    if (!(method1 instanceof SyntheticElement) && !(method2 instanceof SyntheticElement)) {
      final PsiType returnType1 = substitutor1.substitute(method1.getReturnType());
      final PsiType returnType2 = substitutor2.substitute(method2.getReturnType());

      if (!TypesUtil.isAssignableWithoutConversions(returnType1, returnType2, myPlace) &&
          TypesUtil.isAssignableWithoutConversions(returnType2, returnType1, myPlace)) {
        return false;
      }
    }

    return true;
  }

  private boolean typesAgree(@NotNull PsiType type1, @NotNull PsiType type2) {
    if (argumentsSupplied() && type1 instanceof PsiArrayType && !(type2 instanceof PsiArrayType)) {
      type1 = ((PsiArrayType) type1).getComponentType();
    }
    return argumentsSupplied() ? //resolve, otherwise same_name_variants
        TypesUtil.isAssignableWithoutConversions(type1, type2, myPlace) :
        type1.equals(type2);
  }

  private boolean argumentsSupplied() {
    return myArgumentTypes != null;
  }


  public boolean hasCandidates() {
    return super.hasCandidates() || !myInapplicableCandidates.isEmpty();
  }

  public boolean hasApplicableCandidates() {
    return super.hasCandidates();
  }

  @Nullable
  public PsiType[] getArgumentTypes() {
    return myArgumentTypes;
  }

  @Nullable
  @Override
  public PsiType[] getTypeArguments() {
    return mySubstitutorComputer.getTypeArguments();
  }

  @Override
  public void handleEvent(Event event, Object associated) {
    super.handleEvent(event, associated);
    if (JavaScopeProcessorEvent.CHANGE_LEVEL == event && super.hasCandidates()) {
      myStopExecuting = true;
    }
  }

  @Nullable
  @Override
  public PsiType getThisType() {
    return myThisType;
  }

  @NotNull
  @Override
  public PsiElement getPlace() {
    return myPlace;
  }

}

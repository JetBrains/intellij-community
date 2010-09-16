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

package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.scope.JavaScopeProcessorEvent;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.DominanceAwareMethod;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.*;

/**
 * @author ven
 */
public class MethodResolverProcessor extends ResolverProcessor {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.lang.resolve.processors.MethodResolverProcessor");
  private final PsiType myThisType;
  @Nullable
  private final PsiType[] myArgumentTypes;
  private final PsiType[] myTypeArguments;
  private final boolean myAllVariants;

  private final Set<GroovyResolveResult> myInapplicableCandidates = new LinkedHashSet<GroovyResolveResult>();
  private final boolean myIsConstructor;

  private boolean myStopExecuting = false;

  public MethodResolverProcessor(String name, GroovyPsiElement place, boolean isConstructor, PsiType thisType, @Nullable PsiType[] argumentTypes, PsiType[] typeArguments) {
    this(name, place, isConstructor, thisType, argumentTypes, typeArguments, false);
  }
  public MethodResolverProcessor(String name, GroovyPsiElement place, boolean isConstructor, PsiType thisType, @Nullable PsiType[] argumentTypes, PsiType[] typeArguments, boolean allVariants) {
    super(name, RESOLVE_KINDS_METHOD_PROPERTY, place, PsiType.EMPTY_ARRAY);
    myIsConstructor = isConstructor;
    myThisType = thisType;
    myArgumentTypes = argumentTypes;
    myTypeArguments = typeArguments;
    myAllVariants = allVariants;
  }

  public boolean execute(PsiElement element, ResolveState state) {
    if (myStopExecuting) {
      return false;
    }
    PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);
    if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) element;

      if (method.isConstructor() != myIsConstructor) return true;
      if (substitutor == null) substitutor = PsiSubstitutor.EMPTY;
      substitutor = obtainSubstitutor(substitutor, method);
      boolean isAccessible = isAccessible(method);
      GroovyPsiElement fileResolveContext = state.get(RESOLVE_CONTEXT);
      boolean isStaticsOK = isStaticsOK(method, fileResolveContext);
      if (!myAllVariants && PsiUtil.isApplicable(myArgumentTypes, method, substitutor, fileResolveContext instanceof GrMethodCallExpression, (GroovyPsiElement)myPlace) && isStaticsOK) {
        addCandidate(new GroovyResolveResultImpl(method, fileResolveContext, substitutor, isAccessible, isStaticsOK));
      } else {
        myInapplicableCandidates.add(new GroovyResolveResultImpl(method, fileResolveContext, substitutor, isAccessible, isStaticsOK));
      }

      return true;
    }

    return true;
  }

  private PsiSubstitutor obtainSubstitutor(PsiSubstitutor substitutor, PsiMethod method) {
    final PsiTypeParameter[] typeParameters = method.getTypeParameters();
    if (myTypeArguments.length == typeParameters.length) {
      for (int i = 0; i < typeParameters.length; i++) {
        PsiTypeParameter typeParameter = typeParameters[i];
        final PsiType typeArgument = myTypeArguments[i];
        substitutor = substitutor.put(typeParameter, typeArgument);
      }
      return substitutor;
    }

    if (argumentsSupplied() && method.hasTypeParameters()) {
      PsiType[] argTypes = myArgumentTypes;
      if (method instanceof GrGdkMethod) {
        assert argTypes != null;
        //type inference should be performed from static method
        PsiType[] newArgTypes = new PsiType[argTypes.length + 1];
        newArgTypes[0] = myThisType;
        System.arraycopy(argTypes, 0, newArgTypes, 1, argTypes.length);
        argTypes = newArgTypes;

        method = ((GrGdkMethod) method).getStaticMethod();
        LOG.assertTrue(method.isValid());
      }
      return inferMethodTypeParameters(method, substitutor, typeParameters, argTypes);
    }

    return substitutor;
  }

  private PsiSubstitutor inferMethodTypeParameters(PsiMethod method, PsiSubstitutor partialSubstitutor, final PsiTypeParameter[] typeParameters, final PsiType[] argTypes) {
    if (typeParameters.length == 0) return partialSubstitutor;

    if (argumentsSupplied()) {
      final GrClosureSignature erasedSignature = GrClosureSignatureUtil.createSignatureWithErasedParameterTypes(method);

      final GrClosureSignature signature = GrClosureSignatureUtil.createSignature(method, partialSubstitutor);
      final GrClosureParameter[] params = signature.getParameters();

      final GrClosureSignatureUtil.ArgInfo<PsiType>[] argInfos =
        GrClosureSignatureUtil.mapArgTypesToParameters(erasedSignature, argTypes, (GroovyPsiElement)myPlace, myAllVariants);
      if (argInfos ==  null) return partialSubstitutor;

      int max = Math.max(params.length, argTypes.length);

      PsiType[] parameterTypes = new PsiType[max];
      PsiType[] argumentTypes = new PsiType[max];
      int i = 0;
      for (int paramIndex = 0; paramIndex < argInfos.length; paramIndex++) {
        PsiType paramType = params[paramIndex].getType();

        GrClosureSignatureUtil.ArgInfo<PsiType> argInfo = argInfos[paramIndex];
        if (argInfo != null) {
          if (argInfo.isMultiArg) {
            if (paramType instanceof PsiArrayType) paramType = ((PsiArrayType)paramType).getComponentType();
          }
          for (PsiType type : argInfo.args) {
            argumentTypes[i] = handleConversion(paramType, type);
            parameterTypes[i] = paramType;
            i++;
          }
        } else {
          parameterTypes[i] = paramType;
          argumentTypes[i] = PsiType.NULL;
          i++;
        }
      }
      final PsiResolveHelper helper = JavaPsiFacade.getInstance(method.getProject()).getResolveHelper();
      PsiSubstitutor substitutor = helper.inferTypeArguments(typeParameters, parameterTypes, argumentTypes, LanguageLevel.HIGHEST);
      for (PsiTypeParameter typeParameter : typeParameters) {
        if (!substitutor.getSubstitutionMap().containsKey(typeParameter)) {
          substitutor = inferFromContext(typeParameter, PsiUtil.getSmartReturnType(method), substitutor, helper);
        }
      }

      return partialSubstitutor.putAll(substitutor);
    }

    return partialSubstitutor;
  }

  private PsiType handleConversion(PsiType paramType, PsiType argType) {
    final GroovyPsiElement context = (GroovyPsiElement)myPlace;
    if (!TypesUtil.isAssignable(TypeConversionUtil.erasure(paramType), argType, context.getManager(), context.getResolveScope(), false) &&
        TypesUtil.isAssignableByMethodCallConversion(paramType, argType, context)) {
      return paramType;
    }
    return argType;
  }

  private PsiSubstitutor inferFromContext(PsiTypeParameter typeParameter, PsiType lType, PsiSubstitutor substitutor, PsiResolveHelper helper) {
    if (myPlace != null) {
      final PsiType inferred = helper.getSubstitutionForTypeParameter(typeParameter, lType, getContextType(), false, LanguageLevel.HIGHEST);
      if (inferred != PsiType.NULL) {
        return substitutor.put(typeParameter, inferred);
      }
    }
    return substitutor;
  }

  @Nullable
  private PsiType getContextType() {
    final PsiElement parent = myPlace.getParent().getParent();
    PsiType rType = null;
    if (parent instanceof GrReturnStatement) {
      final GrMethod method = PsiTreeUtil.getParentOfType(parent, GrMethod.class);
      if (method != null) rType = method.getReturnType();
    }
    else if (parent instanceof GrAssignmentExpression && myPlace.equals(((GrAssignmentExpression)parent).getRValue())) {
      rType = ((GrAssignmentExpression)parent).getLValue().getType();
    }
    else if (parent instanceof GrVariable) {
      rType = ((GrVariable)parent).getDeclaredType();
    }
    return rType;
  }

  @NotNull
  public GroovyResolveResult[] getCandidates() {
    if (myAllVariants) {
      return myInapplicableCandidates.toArray(new GroovyResolveResult[myInapplicableCandidates.size()]);
    }

    if (super.hasCandidates()) {
      return getApplicableCandidates();
    }
    if (!myInapplicableCandidates.isEmpty()) {
      return getInapplicableCandidates();
    }
    return GroovyResolveResult.EMPTY_ARRAY;
  }

  public GroovyResolveResult[] getApplicableCandidates() {
    return filterCandidates();
  }

  public GroovyResolveResult[] getInapplicableCandidates() {
    final Set<GroovyResolveResult> resultSet = filterCorrectParameterCount(myInapplicableCandidates);
    return ResolveUtil.filterSameSignatureCandidates(resultSet, myArgumentTypes != null ? myArgumentTypes.length : -1);
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
    Set<GroovyResolveResult> array = getCandidatesInternal();
    if (array.size() == 1) return array.toArray(new GroovyResolveResult[array.size()]);

    List<GroovyResolveResult> result = new ArrayList<GroovyResolveResult>();

    Iterator<GroovyResolveResult> itr = array.iterator();

    result.add(itr.next());

    GlobalSearchScope scope = myPlace.getResolveScope();

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
            final int res = compareMethods(currentMethod, resolveResult.getSubstitutor(), method, otherResolveResult.getSubstitutor(), scope);
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
                             PsiSubstitutor substitutor2,
                             GlobalSearchScope scope) {
    if (!method1.getName().equals(method2.getName())) return 0;

    if (method2 instanceof DominanceAwareMethod && ((DominanceAwareMethod)method2).isMoreConcreteThan(substitutor2, method1, substitutor1, (GroovyPsiElement)myPlace)) {
      return 1;
    }

    if (method1 instanceof DominanceAwareMethod && ((DominanceAwareMethod)method1).isMoreConcreteThan(substitutor1, method2, substitutor2, (GroovyPsiElement)myPlace)) {
      return -1;
    }

    if (dominated(method1, substitutor1, method2, substitutor2, scope)) {
      return 1;
    }
    if (dominated(method2, substitutor2, method1, substitutor1, scope)) {
      return -1;
    }

    return 0;
  }

  private boolean dominated(PsiMethod method1,
                            PsiSubstitutor substitutor1,
                            PsiMethod method2,
                            PsiSubstitutor substitutor2,
                            GlobalSearchScope scope) {  //method1 has more general parameter types thn method2
    if (!method1.getName().equals(method2.getName())) return false;

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

    PsiManager manager = method1.getManager();
    for (int i = 0; i < params2.length; i++) {
      PsiType type1 = substitutor1.substitute(params1[i].getType());
      PsiType type2 = substitutor2.substitute(params2[i].getType());

      if (argTypes != null && argTypes.length > i) {
        PsiType argType = argTypes[i];
        if (argType != null) {
          final boolean converts1 = TypesUtil.isAssignable(type1, argType, manager, scope, false);
          final boolean converts2 = TypesUtil.isAssignable(type2, argType, manager, scope, false);
          if (converts1 != converts2) {
            return converts2;
          }
        }
      }

      if (!typesAgree(manager, scope, type1, type2)) return false;
    }

    return true;
  }

  private boolean typesAgree(PsiManager manager, GlobalSearchScope scope, PsiType type1, PsiType type2) {
    if (argumentsSupplied() && type1 instanceof PsiArrayType && !(type2 instanceof PsiArrayType)) {
      type1 = ((PsiArrayType) type1).getComponentType();
    }
    return argumentsSupplied() ? //resolve, otherwise same_name_variants
        TypesUtil.isAssignable(type1, type2, manager, scope, false) :
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

  @Override
  public void handleEvent(Event event, Object associated) {
    super.handleEvent(event, associated);
    if (JavaScopeProcessorEvent.CHANGE_LEVEL == event && super.hasCandidates()) {
      myStopExecuting = true;
    }
  }
}

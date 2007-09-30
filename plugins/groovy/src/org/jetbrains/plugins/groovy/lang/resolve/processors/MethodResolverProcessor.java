/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.*;

/**
 * @author ven
 * Resolves methods from call expression or function application.
 */
public class MethodResolverProcessor extends ResolverProcessor {
  @Nullable
  PsiType[] myArgumentTypes;

  private Set<GroovyResolveResult> myInapplicableCandidates = new LinkedHashSet<GroovyResolveResult>();
  private boolean myIsConstructor;

  public MethodResolverProcessor(String name, PsiElement place, boolean forCompletion, boolean isConstructor, PsiType[] argumentTypes) {
    super(name, EnumSet.of(ResolveKind.METHOD, ResolveKind.PROPERTY), place, forCompletion, PsiType.EMPTY_ARRAY);
    myIsConstructor = isConstructor;
    myArgumentTypes = argumentTypes;
  }

  public MethodResolverProcessor(String name, PsiElement place, boolean forCompletion, boolean isConstructor) {
    this(name, place, forCompletion, isConstructor, PsiUtil.getArgumentTypes(place, isConstructor));
  }

  public boolean execute(PsiElement element, PsiSubstitutor substitutor) {
    if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) element;
      if (method.isConstructor() != myIsConstructor) return true;

      if (!isAccessible((PsiNamedElement) element)) return true;

      substitutor = inferMethodTypeParameters(method, substitutor);
      if (myForCompletion || PsiUtil.isApplicable(myArgumentTypes, method, PsiSubstitutor.EMPTY)) { //do not use substitutor here!
        myCandidates.add(new GroovyResolveResultImpl(method, true, myImportStatementContext, substitutor));
      }
      else {
        myInapplicableCandidates.add(new GroovyResolveResultImpl(method, true, myImportStatementContext, substitutor));
      }

      return true;
    } else if (element instanceof PsiVariable) {
      if (myForCompletion ||
          element instanceof GrField && ((GrField)element).isProperty() ||
          ((PsiVariable) element).getType().equalsToText("groovy.lang.Closure")) {
        return super.execute(element, substitutor);
      }
    }

    return true;
  }

  private PsiSubstitutor inferMethodTypeParameters(PsiMethod method, PsiSubstitutor partialSubstitutor) {
    final PsiTypeParameter[] typeParameters = method.getTypeParameters();
    if (myArgumentTypes != null) {
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      final int max = Math.max(parameters.length, myArgumentTypes.length);
      PsiType[] parameterTypes = new PsiType[max];
      PsiType[] argumentTypes = new PsiType[max];
      for (int i = 0; i < parameterTypes.length; i++) {
        if (i < parameters.length) {
          final PsiType type = parameters[i].getType();
          if (myArgumentTypes.length == parameters.length &&
              type instanceof PsiEllipsisType &&
              !(myArgumentTypes[myArgumentTypes.length - 1] instanceof PsiArrayType)) {
            parameterTypes[i] = ((PsiEllipsisType) type).getComponentType();
          } else {
            parameterTypes[i] = type;
          }
        }
        else {
          if (parameters.length > 0) {
            final PsiType lastParameterType = parameters[parameters.length - 1].getType();
            if (myArgumentTypes.length > parameters.length && lastParameterType instanceof PsiEllipsisType) {
              parameterTypes[i] = ((PsiEllipsisType) lastParameterType).getComponentType();
            } else {
              parameterTypes[i] = lastParameterType;
            }
          } else {
            parameterTypes[i] = PsiType.NULL;
          }
        }
        argumentTypes[i] = i < myArgumentTypes.length ? myArgumentTypes[i] : PsiType.NULL;
      }

      final PsiResolveHelper helper = method.getManager().getResolveHelper();
      PsiSubstitutor substitutor = helper.inferTypeArguments(typeParameters, parameterTypes, argumentTypes, LanguageLevel.HIGHEST);
      for (PsiTypeParameter typeParameter : typeParameters) {
        if (!substitutor.getSubstitutionMap().containsKey(typeParameter)) {
          substitutor = inferFromContext(typeParameter, method.getReturnType(), substitutor, helper);
        }
      }
      return substitutor;
    }

    return partialSubstitutor;
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

  private PsiType getContextType() {
    final PsiElement parent = myPlace.getParent();
    PsiType rType = null;
    if (parent instanceof GrReturnStatement) {
      final GrMethod method = PsiTreeUtil.getParentOfType(parent, GrMethod.class);
      if (method != null) rType = method.getDeclaredReturnType();
    } else if (parent instanceof GrAssignmentExpression && myPlace.equals(((GrAssignmentExpression) parent).getRValue())) {
      rType = ((GrAssignmentExpression) parent).getLValue().getType();
    } else if (parent instanceof GrVariable) {
      rType = ((GrVariable) parent).getDeclaredType();
    }
    return rType;
  }

  public GroovyResolveResult[] getCandidates() {
    if (myCandidates.size() > 0) {
      return filterCandidates();
    }
    return myInapplicableCandidates.toArray(new GroovyResolveResult[myInapplicableCandidates.size()]);
  }

  private GroovyResolveResult[] filterCandidates() {
    GroovyResolveResult[] array = myCandidates.toArray(new GroovyResolveResult[myCandidates.size()]);
    if (array.length == 1) return array;

    List<GroovyResolveResult> result = new ArrayList<GroovyResolveResult>();
    result.add(array[0]);

    PsiManager manager = myPlace.getManager();
    GlobalSearchScope scope = myPlace.getResolveScope();
    
    boolean methodsPresent = array[0].getElement() instanceof PsiMethod;
    boolean propertiesPresent = !methodsPresent;
    Outer:
    for (int i = 1; i < array.length; i++) {
      PsiElement currentElement = array[i].getElement();
      if (currentElement instanceof PsiMethod) {
        methodsPresent = true;
        PsiMethod currentMethod = (PsiMethod) currentElement;
        for (Iterator<GroovyResolveResult> iterator = result.iterator(); iterator.hasNext();) {
          PsiElement element = iterator.next().getElement();
          if (element instanceof PsiMethod) {
            PsiMethod method = (PsiMethod) element;
            if (dominated(currentMethod, method, manager, scope)) {
              continue Outer;
            } else if (dominated(method, currentMethod, manager, scope)) {
              iterator.remove();
            }
          }
        }
      } else {
        propertiesPresent = true;
      }

      result.add(array[i]);
    }

    if (!myForCompletion) {
      if (methodsPresent && propertiesPresent) {
        for (Iterator<GroovyResolveResult> iterator = result.iterator(); iterator.hasNext();) {
          GroovyResolveResult resolveResult =  iterator.next();
          if (!(resolveResult.getElement() instanceof PsiMethod)) iterator.remove();
        }
      }
    }

    return result.toArray(new GroovyResolveResult[result.size()]);
  }

  private boolean dominated(PsiMethod method1, PsiMethod method2, PsiManager manager, GlobalSearchScope scope) {  //method1 has more general parameter types thn method2
    if (!method1.getName().equals(method2.getName())) return false;
    
    PsiParameter[] params1 = method1.getParameterList().getParameters();
    PsiParameter[] params2 = method2.getParameterList().getParameters();

    if (params1.length < params2.length) {
      if (params1.length == 0) return false;
      final PsiType lastType = params1[params1.length - 1].getType(); //varargs applicability
      return lastType instanceof PsiArrayType;
    }

    for (int i = 0; i < params2.length; i++) {
      PsiType type1 = params1[i].getType();
      PsiType type2 = params2[i].getType();
      if (type1 instanceof PsiArrayType && !(type2 instanceof PsiArrayType)) {
        type1 = ((PsiArrayType) type1).getComponentType();
      }
      if (!TypesUtil.isAssignable(type1, type2, manager, scope)) return false;
    }

    return true;
  }


  public boolean hasCandidates() {
    return super.hasCandidates() || myInapplicableCandidates.size() > 0;
  }
}

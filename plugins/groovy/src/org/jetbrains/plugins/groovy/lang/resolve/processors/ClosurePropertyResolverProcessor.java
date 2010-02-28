/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.*;

/**
 * @author Maxim.Medvedev
 */
public class ClosurePropertyResolverProcessor extends ResolverProcessor {
  @Nullable
  private PsiType[] myArgumentTypes;

  private final Set<GroovyResolveResult> myInapplicableCandidates = new LinkedHashSet<GroovyResolveResult>();

  private boolean myStopExecuting = false;
  private final boolean myField;

  public ClosurePropertyResolverProcessor(String name, GroovyPsiElement place, @Nullable PsiType[] argumentTypes, boolean searchForFields) {
    super(name, searchForFields ? EnumSet.of(ResolveKind.PROPERTY) : EnumSet.of(ResolveKind.METHOD), place, PsiType.EMPTY_ARRAY);
    myArgumentTypes = argumentTypes;
    myField = searchForFields;
  }

  public boolean execute(PsiElement element, ResolveState state) {
    if (myStopExecuting) {
      return false;
    }
    PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);

    /*
    if (element instanceof GrField && ((GrField)element).isProperty()) {
      if (myProperty == null) {
        PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);
        substitutor = substitutor == null ? PsiSubstitutor.EMPTY : substitutor;
        myProperty = new GroovyResolveResultImpl(element, myCurrentFileResolveContext, substitutor, isAccessible((PsiNamedElement)element),
                                                 isStaticsOK((PsiNamedElement)element));
      }
      return true;
    }
    else if (element instanceof GrReferenceExpression && ((GrReferenceExpression)element).getQualifier() != null) {
      return true;
    }
    return super.execute(element, state);
    */

    if (myField) {
      if (element instanceof GrReferenceExpression) {

      }
      if (element instanceof PsiVariable) {
        if (isApplicableClosure(element)) {
          myCandidates.add(new GroovyResolveResultImpl(element, myCurrentFileResolveContext, substitutor, isAccessible((PsiVariable)element),
                                                       isStaticsOK((PsiVariable)element)));
        }
        else {
          myInapplicableCandidates.add(
            new GroovyResolveResultImpl(element, myCurrentFileResolveContext, substitutor, isAccessible((PsiVariable)element),
                                        isStaticsOK((PsiVariable)element)));
        }
      }
      else if (!myField && element instanceof PsiMethod && !(element instanceof GrAccessorMethod)) {
        if (isApplicableClosure(element)) {
          myCandidates.add(new GroovyResolveResultImpl(element, myCurrentFileResolveContext, substitutor, isAccessible((PsiMethod)element),
                                                       isStaticsOK((PsiMethod)element)));
        }
        else {
          myInapplicableCandidates.add(
            new GroovyResolveResultImpl(element, myCurrentFileResolveContext, substitutor, isAccessible((PsiMethod)element),
                                        isStaticsOK((PsiMethod)element)));
        }
      }
    }
    else if (!myField && element instanceof PsiMethod && !(element instanceof GrAccessorMethod)) {
      if (isApplicableClosure(element)) {
        myCandidates.add(new GroovyResolveResultImpl(element, myCurrentFileResolveContext, substitutor, isAccessible((PsiMethod)element),
                                                     isStaticsOK((PsiMethod)element)));
      }
      else {
        myInapplicableCandidates.add(
          new GroovyResolveResultImpl(element, myCurrentFileResolveContext, substitutor, isAccessible((PsiMethod)element),
                                      isStaticsOK((PsiMethod)element)));
      }
    }
    return true;
  }

  private boolean isApplicableClosure(PsiElement element) {
    if (element instanceof GrVariable) {
      final PsiType type = ((GrVariable)element).getTypeGroovy();
      if (type == null) return false;
      if (type instanceof GrClosureType) {
        return PsiUtil.isApplicable(myArgumentTypes, (GrClosureType)type, element.getManager());
      }
      if (type.equalsToText(GrClosableBlock.GROOVY_LANG_CLOSURE)) return true;
    }
    else if (element instanceof PsiVariable) {
      return ((PsiVariable)element).getType().equalsToText(GrClosableBlock.GROOVY_LANG_CLOSURE);
    }
    else if (element instanceof PsiMethod) {
      final PsiType type = ((PsiMethod)element).getReturnType();
      if (type == null) return false;
      if (type instanceof GrClosureType) {
        return PsiUtil.isApplicable(myArgumentTypes, (GrClosureType)type, element.getManager());
      }
      if (type.equalsToText(GrClosableBlock.GROOVY_LANG_CLOSURE)) return true;
    }
    return false;
  }


  @NotNull
  public GroovyResolveResult[] getCandidates() {
    if (!myCandidates.isEmpty()) {
      return filterCandidates();
    }
    if (!myInapplicableCandidates.isEmpty()) {
      return ResolveUtil.filterSameSignatureCandidates(myInapplicableCandidates, myArgumentTypes != null ? myArgumentTypes.length : -1);
    }
    return GroovyResolveResult.EMPTY_ARRAY;
  }

  private GroovyResolveResult[] filterCandidates() {
    GroovyResolveResult[] array = myCandidates.toArray(new GroovyResolveResult[myCandidates.size()]);
    if (array.length == 1) return array;

    List<GroovyResolveResult> result = new ArrayList<GroovyResolveResult>();
    result.add(array[0]);

    PsiManager manager = myPlace.getManager();
    GlobalSearchScope scope = myPlace.getResolveScope();

    Outer:
    for (int i = 1; i < array.length; i++) {
      PsiElement currentElement = array[i].getElement();
      PsiVariable currentVariable = (PsiVariable)currentElement;
      final PsiType currentType = currentVariable.getType();
      for (Iterator<GroovyResolveResult> iterator = result.iterator(); iterator.hasNext();) {
        final GroovyResolveResult otherResolveResult = iterator.next();
        PsiElement element = otherResolveResult.getElement();
        if (element instanceof PsiVariable) {
          PsiVariable variable = (PsiVariable)element;
          final PsiType type = variable.getType();
          if (dominated(currentType, array[i].getSubstitutor(), type, otherResolveResult.getSubstitutor(), manager, scope)) {
            continue Outer;
          }
          else if (dominated(type, otherResolveResult.getSubstitutor(), currentType, array[i].getSubstitutor(), manager, scope)) {
            iterator.remove();
          }
        }
      }

      result.add(array[i]);
    }

    return result.toArray(new GroovyResolveResult[result.size()]);
  }

  private boolean dominated(PsiType closureType1,
                            PsiSubstitutor substitutor1,
                            PsiType closureType2,
                            PsiSubstitutor substitutor2,
                            PsiManager manager,
                            GlobalSearchScope scope) {  //method1 has more general parameter types thn method2
    if (closureType1 instanceof GrClosureType && !(closureType2 instanceof GrClosureType)) return true;
    if (!(closureType1 instanceof GrClosureType) && closureType2 instanceof GrClosureType) return false;
    if (!(closureType1 instanceof GrClosureType) && !(closureType2 instanceof GrClosureType)) return false;

    final GrClosureSignature signature1 = ((GrClosureType)closureType1).getSignature();
    final GrClosureSignature signature2 = ((GrClosureType)closureType2).getSignature();

    GrClosureParameter[] params1 = signature1.getParameters();
    GrClosureParameter[] params2 = signature2.getParameters();
    if (myArgumentTypes == null && params1.length != params2.length) return false;

    if (params1.length < params2.length) {
      if (params1.length == 0) return false;
      final PsiType lastType = params1[params1.length - 1].getType(); //varargs applicability
      return lastType instanceof PsiArrayType;
    }

    for (int i = 0; i < params2.length; i++) {
      PsiType type1 = substitutor1.substitute(params1[i].getType());
      PsiType type2 = substitutor2.substitute(params2[i].getType());
      if (!typesAgree(manager, scope, type1, type2)) return false;
    }

    return true;
  }

  private boolean typesAgree(PsiManager manager, GlobalSearchScope scope, PsiType type1, PsiType type2) {
    if (argumentsSupplied() && type1 instanceof PsiArrayType && !(type2 instanceof PsiArrayType)) {
      type1 = ((PsiArrayType)type1).getComponentType();
    }
    return argumentsSupplied() ? //resolve, otherwise same_name_variants
           TypesUtil.isAssignable(type1, type2, manager, scope) : type1.equals(type2);
  }

  private boolean argumentsSupplied() {
    return myArgumentTypes != null;
  }


  public boolean hasCandidates() {
    return super.hasCandidates() || !myInapplicableCandidates.isEmpty();
  }

  public boolean hasApplicableCandidates() {
    return !myCandidates.isEmpty();
  }

  @Nullable
  public PsiType[] getArgumentTypes() {
    return myArgumentTypes;
  }

  @Override
  public void handleEvent(Event event, Object associated) {
    super.handleEvent(event, associated);
    if (JavaScopeProcessorEvent.CHANGE_LEVEL == event && myCandidates.size() > 0) {
      myStopExecuting = true;
    }
  }
}

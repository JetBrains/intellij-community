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

import com.intellij.psi.*;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * @author ven
 * Resolves methods from method call or function application.
 */
public class MethodResolverProcessor extends ResolverProcessor {
  PsiType[] myArgumentTypes;

  private List<GroovyResolveResult> myInapplicableCandidates = new ArrayList<GroovyResolveResult>();
  private List<PsiMethod> myCandidateMethods = new ArrayList<PsiMethod>();

  public MethodResolverProcessor(String name, GroovyPsiElement place, boolean forCompletion) {
    super(name, EnumSet.of(ResolveKind.METHOD, ResolveKind.PROPERTY), place, forCompletion);
    myArgumentTypes = getArgumentTypes(place);
  }

  public boolean execute(PsiElement element, PsiSubstitutor substitutor) {
    if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) element;
      if (method.isConstructor()) return true; //not interested in constructors <now>

      if (!isAccessible((PsiNamedElement) element)) return true;

      if (!myForCompletion) {
        if (ResolveUtil.isSuperMethodDominated(method, myCandidateMethods)) return true;
      }

      if (myForCompletion || isApplicable(method)) {
        myCandidates.add(new GroovyResolveResultImpl(method, true));
      }
      else {
        myInapplicableCandidates.add(new GroovyResolveResultImpl(method, true));
      }

      myCandidateMethods.add(method);
      return true;
    } else {
      return super.execute(element, substitutor);
    }
  }

  public GroovyResolveResult[] getCandidates() {
    return myCandidates.size() > 0 ? super.getCandidates() :
        myInapplicableCandidates.toArray(new GroovyResolveResult[myInapplicableCandidates.size()]);
  }

  public boolean hasCandidates() {
    return super.hasCandidates() || myInapplicableCandidates.size() > 0;
  }

  private boolean isApplicable(PsiMethod method) {
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length > myArgumentTypes.length) return false;
    if (parameters.length == 0 && myArgumentTypes.length > 0) return false;

    for (int i = 0; i < myArgumentTypes.length; i++) {
      PsiType argType = myArgumentTypes[i];
      PsiType parameterTypeToCheck;
      if (i < parameters.length - 1) {
        parameterTypeToCheck = parameters[i].getType();
      } else {
        PsiType lastParameterType = parameters[parameters.length - 1].getType();
        if (lastParameterType instanceof PsiArrayType) {
          parameterTypeToCheck = ((PsiArrayType) lastParameterType).getComponentType();
        } else if (parameters.length == myArgumentTypes.length) {
            parameterTypeToCheck = lastParameterType;
          } else {
            return false;
          }
      }
      parameterTypeToCheck =
          PsiUtil.boxPrimitiveType(parameterTypeToCheck, method.getManager(), method.getResolveScope());
      if (!parameterTypeToCheck.isAssignableFrom(argType)) return false;
    }

    return true;
  }


  private static PsiType[] getArgumentTypes(GroovyPsiElement place) {
    PsiElementFactory factory = place.getManager().getElementFactory();
    PsiElement parent = place.getParent();
    if (parent instanceof GrMethodCall) {
      List<PsiType> result = new ArrayList<PsiType>();
      GrMethodCall methodCall = (GrMethodCall) parent;
      GrNamedArgument[] namedArgs = methodCall.getNamedArguments();
      if (namedArgs.length > 0) {
        result.add(factory.createTypeByFQClassName("java.util.HashMap", place.getResolveScope()));
      }
      GrExpression[] expressions = methodCall.getExpressionArguments();
      for (GrExpression expression : expressions) {
        PsiType type = expression.getType();
        if (type == null) {
          result.add(PsiType.NULL);
        } else {
          result.add(type);
        }
      }
      return result.toArray(new PsiType[result.size()]);

    } else if (parent instanceof GrApplicationExpression) {
      GrExpression[] args = ((GrApplicationExpression) parent).getArguments();
      PsiType[] result = new PsiType[args.length];
      for (int i = 0; i < result.length; i++) {
        PsiType argType = args[i].getType();
        if (argType == null) {
          result[i] = PsiType.NULL;
        } else {
          result[i] = argType;
        }
      }

      return result;
    }

    return PsiType.EMPTY_ARRAY;
  }
}

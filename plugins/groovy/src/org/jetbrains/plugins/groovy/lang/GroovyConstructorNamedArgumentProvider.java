/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang;

import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.extensions.GroovyNamedArgumentProvider;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;

import java.util.EnumSet;
import java.util.Map;

import static org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint.ResolveKind.*;

/**
 * @author Sergey Evdokimov
 */
public class GroovyConstructorNamedArgumentProvider extends GroovyNamedArgumentProvider {

  private static final String METACLASS = "metaClass";

  @Override
  public void getNamedArguments(@NotNull GrCall call,
                                @Nullable PsiElement resolve,
                                @Nullable String argumentName,
                                boolean forCompletion,
                                Map<String, ArgumentDescriptor> result) {
    if (!(call instanceof GrNewExpression)) return;

    if (resolve != null) {
      if (!(resolve instanceof PsiMethod)) return;
      PsiMethod method = (PsiMethod)resolve;
      if (method.getParameterList().getParametersCount() > 0 || !method.isConstructor()) return;
    }

    GrNewExpression newCall = (GrNewExpression)call;

    GrArgumentList argumentList = newCall.getArgumentList();
    if (argumentList == null) return;

    GrExpression[] expressionArguments = argumentList.getExpressionArguments();
    if (expressionArguments.length > 1 || (expressionArguments.length == 1 && !(expressionArguments[0] instanceof GrReferenceExpression))) {
      return;
    }

    for (GroovyResolveResult resolveResult : newCall.multiResolveClass()) {
      PsiElement element = resolveResult.getElement();
      if (!(element instanceof PsiClass)) continue;

      PsiClass aClass = (PsiClass)element;

      if (!isClassHasDefaultConstructorWithMap(aClass)) continue;

      PsiClassType classType = JavaPsiFacade.getElementFactory(aClass.getProject()).createType(aClass);

      processClass(call, classType, argumentName, result);
    }
  }

  public static void processClass(@NotNull GrCall call,
                                  PsiClassType type,
                                  @Nullable String argumentName,
                                  Map<String, ArgumentDescriptor> result) {
    if (argumentName == null) {
      ResolveUtil.processAllDeclarations(type, new MyPsiScopeProcessor(result, call), ResolveState.initial(), call);
    }
    else {
      ResolveUtil.processAllDeclarations(type, new MyPsiScopeProcessor(argumentName, true, result, call), ResolveState.initial(), call);
      ResolveUtil.processAllDeclarations(type, new MyPsiScopeProcessor(argumentName, false, result, call), ResolveState.initial(), call);
    }
  }

  private static boolean isClassHasDefaultConstructorWithMap(PsiClass aClass) {
    PsiMethod[] constructors = aClass.getConstructors();

    if (constructors.length == 0) return true;

    boolean hasDefaultConstructor = false;

    for (PsiMethod constructor : constructors) {
      PsiParameterList parameterList = constructor.getParameterList();

      PsiParameter[] parameters = parameterList.getParameters();

      if (parameters.length == 0) {
        hasDefaultConstructor = true;
      }
      else {
        if (InheritanceUtil.isInheritor(parameters[0].getType(), CommonClassNames.JAVA_UTIL_MAP)) {
          boolean hasRequiredParameter = false;

          for (int i = 1; i < parameters.length; i++) {
            PsiParameter parameter = parameters[i];
            if (!(parameter instanceof GrParameter) || !((GrParameter)parameter).isOptional()) {
              hasRequiredParameter = true;
              break;
            }
          }

          if (!hasRequiredParameter) {
            return false;
          }
        }
      }
    }

    return hasDefaultConstructor;
  }

  private static class MyPsiScopeProcessor implements PsiScopeProcessor, NameHint, ClassHint, ElementClassHint {
    private final String myNameHint;
    private final Map<String, ArgumentDescriptor> myResult;
    private final EnumSet<ResolveKind> myResolveTargetKinds;

    private MyPsiScopeProcessor(Map<String, ArgumentDescriptor> result, GroovyPsiElement context) {
      myResolveTargetKinds = ResolverProcessor.RESOLVE_KINDS_METHOD_PROPERTY;
      myNameHint = null;
      myResult = result;
    }

    private MyPsiScopeProcessor(@NotNull String propertyName, boolean findSetter, Map<String, ArgumentDescriptor> result, GroovyPsiElement context) {
      if (findSetter) {
        myResolveTargetKinds = ResolverProcessor.RESOLVE_KINDS_METHOD;
        myNameHint = GroovyPropertyUtils.getSetterName(propertyName);
      }
      else {
        myResolveTargetKinds = ResolverProcessor.RESOLVE_KINDS_PROPERTY;
        myNameHint = propertyName;
      }

      myResult = result;
    }

    @Override
    public boolean execute(PsiElement element, ResolveState state) {
      if (element instanceof PsiMethod || element instanceof PsiField) {
        String propertyName;
        PsiType type;

        if (element instanceof PsiMethod) {
          PsiMethod method = (PsiMethod)element;
          if (!GroovyPropertyUtils.isSimplePropertySetter(method)) return true;

          propertyName = GroovyPropertyUtils.getPropertyNameBySetter(method);
          if (propertyName == null) return true;

          type = method.getParameterList().getParameters()[0].getType();
        }
        else {
          type = ((PsiField)element).getType();
          propertyName = ((PsiField)element).getName();
        }

        if (((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.STATIC)) return true;

        if (myResult.containsKey(propertyName) || propertyName.equals(METACLASS)) return true;

        PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);
        if (substitutor != null) {
          type = substitutor.substitute(type);
        }

        myResult.put(propertyName, new TypeCondition(type, element));
      }

      return true;
    }

    @Override
    public <T> T getHint(Key<T> hintKey) {
      if ((NameHint.KEY == hintKey && myNameHint != null) || ClassHint.KEY == hintKey || ElementClassHint.KEY == hintKey) {
        //noinspection unchecked
        return (T) this;
      }

      return null;
    }

    @Override
    public void handleEvent(Event event, Object associated) {

    }

    @Override
    public boolean shouldProcess(ResolveKind resolveKind) {
      return myResolveTargetKinds.contains(resolveKind);
    }

    @Override
    public boolean shouldProcess(DeclarationKind kind) {
      switch (kind) {
        case CLASS:
          return shouldProcess(CLASS);

        case ENUM_CONST:
        case VARIABLE:
        case FIELD:
          return shouldProcess(PROPERTY);

        case METHOD:
          return shouldProcess(METHOD);

        case PACKAGE:
          return shouldProcess(PACKAGE);

        default:
          return false;
      }
    }

    @Override
    public String getName(ResolveState state) {
      return myNameHint;
    }
  }
}

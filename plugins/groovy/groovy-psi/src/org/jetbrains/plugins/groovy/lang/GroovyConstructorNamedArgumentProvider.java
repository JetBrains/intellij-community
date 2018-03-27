// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.*;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.extensions.GroovyNamedArgumentProvider;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.extensions.impl.TypeCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public abstract class GroovyConstructorNamedArgumentProvider extends GroovyNamedArgumentProvider {

  private static final String METACLASS = "metaClass";

  @NotNull
  public abstract List<PsiClass> getCorrespondingClasses(@NotNull GrCall call, @NotNull GroovyResolveResult resolveResult);

  @Override
  public void getNamedArguments(@NotNull GrCall call,
                                @NotNull GroovyResolveResult resolveResult,
                                @Nullable String argumentName,
                                boolean forCompletion,
                                @NotNull Map<String, NamedArgumentDescriptor> result) {
    GrArgumentList argumentList = call.getArgumentList();
    if (argumentList == null) return;
    GrExpression[] expressionArguments = argumentList.getExpressionArguments();
    if (expressionArguments.length > 1 || (expressionArguments.length == 1 && !(expressionArguments[0] instanceof GrReferenceExpression))) {
      return;
    }

    for (PsiClass psiClass : getCorrespondingClasses(call, resolveResult)) {
      if (!isClassHasConstructorWithMap(psiClass)) continue;

      PsiClassType classType = JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(psiClass);

      processClass(call, classType, argumentName, result);
    }
  }


  public static void processClass(@NotNull GrCall call,
                                  PsiClassType type,
                                  @Nullable String argumentName,
                                  final Map<String, NamedArgumentDescriptor> result) {
    if (argumentName == null) {
      final HashMap<String, Trinity<PsiType, PsiElement, PsiSubstitutor>> map = ContainerUtil.newHashMap();

      MyPsiScopeProcessor processor = new MyPsiScopeProcessor() {
        @Override
        protected void addNamedArgument(String propertyName, PsiType type, PsiElement element, PsiSubstitutor substitutor) {
          if (result.containsKey(propertyName)) return;

          Trinity<PsiType, PsiElement, PsiSubstitutor> pair = map.get(propertyName);
          if (pair != null) {
            if (element instanceof PsiMethod && pair.second instanceof PsiField) {
              // methods should override fields
            }
            else {
              return;
            }
          }

          map.put(propertyName, Trinity.create(type, element, substitutor));
        }
      };

      processor.setResolveTargetKinds(ClassHint.RESOLVE_KINDS_METHOD_PROPERTY);

      ResolveUtil.processAllDeclarations(type, processor, ResolveState.initial(), call);

      for (Map.Entry<String, Trinity<PsiType, PsiElement, PsiSubstitutor>> entry : map.entrySet()) {
        result.put(entry.getKey(), new TypeCondition(
          entry.getValue().first, entry.getValue().getSecond(), entry.getValue().getThird(), NamedArgumentDescriptor.Priority.AS_LOCAL_VARIABLE
        ));
      }
    }
    else {
      MyPsiScopeProcessor processor = new MyPsiScopeProcessor() {
        @Override
        protected void addNamedArgument(String propertyName, PsiType type, PsiElement element, PsiSubstitutor substitutor) {
          if (result.containsKey(propertyName)) return;
          result.put(propertyName, new TypeCondition(type, element, substitutor, NamedArgumentDescriptor.Priority.AS_LOCAL_VARIABLE));
        }
      };

      processor.setResolveTargetKinds(ClassHint.RESOLVE_KINDS_METHOD);
      processor.setNameHint(GroovyPropertyUtils.getSetterName(argumentName));

      ResolveUtil.processAllDeclarations(type, processor, ResolveState.initial(), call);

      processor.setResolveTargetKinds(ClassHint.RESOLVE_KINDS_PROPERTY);
      processor.setNameHint(argumentName);

      ResolveUtil.processAllDeclarations(type, processor, ResolveState.initial(), call);
    }
  }

  public static boolean isClassHasConstructorWithMap(PsiClass aClass) {
    PsiMethod[] constructors = aClass.getConstructors();

    if (constructors.length == 0) return true;

    for (PsiMethod constructor : constructors) {
      PsiParameterList parameterList = constructor.getParameterList();

      PsiParameter[] parameters = parameterList.getParameters();

      if (parameters.length == 0) return true;

      final PsiParameter first = parameters[0];
      if (InheritanceUtil.isInheritor(first.getType(), CommonClassNames.JAVA_UTIL_MAP)) return true;
      if (first instanceof GrParameter && ((GrParameter)first).getTypeGroovy() == null) return true;

      //if constructor has only optional parameters it can be used as default constructor with map args
      if (!PsiUtil.isConstructorHasRequiredParameters(constructor)) return true;
    }
    return false;
  }

  private abstract static class MyPsiScopeProcessor implements PsiScopeProcessor, NameHint, ClassHint, ElementClassHint {
    private String myNameHint;
    private EnumSet<DeclarationKind> myResolveTargetKinds;

    @Override
    public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
      if (element instanceof PsiMethod || element instanceof PsiField) {
        String propertyName;
        PsiType type;

        if (element instanceof PsiMethod) {
          if (!myResolveTargetKinds.contains(DeclarationKind.METHOD)) return true;

          PsiMethod method = (PsiMethod)element;
          if (!GroovyPropertyUtils.isSimplePropertySetter(method)) return true;

          propertyName = GroovyPropertyUtils.getPropertyNameBySetter(method);
          if (propertyName == null) return true;

          type = method.getParameterList().getParameters()[0].getType();
        }
        else {
          if (!myResolveTargetKinds.contains(DeclarationKind.FIELD)) return true;

          type = ((PsiField)element).getType();
          propertyName = ((PsiField)element).getName();
        }

        if (METACLASS.equals(propertyName)) return true;

        if (((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.STATIC)) return true;

        PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);
        if (substitutor != null) {
          type = substitutor.substitute(type);
        }

        addNamedArgument(propertyName, type, element, substitutor);
      }

      return true;
    }

    protected abstract void addNamedArgument(String propertyName, PsiType type, PsiElement element, PsiSubstitutor substitutor);

    @Override
    public <T> T getHint(@NotNull Key<T> hintKey) {
      if (NameHint.KEY == hintKey && myNameHint != null || ElementClassHint.KEY == hintKey) {
        //noinspection unchecked
        return (T) this;
      }

      return null;
    }

    @Override
    public boolean shouldProcess(DeclarationKind kind) {
      return myResolveTargetKinds.contains(kind);
    }

    @Override
    public String getName(@NotNull ResolveState state) {
      return myNameHint;
    }

    public void setNameHint(String nameHint) {
      myNameHint = nameHint;
    }

    public void setResolveTargetKinds(EnumSet<DeclarationKind> resolveTargetKinds) {
      myResolveTargetKinds = resolveTargetKinds;
    }
  }
}

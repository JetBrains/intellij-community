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
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.Condition;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.InitialPatternCondition;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.extensions.GroovyNamedArgumentProvider;
import org.jetbrains.plugins.groovy.lang.completion.handlers.NamedArgumentInsertHandler;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConstructorCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyElementPattern;
import org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyPatterns;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author peter
 */
class MapArgumentCompletionProvider extends CompletionProvider<CompletionParameters> {

  private MapArgumentCompletionProvider() {
  }

  public static void register(CompletionContributor contributor) {
    MapArgumentCompletionProvider instance = new MapArgumentCompletionProvider();

    ElementPattern<PsiElement> inArgumentListOfCall = psiElement().withParent(psiElement(GrReferenceExpression.class).withParent(
      StandardPatterns.or(
        psiElement(GrArgumentList.class).withParent(GrCall.class),
        new GroovyElementPattern.Capture<GrListOrMap>(new InitialPatternCondition<GrListOrMap>(GrListOrMap.class) {
          @Override
          public boolean accepts(@Nullable Object o, ProcessingContext context) {
            if (!(o instanceof GrListOrMap)) return false;
            PsiElement parent = ((GrListOrMap)o).getParent();
            if (!(parent instanceof GrArgumentList)) return false;

            GrArgumentList argumentList = (GrArgumentList)parent;
            if (argumentList.getNamedArguments().length > 0) return false;
            if (argumentList.getExpressionArgumentIndex((GrListOrMap)o) > 0) return false;

            if (!(argumentList.getParent() instanceof GrCall)) return false;

            return true;
          }
        })
      )
    ));

    contributor.extend(CompletionType.BASIC, inArgumentListOfCall, instance);

    contributor.extend(CompletionType.BASIC, psiElement(GroovyTokenTypes.mIDENT).withParent(psiElement(GrArgumentLabel.class).withParent(
      GroovyPatterns.methodNamedParameter(null))), instance);
  }

  @Override
  protected void addCompletions(@NotNull CompletionParameters parameters,
                                ProcessingContext context,
                                @NotNull CompletionResultSet result) {
    PsiElement mapOrArgumentList;

    PsiElement parent = parameters.getPosition().getParent();
    if (parent instanceof GrReferenceExpression) {
      if (((GrReferenceExpression)parent).getQualifier() != null) return;
      mapOrArgumentList = parent.getParent();
    }
    else {
      mapOrArgumentList = parent.getParent().getParent();
    }

    PsiElement argumentList = mapOrArgumentList instanceof GrArgumentList ? mapOrArgumentList : mapOrArgumentList.getParent();

    final GrCall call = (GrCall)argumentList.getParent();
    List<GroovyResolveResult> results = new ArrayList<GroovyResolveResult>();
    //constructor call
    if (call instanceof GrConstructorCall) {
      GrConstructorCall constructorCall = (GrConstructorCall)call;
      ContainerUtil.addAll(results, constructorCall.multiResolveConstructor());
      ContainerUtil.addAll(results, constructorCall.multiResolveClass());
    }
    else if (call instanceof GrCallExpression) {
      GrCallExpression callExpression = (GrCallExpression)call;
      ContainerUtil.addAll(results, callExpression.getCallVariants(null));

      final PsiType type = ((GrCallExpression)call).getType();
      if (type instanceof PsiClassType) {
        final PsiClass psiClass = ((PsiClassType)type).resolve();
        results.add(new GroovyResolveResultImpl(psiClass, true));
      }
    }

    Set<PsiClass> usedClasses = new HashSet<PsiClass>();
    Set<String> usedNames = new HashSet<String>();
    for (GrNamedArgument argument : PsiUtil.getFirstMapNamedArguments(call)) {
      final GrArgumentLabel label = argument.getLabel();
      if (label != null) {
        ContainerUtil.addIfNotNull(label.getName(), usedNames);
      }
    }

    for (GroovyResolveResult resolveResult : results) {
      PsiElement element = resolveResult.getElement();
      if (element instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)element;
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass != null) {
          addPropertiesForClass(result, usedClasses, usedNames, containingClass, call);
        }

        Map<String, Condition<PsiType>> namedArguments = GroovyNamedArgumentProvider.getNamedArguments(call, method);

        for (String namedArgumentName : namedArguments.keySet()) {
          if (usedNames.add(namedArgumentName)) {
            final LookupElementBuilder lookup = LookupElementBuilder.create(namedArgumentName)
              .setIcon(GroovyIcons.DYNAMIC)
              .setInsertHandler(NamedArgumentInsertHandler.INSTANCE);
            result.addElement(lookup);
          }
        }
      }
      else if (element instanceof PsiClass) {
        addPropertiesForClass(result, usedClasses, usedNames, (PsiClass)element, call);
      }
    }
  }

  private static void addPropertiesForClass(CompletionResultSet result,
                                            Set<PsiClass> usedClasses,
                                            Set<String> usedNames,
                                            PsiClass containingClass,
                                            GrCall call) {
    if (!usedClasses.add(containingClass)) return;

    final PsiClass eventListener = JavaPsiFacade.getInstance(call.getProject()).findClass("java.util.EventListener", call.getResolveScope());

    Map<String, PsiMethod> writableProperties = new HashMap<String, PsiMethod>();
    for (PsiMethod method : containingClass.getAllMethods()) {
      if (GroovyPropertyUtils.isSimplePropertySetter(method) && !method.hasModifierProperty(PsiModifier.STATIC)) {
        final String name = GroovyPropertyUtils.getPropertyNameBySetter(method);
        if (name != null && !writableProperties.containsKey(name)) {
          writableProperties.put(name, method);
        }
      }
      else if (eventListener != null) {
        consumeListenerProperties(result, usedNames, method, eventListener);
      }
    }

    for (String name : writableProperties.keySet()) {
      if (usedNames.contains(name)) continue;
      usedNames.add(name);
      final LookupElementBuilder builder =
        LookupElementBuilder.create(writableProperties.get(name), name).setIcon(GroovyIcons.PROPERTY)
          .setInsertHandler(NamedArgumentInsertHandler.INSTANCE);
      result.addElement(builder);
    }
  }

  private static void consumeListenerProperties(CompletionResultSet result,
                                                Set<String> usedNames, PsiMethod method, PsiClass eventListenerClass) {
    if (method.getName().startsWith("add") && method.getParameterList().getParametersCount() == 1) {
      final PsiParameter parameter = method.getParameterList().getParameters()[0];
      final PsiType type = parameter.getType();
      if (type instanceof PsiClassType) {
        final PsiClassType classType = (PsiClassType)type;
        final PsiClass listenerClass = classType.resolve();
        if (listenerClass != null) {
          if (InheritanceUtil.isInheritorOrSelf(listenerClass, eventListenerClass, true)) {
            PsiMethod[] listenerMethods = listenerClass.getMethods();
            for (PsiMethod listenerMethod : listenerMethods) {
              final String name = listenerMethod.getName();
              if (usedNames.add(name)) {
                result.addElement(
                  LookupElementBuilder.create(name).setIcon(GroovyIcons.PROPERTY).setInsertHandler(NamedArgumentInsertHandler.INSTANCE));
              }
            }
          }
        }
      }
    }
  }
}

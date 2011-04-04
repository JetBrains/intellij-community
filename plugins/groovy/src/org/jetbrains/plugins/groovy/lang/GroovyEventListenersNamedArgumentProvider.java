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

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.extensions.GroovyNamedArgumentProvider;
import org.jetbrains.plugins.groovy.lang.completion.handlers.NamedArgumentInsertHandler;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrConstructor;

import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class GroovyEventListenersNamedArgumentProvider extends GroovyNamedArgumentProvider {
  @Override
  public void getNamedArguments(@NotNull GrCall call,
                                @Nullable PsiMethod method,
                                @Nullable String argumentName,
                                boolean forCompletion,
                                Map<String, ArgumentDescriptor> result) {
    // TODO IDEA-67465

    //if (!forCompletion || method == null || method instanceof GrConstructor) return;
    //
    //PsiClass aClass = method.getContainingClass();
    //if (aClass == null) return;
    //
    //for (PsiMethod m : aClass.getAllMethods()) {
    //  if (m.getName().startsWith("add") && method.getParameterList().getParametersCount() == 1) {
    //    final PsiParameter parameter = method.getParameterList().getParameters()[0];
    //    final PsiType type = parameter.getType();
    //    if (type instanceof PsiClassType) {
    //      final PsiClassType classType = (PsiClassType)type;
    //      final PsiClass listenerClass = classType.resolve();
    //      if (listenerClass != null) {
    //        if (InheritanceUtil.isInheritor(listenerClass, "java.util.EventListener")) {
    //          PsiMethod[] listenerMethods = listenerClass.getMethods();
    //          for (PsiMethod listenerMethod : listenerMethods) {
    //            final String name = listenerMethod.getName();
    //
    //            ArgumentDescriptor oldValue = result.put(name, TYPE_ANY);
    //            if (oldValue != null) result.put(name, oldValue);
    //          }
    //        }
    //      }
    //    }
    //  }
    //
    //}
  }
}

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
package org.jetbrains.plugins.groovy.refactoring.convertToJava.invocators;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.refactoring.convertToJava.ExpressionGenerator;

/**
 * @author Max Medvedev
 */
public abstract class CustomMethodInvocator {
  private static final ExtensionPointName<CustomMethodInvocator> EP_NAME =
    ExtensionPointName.create("org.intellij.groovy.convertToJava.customMethodInvocator");

  protected abstract boolean invoke(@NotNull ExpressionGenerator generator,
                                    @NotNull PsiMethod method,
                                    @Nullable GrExpression caller,
                                    @NotNull GrExpression[] exprs,
                                    @NotNull GrNamedArgument[] namedArgs,
                                    @NotNull GrClosableBlock[] closures,
                                    @NotNull PsiSubstitutor substitutor,
                                    @NotNull GroovyPsiElement context);

  public static boolean invokeMethodOn(@NotNull ExpressionGenerator generator,
                                       @NotNull GrGdkMethod method,
                                       @Nullable GrExpression caller,
                                       @NotNull GrExpression[] exprs,
                                       @NotNull GrNamedArgument[] namedArgs,
                                       @NotNull GrClosableBlock[] closures,
                                       @NotNull PsiSubstitutor substitutor,
                                       @NotNull GroovyPsiElement context) {
    final PsiMethod staticMethod = method.getStaticMethod();
    for (CustomMethodInvocator invocator : EP_NAME.getExtensions()) {
      if (invocator.invoke(generator, staticMethod, caller, exprs, namedArgs, closures, substitutor, context)) {
        return true;
      }
    }

    return false;
  }
}

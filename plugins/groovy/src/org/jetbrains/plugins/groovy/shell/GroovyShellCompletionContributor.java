/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.shell;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCommandArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * Created by Max Medvedev on 9/8/13
 */
public class GroovyShellCompletionContributor extends CompletionContributor {
  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    PsiFile file = parameters.getOriginalFile();
    if (!(file instanceof GroovyShellCodeFragment)) return;

    PsiElement position = parameters.getPosition();
    PsiElement parent = position.getParent();

    if (!(parent instanceof GrReferenceExpression && !((GrReferenceExpression)parent).isQualified())) return;

    if (PsiUtil.isExpressionStatement(parent)) {
      addAllCommands(result);
    }
    else if (parent.getParent() instanceof GrCommandArgumentList) {
      PsiElement ppparent = parent.getParent().getParent();
      if (ppparent instanceof GrMethodCall && isFirstArg((GrMethodCall)ppparent, parent)) {
        GrExpression invokedExpression = ((GrMethodCall)ppparent).getInvokedExpression();
        if (invokedExpression instanceof GrReferenceExpression && !((GrReferenceExpression)invokedExpression).isQualified()) {
          String name = ((GrReferenceExpression)invokedExpression).getReferenceName();

          if ("help".equals(name)) {
            addAllCommands(result);
          }
          else if ("show".equals(name)) {
            add(result, "classes");
            add(result, "imports");
            add(result, "preferences");
            add(result, "all");
          }
          else if ("purge".equals(name)) {
            add(result, "variables");
            add(result, "classes");
            add(result, "imports");
            add(result, "preferences");
            add(result, "all");
          }
          else if ("record".equals(name)) {
            add(result, "start");
            add(result, "stop");
            add(result, "status");
          }
          else if ("history".equals(name)) {
            add(result, "show");
            add(result, "recall");
            add(result, "flush");
            add(result, "clear");
          }
        }
      }
    }
  }

  private static void add(@NotNull CompletionResultSet result, @NotNull String name) {
    result.addElement(LookupElementBuilder.create(name));
  }

  private static void addAllCommands(@NotNull CompletionResultSet result) {
    add(result, "help");
    add(result, "exit");
    add(result, "quit");
    add(result, "display");
    add(result, "clear");
    add(result, "show");
    add(result, "inspect");
    add(result, "purge");
    add(result, "edit");
    add(result, "load");
    add(result, "save");
    add(result, "record");
    add(result, "history");
    add(result, "alias");
    add(result, "set");
  }

  private static boolean isFirstArg(@NotNull GrMethodCall ppparent, @NotNull PsiElement parent) {
    GroovyPsiElement[] arguments = ppparent.getArgumentList().getAllArguments();
    return arguments.length > 0 && arguments[0] == parent;
  }
}

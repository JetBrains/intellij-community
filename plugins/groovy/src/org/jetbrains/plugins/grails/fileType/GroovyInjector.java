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

package org.jetbrains.plugins.grails.fileType;

import com.intellij.lang.Language;
import com.intellij.lang.injection.ConcatenationAwareInjector;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;

public class GroovyInjector implements ProjectComponent {
  private Project myProject;

  public GroovyInjector(Project project) {
    myProject = project;
  }

  public void initComponent() {
    InjectedLanguageManager.getInstance(myProject).registerConcatenationInjector(new MyLanguageInjector());
  }

  public void disposeComponent() {
  }

  @NotNull
  public String getComponentName() {
    return "GroovyInjector";
  }

  public void projectOpened() {
    // called when project is opened
  }

  public void projectClosed() {
    // called when project is being closed
  }

  public static final String EVAL_NAME = "evaluate";
  public static final String PARSE_NAME = "parse";
  private static final String GROOVY_SHELL_QNAME = "groovy.lang.GroovyShell";

  private static class MyLanguageInjector implements ConcatenationAwareInjector {
    public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement... operands) {
      for (PsiElement operand : operands) {
        if (!(operand instanceof PsiLanguageInjectionHost)) return;
      }

      final PsiExpressionList argList = PsiTreeUtil.getParentOfType(operands[0], PsiExpressionList.class);
      if (argList != null) {
        final PsiExpression firstArg = argList.getExpressions()[0];
        if (contains(firstArg, operands)) {
          final PsiElement parent = argList.getParent();
          if (parent instanceof PsiMethodCallExpression) {
            final PsiMethodCallExpression call = (PsiMethodCallExpression) parent;
            final String refName = call.getMethodExpression().getReferenceName();
            if (PARSE_NAME.equals(refName) || EVAL_NAME.equals(refName)) {
              final PsiMethod method = call.resolveMethod();
              if (method != null) {
                final PsiClass clazz = method.getContainingClass();
                if (clazz != null) {
                  if (GROOVY_SHELL_QNAME.equals(clazz.getQualifiedName())) {
                    final Language groovyLanguage = GroovyFileType.GROOVY_FILE_TYPE.getLanguage();
                    registrar.startInjecting(groovyLanguage);
                    for (PsiElement operand : operands) {
                      registrar.addPlace("", "", (PsiLanguageInjectionHost)operand, new TextRange(1, operand.getTextLength() - 1));
                    }
                    registrar.doneInjecting();
                  }
                }
              }
            }
          }
        }
      }
    }

    private boolean contains(PsiExpression arg, PsiElement[] operands) {
      if (operands.length == 1) return arg.equals(operands[0]);
      if (arg instanceof PsiBinaryExpression) {
        final PsiExpression lop = ((PsiBinaryExpression) arg).getLOperand();
        for (PsiElement operand : operands) {
          if (operand.equals(lop)) return true;
        }
      }

      return false;
    }
  }
}

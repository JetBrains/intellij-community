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
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;

public class GroovyInjector implements ProjectComponent {
  private Project myProject;

  public GroovyInjector(Project project) {
    myProject = project;
  }

  public void initComponent() {
    PsiManager.getInstance(myProject).registerLanguageInjector(new MyLanguageInjector());
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

  private static class MyLanguageInjector implements LanguageInjector {

    public void getLanguagesToInject(@NotNull PsiLanguageInjectionHost host, @NotNull InjectedLanguagePlaces injectionPlacesRegistrar) {
      final Language groovyLanguage = GroovyFileType.GROOVY_FILE_TYPE.getLanguage();
      if (host instanceof PsiLiteralExpression && host.getParent() instanceof PsiExpressionList) {
        final PsiExpression[] args = ((PsiExpressionList) host.getParent()).getExpressions();
        if (host == args[0]) {
          final PsiElement pparent = host.getParent().getParent();
          if (pparent instanceof PsiMethodCallExpression) {
            final PsiMethodCallExpression call = (PsiMethodCallExpression) pparent;
            final String refName = call.getMethodExpression().getReferenceName();
            if (PARSE_NAME.equals(refName) || EVAL_NAME.equals(refName)) {
              final PsiMethod method = call.resolveMethod();
              if (method != null) {
                final PsiClass clazz = method.getContainingClass();
                if (clazz != null) {
                  if (GROOVY_SHELL_QNAME.equals(clazz.getQualifiedName())) {
                    injectionPlacesRegistrar.addPlace(groovyLanguage, new TextRange(1, host.getTextLength() - 1), "", "");
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

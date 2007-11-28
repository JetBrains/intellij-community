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

package org.jetbrains.plugins.groovy.refactoring.inline;

import com.intellij.lang.refactoring.InlineHandler;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ControlFlowBuilder;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author ilyas
 */
public abstract class GroovyInlineMethodUtil {

  private static String myErrorMessage = "ok";
  public static final String REFACTORING_NAME = GroovyRefactoringBundle.message("inline.method.title");

  public static InlineHandler.Settings inlineMethodSettings(GrMethod method, Editor editor) {

    final String methodName = method.getNameIdentifierGroovy().getText();
    final Project project = method.getProject();
    final Collection<PsiReference> refs = ReferencesSearch.search(method, GlobalSearchScope.projectScope(method.getProject()), false).findAll();
    ArrayList<PsiElement> exprs = new ArrayList<PsiElement>();
    for (PsiReference ref : refs) {
      exprs.add(ref.getElement());
    }

    GroovyRefactoringUtil.highlightOccurrences(project, editor, exprs.toArray(PsiElement.EMPTY_ARRAY));
    if (method.getBody() == null) {
      String message;
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        message = GroovyRefactoringBundle.message("refactoring.cannot.be.applied.to.abstract.methods", REFACTORING_NAME);
      } else {
        message = GroovyRefactoringBundle.message("refactoring.cannot.be.applied.no.sources.attached", REFACTORING_NAME);
      }
      showErrorMessage(message, project);
      return null;
    }

    return null;
  }

  private static void showErrorMessage(String message, final Project project) {
    Application application = ApplicationManager.getApplication();
    myErrorMessage = message;
    if (!application.isUnitTestMode()) {
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INLINE_METHOD, project);
    }
  }

  static String getInvokedResult(){
    Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode()) {
      return myErrorMessage;
    } else {
      return null;
    }
  }

}

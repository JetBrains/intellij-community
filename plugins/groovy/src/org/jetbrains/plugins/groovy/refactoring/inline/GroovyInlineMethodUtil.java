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

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.lang.refactoring.InlineHandler;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
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

  public static InlineHandler.Settings inlineMethodSettings(GrMethod method, Editor editor, boolean invokedOnReference) {

    final String methodName = method.getNameIdentifierGroovy().getText();
    final Project project = method.getProject();
    final Collection<PsiReference> refs = ReferencesSearch.search(method, GlobalSearchScope.projectScope(method.getProject()), false).findAll();
    ArrayList<PsiElement> exprs = new ArrayList<PsiElement>();
    for (PsiReference ref : refs) {
      PsiElement element = ref.getElement();
      if (element!= null && element.getContainingFile() instanceof GroovyFile) {
        if (isStaticMethod(method) || areInSameClass(element, method)) { // todo implement for other cases
          exprs.add(element);
        }
      }
    }

    PsiReference reference = editor != null ? TargetElementUtil.findReference(editor, editor.getCaretModel().getOffset()) : null;
    GroovyRefactoringUtil.highlightOccurrences(project, editor, exprs.toArray(PsiElement.EMPTY_ARRAY));
    if (method.getBlock() == null) {
      String message;
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        message = GroovyRefactoringBundle.message("refactoring.cannot.be.applied.to.abstract.methods", REFACTORING_NAME);
      } else {
        message = GroovyRefactoringBundle.message("refactoring.cannot.be.applied.no.sources.attached", REFACTORING_NAME);
      }
      showErrorMessage(message, project);
      return null;
    }

    if (checkBadReturns(method)) { //todo process tail method calls
      String message = GroovyRefactoringBundle.message("refactoring.is.not.supported.when.return.statement.interrupts.the.execution.flow", REFACTORING_NAME);
      showErrorMessage(message, project);
      return null;
    }
    // if invoked on method definition
    if (reference == null && checkRecursive(method)) {
      String message = GroovyRefactoringBundle.message("refactoring.is.not.supported.for.recursive.methods", REFACTORING_NAME);
      showErrorMessage(message, project);
      return null;
    }

    if (method.isConstructor()) { // todo implement refactoring for some constructor cases
      String message = GroovyRefactoringBundle.message("refactoring.cannot.be.applied.to.constructors", REFACTORING_NAME);
      showErrorMessage(message, project);
      return null;
    }

    return new InlineHandler.Settings(){

      public boolean isOnlyOneReferenceToInline() {
        return false;
      }
    };
  }

  private static boolean checkBadReturns(GrMethod method) {
    GrReturnStatement[] returnStatements = GroovyRefactoringUtil.findReturnStatements(method);
    GrOpenBlock block = method.getBlock();
    if (block == null || returnStatements.length == 0) return false;
    GrStatement[] statements = block.getStatements();
    if (statements.length == 0) return false;

    ControlFlowBuilder builder = new ControlFlowBuilder();
    Instruction[] instructions = builder.buildControlFlow(block, null, null);

    return false;
  }

  private static void showErrorMessage(String message, final Project project) {
    Application application = ApplicationManager.getApplication();
    myErrorMessage = message;
    if (!application.isUnitTestMode()) {
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INLINE_METHOD, project);
    }
  }

  static String getInvokedResult() {
    Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode()) {
      String message = myErrorMessage;
      myErrorMessage = "ok";
      return message;
    } else {
      return null;
    }
  }

  public static boolean checkRecursive(GrMethod method) {
    return checkCalls(method.getBlock(), method);
  }

  private static boolean checkCalls(PsiElement scope, GrMethod method) {
    if (scope instanceof GrMethodCallExpression) {
      GrExpression expression = ((GrMethodCallExpression) scope).getInvokedExpression();
      PsiReference ref = expression.getReference();
      if (ref != null) {
        PsiElement element = ref.resolve();
        if (element instanceof GrMethod && method.equals(element)) return true;
      }
    }
    for (PsiElement child = scope.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (checkCalls(child, method)) return true;
    }
    return false;
  }

  static boolean isStaticMethod(@NotNull GrMethod method){
    return method.hasModifierProperty(PsiModifier.STATIC);
  }

  static boolean areInSameClass(PsiElement element, GrMethod method){
    PsiElement parent = element;
    while (!(parent == null || parent instanceof PsiClass || parent instanceof PsiFile)){
      parent = parent.getParent();
    }
    if (parent instanceof PsiClass) {
      PsiClass methodClass = method.getContainingClass();
      return parent == methodClass;
    }
    if (parent instanceof GroovyFile) {
      PsiElement mParent = method.getParent();
      return  mParent instanceof GroovyFile && mParent == parent;
    }
    return false;
  }


}

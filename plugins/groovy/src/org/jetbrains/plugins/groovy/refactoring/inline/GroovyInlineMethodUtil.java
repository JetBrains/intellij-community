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
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringMessageDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author ilyas
 */
public abstract class GroovyInlineMethodUtil {

  private static String myErrorMessage = "ok";
  public static final String REFACTORING_NAME = GroovyRefactoringBundle.message("inline.method.title");

  public static InlineHandler.Settings inlineMethodSettings(GrMethod method, Editor editor, boolean invokedOnReference) {

    final Project project = method.getProject();
    if (method.isConstructor()) {
      String message = GroovyRefactoringBundle.message("refactoring.cannot.be.applied.to.constructors", REFACTORING_NAME);
      showErrorMessage(message, project);
      return null;
    }

    PsiReference reference = editor != null ? TargetElementUtil.findReference(editor, editor.getCaretModel().getOffset()) : null;
    if (!invokedOnReference || reference == null) {
      String message = GroovyRefactoringBundle.message("multiple.method.inline.is.not.suppored", REFACTORING_NAME);
      showErrorMessage(message, project);
      return null;
    }

    PsiElement element = reference.getElement();

    if (element.getContainingFile() instanceof GroovyFile) {
      if (!(isStaticMethod(method) || areInSameClass(element, method))) { // todo implement for other cases
//        showErrorMessage("Other class support will be implemented soon", project);
//        return null;
      }
    }

    if (!(element instanceof GrExpression && element.getParent() instanceof GrCallExpression)) {
      String message = GroovyRefactoringBundle.message("refactoring.is.available.only.for.method.calls", REFACTORING_NAME);
      showErrorMessage(message, project);
      return null;
    }

    GrCallExpression call = (GrCallExpression) element.getParent();

    if (PsiTreeUtil.getParentOfType(element, GrParameter.class) != null) {
      String message = GroovyRefactoringBundle.message("refactoring.is.not.supported.in.parameter.initializers", REFACTORING_NAME);
      showErrorMessage(message, project);
      return null;
    }


    GroovyRefactoringUtil.highlightOccurrences(project, editor, new GrExpression[]{call});
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

    if (hasBadReturns(method) && !isTailMethodCall(call)) { 
      String message = GroovyRefactoringBundle.message("refactoring.is.not.supported.when.return.statement.interrupts.the.execution.flow", REFACTORING_NAME);
      showErrorMessage(message, project);
      return null;
    }

    return inlineMethodDialogResult(GroovyRefactoringUtil.getMethodSignature(method), project);
  }

  /**
   * Checks wheter given method call is tail call of other method or closure
   * @param call [tail?] Method call
   * @return
   */
  static boolean isTailMethodCall(GrCallExpression call) {
    GrStatement stmt = call;
    PsiElement parent = call.getParent();

    // return statement
    if (parent instanceof GrReturnStatement) {
      stmt = ((GrReturnStatement) parent);
      parent = parent.getParent();
    }
    // method body result
    if (parent instanceof GrOpenBlock) {
      if (parent.getParent() instanceof GrMethod) {
        GrStatement[] statements = ((GrOpenBlock) parent).getStatements();
        return statements.length > 0 && stmt == statements[statements.length - 1];

      }
    }
    // closure result
    if (parent instanceof GrClosableBlock) {
      GrStatement[] statements = ((GrClosableBlock) parent).getStatements();
      return statements.length > 0 && stmt == statements[statements.length - 1];
    }

    // todo add for inner method block statements
    // todo test me!
    if (stmt instanceof GrReturnStatement) {
      GrMethod method = PsiTreeUtil.getParentOfType(stmt, GrMethod.class);
      if (method != null) {
        Collection<GrReturnStatement> returnStatements = GroovyRefactoringUtil.findReturnStatements(method);
        return returnStatements.contains(stmt) && !hasBadReturns(method);
      }
    }

    return false;
  }

  /**
   * Shows dialog with question to inline
   */
  private static InlineHandler.Settings inlineMethodDialogResult(String methodSignature, Project project) {
    Application application = ApplicationManager.getApplication();
    if (!application.isUnitTestMode()) {
      final String question = GroovyRefactoringBundle.message("inline.method.prompt.0", methodSignature);
      RefactoringMessageDialog dialog = new RefactoringMessageDialog(
          REFACTORING_NAME,
          question,
          HelpID.INLINE_METHOD,
          "OptionPane.questionIcon",
          true,
          project);
      dialog.show();
      if (!dialog.isOK()) {
        WindowManager.getInstance().getStatusBar(project).setInfo(GroovyRefactoringBundle.message("press.escape.to.remove.the.highlighting"));
        return null;
      }
    }
    return new InlineHandler.Settings() {
      public boolean isOnlyOneReferenceToInline() {
        // todo implement multiple inline
        return true;
      }
    };

  }

  private static boolean hasBadReturns(GrMethod method) {
    Collection<GrReturnStatement> returnStatements = GroovyRefactoringUtil.findReturnStatements(method);
    GrOpenBlock block = method.getBlock();
    if (block == null || returnStatements.size() == 0) return false;
    boolean checked = checkTailOpenBlock(block, returnStatements);
    return !(checked && returnStatements.isEmpty());
  }

  private static boolean checkTailIfStatement(GrIfStatement ifStatement, Collection<GrReturnStatement> returnStatements) {
    GrStatement thenBranch = ifStatement.getThenBranch();
    GrStatement elseBranch = ifStatement.getElseBranch();
    if (elseBranch == null) return false;
    boolean tb = false;
    boolean eb = false;
    if (thenBranch instanceof GrReturnStatement) {
      tb = returnStatements.remove(thenBranch);
    } else if (thenBranch instanceof GrBlockStatement) {
      tb = checkTailOpenBlock(((GrBlockStatement) thenBranch).getBlock(), returnStatements);
    }
    if (elseBranch instanceof GrReturnStatement) {
      eb = returnStatements.remove(elseBranch);
    } else if (thenBranch instanceof GrBlockStatement) {
      eb = checkTailOpenBlock(((GrBlockStatement) elseBranch).getBlock(), returnStatements);
    }

    return tb && eb;
  }

  private static boolean checkTailOpenBlock(GrOpenBlock block, Collection<GrReturnStatement> returnStatements) {
    if (block == null) return false;
    GrStatement[] statements = block.getStatements();
    if (statements.length == 0) return false;
    GrStatement last = statements[statements.length - 1];
    if (last instanceof GrReturnStatement && returnStatements.contains(last)) {
      returnStatements.remove(last);
      return true;
    }
    if (last instanceof GrIfStatement) {
      return checkTailIfStatement(((GrIfStatement) last), returnStatements);
    }
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

  static boolean isStaticMethod(@NotNull GrMethod method) {
    return method.hasModifierProperty(PsiModifier.STATIC);
  }

  static boolean areInSameClass(PsiElement element, GrMethod method) {
    PsiElement parent = element;
    while (!(parent == null || parent instanceof PsiClass || parent instanceof PsiFile)) {
      parent = parent.getParent();
    }
    if (parent instanceof PsiClass) {
      PsiClass methodClass = method.getContainingClass();
      return parent == methodClass;
    }
    if (parent instanceof GroovyFile) {
      PsiElement mParent = method.getParent();
      return mParent instanceof GroovyFile && mParent == parent;
    }
    return false;
  }

  public static Collection<ReferenceExpressionInfo> collectReferenceInfo(GrCallExpression call, GrMethod method) {
    return null;
  }

  static class ReferenceExpressionInfo {
    public final PsiElement declaration;
    public final GrReferenceExpression expression;
    public final int offsetInMethod;
    public final PsiClass containingClass;


    public ReferenceExpressionInfo(GrReferenceExpression expression, int offsetInMethod, PsiElement declaration, PsiClass containingClass) {
      this.expression = expression;
      this.offsetInMethod = offsetInMethod;
      this.declaration = declaration;
      this.containingClass = containingClass;
    }
  }


}

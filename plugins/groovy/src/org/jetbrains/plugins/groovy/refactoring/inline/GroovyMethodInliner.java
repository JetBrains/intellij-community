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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author ilyas
 */
public class GroovyMethodInliner implements InlineHandler.Inliner {

  private final GrMethod myMethod;
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.inline.GroovyMethodInliner");

  public GroovyMethodInliner(GrMethod method) {
    myMethod = method;
  }

  @Nullable
  public Collection<String> getConflicts(PsiReference reference, PsiElement referenced) {
    return new ArrayList<String>();
  }

  public void inlineReference(PsiReference reference, PsiElement referenced) {
    PsiElement element = reference.getElement();
    assert element instanceof GrExpression && element.getParent() instanceof GrCallExpression;
    GrCallExpression call = (GrCallExpression) element.getParent();
    SmartPsiElementPointer<GrExpression> pointer =
        inlineReferenceImpl(call, myMethod, isOnExpressionOrReturnPlace(call), GroovyInlineMethodUtil.isTailMethodCall(call));

    // highilght replaced result
    if (pointer != null && pointer.getElement() != null) {
      Project project = referenced.getProject();
      FileEditorManager manager = FileEditorManager.getInstance(project);
      Editor editor = manager.getSelectedTextEditor();
      GroovyRefactoringUtil.highlightOccurrences(project, editor, new PsiElement[]{pointer.getElement()});
      WindowManager.getInstance().getStatusBar(project).setInfo(GroovyRefactoringBundle.message("press.escape.to.remove.the.highlighting"));
      GrExpression expression = pointer.getElement();
      if (editor != null && expression != null) {
        editor.getCaretModel().moveToOffset(expression.getTextRange().getEndOffset());
      }
    }
  }

  /*
  Method call is used as expression in some enclosing expression or
  is method return result
  */
  private static boolean isOnExpressionOrReturnPlace(GrCallExpression call) {
    PsiElement parent = call.getParent();
    if (!(parent instanceof GrVariableDeclarationOwner)) {
      return true;
    }

    // tail calls in methods and closures
    GrVariableDeclarationOwner owner = (GrVariableDeclarationOwner) parent;
    if (owner instanceof GrClosableBlock ||
        owner instanceof GrOpenBlock && owner.getParent() instanceof GrMethod) {
      GrStatement[] statements = ((GrCodeBlock) owner).getStatements();
      assert statements.length > 0;
      GrStatement last = statements[statements.length - 1];
      if (last == call) return true;
      if (last instanceof GrReturnStatement && call == ((GrReturnStatement) last).getReturnValue()) {
        return true;
      }
    }
    return false;
  }


  static SmartPsiElementPointer<GrExpression> inlineReferenceImpl(GrCallExpression call, GrMethod method, boolean replaceCall, boolean isTailMethodCall) {
    try {

      GrMethod newMethod = prepareNewMethod(call, method);
      GrExpression result = getAloneResultExpression(newMethod);
      if (result != null) {
        GrExpression expression = call.replaceWithExpression(result, true);
        return SmartPointerManager.getInstance(result.getProject()).createSmartPsiElementPointer(expression);
      }

      String resultName = InlineMethodConflictSolver.suggestNewName("result", newMethod, call);
      GroovyElementFactory factory = GroovyElementFactory.getInstance(call.getProject());

      // Add variable for method result
      Collection<GrReturnStatement> returnStatements = GroovyRefactoringUtil.findReturnStatements(newMethod);
      boolean hasTailExpr = GroovyRefactoringUtil.hasTailReturnExpression(method);
      boolean hasReturnStatements = returnStatements.size() > 0;
      PsiType methodType = method.getReturnType();
      GrOpenBlock body = newMethod.getBlock();
      assert body != null;
      GrStatement[] statements = body.getStatements();
      GrExpression replaced = null;
      if (replaceCall && !isTailMethodCall) {
        GrExpression resultExpr;
        if (PsiType.VOID == methodType) {
          resultExpr = factory.createExpressionFromText("null");
        } else if (hasReturnStatements) {
          resultExpr = factory.createExpressionFromText(resultName);
        } else if (hasTailExpr) {
          GrExpression expr = (GrExpression) statements[statements.length - 1];
          resultExpr = factory.createExpressionFromText(expr.getText());
        } else {
          resultExpr = factory.createExpressionFromText("null");
        }
        replaced = call.replaceWithExpression(resultExpr, true);
      }

      // Calculate anchor to insert before
      GrExpression enclosingExpr = changeEnclosingStatement(replaced != null ? replaced : call);
      GrVariableDeclarationOwner owner = PsiTreeUtil.getParentOfType(enclosingExpr, GrVariableDeclarationOwner.class);
      PsiElement element = enclosingExpr;
      assert owner != null;
      while (element != null && element.getParent() != owner) {
        element = element.getParent();
      }
      assert element != null && element instanceof GrStatement;
      GrStatement anchor = (GrStatement) element;

      if (!replaceCall) {
        assert anchor == enclosingExpr;
      }

      // Process method return statements
      if (hasReturnStatements && PsiType.VOID != methodType && !isTailMethodCall) {
        GrVariableDeclaration resultDecl = factory.createVariableDeclaration(new String[0], resultName, null, methodType, false);
        owner.addStatementBefore(resultDecl, anchor);

        // Replace all return statements with assignments to 'result' variable
        for (GrReturnStatement returnStatement : returnStatements) {
          GrExpression value = returnStatement.getReturnValue();
          if (value != null) {
            GrExpression assignment = factory.createExpressionFromText(resultName + " = " + value.getText());
            returnStatement.replaceWithStatement(assignment);
          } else {
            returnStatement.replaceWithStatement(factory.createExpressionFromText(resultName + " = null"));
          }
        }
      }
      if (PsiType.VOID == methodType && !isTailMethodCall) {
        for (GrReturnStatement returnStatement : returnStatements) {
          returnStatement.removeStatement();
        }
      }

      // Add all method statements
      statements = body.getStatements();
      for (GrStatement statement : statements) {
        if (!(statements.length > 0 && statement == statements[statements.length - 1] && hasTailExpr)) {
          owner.addStatementBefore(statement, anchor);
        }
      }
      if (replaceCall && !isTailMethodCall) {
        assert replaced != null;
        SmartPsiElementPointer<GrExpression> pointer = SmartPointerManager.getInstance(replaced.getProject()).createSmartPsiElementPointer(replaced);
        reformatOwner(owner);
        return pointer;
      } else {
        GrStatement stmt;
        if (isTailMethodCall && enclosingExpr.getParent() instanceof GrReturnStatement) {
          stmt = ((GrReturnStatement) enclosingExpr.getParent());
        } else {
          stmt = enclosingExpr;
        }
        stmt.removeStatement();
        reformatOwner(owner);
        return null;
      }
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return null;
  }

  private static void reformatOwner(GrVariableDeclarationOwner owner) throws IncorrectOperationException {
    if (owner == null) return;
    PsiFile file = owner.getContainingFile();
    Project project = file.getProject();
    PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
    Document document = manager.getDocument(file);
    if (document != null) {
      manager.doPostponedOperationsAndUnblockDocument(document);
      CodeStyleManager.getInstance(project).adjustLineIndent(file, owner.getTextRange());
    }
  }

  private static GrExpression changeEnclosingStatement(GrExpression expr) throws IncorrectOperationException {

    PsiElement parent = expr.getParent();
    while (!(parent instanceof GrLoopStatement) &&
        !(parent instanceof GrIfStatement) &&
        !(parent instanceof GrVariableDeclarationOwner) &&
        parent != null) {
      parent = parent.getParent();
    }
    assert parent != null;
    if (parent instanceof GrVariableDeclarationOwner) {
      return expr;
    } else {
      GroovyElementFactory factory = GroovyElementFactory.getInstance(expr.getProject());
      PsiElement tempStmt = expr;
      while (parent != tempStmt.getParent()) {
        tempStmt = tempStmt.getParent();
      }
      GrBlockStatement blockStatement = factory.createBlockStatement();
      if (parent instanceof GrLoopStatement) {
        ((GrLoopStatement) parent).replaceBody(blockStatement);
      } else {
        GrIfStatement ifStatement = (GrIfStatement) parent;
        if (tempStmt == ifStatement.getThenBranch()) {
          ifStatement.replaceThenBranch(blockStatement);
        } else if (tempStmt == ifStatement.getElseBranch()) {
          ifStatement.replaceElseBranch(blockStatement);
        }
      }
      blockStatement.getBlock().addStatementBefore(((GrStatement) tempStmt), null);
      GrStatement statement = blockStatement.getBlock().getStatements()[0];
      if (statement instanceof GrReturnStatement) {
        expr = ((GrReturnStatement) statement).getReturnValue();
      } else {
        expr = ((GrExpression) statement);
      }
      return expr;
    }
  }


  /*
  Parepare temporary method sith non-conflicting local names
  */
  private static GrMethod prepareNewMethod(GrCallExpression call, GrMethod method) throws IncorrectOperationException {
    GroovyElementFactory factory = GroovyElementFactory.getInstance(method.getProject());
    GrMethod newMethod = factory.createMethodFromText(method.getText());
    ArrayList<GrVariable> innerVariables = new ArrayList<GrVariable>();
    collectInnerVariables(newMethod.getBlock(), innerVariables);
    // there are only local variables and parameters (possible of inner closures)
    for (GrVariable variable : innerVariables) {
      String name = variable.getName();
      if (name != null) {
        String newName = InlineMethodConflictSolver.suggestNewName(name, method, call);
        if (!newName.equals(variable.getName())) {
          variable.setName(newName);
        }
      }
    }
    GroovyRefactoringUtil.replaceParamatersWithArguments(call, newMethod);
    return newMethod;
  }


  private static void collectInnerVariables(PsiElement element, ArrayList<GrVariable> variables) {
    if (element == null) return;
    for (PsiElement child : element.getChildren()) {
      if (child instanceof GrVariable && !(child instanceof GrParameter)) {
        variables.add(((GrVariable) child));
      }
      if (!(child instanceof GrClosableBlock)) {
        collectInnerVariables(child, variables);
      }
    }
  }

  /**
   * Get method result expression (if it is alone in method)
   *
   * @return null if method has more or less than one return statement or has void type
   */
  static GrExpression getAloneResultExpression(GrMethod method) {
    GrOpenBlock body = method.getBlock();
    assert body != null;
    GrStatement[] statements = body.getStatements();
    if (statements.length == 1) {
      if (statements[0] instanceof GrExpression) return ((GrExpression) statements[0]);
      if (statements[0] instanceof GrReturnStatement) {
        GrExpression value = ((GrReturnStatement) statements[0]).getReturnValue();
        if (value == null && (method.getReturnType() != PsiType.VOID)) {
          return GroovyElementFactory.getInstance(method.getProject()).createExpressionFromText("null");
        }
        return value;
      }
    }
    return null;
  }


}

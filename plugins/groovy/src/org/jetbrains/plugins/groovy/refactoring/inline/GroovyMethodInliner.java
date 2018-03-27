/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.refactoring.inline;

import com.intellij.lang.refactoring.InlineHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyNameSuggestionUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.refactoring.NameValidator;
import org.jetbrains.plugins.groovy.refactoring.util.AnySupers;

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

  @Override
  @Nullable
  public MultiMap<PsiElement, String> getConflicts(@NotNull PsiReference reference, @NotNull PsiElement referenced) {
    PsiElement element = reference.getElement();
    if (!(element instanceof GrExpression && element.getParent() instanceof GrCallExpression)) {
      final MultiMap<PsiElement, String> map = new MultiMap<>();
      map.putValue(element, GroovyRefactoringBundle.message("cannot.inline.reference.0", element.getText()));
      return map;
    }
    GrCallExpression call = (GrCallExpression) element.getParent();
    Collection<GroovyInlineMethodUtil.ReferenceExpressionInfo> infos = GroovyInlineMethodUtil.collectReferenceInfo(myMethod);
    return collectConflicts(call, infos);
  }

  @NotNull
  private static MultiMap<PsiElement, String> collectConflicts(@NotNull GrCallExpression call, @NotNull Collection<GroovyInlineMethodUtil.ReferenceExpressionInfo> infos) {
    MultiMap<PsiElement, String> conflicts = new MultiMap<>();

    for (GroovyInlineMethodUtil.ReferenceExpressionInfo info : infos) {
      if (!PsiUtil.isAccessible(call, info.declaration)) {
        if (info.declaration instanceof PsiMethod) {
          String className = info.containingClass.getName();
          String signature = GroovyRefactoringUtil.getMethodSignature((PsiMethod)info.declaration);
          String name = CommonRefactoringUtil.htmlEmphasize(className + "." + signature);
          conflicts.putValue(info.declaration, GroovyRefactoringBundle.message("method.is.not.accessible.form.context.0", name));
        }
        else if (info.declaration instanceof PsiField) {
          if (!(info.declaration instanceof GrField && ((GrField)info.declaration).getGetters().length > 0)) { // conflict if field doesn't have implicit getters
            String className = info.containingClass.getName();
            String name = CommonRefactoringUtil.htmlEmphasize(className + "." + info.getPresentation());
            conflicts.putValue(info.declaration, GroovyRefactoringBundle.message("field.is.not.accessible.form.context.0", name));
          }
        }
      }
      AnySupers visitor = new AnySupers();
      info.expression.accept(visitor);
      if (visitor.containsSupers()) {
        conflicts.putValue(info.expression, GroovyRefactoringBundle.message("super.reference.is.used"));
      }
    }

    return conflicts;
  }

  @Override
  public void inlineUsage(@NotNull UsageInfo usage, @NotNull PsiElement referenced) {
    PsiElement element=usage.getElement();

    if (!(element instanceof GrExpression && element.getParent() instanceof GrCallExpression)) return;

    final Editor editor = getCurrentEditorIfApplicable(element);

    GrCallExpression call = (GrCallExpression) element.getParent();
    RangeMarker marker = inlineReferenceImpl(call, myMethod, isOnExpressionOrReturnPlace(call), GroovyInlineMethodUtil.isTailMethodCall(call), editor);

    // highlight replaced result
    if (marker != null) {
      Project project = referenced.getProject();
      TextRange range = TextRange.create(marker);
      GroovyRefactoringUtil.highlightOccurrencesByRanges(project, editor, new TextRange[]{range});

      WindowManager.getInstance().getStatusBar(project).setInfo(GroovyRefactoringBundle.message("press.escape.to.remove.the.highlighting"));
      if (editor != null) {
        editor.getCaretModel().moveToOffset(marker.getEndOffset());
      }
    }
  }

  @Nullable
  private static Editor getCurrentEditorIfApplicable(@NotNull PsiElement element) {
    final Project project = element.getProject();
    final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();

    if (editor != null &&
        editor.getDocument() == PsiDocumentManager.getInstance(project).getDocument(element.getContainingFile())) {
      return editor;
    }

    return null;
  }

  @Nullable
  static RangeMarker inlineReferenceImpl(@NotNull GrCallExpression call,
                                         @NotNull GrMethod method,
                                         boolean resultOfCallExplicitlyUsed,
                                         boolean isTailMethodCall,
                                         @Nullable Editor editor) {
    try {
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(call.getProject());
      final Project project = call.getProject();

      // Variable declaration for qualifier expression
      GrVariableDeclaration qualifierDeclaration = null;
      GrReferenceExpression innerQualifier = null;
      GrExpression qualifier = null;
      if (call instanceof GrMethodCallExpression && ((GrMethodCallExpression) call).getInvokedExpression() != null) {
        GrExpression invoked = ((GrMethodCallExpression) call).getInvokedExpression();
        if (invoked instanceof GrReferenceExpression && ((GrReferenceExpression) invoked).getQualifierExpression() != null) {
          qualifier = ((GrReferenceExpression) invoked).getQualifierExpression();
          if (PsiUtil.isSuperReference(qualifier)) {
            qualifier = null;
          }
          else if (!GroovyInlineMethodUtil.isSimpleReference(qualifier)) {
            String qualName = generateQualifierName(call, method, project, qualifier);
            qualifier = (GrExpression)PsiUtil.skipParentheses(qualifier, false);
            qualifierDeclaration = factory.createVariableDeclaration(ArrayUtil.EMPTY_STRING_ARRAY, qualifier, null, qualName);
            innerQualifier = (GrReferenceExpression) factory.createExpressionFromText(qualName);
          } else {
            innerQualifier = (GrReferenceExpression) qualifier;
          }
        }
      }


      GrMethod _method = prepareNewMethod(call, method, qualifier);
      GrExpression result = getAloneResultExpression(_method);
      if (result != null) {
        GrExpression expression = call.replaceWithExpression(result, false);
        TextRange range = expression.getTextRange();
        return editor != null ? editor.getDocument().createRangeMarker(range.getStartOffset(), range.getEndOffset(), true) : null;
      }

      GrMethod newMethod = prepareNewMethod(call, method, innerQualifier);
      String resultName = InlineMethodConflictSolver.suggestNewName("result", newMethod, call);

      // Add variable for method result
      Collection<GrStatement> returnStatements = ControlFlowUtils.collectReturns(newMethod.getBlock());
      final int returnCount = returnStatements.size();
      PsiType methodType = method.getInferredReturnType();
      GrOpenBlock body = newMethod.getBlock();
      assert body != null;


      GrExpression replaced;
      if (resultOfCallExplicitlyUsed && !isTailMethodCall) {
        GrExpression resultExpr = null;
        if (PsiType.VOID.equals(methodType)) {
          resultExpr = factory.createExpressionFromText("null");
        }
        else if (returnCount == 1) {
          final GrExpression returnExpression = ControlFlowUtils.extractReturnExpression(returnStatements.iterator().next());
          if (returnExpression != null) {
            resultExpr = factory.createExpressionFromText(returnExpression.getText());
          }
        }
        else if (returnCount > 1) {
          resultExpr = factory.createExpressionFromText(resultName);
        }

        if (resultExpr == null) {
          resultExpr = factory.createExpressionFromText("null");
        }
        replaced = call.replaceWithExpression(resultExpr, false);
      }
      else {
        replaced = call;
      }

      // Calculate anchor to insert before
      GrExpression enclosingExpr = GroovyRefactoringUtil.addBlockIntoParent(replaced);
      GrVariableDeclarationOwner owner = PsiTreeUtil.getParentOfType(enclosingExpr, GrVariableDeclarationOwner.class);
      assert owner != null;
      PsiElement element = enclosingExpr;
      while (element != null && element.getParent() != owner) {
        element = element.getParent();
      }
      assert element instanceof GrStatement;
      GrStatement anchor = (GrStatement) element;

      if (!resultOfCallExplicitlyUsed) {
        assert anchor == enclosingExpr;
      }

      // add qualifier reference declaration
      if (qualifierDeclaration != null) {
        owner.addVariableDeclarationBefore(qualifierDeclaration, anchor);
      }

      // Process method return statements
      if (returnCount > 1 && !PsiType.VOID.equals(methodType) && !isTailMethodCall) {
        PsiType type = methodType != null && methodType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) ? null : methodType;
        GrVariableDeclaration resultDecl = factory.createVariableDeclaration(ArrayUtil.EMPTY_STRING_ARRAY, "", type, resultName);
        GrStatement statement = ((GrStatementOwner) owner).addStatementBefore(resultDecl, anchor);
        JavaCodeStyleManager.getInstance(statement.getProject()).shortenClassReferences(statement);

        // Replace all return statements with assignments to 'result' variable
        for (GrStatement returnStatement : returnStatements) {
          GrExpression value = ControlFlowUtils.extractReturnExpression(returnStatement);
          if (value != null) {
            GrExpression assignment = factory.createExpressionFromText(resultName + " = " + value.getText());
            returnStatement.replaceWithStatement(assignment);
          } else {
            returnStatement.replaceWithStatement(factory.createExpressionFromText(resultName + " = null"));
          }
        }
      }
      if (!isTailMethodCall && resultOfCallExplicitlyUsed && returnCount == 1) {
        returnStatements.iterator().next().removeStatement();
      }
      else if (!isTailMethodCall && (PsiType.VOID.equals(methodType) || returnCount == 1)) {
        for (GrStatement returnStatement : returnStatements) {
          if (returnStatement instanceof GrReturnStatement) {
            final GrExpression returnValue = ((GrReturnStatement)returnStatement).getReturnValue();
            if (returnValue != null && GroovyRefactoringUtil.hasSideEffect(returnValue)) {
              returnStatement.replaceWithStatement(returnValue);
              continue;
            }
          }
          else if (GroovyRefactoringUtil.hasSideEffect(returnStatement)) {
            continue;
          }
          returnStatement.removeStatement();
        }
      }

      // Add all method statements
      GrStatement[] statements = body.getStatements();
      for (GrStatement statement : statements) {
        ((GrStatementOwner) owner).addStatementBefore(statement, anchor);
      }
      if (resultOfCallExplicitlyUsed && !isTailMethodCall) {
        TextRange range = replaced.getTextRange();
        RangeMarker marker = editor != null ? editor.getDocument().createRangeMarker(range.getStartOffset(), range.getEndOffset(), true) : null;
        reformatOwner(owner);
        return marker;
      } else {
        GrStatement stmt;
        if (isTailMethodCall && enclosingExpr.getParent() instanceof GrReturnStatement) {
          stmt = (GrReturnStatement) enclosingExpr.getParent();
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

  @NotNull
  private static String generateQualifierName(@NotNull GrCallExpression call, @Nullable GrMethod method, @NotNull final Project project, @NotNull GrExpression qualifier) {
    String[] possibleNames = GroovyNameSuggestionUtil.suggestVariableNames(qualifier, new NameValidator() {
      @Override
      public String validateName(String name, boolean increaseNumber) {
        return name;
      }

      @Override
      public Project getProject() {
        return project;
      }
    });
    String qualName = possibleNames[0];
    qualName = InlineMethodConflictSolver.suggestNewName(qualName, method, call);
    return qualName;
  }

  private static void reformatOwner(@Nullable GrVariableDeclarationOwner owner) throws IncorrectOperationException {
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


  /**
   * Prepare temporary method with non-conflicting local names
   */
  @NotNull
  private static GrMethod prepareNewMethod(@NotNull GrCallExpression call, @NotNull GrMethod method, @Nullable GrExpression qualifier) throws IncorrectOperationException {
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(method.getProject());

    if (method instanceof GrReflectedMethod) {
      method = ((GrReflectedMethod)method).getBaseMethod();
    }

    GrMethod newMethod = factory.createMethodFromText(method.getText(), call);
    if (qualifier != null) {
      Collection<GroovyInlineMethodUtil.ReferenceExpressionInfo> infos = GroovyInlineMethodUtil.collectReferenceInfo(method);
      GroovyInlineMethodUtil.addQualifiersToInnerReferences(newMethod, infos, qualifier);
    }

    ArrayList<PsiNamedElement> innerDefinitions = new ArrayList<>();
    collectInnerDefinitions(newMethod.getBlock(), innerDefinitions);

    // there are only local variables and parameters (possible of inner closures)
    for (PsiNamedElement namedElement : innerDefinitions) {
      String name = namedElement.getName();
      if (name != null) {
        String newName = qualifier instanceof GrReferenceExpression ?
                         InlineMethodConflictSolver.suggestNewName(name, method, call, ((GrReferenceExpression)qualifier).getReferenceName()) :
                         InlineMethodConflictSolver.suggestNewName(name, method, call);
        if (!newName.equals(namedElement.getName())) {
          final Collection<PsiReference> refs = ReferencesSearch.search(namedElement).findAll();
          for (PsiReference ref : refs) {
            PsiElement element = ref.getElement();
            if (element instanceof GrReferenceExpression) {
              GrExpression newExpr = factory.createExpressionFromText(newName);
              ((GrReferenceExpression) element).replaceWithExpression(newExpr, false);
            }
          }
          namedElement.setName(newName);
        }
      }
    }
    GroovyInlineMethodUtil.replaceParametersWithArguments(call, newMethod);
    return newMethod;
  }

  private static void collectInnerDefinitions(@Nullable PsiElement element, ArrayList<PsiNamedElement> defintions) {
    if (element == null) return;
    for (PsiElement child : element.getChildren()) {
      if (child instanceof GrVariable && !(child instanceof GrParameter)) {
        defintions.add((GrVariable) child);
      }
      if (!(child instanceof GrClosableBlock)) {
        collectInnerDefinitions(child, defintions);
      }
    }
  }

  /**
   * Get method result expression (if it is alone in method)
   *
   * @return null if method has more or less than one return statement or has void type
   */
  @Nullable
  static GrExpression getAloneResultExpression(@NotNull GrMethod method) {
    GrOpenBlock body = method.getBlock();
    assert body != null;
    GrStatement[] statements = body.getStatements();
    if (statements.length == 1) {
      if (statements[0] instanceof GrExpression) return (GrExpression) statements[0];
      if (statements[0] instanceof GrReturnStatement) {
        GrExpression value = ((GrReturnStatement) statements[0]).getReturnValue();
        if (value == null && !PsiType.VOID.equals(PsiUtil.getSmartReturnType(method))) {
          return GroovyPsiElementFactory.getInstance(method.getProject()).createExpressionFromText("null");
        }
        return value;
      }
    }
    return null;
  }


  /*
  Method call is used as expression in some enclosing expression or
  is method return result
  */
  private static boolean isOnExpressionOrReturnPlace(@NotNull GrCallExpression call) {
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
}

// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.extract.method;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GrRefactoringError;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.refactoring.extract.ExtractUtil;
import org.jetbrains.plugins.groovy.refactoring.extract.GroovyExtractChooser;
import org.jetbrains.plugins.groovy.refactoring.extract.InitialInfo;
import org.jetbrains.plugins.groovy.refactoring.extract.ParameterInfo;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.jetbrains.annotations.Nls.Capitalization.Title;

public class GroovyExtractMethodHandler implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance(GroovyExtractMethodHandler.class);

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file, @Nullable DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    final SelectionModel model = editor.getSelectionModel();
    if (model.hasSelection()) {
      invokeImpl(project, editor, file, model.getSelectionStart(), model.getSelectionEnd());
    }
    else {
      final List<GrExpression> expressions = GrIntroduceHandlerBase.collectExpressions(file, editor, editor.getCaretModel().getOffset(), true);
      final Consumer<GrExpression> callback = new Callback(project, editor, file);
      if (expressions.size() == 1) {
        callback.accept(expressions.get(0));
      }
      else if (expressions.isEmpty()) {
        model.selectLineAtCaret();
        invokeImpl(project, editor, file, model.getSelectionStart(), model.getSelectionEnd());
      }
      else {
        IntroduceTargetChooser.showChooser(editor, expressions, Pass.create(callback), GrIntroduceHandlerBase.GR_EXPRESSION_RENDERER);
      }
    }
  }

  private final class Callback implements Consumer<GrExpression> {
    private final Project project;
    private final Editor editor;
    private final PsiFile file;


    private Callback(Project project, Editor editor, PsiFile file) {
      this.project = project;
      this.editor = editor;
      this.file = file;
    }

    @Override
    public void accept(@NotNull final GrExpression selectedValue) {
      final TextRange range = selectedValue.getTextRange();
      invokeImpl(project, editor, file, range.getStartOffset(), range.getEndOffset());
    }
  }

  private void invokeImpl(Project project, Editor editor, PsiFile file, final int startOffset, final int endOffset) {
    try {
      final InitialInfo initialInfo = GroovyExtractChooser.invoke(project, editor, file, startOffset, endOffset, true);

      if (findConflicts(initialInfo)) return;

      performRefactoring(initialInfo, editor);
    }
    catch (GrRefactoringError e) {
      CommonRefactoringUtil.showErrorHint(project, editor, e.getMessage(), getRefactoringName(), HelpID.EXTRACT_METHOD);
    }
  }

  private static boolean findConflicts(InitialInfo info) {
    //new ConflictsDialog()
    final MultiMap<PsiElement, String> conflicts = new MultiMap<>();

    final PsiElement declarationOwner = info.getContext().getParent();

    GroovyRecursiveElementVisitor visitor = new GroovyRecursiveElementVisitor() {
      @Override
      public void visitReferenceExpression(@NotNull GrReferenceExpression referenceExpression) {
        super.visitReferenceExpression(referenceExpression);

        GroovyResolveResult resolveResult = referenceExpression.advancedResolve();
        PsiElement resolveContext = resolveResult.getCurrentFileResolveContext();
        if (resolveContext != null &&
            !(resolveContext instanceof GrImportStatement) &&
            !PsiTreeUtil.isAncestor(declarationOwner, resolveContext, true) && !skipResult(resolveResult)) {
          conflicts.putValue(referenceExpression, GroovyRefactoringBundle
            .message("ref.0.will.not.be.resolved.outside.of.current.context", referenceExpression.getText()));
        }
      }

      //skip 'print' and 'println'
      private boolean skipResult(GroovyResolveResult result) {
        PsiElement element = result.getElement();
        if (element instanceof PsiMethod) {
          String name = ((PsiMethod)element).getName();
          if (!name.startsWith("print")) return false;

          if (element instanceof GrGdkMethod) element = ((GrGdkMethod)element).getStaticMethod();

          PsiClass aClass = ((PsiMethod)element).getContainingClass();
          if (aClass != null) {
            String qname = aClass.getQualifiedName();
            return GroovyCommonClassNames.DEFAULT_GROOVY_METHODS.equals(qname);
          }
        }
        return false;
      }
    };

    GrStatement[] statements = info.getStatements();
    for (GrStatement statement : statements) {
      statement.accept(visitor);
    }

    return !BaseRefactoringProcessor.processConflicts(info.getProject(), conflicts);
  }

  private void performRefactoring(@NotNull final InitialInfo initialInfo, @Nullable final Editor editor) {
    final PsiClass owner = PsiUtil.getContextClass(initialInfo.getContext());
    LOG.assertTrue(owner!=null);

    final ExtractMethodInfoHelper helper = getSettings(initialInfo, owner);
    if (helper == null) return;

    CommandProcessor.getInstance().executeCommand(helper.getProject(), () -> WriteAction.run(() -> {
      createMethod(helper, owner);
      GrStatementOwner declarationOwner =
        helper.getStringPartInfo() == null ? GroovyRefactoringUtil.getDeclarationOwner(helper.getStatements()[0]) : null;
      GrStatement realStatement = ExtractUtil.replaceStatement(declarationOwner, helper);

      // move to offset
      if (editor != null) {
        PsiDocumentManager.getInstance(helper.getProject()).commitDocument(editor.getDocument());
        editor.getSelectionModel().removeSelection();
        editor.getCaretModel().moveToOffset(ExtractUtil.getCaretOffset(realStatement));
      }
    }), getRefactoringName(), null);
  }

  private static void createMethod(ExtractMethodInfoHelper helper, PsiClass owner) {
    final GrMethod method = ExtractUtil.createMethod(helper);
    PsiElement anchor = calculateAnchorToInsertBefore(owner, helper.getContext());
    GrMethod newMethod = (GrMethod)owner.addBefore(method, anchor);
    renameParameterOccurrences(newMethod, helper);
    JavaCodeStyleManager.getInstance(newMethod.getProject()).shortenClassReferences(newMethod);
    PsiElement prev = newMethod.getPrevSibling();
    if (!PsiUtil.isLineFeed(prev)) {
      newMethod.getParent().getNode().addLeaf(GroovyTokenTypes.mNLS, "\n", newMethod.getNode());
    }
  }

  @Nullable
  protected ExtractMethodInfoHelper getSettings(@NotNull InitialInfo initialInfo, PsiClass owner) {
    GroovyExtractMethodDialog dialog = new GroovyExtractMethodDialog(initialInfo, owner);
    if (!dialog.showAndGet()) {
      return null;
    }

    return dialog.getHelper();
  }


  @Override
  public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    // does nothing
  }

  @Nullable
  private static PsiElement calculateAnchorToInsertBefore(PsiClass owner, PsiElement startElement) {
    while (startElement != null && !isEnclosingDefinition(owner, startElement)) {
      if (startElement.getParent() instanceof GroovyFile) {
        return startElement.getNextSibling();
      }
      startElement = startElement.getParent();
      PsiElement parent = startElement.getParent();
      if (parent instanceof GroovyFile && ((GroovyFile) parent).getScriptClass() == owner) {
        return startElement.getNextSibling();
      }
    }
    return startElement == null ? null : startElement.getNextSibling();
  }

  private static boolean isEnclosingDefinition(PsiClass owner, PsiElement startElement) {
    if (owner instanceof GrTypeDefinition definition) {
      return startElement.getParent() == definition.getBody();
    }
    return false;
  }

  private static void renameParameterOccurrences(GrMethod method, ExtractMethodInfoHelper helper) throws IncorrectOperationException {
    GrOpenBlock block = method.getBlock();
    if (block == null) return;
    GrStatement[] statements = block.getStatements();

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(helper.getProject());
    for (ParameterInfo info : helper.getParameterInfos()) {
      final String oldName = info.getOriginalName();
      final String newName = info.getName();
      final ArrayList<GrExpression> result = new ArrayList<>();
      if (!oldName.equals(newName)) {
        for (final GrStatement statement : statements) {
          statement.accept(new PsiRecursiveElementVisitor() {
            @Override
            public void visitElement(@NotNull final PsiElement element) {
              super.visitElement(element);
              if (element instanceof GrReferenceExpression expr) {
                if (!expr.isQualified() && oldName.equals(expr.getReferenceName())) {
                  result.add(expr);
                }
              }
            }
          });
          for (GrExpression expr : result) {
            expr.replaceWithExpression(factory.createExpressionFromText(newName), false);
          }
        }
      }
    }
  }

  static @Nls(capitalization = Title) String getRefactoringName() {
    return GroovyRefactoringBundle.message("extract.method.title");
  }
}

package org.jetbrains.plugins.groovy.intentions.base;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.intentions.utils.BoolUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;


public abstract class Intention implements IntentionAction {
  private final PsiElementPredicate predicate;

  /**
   * @noinspection AbstractMethodCallInConstructor,OverridableMethodCallInConstructor
   */
  protected Intention() {
    super();
    predicate = getElementPredicate();
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file)
      throws IncorrectOperationException {
    if (isFileReadOnly(project, file)) {
      return;
    }
    final PsiElement element = findMatchingElement(file, editor);
    if (element == null) {
      return;
    }
    processIntention(element);
  }

  protected abstract void processIntention(@NotNull PsiElement element)
      throws IncorrectOperationException;

  @NotNull
  protected abstract PsiElementPredicate getElementPredicate();

  protected static void replaceExpression(@NotNull String newExpression,
                                          @NotNull GrExpression expression)
      throws IncorrectOperationException {
    final PsiManager mgr = expression.getManager();
    final GroovyElementFactory factory = GroovyElementFactory.getInstance(expression.getProject());
    final GrExpression newCall =
        factory.createExpressionFromText(newExpression);
    final PsiElement insertedElement = expression.replaceWithExpression(newCall, true);
    //  final CodeStyleManager codeStyleManager = mgr.getCodeStyleManager();
    // codeStyleManager.reformat(insertedElement);
  }


  protected static void replaceStatement(
      @NonNls @NotNull String newStatement,
      @NonNls @NotNull GrStatement statement)
      throws IncorrectOperationException {
    final PsiManager mgr = statement.getManager();
    final GroovyElementFactory factory = GroovyElementFactory.getInstance(statement.getProject());
    final GrStatement newCall =
        (GrStatement) factory.createTopElementFromText(newStatement);
    final PsiElement insertedElement = statement.replaceWithStatement(newCall);
    //  final CodeStyleManager codeStyleManager = mgr.getCodeStyleManager();
    // codeStyleManager.reformat(insertedElement);
  }


  protected static void replaceExpressionWithNegatedExpressionString(
      @NotNull String newExpression,
      @NotNull GrExpression expression)
      throws IncorrectOperationException {
    final PsiManager mgr = expression.getManager();
    final GroovyElementFactory factory = GroovyElementFactory.getInstance(expression.getProject());

    GrExpression expressionToReplace = expression;
    final String expString;
    if (BoolUtils.isNegated(expression)) {
      expressionToReplace = BoolUtils.findNegation(expression);
      expString = newExpression;
    } else {
      expString = "!(" + newExpression + ')';
    }
    final GrExpression newCall =
        factory.createExpressionFromText(expString);
    assert expressionToReplace != null;
    final PsiElement insertedElement = expressionToReplace.replaceWithExpression(newCall, true);
    //  final CodeStyleManager codeStyleManager = mgr.getCodeStyleManager();
    //  codeStyleManager.reformat(insertedElement);
  }


  @Nullable
  PsiElement findMatchingElement(PsiFile file,
                                 Editor editor) {
    final CaretModel caretModel = editor.getCaretModel();
    final int position = caretModel.getOffset();
    PsiElement element = file.findElementAt(position);
    while (element != null) {
      if (predicate.satisfiedBy(element)) {
        return element;
      } else {
        element = element.getParent();
        if (element instanceof PsiFile) {
          break;
        }
      }
    }
    return null;
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return findMatchingElement(file, editor) != null;
  }

  public boolean startInWriteAction() {
    return true;
  }

  private static boolean isFileReadOnly(Project project, PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    final ReadonlyStatusHandler readonlyStatusHandler =
        ReadonlyStatusHandler.getInstance(project);
    final ReadonlyStatusHandler.OperationStatus operationStatus =
        readonlyStatusHandler.ensureFilesWritable(virtualFile);
    return operationStatus.hasReadonlyFiles();
  }

  private String getPrefix() {
    final Class<? extends Intention> aClass = getClass();
    final String name = aClass.getSimpleName();
    final StringBuilder buffer = new StringBuilder(name.length() + 10);
    buffer.append(Character.toLowerCase(name.charAt(0)));
    for (int i = 1; i < name.length(); i++) {
      final char c = name.charAt(i);
      if (Character.isUpperCase(c)) {
        buffer.append('.');
        buffer.append(Character.toLowerCase(c));
      } else {
        buffer.append(c);
      }
    }
    return buffer.toString();
  }

  @NotNull
  public String getText() {
    return GroovyIntentionsBundle.message(getPrefix() + ".name");
  }

  @NotNull
  public String getFamilyName() {
    return GroovyIntentionsBundle.message(getPrefix() + ".family.name");
  }
}

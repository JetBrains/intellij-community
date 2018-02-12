// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.assignment;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrThrowStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTypeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

/**
 * @author Maxim.Medvedev
 */
public class GrCastFix extends GroovyFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance(GrCastFix.class);
  private final PsiType myExpectedType;
  private final boolean mySafe;

  public GrCastFix(PsiType expectedType, GrExpression expression) {
    this(expectedType, expression, true);
  }
  public GrCastFix(PsiType expectedType, GrExpression expression, boolean safe) {
    mySafe = safe;
    myExpectedType = PsiImplUtil.normalizeWildcardTypeByPosition(expectedType, expression);
  }

  @Override
  protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) throws IncorrectOperationException {
    final GrExpression cast = findExpressionToCast(descriptor);
    if (cast == null) return;
    if (mySafe) doSafeCast(project, myExpectedType, cast);
      else doCast(project, myExpectedType, cast);
  }

  private static void doCast(Project project, PsiType type, GrExpression expr) {
    if (!type.isValid()) return;

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
    final GrTypeCastExpression cast = (GrTypeCastExpression)factory.createExpressionFromText("(String)foo");
    final GrTypeElement typeElement = factory.createTypeElement(type);
    GrExpression operand = cast.getOperand();
    if (operand == null) return;
    operand.replaceWithExpression(expr, true);
    cast.getCastTypeElement().replace(typeElement);

    final GrExpression replaced = expr.replaceWithExpression(cast, true);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(replaced);
  }

  private static GrExpression findExpressionToCast(ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    final PsiElement parent = element.getParent();
    if (parent instanceof GrVariable) {
      return ((GrVariable)parent).getInitializerGroovy();
    }
    else if (parent instanceof GrAssignmentExpression) {
      return ((GrAssignmentExpression)parent).getRValue();
    }
    else if (parent instanceof GrThrowStatement) {
      return ((GrThrowStatement)parent).getException();
    }
    else if (parent instanceof GrReturnStatement) {
      return ((GrReturnStatement)parent).getReturnValue();
    }
    else if (element instanceof GrExpression) {
      return (GrExpression)element;
    } else if (parent instanceof GrForInClause) {
      return ((GrForInClause)parent).getIteratedExpression();
    }

    PsiFile file = element.getContainingFile();
    VirtualFile virtualFile = file.getVirtualFile();
    String url = virtualFile == null ? "" : virtualFile.getPresentableUrl();
    LOG.error("can't find expression to cast at position " + element.getTextRange(), new Attachment(url, file.getText()));
    return null;
  }

  static void doSafeCast(@NotNull Project project, @NotNull PsiType type, @NotNull GrExpression expr) {
    if (!type.isValid()) return;

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
    final GrSafeCastExpression cast = (GrSafeCastExpression)factory.createExpressionFromText("foo as String");
    final GrTypeElement typeElement = factory.createTypeElement(type);
    cast.getOperand().replaceWithExpression(expr, true);
    cast.getCastTypeElement().replace(typeElement);

    final GrExpression replaced = expr.replaceWithExpression(cast, true);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(replaced);
  }

  @NotNull
  @Override
  public String getName() {
    return "Cast to " + myExpectedType.getPresentableText();
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return "Add cast";
  }
}

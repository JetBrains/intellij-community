// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.assignment;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiType;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTypeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

/**
 * @author Maxim.Medvedev
 */
public class GrCastFix extends ModCommandQuickFix {
  private final PsiType myExpectedType;
  private final boolean mySafe;

  private final @NotNull SmartPsiElementPointer<GrExpression> pointer;

  private final @NotNull @IntentionName String myName;

  public GrCastFix(PsiType expectedType, GrExpression expression) {
    this(expectedType, expression, true);
  }

  public GrCastFix(PsiType expectedType, GrExpression expression, boolean safe) {
    this(expectedType, expression, safe, GroovyBundle.message("intention.name.cast.to.0", expectedType.getPresentableText()));
  }

  public GrCastFix(PsiType expectedType, GrExpression expression, boolean safe, @NotNull @IntentionName String name) {
    mySafe = safe;
    myName = name;
    myExpectedType = PsiImplUtil.normalizeWildcardTypeByPosition(expectedType, expression);
    pointer = SmartPointerManager.getInstance(expression.getProject()).createSmartPsiElementPointer(expression);
  }

  @Override
  public @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    GrExpression expression = pointer.getElement();
    if (expression == null) return ModCommand.nop();
    return ModCommand.psiUpdate(expression, e -> {
      if (mySafe) {
        doSafeCast(project, myExpectedType, e);
      }
      else {
        doCast(project, myExpectedType, e);
      }
    });
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

  @ApiStatus.Internal
  public static void doSafeCast(@NotNull Project project, @NotNull PsiType type, @NotNull GrExpression expr) {
    if (!type.isValid()) return;

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
    final GrSafeCastExpression cast = (GrSafeCastExpression)factory.createExpressionFromText("foo as String");
    final GrTypeElement typeElement = factory.createTypeElement(type);
    cast.getOperand().replaceWithExpression(expr, true);
    GrTypeElement castTypeElement = cast.getCastTypeElement();
    if (castTypeElement != null) {
      castTypeElement.replace(typeElement);
    }
    final GrExpression replaced = expr.replaceWithExpression(cast, true);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(replaced);
  }

  @Override
  public @NotNull String getName() {
    return myName;
  }

  @Override
  public @Nls @NotNull String getFamilyName() {
    return GroovyBundle.message("intention.family.name.add.cast");
  }
}

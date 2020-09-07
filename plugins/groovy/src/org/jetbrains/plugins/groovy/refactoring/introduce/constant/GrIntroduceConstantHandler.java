// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.introduce.constant;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.refactoring.GrRefactoringError;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.introduce.*;

import java.util.*;

import static org.jetbrains.annotations.Nls.Capitalization.Title;

/**
 * @author Maxim.Medvedev
 */
public class GrIntroduceConstantHandler extends GrIntroduceFieldHandlerBase<GrIntroduceConstantSettings> {

  @Override
  protected @Nls(capitalization = Title) @NotNull String getRefactoringName() {
    return GroovyBundle.message("introduce.constant.title");
  }

  @NotNull
  @Override
  protected String getHelpID() {
    return HelpID.INTRODUCE_CONSTANT;
  }

  @Override
  protected void checkExpression(@NotNull GrExpression selectedExpr) {
    GrVariable variable = GrIntroduceHandlerBase.resolveLocalVar(selectedExpr);
    if (variable != null) {
      checkVariable(variable);
    }
    else {
      selectedExpr.accept(new ConstantChecker(selectedExpr, selectedExpr));
    }
  }

  @Override
  protected void checkVariable(@NotNull GrVariable variable) throws GrRefactoringError {
    final GrExpression initializer = variable.getInitializerGroovy();
    if (initializer == null) {
      throw new GrRefactoringError(JavaRefactoringBundle.message("variable.does.not.have.an.initializer", variable.getName()));
    }
    checkExpression(initializer);
  }

  @Override
  protected void checkStringLiteral(@NotNull StringPartInfo info) throws GrRefactoringError {
    //todo
  }

  @Override
  protected void checkOccurrences(PsiElement @NotNull [] occurrences) {
    if (hasLhs(occurrences)) {
      throw new GrRefactoringError(GroovyRefactoringBundle.message("selected.variable.is.used.for.write"));
    }
  }

  @Nullable
  public static PsiClass findContainingClass(GrIntroduceContext context) {
    return (PsiClass)context.getScope();
  }

  @NotNull
  @Override
  protected GrIntroduceDialog<GrIntroduceConstantSettings> getDialog(@NotNull GrIntroduceContext context) {
    return new GrIntroduceConstantDialog(context, findContainingClass(context));
  }

  @Override
  public GrField runRefactoring(@NotNull GrIntroduceContext context, @NotNull GrIntroduceConstantSettings settings) {
    return new GrIntroduceConstantProcessor(context, settings).run();
  }

  @Override
  protected GrAbstractInplaceIntroducer<GrIntroduceConstantSettings> getIntroducer(@NotNull GrIntroduceContext context, @NotNull OccurrencesChooser.ReplaceChoice choice) {
    final Ref<GrIntroduceContext> contextRef = Ref.create(context);

    if (context.getStringPart() != null) {
      extractStringPart(contextRef);
    }

    return new GrInplaceConstantIntroducer(contextRef.get(), choice);
  }

  @NotNull
  @Override
  protected Map<OccurrencesChooser.ReplaceChoice, List<Object>> getOccurrenceOptions(@NotNull GrIntroduceContext context) {
    HashMap<OccurrencesChooser.ReplaceChoice, List<Object>> map = new LinkedHashMap<>();

    GrVariable localVar = resolveLocalVar(context);
    if (localVar != null) {
      map.put(OccurrencesChooser.ReplaceChoice.ALL, Arrays.asList(context.getOccurrences()));
      return map;
    }

    if (context.getExpression() != null) {
      map.put(OccurrencesChooser.ReplaceChoice.NO, Collections.singletonList(context.getExpression()));
    }
    else if (context.getStringPart() != null) {
      map.put(OccurrencesChooser.ReplaceChoice.NO, Collections.singletonList(context.getStringPart()));
    }

    PsiElement[] occurrences = context.getOccurrences();
    if (occurrences.length > 1) {
      map.put(OccurrencesChooser.ReplaceChoice.ALL, Arrays.asList(occurrences));
    }
    return map;
  }

  private static final class ConstantChecker extends GroovyRecursiveElementVisitor {
    private final PsiElement scope;
    private final GrExpression expr;

    @Override
    public void visitReferenceExpression(@NotNull GrReferenceExpression referenceExpression) {
      final PsiElement resolved = referenceExpression.resolve();
      if (resolved instanceof PsiVariable) {
        if (!isStaticFinalField((PsiVariable)resolved)) {
          if (expr instanceof GrClosableBlock) {
            if (!PsiTreeUtil.isContextAncestor(scope, resolved, true)) {
              throw new GrRefactoringError(GroovyRefactoringBundle.message("closure.uses.external.variables"));
            }
          }
          else {
            throw new GrRefactoringError(JavaRefactoringBundle.message("selected.expression.cannot.be.a.constant.initializer"));
          }
        }
      }
      else if (resolved instanceof PsiMethod && ((PsiMethod)resolved).getContainingClass() != null) {
        final GrExpression qualifier = referenceExpression.getQualifierExpression();
        if (qualifier == null ||
            (qualifier instanceof GrReferenceExpression && ((GrReferenceExpression)qualifier).resolve() instanceof PsiClass)) {
          if (!((PsiMethod)resolved).hasModifierProperty(PsiModifier.STATIC)) {
            throw new GrRefactoringError(JavaRefactoringBundle.message("selected.expression.cannot.be.a.constant.initializer"));
          }
        }
      }
    }

    private static boolean isStaticFinalField(PsiVariable var) {
      return var instanceof PsiField && var.hasModifierProperty(PsiModifier.FINAL) && var.hasModifierProperty(PsiModifier.STATIC);
    }

    @Override
    public void visitClosure(@NotNull GrClosableBlock closure) {
      if (closure == expr) {
        super.visitClosure(closure);
      }
      else {
        closure.accept(new ConstantChecker(closure, scope));
      }
    }

    private ConstantChecker(GrExpression expr, PsiElement expressionScope) {
      scope = expressionScope;
      this.expr = expr;
    }
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public final class ConvertSimpleGetterToPropertyIntention extends GrPsiUpdateIntention {

  private static final String[] MODIFIERS_TO_CHECK = {
    PsiModifier.STATIC, PsiModifier.PRIVATE, PsiModifier.PROTECTED
  };

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    GrMethod method = (GrMethod)element.getParent();

    GrOpenBlock block = method.getBlock();
    if (block == null) return;
    GrStatement statement = block.getStatements()[0];

    GrExpression value;
    if (statement instanceof GrReturnStatement) {
      value = ((GrReturnStatement)statement).getReturnValue();
    }
    else {
      value = (GrExpression)statement;
    }

    String fieldName = GroovyPropertyUtils.getPropertyNameByGetter(method);
    if (fieldName == null) return;

    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return;

    List<String> modifiers = new ArrayList<>();
    for (String modifier : MODIFIERS_TO_CHECK) {
      if (method.hasModifierProperty(modifier)) modifiers.add(modifier);
    }
    modifiers.add(PsiModifier.FINAL);

    GrTypeElement returnTypeElement = method.getReturnTypeElementGroovy();
    PsiType returnType = returnTypeElement == null ? null : returnTypeElement.getType();

    GrVariableDeclaration declaration = GroovyPsiElementFactory.getInstance(context.project()).createFieldDeclaration(
      ArrayUtilRt.toStringArray(modifiers), fieldName, value, returnType
    );

    PsiElement replaced = method.replace(declaration);
    JavaCodeStyleManager.getInstance(context.project()).shortenClassReferences(replaced);
  }

  @Override
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(@NotNull PsiElement element) {
        PsiElement parent = element.getParent();
        if (!(parent instanceof GrMethod method) || method.getNameIdentifierGroovy() != element) return false;

        GrOpenBlock block = method.getBlock();
        if (block == null) return false;

        GrStatement[] statements = block.getStatements();
        if (statements.length != 1) return false;

        if (!GroovyPropertyUtils.isSimplePropertyGetter(method)) return false;
        if (GroovyPropertyUtils.findFieldForAccessor(method, true) != null) return false;


        GrStatement statement = statements[0];
        if (!(statement instanceof GrReturnStatement && ((GrReturnStatement)statement).getReturnValue() != null ||
              statement instanceof GrExpression && !PsiTypes.voidType().equals(((GrExpression)statement).getType()))) {
          return false;
        }
        return true;
      }
    };
  }
}

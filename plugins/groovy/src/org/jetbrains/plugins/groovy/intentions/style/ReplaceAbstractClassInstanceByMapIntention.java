// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.style;

import com.intellij.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.infos.CandidateInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public final class ReplaceAbstractClassInstanceByMapIntention extends GrPsiUpdateIntention {
  @Override
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return new MyPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement psiElement, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    GrCodeReferenceElement ref = (GrCodeReferenceElement)psiElement;
    final GrAnonymousClassDefinition anonymous = (GrAnonymousClassDefinition)ref.getParent();
    final GrNewExpression newExpr = (GrNewExpression)anonymous.getParent();

    GrTypeDefinitionBody body = anonymous.getBody();
    assert body != null;

    List<Pair<PsiMethod, GrOpenBlock>> methods = new ArrayList<>();
    for (GrMethod method : body.getMethods()) {
      methods.add(new Pair<>(method, method.getBlock()));
    }

    final Collection<CandidateInfo> collection = OverrideImplementExploreUtil.getMethodsToOverrideImplement(anonymous, true);
    for (CandidateInfo info : collection) {
      methods.add(new Pair<>((PsiMethod)info.getElement(), null));
    }

    StringBuilder buffer = new StringBuilder();
    if (methods.size() == 1) {
      final Pair<PsiMethod, GrOpenBlock> pair = methods.getFirst();
      appendClosureTextByMethod(pair.getFirst(), buffer, pair.getSecond(), newExpr);
      if (!GroovyConfigUtils.getInstance().isVersionAtLeast(psiElement, GroovyConfigUtils.GROOVY2_2)) {
        buffer.append(" as ").append(ref.getText());
      }
    }
    else {
      buffer.append("[\n");
      for (Pair<PsiMethod, GrOpenBlock> pair : methods) {
        final PsiMethod method = pair.getFirst();
        final GrOpenBlock block = pair.getSecond();
        buffer.append(method.getName()).append(": ");
        appendClosureTextByMethod(method, buffer, block, newExpr);
        buffer.append(",\n");
      }
      if (!methods.isEmpty()) {
        buffer.delete(buffer.length() - 2, buffer.length());
        buffer.append('\n');
      }
      buffer.append("] as ").append(ref.getText());
    }

    createAndAdjustNewExpression(context.project(), newExpr, buffer);
  }

  private static void createAndAdjustNewExpression(Project project, GrNewExpression newExpression, StringBuilder buffer) {
    final GrExpression expr = GroovyPsiElementFactory.getInstance(project).createExpressionFromText(buffer.toString());
    final GrExpression safeTypeExpr = newExpression.replaceWithExpression(expr, false);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(safeTypeExpr);
  }

  private static void appendClosureTextByMethod(PsiMethod method,
                                                StringBuilder buffer,
                                                @Nullable GrOpenBlock block,
                                                GroovyPsiElement context) {
    final PsiParameterList list = method.getParameterList();
    buffer.append("{ ");
    final PsiParameter[] parameters = list.getParameters();
    Set<String> generatedNames = new HashSet<>();
    if (parameters.length > 0) {
      final PsiParameter first = parameters[0];
      final PsiType type = first.getType();
      buffer.append(type.getCanonicalText()).append(" ");
      buffer.append(createName(generatedNames, first, type, context));
    }
    for (int i = 1; i < parameters.length; i++) {
      buffer.append(", ");
      final PsiParameter param = parameters[i];
      final PsiType type = param.getType();
      buffer.append(type.getCanonicalText()).append(" ");
      buffer.append(createName(generatedNames, param, type, context));
    }
    if (parameters.length > 0) {
      buffer.append(" ->");
    }

    if (block != null) {
      final PsiElement lBrace = block.getLBrace();
      final PsiElement rBrace = block.getRBrace();
      for (PsiElement child = lBrace != null ? lBrace.getNextSibling() : block.getFirstChild();
           child != null && child != rBrace;
           child = child.getNextSibling()) {
        buffer.append(child.getText());
      }
    }
    buffer.append(" }");
  }

  private static String createName(Set<String> generatedNames, PsiParameter param, PsiType type, GroovyPsiElement context) {
    String name = param.getName();
    generatedNames.add(name);
    return name;
  }

  static class MyPredicate implements PsiElementPredicate {
    @Override
    public boolean satisfiedBy(@NotNull PsiElement element) {
      if (element instanceof GrCodeReferenceElement && element.getParent() instanceof GrAnonymousClassDefinition anonymous) {
        if (anonymous.getFields().length == 0) {
          return true;
        }
      }
      return false;
    }
  }
}



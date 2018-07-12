// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.other;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSwitchStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import java.util.Collections;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GrCreateMissingSwitchBranchesIntention extends Intention {
  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull Project project, Editor editor) throws IncorrectOperationException {
    if (!(element instanceof GrSwitchStatement)) return;

    final List<PsiEnumConstant> constants = findUnusedConstants((GrSwitchStatement)element);
    if (constants.isEmpty()) return;

    final PsiEnumConstant first = constants.get(0);
    final PsiClass aClass = first.getContainingClass();
    if (aClass == null) return;
    String qName = aClass.getQualifiedName();

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
    PsiElement anchor = findAnchor(element);
    for (PsiEnumConstant constant : constants) {
      final GrCaseSection section = factory.createSwitchSection("case " + qName + "." + constant.getName() + ":\n break");
      final PsiElement added = element.addBefore(section, anchor);
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(added);
    }
  }

  @Nullable
  private static PsiElement findAnchor(PsiElement element) {
    final PsiElement last = element.getLastChild();
    if (last != null && last.getNode().getElementType() == GroovyTokenTypes.mRCURLY) return last;
    return null;
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(@NotNull PsiElement element) {
        if (!(element instanceof GrSwitchStatement)) return false;


        final List<PsiEnumConstant> unused = findUnusedConstants((GrSwitchStatement)element);
        return !unused.isEmpty();
      }
    };
  }

  private static List<PsiEnumConstant> findUnusedConstants(GrSwitchStatement switchStatement) {
    final GrExpression condition = switchStatement.getCondition();
    if (condition == null) return Collections.emptyList();

    final PsiType type = condition.getType();
    if (!(type instanceof PsiClassType)) return Collections.emptyList();

    final PsiClass resolved = ((PsiClassType)type).resolve();
    if (resolved == null || !resolved.isEnum()) return Collections.emptyList();

    final PsiField[] fields = resolved.getFields();
    final List<PsiEnumConstant> constants = ContainerUtil.findAll(fields, PsiEnumConstant.class);

    final GrCaseSection[] sections = switchStatement.getCaseSections();
    for (GrCaseSection section : sections) {
      for (GrCaseLabel label : section.getCaseLabels()) {
        final GrExpression value = label.getValue();
        if (value instanceof GrReferenceExpression) {
          final PsiElement r = ((GrReferenceExpression)value).resolve();
          constants.remove(r);
        }
      }
    }
    return constants;
  }
}

/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ig.migration;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.extractMethod.InputVariables;
import com.intellij.refactoring.util.duplicates.DuplicatesFinder;
import com.intellij.refactoring.util.duplicates.Match;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole, Bas Leijdekkers
 */
public class TryWithIdenticalCatchesInspection extends BaseInspection {

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiType type = (PsiType)infos[1];
    return InspectionGadgetsBundle.message("try.with.identical.catches.problem.descriptor", type.getPresentableText());
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TryWithIdenticalCatchesVisitor();
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("try.with.identical.catches.display.name");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new CollapseCatchSectionsFix(((Integer)infos[0]).intValue());
  }

  private static class TryWithIdenticalCatchesVisitor extends BaseInspectionVisitor {

    @Override
    public void visitTryStatement(PsiTryStatement statement) {
      super.visitTryStatement(statement);
      if (!PsiUtil.isLanguageLevel7OrHigher(statement)) {
        return;
      }
      final PsiCatchSection[] catchSections = statement.getCatchSections();
      if (catchSections.length < 2) {
        return;
      }
      final PsiParameter[] parameters = statement.getCatchBlockParameters();
      if (catchSections.length != parameters.length) {
        return;
      }
      final boolean[] duplicates = new boolean[catchSections.length];
      for (int i = 0; i < catchSections.length - 1; i++) {
        final PsiCatchSection catchSection = catchSections[i];
        final PsiCodeBlock catchBlock = catchSection.getCatchBlock();
        if (catchBlock == null) {
          continue;
        }
        final PsiParameter parameter = catchSection.getParameter();
        if (parameter == null) {
          continue;
        }
        final InputVariables inputVariables = new InputVariables(Collections.singletonList(parameter),
                                                                 statement.getProject(),
                                                                 new LocalSearchScope(catchBlock),
                                                                 false);
        final DuplicatesFinder finder = new DuplicatesFinder(new PsiElement[]{catchBlock},
                                                             inputVariables, null, Collections.<PsiVariable>emptyList());
        for (int j = i + 1; j < catchSections.length; j++) {
          if (duplicates[j]) {
            continue;
          }
          final PsiCatchSection otherSection = catchSections[j];
          final PsiCodeBlock otherCatchBlock = otherSection.getCatchBlock();
          if (otherCatchBlock == null) {
            continue;
          }
          final Match match = finder.isDuplicate(otherCatchBlock, true);
          if (match == null || match.getReturnValue() != null) {
            continue;
          }
          final List<PsiElement> parameterValues = match.getParameterValues(parameter);
          if (parameterValues != null && (parameterValues.size() != 1 || !(parameterValues.get(0) instanceof PsiReferenceExpression))) {
            continue;
          }
          if (!canCollapse(parameters, i, j)) {
            continue;
          }
          final PsiJavaToken rParenth = otherSection.getRParenth();
          if (rParenth != null) {
            registerErrorAtOffset(otherSection, 0, rParenth.getStartOffsetInParent() + 1, Integer.valueOf(i), parameter.getType());
          }
          duplicates[i] = true;
          duplicates[j] = true;
        }
      }
    }

    private static boolean canCollapse(PsiParameter[] parameters, int index1, int index2) {
      if (index2 > index1) {
        final PsiType type = parameters[index2].getType();
        for (int i = index1 + 1; i < index2; i++) {
          final PsiType otherType = parameters[i].getType();
          if (TypeConversionUtil.isAssignable(type, otherType)) {
            return false;
          }
        }
        return true;
      }
      else {
        final PsiType type = parameters[index1].getType();
        for (int i = index2 + 1; i < index1; i++) {
          final PsiType otherType = parameters[i].getType();
          if (TypeConversionUtil.isAssignable(otherType, type)) {
            return false;
          }
        }
        return true;
      }
    }
  }

  private static class CollapseCatchSectionsFix extends InspectionGadgetsFix {

    private final int myCollapseIntoIndex;

    public CollapseCatchSectionsFix(int collapseIntoIndex) {
      myCollapseIntoIndex = collapseIntoIndex;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("try.with.identical.catches.quickfix");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiCatchSection section = (PsiCatchSection)descriptor.getPsiElement();
      final PsiTryStatement tryStatement = (PsiTryStatement)section.getParent();
      final PsiCatchSection[] catchSections = tryStatement.getCatchSections();
      if (myCollapseIntoIndex >= catchSections.length) {
        return;   // something has gone stale
      }
      final PsiCatchSection collapseInto = catchSections[myCollapseIntoIndex];
      final PsiParameter parameter1 = collapseInto.getParameter();
      final PsiParameter parameter2 = section.getParameter();
      if (parameter1 == null || parameter2 == null) {
        return;
      }
      final PsiType type1 = parameter1.getType();
      final PsiType type2 = parameter2.getType();
      if (TypeConversionUtil.isAssignable(type1, type2)) {
        section.delete();
        return;
      }
      else if (TypeConversionUtil.isAssignable(type2, type1)) {
        collapseInto.delete();
        return;
      }
      final List<PsiType> types = new ArrayList();
      collectDisjunctTypes(type1, types);
      collectDisjunctTypes(type2, types);
      final StringBuilder typeText = new StringBuilder();
      for (PsiType type : types) {
        if (typeText.length() > 0) {
          typeText.append(" | ");
        }
        typeText.append(type.getCanonicalText());
      }
      final PsiTypeElement newTypeElement =
        JavaPsiFacade.getElementFactory(project).createTypeElementFromText(typeText.toString(), tryStatement);
      final PsiTypeElement typeElement = parameter1.getTypeElement();
      if (typeElement == null) {
        return;
      }
      typeElement.replace(newTypeElement);
      section.delete();
    }

    private static void collectDisjunctTypes(PsiType type, List<PsiType> out) {
      if (type instanceof PsiDisjunctionType) {
        final PsiDisjunctionType disjunctionType = (PsiDisjunctionType)type;
        final List<PsiType> disjunctions = disjunctionType.getDisjunctions();
        for (PsiType disjunction : disjunctions) {
          collectDisjunctTypes(disjunction, out);
        }
        return;
      }
      final int size = out.size();
      for (int i = 0; i < size; i++) {
        final PsiType collectedType = out.get(i);
        if (TypeConversionUtil.isAssignable(type, collectedType)) {
          out.remove(i);
          out.add(type);
          return;
        } else if (TypeConversionUtil.isAssignable(collectedType, type)) {
          return;
        }
      }
      out.add(type);
    }
  }
}

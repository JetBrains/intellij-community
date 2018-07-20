/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.refactoring.extractMethod.InputVariables;
import com.intellij.refactoring.util.duplicates.DuplicatesFinder;
import com.intellij.refactoring.util.duplicates.Match;
import com.intellij.refactoring.util.duplicates.ReturnValue;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    final PsiType type = (PsiType)infos[0];
    return InspectionGadgetsBundle.message("try.with.identical.catches.problem.descriptor", type.getPresentableText());
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("try.with.identical.catches.display.name");
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return JavaFeature.MULTI_CATCH.isFeatureSupported(file);
  }

  @Override
  public boolean isSuppressedFor(@NotNull PsiElement element) {
    if (element instanceof PsiCatchSection) {
      final PsiCatchSection catchSection = (PsiCatchSection)element;
      final PsiParameter parameter = catchSection.getParameter();
      if (parameter != null && super.isSuppressedFor(parameter)) {
        return true;
      }
    }
    return super.isSuppressedFor(element);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TryWithIdenticalCatchesVisitor();
  }

  private static class TryWithIdenticalCatchesVisitor extends BaseInspectionVisitor {

    @Override
    public void visitTryStatement(PsiTryStatement statement) {
      super.visitTryStatement(statement);

      final CatchSectionWrapper[] sections = CatchSectionWrapper.createWrappers(statement);
      if (sections == null) return;

      final CatchSectionIndices[] catchSectionIndices = getCatchSectionIndices(sections);
      if (catchSectionIndices == null) return;

      for (int index = 0; index < catchSectionIndices.length; index++) {
        int collapseIntoIndex = catchSectionIndices[index].myCollapseIntoIndex;
        if (collapseIntoIndex >= 0) {
          registerProblem(sections, index, collapseIntoIndex);
        }
      }
    }

    private void registerProblem(@NotNull CatchSectionWrapper[] sections, int at, int collapseIntoIndex) {
      final PsiCatchSection section = sections[at].myCatchSection;
      final PsiJavaToken rParenth = section.getRParenth();
      if (rParenth != null) {
        registerErrorAtOffset(section, 0, rParenth.getStartOffsetInParent() + 1, sections[collapseIntoIndex].myParameter.getType());
      }
    }
  }

  @Nullable
  static CatchSectionIndices[] getCatchSectionIndices(@NotNull CatchSectionWrapper[] sections) {
    final CatchSectionIndices[] indices = new CatchSectionIndices[sections.length];
    for (int index = 0; index < sections.length; index++) {
      indices[index] = new CatchSectionIndices(index);
    }

    boolean duplicateFound = false;
    for (int from = 0; from < sections.length - 1; from++) {
      if (indices[from].myHasDuplicate) continue;
      final CatchSectionWrapper section = sections[from];
      if (section == null) continue;

      for (int to = from + 1; to < sections.length; to++) {
        if (indices[to].myHasDuplicate) continue;
        final CatchSectionWrapper otherSection = sections[to];
        if (otherSection == null || !section.isDuplicate(otherSection)) continue;

        indices[from].addDuplicate(indices[to]);
        duplicateFound = true;
      }
    }
    if (!duplicateFound) return null;

    final boolean[][] canSwap = new boolean[sections.length][sections.length];
    for (int from = 0; from < sections.length; from++) {
      for (int to = from + 1; to < sections.length; to++) {
        canSwap[from][to] = canSwap[to][from] = sections[from] != null && sections[from].canSwapWith(sections[to]);
      }
    }

    for (int index = 0; index < sections.length; index++) {
      indices[index].computeInsertionRange(canSwap);
    }

    for (CatchSectionIndices idx : indices) {
      final int[] duplicates = idx.myDuplicates;
      if (duplicates == null) continue;

      for (int from : duplicates) {
        for (int to : duplicates) {
          indices[to].tryCollapseInto(indices[from]);
        }
      }
    }
    return indices;
  }

  private static class CatchSectionIndices {
    final int myIndex;
    int myCanInsertBefore = -1;
    int myCanInsertAfter = -1;

    boolean myHasDuplicate;
    int[] myDuplicates;
    int myCollapseIntoIndex = -1;

    CatchSectionIndices(int index) {
      myIndex = index;
    }

    void addDuplicate(CatchSectionIndices duplicate) {
      if (myDuplicates == null) {
        myDuplicates = new int[]{myIndex, duplicate.myIndex};
        myHasDuplicate = true;
      }
      else {
        myDuplicates = ArrayUtil.append(myDuplicates, duplicate.myIndex);
      }
      duplicate.myHasDuplicate = true;
    }

    void computeInsertionRange(@NotNull boolean[][] canSwap) {
      boolean[] canSwapWith = canSwap[myIndex];

      for (int before = myIndex; ; before--) {
        if (before - 1 < 0 || !canSwapWith[before - 1]) {
          myCanInsertBefore = before;
          break;
        }
      }

      for (int after = myIndex; ; after++) {
        if (after + 1 >= canSwapWith.length || !canSwapWith[after + 1]) {
          myCanInsertAfter = after;
          break;
        }
      }
    }

    public void tryCollapseInto(CatchSectionIndices collapseInto) {
      if (myCollapseIntoIndex < 0 && myIndex > collapseInto.myIndex && myCanInsertBefore <= collapseInto.myCanInsertAfter + 1) {
        myCollapseIntoIndex = collapseInto.myIndex;
      }
    }
  }

  private static class CatchSectionWrapper {
    @NotNull final PsiCatchSection myCatchSection;
    @NotNull final PsiCodeBlock myCodeBlock;
    @NotNull final PsiParameter myParameter;
    @NotNull final List<PsiClassType> myTypes;
    @NotNull final DuplicatesFinder myFinder;

    private CatchSectionWrapper(@NotNull PsiCatchSection catchSection,
                                @NotNull PsiCodeBlock codeBlock,
                                @NotNull PsiParameter parameter,
                                @NotNull List<PsiClassType> types,
                                @NotNull DuplicatesFinder finder) {
      myCatchSection = catchSection;
      myCodeBlock = codeBlock;
      myParameter = parameter;
      myTypes = types;
      myFinder = finder;
    }

    boolean isDuplicate(@NotNull CatchSectionWrapper section) {
      final Match match = findDuplicate(section);
      if (match == null) {
        return false;
      }
      final Match otherMatch = section.findDuplicate(this);
      if (otherMatch == null) {
        return false;
      }
      final ReturnValue returnValue = match.getReturnValue();
      final ReturnValue otherReturnValue = otherMatch.getReturnValue();
      if (returnValue == null) {
        return otherReturnValue == null;
      }
      return returnValue.isEquivalent(otherReturnValue);
    }

    private Match findDuplicate(@NotNull CatchSectionWrapper section) {
      return myFinder.isDuplicate(section.myCodeBlock, true);
    }

    boolean canSwapWith(@Nullable CatchSectionWrapper section) {
      if (section == null) return false;
      for (PsiClassType type : myTypes) {
        for (PsiClassType otherType : section.myTypes) {
          if (type.isAssignableFrom(otherType) || otherType.isAssignableFrom(type)) {
            return false;
          }
        }
      }
      return true;
    }

    @Nullable
    static CatchSectionWrapper[] createWrappers(@NotNull PsiTryStatement statement) {
      final PsiCatchSection[] catchSections = statement.getCatchSections();
      if (catchSections.length < 2) {
        return null;
      }
      final PsiParameter[] parameters = statement.getCatchBlockParameters();
      if (catchSections.length != parameters.length) {
        return null;
      }
      final CatchSectionWrapper[] sections = new CatchSectionWrapper[catchSections.length];
      for (int i = 0; i < sections.length; i++) {
        sections[i] = createWrapper(catchSections[i]);
      }
      return sections;
    }

    @Nullable
    private static CatchSectionWrapper createWrapper(@NotNull PsiCatchSection catchSection) {
      final PsiParameter parameter = catchSection.getParameter();
      final PsiCodeBlock codeBlock = catchSection.getCatchBlock();
      if (parameter != null && codeBlock != null) {
        final List<PsiClassType> types = getClassTypes(parameter.getType());
        if (types != null) {
          final DuplicatesFinder finder = buildDuplicatesFinder(codeBlock, parameter);
          return new CatchSectionWrapper(catchSection, codeBlock, parameter, types, finder);
        }
      }
      return null;
    }

    @Nullable
    private static List<PsiClassType> getClassTypes(@Nullable PsiType type) {
      if (type instanceof PsiClassType) {
        return Collections.singletonList((PsiClassType)type);
      }
      if (type instanceof PsiDisjunctionType) {
        final List<PsiType> disjunctions = ((PsiDisjunctionType)type).getDisjunctions();
        if (!disjunctions.isEmpty()) {
          final List<PsiClassType> classTypes = ContainerUtil.mapNotNull(disjunctions, t -> ObjectUtils.tryCast(t, PsiClassType.class));
          if (classTypes.size() == disjunctions.size()) {
            return classTypes;
          }
        }
      }
      return null;
    }

    @NotNull
    private static DuplicatesFinder buildDuplicatesFinder(@NotNull PsiCodeBlock catchBlock, @NotNull PsiParameter parameter) {
      final InputVariables inputVariables =
        new InputVariables(Collections.singletonList(parameter), parameter.getProject(), new LocalSearchScope(catchBlock), false);
      return new DuplicatesFinder(new PsiElement[]{catchBlock}, inputVariables, null, Collections.emptyList());
    }
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new CollapseCatchSectionsFix();
  }

  private static class CollapseCatchSectionsFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("try.with.identical.catches.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      // smart psi pointer lost correct catch section when multiple catch sections were collapsed in batch mode
      // so we need to re-calculate everything based on what exists at this point
      final PsiCatchSection catchSection = (PsiCatchSection)descriptor.getPsiElement();
      final PsiTryStatement tryStatement = (PsiTryStatement)catchSection.getParent();

      final CatchSectionWrapper[] sections = CatchSectionWrapper.createWrappers(tryStatement);
      if (sections == null) return;

      int sectionIndex = getSectionIndex(sections, catchSection);
      if (sectionIndex < 0) return;

      CatchSectionWrapper duplicateSection = sections[sectionIndex];
      if (duplicateSection == null) return;

      final CatchSectionIndices[] duplicatesIndices = getCatchSectionIndices(sections);
      if (duplicatesIndices == null) return;

      final int collapseIntoIndex = duplicatesIndices[sectionIndex].myCollapseIntoIndex;
      if (collapseIntoIndex < 0) return;

      final CatchSectionWrapper collapseIntoSection = sections[collapseIntoIndex];
      if (collapseIntoSection == null) return;

      final PsiTypeElement collapseIntoTypeElement = collapseIntoSection.myParameter.getTypeElement();
      if (collapseIntoTypeElement == null) return;

      final List<PsiType> parameterTypes = new ArrayList<>(collapseIntoSection.myTypes);
      parameterTypes.addAll(duplicateSection.myTypes);

      final List<PsiType> filteredTypes = PsiDisjunctionType.flattenAndRemoveDuplicates(parameterTypes);
      final PsiType disjunction = PsiDisjunctionType.createDisjunction(filteredTypes, tryStatement.getManager());
      final PsiTypeElement newTypeElement = JavaPsiFacade.getElementFactory(project).createTypeElement(disjunction);

      JavaCodeStyleManager.getInstance(project).shortenClassReferences(collapseIntoTypeElement.replace(newTypeElement));

      int insertBeforeIndex = duplicatesIndices[sectionIndex].myCanInsertBefore;
      if (collapseIntoIndex < insertBeforeIndex) {
        // We can't leave the merged 'catch' section at collapseIntoIndex because it conflicts with other caught exceptions
        final PsiCatchSection[] catchSections = tryStatement.getCatchSections();
        if (insertBeforeIndex < catchSections.length && catchSections[insertBeforeIndex] != null) {
          tryStatement.addBefore(collapseIntoSection.myCatchSection, catchSections[insertBeforeIndex]);
          collapseIntoSection.myCatchSection.delete();
        }
      }

      duplicateSection.myCatchSection.delete();
    }

    private static int getSectionIndex(@NotNull CatchSectionWrapper[] sections, @NotNull PsiElement catchSection) {
      for (int i = 0; i < sections.length; i++) {
        if (sections[i].myCatchSection == catchSection) {
          return i;
        }
      }
      return -1;
    }
  }
}

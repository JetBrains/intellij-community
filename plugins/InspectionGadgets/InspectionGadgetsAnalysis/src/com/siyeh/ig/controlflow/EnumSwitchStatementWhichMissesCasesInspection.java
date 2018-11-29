/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.controlflow;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.TextRange;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.SwitchUtils;
import one.util.streamex.Joining;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class EnumSwitchStatementWhichMissesCasesInspection extends AbstractBaseJavaLocalInspectionTool {

  @SuppressWarnings("PublicField")
  public boolean ignoreSwitchStatementsWithDefault = true;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("enum.switch.statement.which.misses.cases.display.name");
  }

  @NotNull
  String buildErrorString(String enumName, Set<String> names) {
    if (names.size() == 1) {
      return InspectionGadgetsBundle
        .message("enum.switch.statement.which.misses.cases.problem.descriptor.single", enumName, names.iterator().next());
    }
    String namesString = formatMissingBranches(names);
    return InspectionGadgetsBundle.message("enum.switch.statement.which.misses.cases.problem.descriptor", enumName, namesString);
  }

  static String formatMissingBranches(Set<String> names) {
    return StreamEx.of(names).map(name -> "'" + name + "'").mapLast("and "::concat)
      .collect(Joining.with(", ").maxChars(50).cutAfterDelimiter());
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("enum.switch.statement.which.misses.cases.option"),
                                          this, "ignoreSwitchStatementsWithDefault");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
        processSwitchBlock(statement);
      }

      @Override
      public void visitSwitchExpression(PsiSwitchExpression expression) {
        processSwitchBlock(expression);
      }

      public void processSwitchBlock(@NotNull PsiSwitchBlock switchBlock) {
        final PsiExpression expression = switchBlock.getExpression();
        if (expression == null) return;
        final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(expression.getType());
        if (aClass == null || !aClass.isEnum()) return;
        Set<String> constants = StreamEx.of(aClass.getAllFields()).select(PsiEnumConstant.class).map(PsiEnumConstant::getName)
          .toCollection(LinkedHashSet::new);
        if (constants.isEmpty()) return;
        boolean hasDefault = false;
        ProblemHighlightType highlighting = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
        for (PsiSwitchLabelStatementBase child : PsiTreeUtil
          .getChildrenOfTypeAsList(switchBlock.getBody(), PsiSwitchLabelStatementBase.class)) {
          if (child.isDefaultCase()) {
            hasDefault = true;
            if (ignoreSwitchStatementsWithDefault) {
              if (!isOnTheFly) return;
              highlighting = ProblemHighlightType.INFORMATION;
            }
            continue;
          }
          List<PsiEnumConstant> enumConstants = findEnumConstants(child);
          if (enumConstants.isEmpty()) {
            // Syntax error or unresolved constant: do not report anything on incomplete code
            return;
          }
          for (PsiEnumConstant constant : enumConstants) {
            if (constant.getContainingClass() != aClass) {
              // Syntax error or unresolved constant: do not report anything on incomplete code
              return;
            }
            constants.remove(constant.getName());
          }
        }
        if (!hasDefault && switchBlock instanceof PsiSwitchExpression) {
          // non-exhaustive switch expression: it's a compilation error 
          // and the compilation fix should be suggested instead of normal inspection
          return;
        }
        if (constants.isEmpty()) return;
        CommonDataflow.DataflowResult dataflow = CommonDataflow.getDataflowResult(expression);
        if (dataflow != null) {
          Set<Object> values = dataflow.getValuesNotEqualToExpression(expression);
          for (Object value : values) {
            if (value instanceof PsiEnumConstant) {
              constants.remove(((PsiEnumConstant)value).getName());
            }
          }
        }
        if (constants.isEmpty()) return;
        String message = buildErrorString(aClass.getQualifiedName(), constants);
        CreateMissingSwitchBranchesFix fix = new CreateMissingSwitchBranchesFix(switchBlock, constants);
        if (highlighting == ProblemHighlightType.INFORMATION ||
            InspectionProjectProfileManager.isInformationLevel(getShortName(), switchBlock)) {
          holder.registerProblem(switchBlock, message, ProblemHighlightType.INFORMATION, fix);
        }
        else {
          int length = switchBlock.getFirstChild().getTextLength();
          holder.registerProblem(switchBlock, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new TextRange(0, length), fix);
          if (isOnTheFly) {
            TextRange range = new TextRange(length, switchBlock.getTextLength());
            holder.registerProblem(switchBlock, message, ProblemHighlightType.INFORMATION, range, fix);
          }
        }
      }
    };
  }

  private static class CreateMissingSwitchBranchesFix implements LocalQuickFix, IntentionAction {
    private final Set<String> myNames;
    private final SmartPsiElementPointer<PsiSwitchBlock> myBlock;

    private CreateMissingSwitchBranchesFix(@NotNull PsiSwitchBlock block, Set<String> names) {
      myBlock = SmartPointerManager.createPointer(block);
      myNames = names;
    }

    @NotNull
    @Override
    public String getText() {
      return getName();
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      if (myNames.size() == 1) {
        return "Create missing switch branch '" + myNames.iterator().next() + "'";
      }
      return "Create missing branches: " + formatMissingBranches(myNames);
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return "Create enum switch branches";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      invoke();
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      invoke();
    }

    @Override
    public boolean startInWriteAction() {
      return true;
    }

    public void invoke() {
      PsiSwitchBlock switchBlock = myBlock.getElement();
      if (switchBlock == null) return;
      final PsiCodeBlock body = switchBlock.getBody();
      final PsiExpression switchExpression = switchBlock.getExpression();
      if (switchExpression == null) return;
      final PsiClassType switchType = (PsiClassType)switchExpression.getType();
      if (switchType == null) return;
      final PsiClass enumClass = switchType.resolve();
      if (enumClass == null) return;
      boolean isRuleBasedFormat = SwitchUtils.isRuleFormatSwitch(switchBlock);
      if (body == null) {
        // replace entire switch statement if no code block is present
        @NonNls final StringBuilder newStatementText = new StringBuilder();
        CommentTracker commentTracker = new CommentTracker();
        newStatementText.append("switch(").append(commentTracker.text(switchExpression)).append("){");
        for (String missingName : myNames) {
          if (isRuleBasedFormat) {
            newStatementText.append("case ").append(missingName).append("-> {}");
          } else {
            newStatementText.append("case ").append(missingName).append(": break;");
          }
        }
        newStatementText.append('}');
        commentTracker.replaceAndRestoreComments(switchBlock, newStatementText.toString());
        return;
      }
      List<PsiEnumConstant> allEnumConstants = StreamEx.of(enumClass.getAllFields()).select(PsiEnumConstant.class).toList();
      Map<PsiEnumConstant, PsiEnumConstant> nextEnumConstants =
        StreamEx.of(allEnumConstants).pairMap(Couple::of).toMap(c -> c.getFirst(), c -> c.getSecond());
      List<PsiEnumConstant> missingEnumElements = StreamEx.of(allEnumConstants).filter(c -> myNames.contains(c.getName())).toList();
      PsiEnumConstant nextEnumConstant = getNextEnumConstant(nextEnumConstants, missingEnumElements);
      PsiElement bodyElement = body.getFirstBodyElement();
      while (bodyElement != null) {
        List<PsiEnumConstant> constants = findEnumConstants(bodyElement);
        while (nextEnumConstant != null && constants.contains(nextEnumConstant)) {
          addSwitchLabelStatementBefore(missingEnumElements.get(0), bodyElement, isRuleBasedFormat);
          missingEnumElements.remove(0);
          if (missingEnumElements.isEmpty()) {
            break;
          }
          nextEnumConstant = getNextEnumConstant(nextEnumConstants, missingEnumElements);
        }
        if (isDefaultSwitchLabelStatement(bodyElement)) {
          for (PsiEnumConstant missingEnumElement : missingEnumElements) {
            addSwitchLabelStatementBefore(missingEnumElement, bodyElement, isRuleBasedFormat);
          }
          missingEnumElements.clear();
          break;
        }
        bodyElement = bodyElement.getNextSibling();
      }
      if (!missingEnumElements.isEmpty()) {
        final PsiElement lastChild = body.getLastChild();
        for (PsiEnumConstant missingEnumElement : missingEnumElements) {
          addSwitchLabelStatementBefore(missingEnumElement, lastChild, isRuleBasedFormat);
        }
      }
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      PsiSwitchBlock startSwitch = myBlock.getElement();
      if (startSwitch == null) return false;
      int offset = Math.min(editor.getCaretModel().getOffset(), startSwitch.getTextRange().getEndOffset() - 1);
      PsiSwitchBlock currentSwitch = PsiTreeUtil.getNonStrictParentOfType(file.findElementAt(offset), PsiSwitchBlock.class);
      return currentSwitch == startSwitch;
    }

    private static void addSwitchLabelStatementBefore(PsiEnumConstant missingEnumElement, PsiElement anchor, boolean isRuleBasedFormat) {
      if (anchor instanceof PsiSwitchLabelStatement) {
        PsiElement sibling = PsiTreeUtil.skipWhitespacesBackward(anchor);
        while (sibling instanceof PsiSwitchLabelStatement) {
          anchor = sibling;
          sibling = PsiTreeUtil.skipWhitespacesBackward(anchor);
        }
      }
      final PsiElement parent = anchor.getParent();
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(anchor.getProject());
      if (isRuleBasedFormat) {
        final PsiStatement caseStatement = factory.createStatementFromText("case " + missingEnumElement.getName() + "-> {}", anchor);
        parent.addBefore(caseStatement, anchor);
      } else {
        final PsiStatement caseStatement = factory.createStatementFromText("case " + missingEnumElement.getName() + ":", anchor);
        parent.addBefore(caseStatement, anchor);
        final PsiStatement breakStatement = factory.createStatementFromText("break;", anchor);
        parent.addBefore(breakStatement, anchor);
      }
    }

    private static PsiEnumConstant getNextEnumConstant(Map<PsiEnumConstant, PsiEnumConstant> nextEnumConstants,
                                                       List<PsiEnumConstant> missingEnumElements) {
      PsiEnumConstant nextEnumConstant = nextEnumConstants.get(missingEnumElements.get(0));
      while (missingEnumElements.contains(nextEnumConstant)) {
        nextEnumConstant = nextEnumConstants.get(nextEnumConstant);
      }
      return nextEnumConstant;
    }

    private static boolean isDefaultSwitchLabelStatement(PsiElement element) {
      return element instanceof PsiSwitchLabelStatement && ((PsiSwitchLabelStatement)element).isDefaultCase();
    }
  }

  @NotNull
  private static List<PsiEnumConstant> findEnumConstants(PsiElement element) {
    if (!(element instanceof PsiSwitchLabelStatementBase)) {
      return Collections.emptyList();
    }
    final PsiSwitchLabelStatementBase switchLabelStatement = (PsiSwitchLabelStatementBase)element;
    final PsiExpressionList list = switchLabelStatement.getCaseValues();
    if (list == null) {
      return Collections.emptyList();
    }
    List<PsiEnumConstant> constants = new ArrayList<>();
    for (PsiExpression value : list.getExpressions()) {
      if (value instanceof PsiReferenceExpression) {
        final PsiElement target = ((PsiReferenceExpression)value).resolve();
        if (target instanceof PsiEnumConstant) {
          constants.add((PsiEnumConstant)target);
          continue;
        }
      }
      return Collections.emptyList();
    }
    return constants;
  }
}

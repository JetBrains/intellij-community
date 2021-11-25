// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.CreateSwitchBranchesUtil;
import com.siyeh.ig.psiutils.SwitchUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

public final class CreateDefaultBranchFix extends BaseSwitchFix {
  @NonNls private static final String PLACEHOLDER_NAME = "$EXPRESSION$";
  private final @IntentionName String myMessage;

  public CreateDefaultBranchFix(@NotNull PsiSwitchBlock block, @IntentionName String message) {
    super(block);
    myMessage = message;
  }

  @NotNull
  @Override
  public String getText() {
    return myMessage == null ? getName() : myMessage;
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("create.default.branch.fix.family.name");
  }

  @Override
  protected void invoke() {
    PsiSwitchBlock switchBlock = myBlock.getElement();
    if (switchBlock == null) return;
    PsiCodeBlock body = switchBlock.getBody();
    if (body == null) return;
    if (SwitchUtils.calculateBranchCount(switchBlock) < 0) {
      // Default already present for some reason
      return;
    }
    PsiExpression switchExpression = switchBlock.getExpression();
    if (switchExpression == null) return;
    boolean isRuleBasedFormat = SwitchUtils.isRuleFormatSwitch(switchBlock);
    PsiElement anchor = body.getRBrace();
    if (anchor == null) return;
    PsiElement parent = anchor.getParent();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(anchor.getProject());
    generateStatements(switchBlock, isRuleBasedFormat).stream()
      .map(text -> factory.createStatementFromText(text, parent))
      .forEach(statement -> parent.addBefore(statement, anchor));
    PsiStatement lastStatement = ArrayUtil.getLastElement(body.getStatements());
    startTemplateOnStatement(lastStatement);
  }

  /**
   * Method selects the statement inside the switch block and offers a user to replace the selected statement
   * with the user-specified value.
   */
  public static void startTemplateOnStatement(@Nullable PsiStatement statementToAdjust) {
    if (statementToAdjust == null) return;
    SmartPsiElementPointer<PsiStatement> pointer = SmartPointerManager.createPointer(statementToAdjust);
    Editor editor = CreateSwitchBranchesUtil.prepareForTemplateAndObtainEditor(statementToAdjust);
    if (editor == null) return;
    statementToAdjust = pointer.getElement();
    if (statementToAdjust == null) return;
    PsiSwitchBlock block = PsiTreeUtil.getParentOfType(statementToAdjust, PsiSwitchBlock.class);
    if (block == null || !block.isPhysical()) return;
    PsiCodeBlock body = block.getBody();
    if (body == null) return;
    if (statementToAdjust instanceof PsiSwitchLabeledRuleStatement) {
      statementToAdjust = ((PsiSwitchLabeledRuleStatement)statementToAdjust).getBody();
    }
    if (statementToAdjust != null) {
      TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(block);
      builder.replaceElement(statementToAdjust, new ConstantNode(statementToAdjust.getText()));
      builder.run(editor, true);
    }
  }

  private static @NonNls List<String> generateStatements(PsiSwitchBlock switchBlock, boolean isRuleBasedFormat) {
    Project project = switchBlock.getProject();
    FileTemplate branchTemplate = FileTemplateManager.getInstance(project).getCodeTemplate(JavaTemplateUtil.TEMPLATE_SWITCH_DEFAULT_BRANCH);
    Properties props = FileTemplateManager.getInstance(project).getDefaultProperties();
    PsiExpression expression = switchBlock.getExpression();
    props.setProperty(FileTemplate.ATTRIBUTE_EXPRESSION, PLACEHOLDER_NAME);
    PsiType expressionType = expression == null ? null : expression.getType();
    props.setProperty(FileTemplate.ATTRIBUTE_EXPRESSION_TYPE, expressionType == null ? "" : expressionType.getCanonicalText());
    PsiStatement statement;
    try {
      @NonNls String text = branchTemplate.getText(props);
      if (text.trim().isEmpty()) {
        if (switchBlock instanceof PsiSwitchExpression) {
          String value = TypeUtils.getDefaultValue(((PsiSwitchExpression)switchBlock).getType());
          text = isRuleBasedFormat ? value + ";" : "break " + value + ";";
        }
      }
      statement = JavaPsiFacade.getElementFactory(project).createStatementFromText("{" + text + "}", switchBlock);
      if (expression != null) {
        PsiElement[] refs = PsiTreeUtil.collectElements(
          statement, e -> e instanceof PsiReferenceExpression && e.textMatches(PLACEHOLDER_NAME));
        for (PsiElement ref : refs) {
          // This would add parentheses when necessary
          ref.replace(expression);
        }
      }
    }
    catch (IOException | IncorrectOperationException e) {
      throw new IncorrectOperationException("Incorrect file template", (Throwable)e);
    }
    PsiStatement stripped = ControlFlowUtils.stripBraces(statement);
    if (!isRuleBasedFormat || stripped instanceof PsiThrowStatement || stripped instanceof PsiExpressionStatement) {
      statement = stripped;
    }
    if (isRuleBasedFormat) {
      return Collections.singletonList("default -> " + statement.getText());
    }
    else {
      PsiStatement lastStatement = ArrayUtil.getLastElement(Objects.requireNonNull(switchBlock.getBody()).getStatements());
      if (lastStatement != null && ControlFlowUtils.statementMayCompleteNormally(lastStatement)) {
        return Arrays.asList("break;", "default:", statement.getText());
      }
      return Arrays.asList("default:", statement.getText());
    }
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    PsiSwitchBlock block = myBlock.getElement();
    return block == null ? null : new CreateDefaultBranchFix(PsiTreeUtil.findSameElementInCopy(block, target), myMessage);
  }
}

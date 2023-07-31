// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.ControlFlowUtils;
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

  @Override
  protected String getText(@NotNull PsiSwitchBlock switchBlock) {
    return myMessage == null ? getFamilyName() : myMessage;
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiSwitchBlock startSwitch) {
    Presentation presentation = super.getPresentation(context, startSwitch);
    return presentation == null ? null : presentation.withFixAllOption(this);
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("create.default.branch.fix.family.name");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiSwitchBlock switchBlock, @NotNull ModPsiUpdater updater) {
    addDefault(switchBlock, updater);
  }

  public static void addDefault(@NotNull PsiSwitchBlock switchBlock, @NotNull ModPsiUpdater updater) {
    PsiCodeBlock body = switchBlock.getBody();
    if (body == null) return;
    if (SwitchUtils.calculateBranchCount(switchBlock) < 0) {
      // Default already present for some reason
      return;
    }
    PsiExpression switchExpression = switchBlock.getExpression();
    if (switchExpression == null) return;
    boolean isRuleBasedFormat = SwitchUtils.isRuleFormatSwitch(switchBlock);
    PsiElement bodyElement = body.getFirstBodyElement();
    if (bodyElement instanceof PsiWhiteSpace && bodyElement == body.getLastBodyElement()) {
      bodyElement.delete();
    }
    PsiElement anchor = body.getRBrace();
    if (anchor == null) return;
    PsiElement parent = anchor.getParent();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(anchor.getProject());
    generateStatements(switchBlock, isRuleBasedFormat, updater).stream()
      .map(text -> factory.createStatementFromText(text, parent))
      .forEach(statement -> parent.addBefore(statement, anchor));
    PsiStatement lastStatement = ArrayUtil.getLastElement(body.getStatements());
    startTemplateOnStatement(lastStatement, updater);
  }

  /**
   * Method selects the statement inside the switch block and offers a user to replace the selected statement
   * with the user-specified value.
   */
  public static void startTemplateOnStatement(@Nullable PsiStatement statementToAdjust, @NotNull ModPsiUpdater updater) {
    if (statementToAdjust == null) return;
    PsiSwitchBlock block = PsiTreeUtil.getParentOfType(statementToAdjust, PsiSwitchBlock.class);
    if (block == null) return;
    PsiCodeBlock body = block.getBody();
    if (body == null) return;
    if (statementToAdjust instanceof PsiSwitchLabeledRuleStatement rule) {
      statementToAdjust = rule.getBody();
    }
    if (statementToAdjust != null) {
      updater.templateBuilder().field(statementToAdjust, statementToAdjust.getText());
    }
  }

  private static @NonNls List<String> generateStatements(PsiSwitchBlock switchBlock, boolean isRuleBasedFormat,
                                                         @NotNull ModPsiUpdater updater) {
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
      String templateName = StringUtil.trimExtensions(JavaTemplateUtil.TEMPLATE_SWITCH_DEFAULT_BRANCH);
      updater.cancel(JavaBundle.message("tooltip.incorrect.file.template", templateName));
      return List.of();
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
}

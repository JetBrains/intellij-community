// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.psiutils.SwitchUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CreateDefaultBranchFix extends BaseSwitchFix {

  public CreateDefaultBranchFix(@NotNull PsiSwitchBlock block) {
    super(block);
  }

  @NotNull
  @Override
  public String getText() {
    return getName();
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return "Insert 'default' branch";
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
    PsiElement anchor = body.getLastChild();
    if (anchor == null) return;
    PsiElement parent = anchor.getParent();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(anchor.getProject());
    generateStatements(switchBlock, isRuleBasedFormat).stream()
      .map(text -> factory.createStatementFromText(text, parent))
      .forEach(statement -> parent.addBefore(statement, anchor));
    adjustEditor(switchBlock);
  }

  private static void adjustEditor(@NotNull PsiSwitchBlock block) {
    PsiCodeBlock body = block.getBody();
    if (body == null) return;
    Editor editor = prepareForTemplateAndObtainEditor(block);
    if (editor == null) return;
    PsiStatement lastStatement = ArrayUtil.getLastElement(body.getStatements());
    PsiExpression expression = null;
    int offset = -1;
    if (lastStatement instanceof PsiBreakStatement) {
      expression = ((PsiBreakStatement)lastStatement).getExpression();
    } else if (lastStatement instanceof PsiSwitchLabeledRuleStatement) {
      PsiStatement ruleBody = ((PsiSwitchLabeledRuleStatement)lastStatement).getBody();
      if (ruleBody instanceof PsiExpressionStatement) {
        expression = ((PsiExpressionStatement)ruleBody).getExpression();
      } else if (ruleBody instanceof PsiBlockStatement) {
        offset = ruleBody.getTextRange().getStartOffset()+1;
      }
    } else if (lastStatement instanceof PsiSwitchLabelStatement) {
      offset = lastStatement.getTextRange().getEndOffset();
    }
    if (expression == null) {
      if (offset != -1) {
        editor.getCaretModel().moveToOffset(offset);
      }
    } else {
      TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(block);
      builder.replaceElement(expression, new ConstantNode(expression.getText()));
      builder.run(editor, true);
    }
  }

  private static List<String> generateStatements(PsiSwitchBlock switchBlock, boolean isRuleBasedFormat) {
    if (switchBlock instanceof PsiSwitchExpression) {
      String value = TypeUtils.getDefaultValue(((PsiSwitchExpression)switchBlock).getType());
      if (isRuleBasedFormat) {
        return Collections.singletonList("default -> " + value + ";");
      } else {
        return Arrays.asList("default:", "break " + value + ";");
      }
    } else {
      if (isRuleBasedFormat) {
        return Collections.singletonList("default -> {}");
      } else {
        return Collections.singletonList("default:");
      }
    }
  }
}

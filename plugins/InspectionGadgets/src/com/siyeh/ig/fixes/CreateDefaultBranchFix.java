// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.SwitchUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

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
    if (lastStatement instanceof PsiSwitchLabeledRuleStatement) {
      lastStatement = ((PsiSwitchLabeledRuleStatement)lastStatement).getBody();
    }
    if (lastStatement != null) {
      TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(block);
      builder.replaceElement(lastStatement, new ConstantNode(lastStatement.getText()));
      builder.run(editor, true);
    }
  }

  private static List<String> generateStatements(PsiSwitchBlock switchBlock, boolean isRuleBasedFormat) {
    Project project = switchBlock.getProject();
    FileTemplate branchTemplate = FileTemplateManager.getInstance(project).getCodeTemplate(JavaTemplateUtil.TEMPLATE_SWITCH_DEFAULT_BRANCH);
    Properties props = FileTemplateManager.getInstance(project).getDefaultProperties();
    PsiExpression expression = switchBlock.getExpression();
    props.setProperty(FileTemplate.ATTRIBUTE_EXPRESSION, expression == null ? "" : expression.getText());
    PsiType expressionType = expression == null ? null : expression.getType();
    props.setProperty(FileTemplate.ATTRIBUTE_EXPRESSION_TYPE, expressionType == null ? "" : expressionType.getCanonicalText());
    PsiStatement statement;
    try {
      String text = branchTemplate.getText(props);
      if (text.trim().isEmpty()) {
        if (switchBlock instanceof PsiSwitchExpression) {
          String value = TypeUtils.getDefaultValue(((PsiSwitchExpression)switchBlock).getType());
          text = isRuleBasedFormat ? value + ";" : "break " + value + ";";
        }
      }
      statement = JavaPsiFacade.getElementFactory(project).createStatementFromText("{" + text + "}", switchBlock);
    }
    catch (IOException | IncorrectOperationException e) {
      throw new IncorrectOperationException("Incorrect file template", (Throwable)e);
    }
    PsiStatement stripped = ControlFlowUtils.stripBraces(statement);
    if (!isRuleBasedFormat || stripped instanceof PsiThrowStatement) {
      statement = stripped;
    }
    if (isRuleBasedFormat) {
      return Collections.singletonList("default -> " + statement.getText());
    }
    else {
      return Arrays.asList("default:", statement.getText());
    }
  }
}

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Couple;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.SwitchUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import one.util.streamex.Joining;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class CreateMissingSwitchBranchesFix extends BaseSwitchFix {
  private final Set<String> myNames;

  public CreateMissingSwitchBranchesFix(@NotNull PsiSwitchBlock block, Set<String> names) {
    super(block);
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
  protected void invoke() {
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
        newStatementText.append(String.join("", generateStatements(missingName, switchBlock, isRuleBasedFormat)));
      }
      newStatementText.append('}');
      commentTracker.replaceAndRestoreComments(switchBlock, newStatementText.toString());
      createTemplate(switchBlock);
      return;
    }
    List<PsiEnumConstant> allEnumConstants = StreamEx.of(enumClass.getAllFields()).select(PsiEnumConstant.class).toList();
    Map<PsiEnumConstant, PsiEnumConstant> nextEnumConstants =
      StreamEx.of(allEnumConstants).pairMap(Couple::of).toMap(c -> c.getFirst(), c -> c.getSecond());
    List<PsiEnumConstant> missingEnumElements = StreamEx.of(allEnumConstants).filter(c -> myNames.contains(c.getName())).toList();
    PsiEnumConstant nextEnumConstant = getNextEnumConstant(nextEnumConstants, missingEnumElements);
    PsiElement bodyElement = body.getFirstBodyElement();
    while (bodyElement != null) {
      List<PsiEnumConstant> constants = SwitchUtils.findEnumConstants(ObjectUtils.tryCast(bodyElement, PsiSwitchLabelStatementBase.class));
      while (nextEnumConstant != null && constants.contains(nextEnumConstant)) {
        addSwitchLabelStatementBefore(missingEnumElements.get(0), bodyElement, switchBlock, isRuleBasedFormat);
        missingEnumElements.remove(0);
        if (missingEnumElements.isEmpty()) {
          break;
        }
        nextEnumConstant = getNextEnumConstant(nextEnumConstants, missingEnumElements);
      }
      if (isDefaultSwitchLabelStatement(bodyElement)) {
        for (PsiEnumConstant missingEnumElement : missingEnumElements) {
          addSwitchLabelStatementBefore(missingEnumElement, bodyElement, switchBlock, isRuleBasedFormat);
        }
        missingEnumElements.clear();
        break;
      }
      bodyElement = bodyElement.getNextSibling();
    }
    if (!missingEnumElements.isEmpty()) {
      final PsiElement lastChild = body.getLastChild();
      for (PsiEnumConstant missingEnumElement : missingEnumElements) {
        addSwitchLabelStatementBefore(missingEnumElement, lastChild, switchBlock, isRuleBasedFormat);
      }
    }
    createTemplate(switchBlock);
  }

  private void createTemplate(@NotNull PsiSwitchBlock block) {
    if (!(block instanceof PsiSwitchExpression)) return;
    Editor editor = BaseSwitchFix.prepareForTemplateAndObtainEditor(block);
    if (editor == null) return;
    TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(block);
    List<PsiSwitchLabelStatementBase> labels = PsiTreeUtil.getChildrenOfTypeAsList(block.getBody(), PsiSwitchLabelStatementBase.class);
    List<PsiExpression> elementsToReplace = getElementsToReplace(labels);
    for (PsiExpression expression : elementsToReplace) {
      builder.replaceElement(expression, new ConstantNode(expression.getText()));
    }
    builder.run(editor, true);
  }

  @NotNull
  private List<PsiExpression> getElementsToReplace(@NotNull List<PsiSwitchLabelStatementBase> labels) {
    List<PsiExpression> elementsToReplace = new ArrayList<>();
    for (PsiSwitchLabelStatementBase label : labels) {
      List<PsiEnumConstant> constants = SwitchUtils.findEnumConstants(label);
      if (constants.size() == 1 && myNames.contains(constants.get(0).getName())) {
        if (label instanceof PsiSwitchLabeledRuleStatement) {
          PsiStatement body = ((PsiSwitchLabeledRuleStatement)label).getBody();
          if (body instanceof PsiExpressionStatement) {
            ContainerUtil.addIfNotNull(elementsToReplace, ((PsiExpressionStatement)body).getExpression());
          }
        } else {
          PsiElement next = PsiTreeUtil.skipWhitespacesAndCommentsForward(label);
          if(next instanceof PsiBreakStatement) {
            ContainerUtil.addIfNotNull(elementsToReplace, ((PsiBreakStatement)next).getValueExpression());
          }
        }
      }
    }
    return elementsToReplace;
  }

  private static List<String> generateStatements(String name, PsiSwitchBlock switchBlock, boolean isRuleBasedFormat) {
    if (switchBlock instanceof PsiSwitchExpression) {
      String value = TypeUtils.getDefaultValue(((PsiSwitchExpression)switchBlock).getType());
      if (isRuleBasedFormat) {
        return Collections.singletonList("case " + name + " -> " + value + ";");
      } else {
        return Arrays.asList("case "+name+":", "break " + value + ";");
      }
    } else {
      if (isRuleBasedFormat) {
        return Collections.singletonList("case "+name+" -> {}");
      } else {
        return Arrays.asList("case "+name+":", "break;");
      }
    }
  }

  private static void addSwitchLabelStatementBefore(PsiEnumConstant missingEnumElement,
                                                    PsiElement anchor,
                                                    PsiSwitchBlock switchBlock,
                                                    boolean isRuleBasedFormat) {
    if (anchor instanceof PsiSwitchLabelStatement) {
      PsiElement sibling = PsiTreeUtil.skipWhitespacesBackward(anchor);
      while (sibling instanceof PsiSwitchLabelStatement) {
        anchor = sibling;
        sibling = PsiTreeUtil.skipWhitespacesBackward(anchor);
      }
    }
    PsiElement correctedAnchor = anchor;
    final PsiElement parent = anchor.getParent();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(anchor.getProject());
    generateStatements(missingEnumElement.getName(), switchBlock, isRuleBasedFormat).stream()
      .map(text -> factory.createStatementFromText(text, parent))
      .forEach(statement -> parent.addBefore(statement, correctedAnchor));
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
    return element instanceof PsiSwitchLabelStatementBase && ((PsiSwitchLabelStatementBase)element).isDefaultCase();
  }

  public static String formatMissingBranches(Set<String> names) {
    return StreamEx.of(names).map(name -> "'" + name + "'").mapLast("and "::concat)
      .collect(Joining.with(", ").maxChars(50).cutAfterDelimiter());
  }
}

/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.introduce.parameter;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.IntroduceParameterRefactoring;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParametersOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrAbstractInplaceIntroducer;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Created by Max Medvedev on 9/1/13
 */
public class GrInplaceParameterIntroducer extends GrAbstractInplaceIntroducer<GrIntroduceParameterSettings> {
  private final IntroduceParameterInfo myInfo;
  private final TIntArrayList myParametersToRemove;

  private JBCheckBox myDelegateCB;

  private final LinkedHashSet<String> mySuggestedNames;

  public GrInplaceParameterIntroducer(IntroduceParameterInfo info, GrIntroduceContext context, OccurrencesChooser.ReplaceChoice choice) {
    super(GrIntroduceParameterHandler.REFACTORING_NAME, choice, context);
    myInfo = info;

    GrVariable localVar = GrIntroduceHandlerBase.resolveLocalVar(context);
    mySuggestedNames = GroovyIntroduceParameterUtil.suggestNames(localVar, context.getExpression(), context.getStringPart(), info.getToReplaceIn(), context.getProject());

    myParametersToRemove = new TIntArrayList(GroovyIntroduceParameterUtil.findParametersToRemove(info).getValues());
  }

  @Override
  protected String getActionName() {
    return GrIntroduceParameterHandler.REFACTORING_NAME;
  }

  @NotNull
  @Override
  protected String[] suggestNames(boolean replaceAll, @Nullable GrVariable variable) {
    return ArrayUtil.toStringArray(mySuggestedNames);
  }

  @Override
  protected JComponent getComponent() {

    JPanel previewPanel = new JPanel(new BorderLayout());
    previewPanel.add(getPreviewEditor().getComponent(), BorderLayout.CENTER);
    previewPanel.setBorder(new EmptyBorder(2, 2, 6, 2));

    myDelegateCB = new JBCheckBox("Delegate via overloading method");
    myDelegateCB.setMnemonic('l');
    myDelegateCB.setFocusable(false);

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(previewPanel, BorderLayout.CENTER);
    panel.add(myDelegateCB, BorderLayout.SOUTH);
    return panel;
  }

  @Override
  protected void saveSettings(@NotNull GrVariable variable) {

  }

  @Override
  protected void updateTitle(@Nullable GrVariable variable) {
    if (variable == null) return;
    updateTitle(variable, variable.getName());
  }

  @Override
  protected void updateTitle(@Nullable GrVariable variable, String value) {
    if (getPreviewEditor() == null || variable == null) return;
    final PsiElement declarationScope = ((PsiParameter)variable).getDeclarationScope();
    if (declarationScope instanceof PsiMethod) {
      final PsiMethod psiMethod = (PsiMethod)declarationScope;
      final StringBuilder buf = new StringBuilder();
      buf.append(psiMethod.getName()).append(" (");
      boolean frst = true;
      final List<TextRange> ranges2Remove = new ArrayList<>();
      TextRange addedRange = null;

      int i = 0;
      for (PsiParameter parameter : psiMethod.getParameterList().getParameters()) {
        if (frst) {
          frst = false;
        }
        else {
          buf.append(", ");
        }
        int startOffset = buf.length();
        /*if (myMustBeFinal || myPanel.isGenerateFinal()) {
          buf.append("final ");
        }*/
        buf.append(parameter.getType().getPresentableText()).append(" ").append(variable == parameter ? value : parameter.getName());
        int endOffset = buf.length();
        if (variable == parameter) {
          addedRange = new TextRange(startOffset, endOffset);
        }
        else if (myParametersToRemove.contains(i)) {
          ranges2Remove.add(new TextRange(startOffset, endOffset));
        }
        i++;
      }

      assert addedRange != null;

      buf.append(")");
      setPreviewText(buf.toString());
      final MarkupModel markupModel = DocumentMarkupModel.forDocument(getPreviewEditor().getDocument(), myProject, true);
      markupModel.removeAllHighlighters();
      for (TextRange textRange : ranges2Remove) {
        markupModel.addRangeHighlighter(textRange.getStartOffset(), textRange.getEndOffset(), 0, getTestAttributesForRemoval(), HighlighterTargetArea.EXACT_RANGE);
      }
      markupModel.addRangeHighlighter(addedRange.getStartOffset(), addedRange.getEndOffset(), 0, getTextAttributesForAdd(), HighlighterTargetArea.EXACT_RANGE);
      //revalidate();
    }
  }

  private static TextAttributes getTextAttributesForAdd() {
    final TextAttributes textAttributes = new TextAttributes();
    textAttributes.setEffectType(EffectType.ROUNDED_BOX);
    textAttributes.setEffectColor(JBColor.RED);
    return textAttributes;
  }

  private static TextAttributes getTestAttributesForRemoval() {
    final TextAttributes textAttributes = new TextAttributes();
    textAttributes.setEffectType(EffectType.STRIKEOUT);
    textAttributes.setEffectColor(JBColor.BLACK);
    return textAttributes;
  }

  @Override
  protected GrVariable runRefactoring(GrIntroduceContext context, GrIntroduceParameterSettings settings, boolean processUsages) {
    GrExpressionWrapper wrapper = createExpressionWrapper(context);
    if (processUsages) {
      GrIntroduceExpressionSettingsImpl patchedSettings =
        new GrIntroduceExpressionSettingsImpl(settings, settings.getName(), settings.declareFinal(), settings.parametersToRemove(),
                                              settings.generateDelegate(), settings.replaceFieldsWithGetters(), context.getExpression(),
                                              context.getVar(), settings.getSelectedType(), context.getVar() != null || settings.replaceAllOccurrences(),
                                              context.getVar() != null, settings.isForceReturn());
      GrIntroduceParameterProcessor processor = new GrIntroduceParameterProcessor(patchedSettings, wrapper);
      processor.run();
    }
    else {
      WriteAction.run(() -> new GrIntroduceParameterProcessor(settings, wrapper).performRefactoring(UsageInfo.EMPTY_ARRAY));
    }
    GrParametersOwner owner = settings.getToReplaceIn();
    return ArrayUtil.getLastElement(owner.getParameters());
  }

  @NotNull
  private static GrExpressionWrapper createExpressionWrapper(@NotNull GrIntroduceContext context) {
    GrExpression expression = context.getExpression();
    GrVariable var = context.getVar();
    assert expression != null || var != null ;

    GrExpression initializer = expression != null ? expression : var.getInitializerGroovy();
    return new GrExpressionWrapper(initializer);
  }

  @Nullable
  @Override
  protected GrIntroduceParameterSettings getInitialSettingsForInplace(@NotNull GrIntroduceContext context,
                                                                      @NotNull OccurrencesChooser.ReplaceChoice choice,
                                                                      String[] names) {
    GrExpression expression = context.getExpression();
    GrVariable var = context.getVar();
    PsiType type = var != null ? var.getDeclaredType() :
                   expression != null ? expression.getType() :
                   null;

    return new GrIntroduceExpressionSettingsImpl(myInfo, names[0], false, new TIntArrayList(), false,
                                                 IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, expression,
                                                 var, type, false, false, false);

  }

  @Override
  protected GrIntroduceParameterSettings getSettings() {
    return new GrIntroduceExpressionSettingsImpl(myInfo, getInputName(), false, myParametersToRemove, myDelegateCB.isSelected(),
                                                 IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, null,
                                                 null, getSelectedType(), isReplaceAllOccurrences(), false, false);
  }
}

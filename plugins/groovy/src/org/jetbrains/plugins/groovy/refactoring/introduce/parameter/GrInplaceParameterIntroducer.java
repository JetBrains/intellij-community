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
package org.jetbrains.plugins.groovy.refactoring.introduce.parameter;

import com.intellij.codeInsight.template.TextResult;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiVariable;
import com.intellij.refactoring.IntroduceParameterRefactoring;
import com.intellij.refactoring.rename.inplace.InplaceRefactoring;
import com.intellij.ui.DottedBorder;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ArrayUtil;
import gnu.trove.TIntArrayList;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParametersOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrInplaceIntroducer;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Created by Max Medvedev on 9/1/13
 */
public class GrInplaceParameterIntroducer extends GrInplaceIntroducer {
  private final JPanel myPanel;
  private EditorEx myPreview;

  private JComponent myPreviewComponent;
  private JBCheckBox myDelegateCB;
  private GrIntroduceParameterSettings mySettings;

  private final GrExpressionWrapper myExpr;

  public GrInplaceParameterIntroducer(@NotNull GrVariable elementToRename,
                                      @NotNull Editor editor,
                                      @NotNull Project project,
                                      @NotNull String title,
                                      @NotNull List<RangeMarker> occurrences,
                                      @Nullable PsiElement elementToIntroduce,
                                      GrIntroduceParameterSettings settings,
                                      GrExpressionWrapper expr) {
    super(elementToRename, editor, project, title, occurrences, elementToIntroduce);

    mySettings = settings;

    initPreview(project);

    myDelegateCB = new JBCheckBox("Delegate via overloading method");
    myDelegateCB.setMnemonic('l');
    myDelegateCB.setFocusable(false);

    myPanel = new JPanel(new BorderLayout());
    myPanel.add(myPreviewComponent, BorderLayout.CENTER);
    myPanel.add(myDelegateCB, BorderLayout.SOUTH);

    myExpr = expr;

  }

  protected final void setPreviewText(final String text) {
    if (myPreview == null) return; //already disposed
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        myPreview.getDocument().replaceString(0, myPreview.getDocument().getTextLength(), text);
      }
    });
  }


  private void initPreview(Project project) {
    myPreview = (EditorEx)EditorFactory.getInstance()
      .createEditor(EditorFactory.getInstance().createDocument(""), project, GroovyFileType.GROOVY_FILE_TYPE, true);
    myPreview.setOneLineMode(true);
    final EditorSettings settings = myPreview.getSettings();
    settings.setAdditionalLinesCount(0);
    settings.setAdditionalColumnsCount(1);
    settings.setRightMarginShown(false);
    settings.setFoldingOutlineShown(false);
    settings.setLineNumbersShown(false);
    settings.setLineMarkerAreaShown(false);
    settings.setIndentGuidesShown(false);
    settings.setVirtualSpace(false);
    myPreview.setHorizontalScrollbarVisible(false);
    myPreview.setVerticalScrollbarVisible(false);
    myPreview.setCaretEnabled(false);
    settings.setLineCursorWidth(1);

    final Color bg = myPreview.getColorsScheme().getColor(EditorColors.CARET_ROW_COLOR);
    myPreview.setBackgroundColor(bg);
    myPreview.setBorder(BorderFactory.createCompoundBorder(new DottedBorder(JBColor.GRAY), new LineBorder(bg, 2)));

    myPreviewComponent = new JPanel(new BorderLayout());
    myPreviewComponent.add(myPreview.getComponent(), BorderLayout.CENTER);
    myPreviewComponent.setBorder(new EmptyBorder(2, 2, 6, 2));

    DocumentAdapter documentAdapter = new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        if (myPreview == null) return;
        final TemplateState templateState = TemplateManagerImpl.getTemplateState(myEditor);
        if (templateState != null) {
          final TextResult value = templateState.getVariableValue(InplaceRefactoring.PRIMARY_VARIABLE_NAME);
          if (value != null) {
            updateTitle(getVariable(), value.getText());
          }
        }
      }
    };
    myEditor.getDocument().addDocumentListener(documentAdapter);

    updateTitle(getVariable(), getVariable().getName());
  }

  @Override
  public LinkedHashSet<String> suggestNames(GrIntroduceContext context) {
    return GroovyIntroduceParameterUtil.suggestNames(null, myExpr.getExpression(), null, (GrParametersOwner)context.getScope(), context.getProject());
  }

  static LinkedHashSet<String> suggestNames(GrIntroduceContext context, GrParametersOwner scope) {
    return GroovyIntroduceParameterUtil.suggestNames(context.getVar(), context.getExpression(), context.getStringPart(), scope, context.getProject());
  }

  @Override
  protected void moveOffsetAfter(boolean success) {
    if (success) {
      final GrVariable parameter = getVariable();
      GrIntroduceParameterSettings settings = generateSettings((GrParameter)parameter, mySettings, myDelegateCB.isSelected());
      assert parameter != null;
      parameter.delete();
      GrIntroduceParameterProcessor processor = new GrIntroduceParameterProcessor(settings, myExpr);
      processor.run();
    }


    super.moveOffsetAfter(success);
  }

  public GrIntroduceParameterSettings generateSettings(GrParameter parameter, IntroduceParameterInfo info, boolean delegate) {

    TObjectIntHashMap<GrParameter> toRemove = GroovyIntroduceParameterUtil.findParametersToRemove(info);
    TIntArrayList removeList = new TIntArrayList(delegate ? ArrayUtil.EMPTY_INT_ARRAY: toRemove.getValues());

    GrExpression _expr = myExpr.getExpression();
    GrVariable _var = GroovyIntroduceParameterUtil.findVar(info);
    return new GrIntroduceExpressionSettingsImpl(info, parameter.getName(), false, removeList, myDelegateCB.isSelected(),
                                                 IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, _expr, _var,
                                                 parameter.getType(), false);
  }

  @Override
  protected JComponent getComponent() {
    updateTitle(getVariable());
    return myPanel;
  }


  protected void updateTitle(@Nullable PsiVariable variable) {
    if (variable == null) return;
    updateTitle(variable, variable.getName());
  }

  protected void updateTitle(@Nullable final PsiVariable variable, final String value) {
    final PsiElement declarationScope = variable != null ? ((PsiParameter)variable).getDeclarationScope() : null;
    if (declarationScope instanceof PsiMethod) {
      final PsiMethod psiMethod = (PsiMethod)declarationScope;
      final StringBuilder buf = new StringBuilder();
      buf.append(psiMethod.getName()).append(" (");
      boolean frst = true;
      final List<TextRange> ranges2Remove = new ArrayList<TextRange>();
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
        else if (mySettings.parametersToRemove().contains(i)) {
          ranges2Remove.add(new TextRange(startOffset, endOffset));
        }
        i++;
      }

      buf.append(")");
      setPreviewText(buf.toString());
      final MarkupModel markupModel = DocumentMarkupModel.forDocument(myPreview.getDocument(), myProject, true);
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
    textAttributes.setEffectColor(Color.BLACK);
    return textAttributes;
  }
}

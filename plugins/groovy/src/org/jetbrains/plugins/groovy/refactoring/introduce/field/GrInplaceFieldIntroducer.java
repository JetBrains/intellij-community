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
package org.jetbrains.plugins.groovy.refactoring.introduce.field;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.introduce.inplace.KeyboardComboSwitcher;
import com.intellij.refactoring.introduceField.IntroduceFieldHandler;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrFinalListener;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrInplaceIntroducer;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContextImpl;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GrInplaceFieldIntroducer extends GrInplaceIntroducer {
  private final GrInplaceIntroduceFieldPanel myPanel;
  private final GrIntroduceContext myContext;
  private final RangeMarker myExpressionRangeMarker;
  private final RangeMarker myStringPartRangeMarker;
  private final GrExpression myInitializer;
  private final GrFinalListener finalListener;
  private final boolean myReplaceAll;

  @Nullable
  @Override
  protected PsiElement checkLocalScope() {
    return getVariable().getContainingFile();
  }

  public GrInplaceFieldIntroducer(GrVariable var,
                                  GrIntroduceContext context,
                                  List<RangeMarker> occurrences,
                                  boolean replaceAll,
                                  @Nullable RangeMarker expressionRangeMarker,
                                  @Nullable RangeMarker stringPartRangeMarker,
                                  GrExpression initializer) {
    super(var, context.getEditor(), context.getProject(), IntroduceFieldHandler.REFACTORING_NAME, occurrences, context.getPlace());

    myContext = context;
    myReplaceAll = replaceAll;
    myExpressionRangeMarker = expressionRangeMarker;
    myStringPartRangeMarker = stringPartRangeMarker;
    myInitializer = initializer;

    myPanel = new GrInplaceIntroduceFieldPanel(context.getProject(),
                                               GrIntroduceFieldHandler.getApplicableInitPlaces(context));

    finalListener = new GrFinalListener(myEditor);
  }

  @Override
  protected void moveOffsetAfter(boolean success) {
    if (success) {
      final GrVariable field = getVariable();
      assert field != null;
      GrIntroduceFieldProcessor processor = new GrIntroduceFieldProcessor(generateContext(), generateSettings()) {
        @NotNull
        @Override
        protected GrExpression getInitializer() {
          return myInitializer;
        }

        @NotNull
        @Override
        protected GrVariableDeclaration insertField(@NotNull PsiClass targetClass, @NotNull GrVariableDeclaration declaration) {
          return (GrVariableDeclaration)field.getParent();
        }
      };
      processor.run();
    }
    super.moveOffsetAfter(success);
  }

  private GrIntroduceContext generateContext() {
    final List<RangeMarker> occurrenceMarkers = getOccurrenceMarkers();

    List<PsiElement> occurrences = ContainerUtil.newArrayList();
    for (RangeMarker marker : occurrenceMarkers) {
      ContainerUtil.addIfNotNull(occurrences, findExpression(marker));
    }

    GrExpression expr = null;
    if (myExpressionRangeMarker != null) expr = findExpression(myExpressionRangeMarker);
    if (myStringPartRangeMarker != null) {
      expr = findExpressionFromStringPartMarker(myStringPartRangeMarker);
      occurrences.add(expr);
    }

    return new GrIntroduceContextImpl(myContext.getProject(), myContext.getEditor(), expr, null, null, PsiUtilCore.toPsiElementArray(
      occurrences), myContext.getScope());
  }

  @Nullable
  private GrExpression findExpressionFromStringPartMarker(RangeMarker marker) {
    PsiFile file = PsiDocumentManager.getInstance(myContext.getProject()).getPsiFile(marker.getDocument());
    if (file == null) return null;
    PsiElement leaf = file.findElementAt(marker.getStartOffset());
    GrBinaryExpression binary = PsiTreeUtil.getParentOfType(leaf, GrBinaryExpression.class);
    if (binary != null) {
      return binary.getRightOperand();
    }
    return null;
  }

  @Nullable
  private GrExpression findExpression(@NotNull RangeMarker marker) {
    PsiFile file = PsiDocumentManager.getInstance(myContext.getProject()).getPsiFile(marker.getDocument());
    if (file == null) return null;
    PsiElement leaf = file.findElementAt(marker.getStartOffset());
    if (leaf != null && leaf.getParent() instanceof GrReferenceExpression) {
      return (GrExpression)leaf.getParent();
    }
    return null;
  }

  private GrIntroduceFieldSettings generateSettings() {
    return new GrIntroduceFieldSettings() {
      @Override
      public boolean declareFinal() {
        return myPanel.isFinal();
      }

      @Override
      public Init initializeIn() {
        return myPanel.getInitPlace();
      }

      @Override
      public String getVisibilityModifier() {
        return PsiModifier.PRIVATE;
      }

      @Override
      public boolean isStatic() {
        return getVariable().hasModifierProperty(PsiModifier.STATIC);
      }

      @Override
      public boolean removeLocalVar() {
        return false;
      }

      @Nullable
      @Override
      public String getName() {
        return getVariable().getName();
      }

      @Override
      public boolean replaceAllOccurrences() {
        return myReplaceAll;
      }

      @Nullable
      @Override
      public PsiType getSelectedType() {
        return getVariable().getDeclaredType();
      }
    };
  }

  @Nullable
  @Override
  protected JComponent getComponent() {
    return myPanel.getRootPane();
  }

  @Override
  public LinkedHashSet<String> suggestNames(GrIntroduceContext context) {
    return new GrFieldNameSuggester(context , new GroovyInplaceFieldValidator(context)).suggestNames();
  }

  public class GrInplaceIntroduceFieldPanel {
    private final Project myProject;
    private JPanel myRootPane;
    private JComboBox myInitCB;
    private NonFocusableCheckBox myDeclareFinalCB;

    public GrInplaceIntroduceFieldPanel(Project project, EnumSet<GrIntroduceFieldSettings.Init> initPlaces) {
      myProject = project;

      KeyboardComboSwitcher.setupActions(myInitCB, project);

      for (GrIntroduceFieldSettings.Init place : initPlaces) {
        myInitCB.addItem(place);
      }

      myDeclareFinalCB.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          new WriteCommandAction(myProject, getCommandName(), getCommandName()) {
            @Override
            protected void run(Result result) throws Throwable {
              PsiDocumentManager.getInstance(myProject).commitDocument(myEditor.getDocument());
              final GrVariable variable = getVariable();
              if (variable != null) {
                finalListener.perform(myDeclareFinalCB.isSelected(), variable);
              }
            }
          }.execute();
        }
      });
    }

    public JPanel getRootPane() {
      return myRootPane;
    }

    public GrIntroduceFieldSettings.Init getInitPlace() {
      return (GrIntroduceFieldSettings.Init)myInitCB.getSelectedItem();
    }

    public boolean isFinal() {
      return myDeclareFinalCB.isSelected();
    }
  }
}

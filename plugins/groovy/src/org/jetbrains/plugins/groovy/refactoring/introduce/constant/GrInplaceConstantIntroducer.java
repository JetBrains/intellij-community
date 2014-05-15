/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.introduce.constant;

import com.intellij.psi.*;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import com.intellij.refactoring.introduceField.IntroduceConstantHandler;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.refactoring.GroovyNameSuggestionUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrAbstractInplaceIntroducer;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;
import org.jetbrains.plugins.groovy.refactoring.introduce.field.GroovyInplaceFieldValidator;

import javax.swing.*;
import java.util.ArrayList;

/**
 * Created by Max Medvedev on 8/29/13
 */
public class GrInplaceConstantIntroducer extends GrAbstractInplaceIntroducer<GrIntroduceConstantSettings> {
  private final GrInplaceIntroduceConstantPanel myPanel;
  private final GrIntroduceContext myContext;
  private final String[] mySuggestedNames;

  public GrInplaceConstantIntroducer(GrIntroduceContext context, OccurrencesChooser.ReplaceChoice choice) {
    super(IntroduceConstantHandler.REFACTORING_NAME, choice, context);

    myContext = context;

    myPanel = new GrInplaceIntroduceConstantPanel();

    GrVariable localVar = GrIntroduceHandlerBase.resolveLocalVar(context);
    if (localVar != null) {
      ArrayList<String> result = ContainerUtil.newArrayList(localVar.getName());

      GrExpression initializer = localVar.getInitializerGroovy();
      if (initializer != null) {
        ContainerUtil.addAll(result, GroovyNameSuggestionUtil.suggestVariableNames(initializer, new GroovyInplaceFieldValidator(context), true));
      }
      mySuggestedNames = ArrayUtil.toStringArray(result);
    }
    else {
      GrExpression expression = context.getExpression();
      assert expression != null;
      mySuggestedNames = GroovyNameSuggestionUtil.suggestVariableNames(expression, new GroovyInplaceFieldValidator(context), true);
    }
  }

  @Override
  protected String getActionName() {
    return GrIntroduceConstantHandler.REFACTORING_NAME;
  }

  @Override
  protected String[] suggestNames(boolean replaceAll, @Nullable GrVariable variable) {
    return mySuggestedNames;
  }

  @Nullable
  @Override
  protected JComponent getComponent() {
    return myPanel.getRootPane();
  }

  @Override
  protected void saveSettings(@NotNull GrVariable variable) {

  }

  @Override
  protected GrVariable runRefactoring(GrIntroduceContext context, GrIntroduceConstantSettings settings, boolean processUsages) {
    if (processUsages) {
      return new GrIntroduceConstantProcessor(context, settings).run();
    }
    else {
      PsiElement scope = context.getScope();
      return new GrIntroduceConstantProcessor(context, settings).addDeclaration(scope instanceof GroovyFileBase ? ((GroovyFileBase)scope).getScriptClass() : (PsiClass)scope).getVariables()[0];
    }
  }

  @Nullable
  @Override
  protected GrIntroduceConstantSettings getInitialSettingsForInplace(@NotNull final GrIntroduceContext context,
                                                                     @NotNull final OccurrencesChooser.ReplaceChoice choice,
                                                                     final String[] names) {
    return new GrIntroduceConstantSettings() {
      @Override
      public String getVisibilityModifier() {
        return PsiModifier.PUBLIC;
      }

      @Nullable
      @Override
      public PsiClass getTargetClass() {
        return (PsiClass)context.getScope();
      }

      @Nullable
      @Override
      public String getName() {
        return names[0];
      }

      @Override
      public boolean replaceAllOccurrences() {
        return isReplaceAllOccurrences();
      }

      @Nullable
      @Override
      public PsiType getSelectedType() {
        GrExpression expression = context.getExpression();
        GrVariable var = context.getVar();
        StringPartInfo stringPart = context.getStringPart();
        return var != null ? var.getDeclaredType() :
               expression != null ? expression.getType() :
               stringPart != null ? stringPart.getLiteral().getType() :
               null;
      }
    };
  }

  @Override
  protected GrIntroduceConstantSettings getSettings() {
    return new GrIntroduceConstantSettings() {
      @Override
      public String getVisibilityModifier() {
        return PsiModifier.PUBLIC;
      }

      @Nullable
      @Override
      public String getName() {
        return getInputName();
      }

      @Override
      public boolean replaceAllOccurrences() {
        return isReplaceAllOccurrences();
      }

      @Nullable
      @Override
      public PsiType getSelectedType() {
        return GrInplaceConstantIntroducer.this.getSelectedType();
      }

      @Nullable
      @Override
      public PsiClass getTargetClass() {
        return (PsiClass)myContext.getScope();
      }
    };
  }

  @Nullable
  @Override
  protected PsiElement checkLocalScope() {
    return ((PsiField)getVariable()).getContainingClass();
  }


  @Override
  protected boolean performRefactoring() {
    JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_MOVE_TO_ANOTHER_CLASS = myPanel.isMoveToAnotherClass();
    if (myPanel.isMoveToAnotherClass()) {
      try {
        myEditor.putUserData(INTRODUCE_RESTART, true);
        myEditor.putUserData(ACTIVE_INTRODUCE, this);
        final GrIntroduceConstantHandler constantHandler = new GrIntroduceConstantHandler();
        final PsiLocalVariable localVariable = (PsiLocalVariable)getLocalVariable();
        constantHandler.getContextAndInvoke(myProject, myEditor, ((GrExpression)myExpr), (GrVariable)localVariable, null);
      }
      finally {
        myEditor.putUserData(INTRODUCE_RESTART, false);
        myEditor.putUserData(ACTIVE_INTRODUCE, null);
        releaseResources();
        if (myLocalMarker != null) {
          myLocalMarker.dispose();
        }
        if (myExprMarker != null) {
          myExprMarker.dispose();
        }
      }
      return false;
    }
    return super.performRefactoring();
  }

  /**
   * Created by Max Medvedev on 8/29/13
   */
  public class GrInplaceIntroduceConstantPanel {
    private JBCheckBox myMoveToAnotherClassJBCheckBox;
    private JPanel myRootPane;
    private JComponent myPreview;

    public boolean isMoveToAnotherClass() {
      return myMoveToAnotherClassJBCheckBox.isSelected();
    }

    public JComponent getRootPane() {
      return myRootPane;
    }

    private void createUIComponents() {
      myPreview = getPreviewComponent();
    }
  }
}

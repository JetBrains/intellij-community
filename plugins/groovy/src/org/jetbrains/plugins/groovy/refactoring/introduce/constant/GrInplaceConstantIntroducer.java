// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.introduce.constant;

import com.intellij.psi.*;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import com.intellij.refactoring.introduceField.IntroduceConstantHandler;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
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
import java.util.List;

public class GrInplaceConstantIntroducer extends GrAbstractInplaceIntroducer<GrIntroduceConstantSettings> {
  private final GrInplaceIntroduceConstantPanel myPanel;
  private final GrIntroduceContext myContext;
  private final String[] mySuggestedNames;

  public GrInplaceConstantIntroducer(GrIntroduceContext context, OccurrencesChooser.ReplaceChoice choice) {
    super(IntroduceConstantHandler.getRefactoringNameText(), choice, context);

    myContext = context;

    myPanel = new GrInplaceIntroduceConstantPanel();

    GrVariable localVar = GrIntroduceHandlerBase.resolveLocalVar(context);
    if (localVar != null) {
      List<String> result = new SmartList<>(localVar.getName());

      GrExpression initializer = localVar.getInitializerGroovy();
      if (initializer != null) {
        ContainerUtil.addAll(result, GroovyNameSuggestionUtil.suggestVariableNames(initializer, new GroovyInplaceFieldValidator(context), true));
      }
      mySuggestedNames = ArrayUtilRt.toStringArray(result);
    }
    else {
      GrExpression expression = context.getExpression();
      assert expression != null;
      mySuggestedNames = GroovyNameSuggestionUtil.suggestVariableNames(expression, new GroovyInplaceFieldValidator(context), true);
    }
  }

  @Override
  protected String getActionName() {
    return GroovyBundle.message("introduce.constant.title");
  }

  @Override
  protected String @NotNull [] suggestNames(boolean replaceAll, @Nullable GrVariable variable) {
    return mySuggestedNames;
  }

  @Override
  protected @Nullable JComponent getComponent() {
    return myPanel.getRootPane();
  }

  @Override
  protected void saveSettings(@NotNull GrVariable variable) {

  }

  @Override
  protected GrVariable runRefactoring(GrIntroduceContext context, GrIntroduceConstantSettings settings, boolean processUsages) {
    return refactorInWriteAction(() -> {
      if (processUsages) {
        return new GrIntroduceConstantProcessor(context, settings).run();
      }
      else {
        PsiElement scope = context.getScope();
        return new GrIntroduceConstantProcessor(context, settings).addDeclaration(scope instanceof GroovyFileBase ? ((GroovyFileBase)scope).getScriptClass() : (PsiClass)scope).getVariables()[0];
      }
    });
  }

  @Override
  protected @Nullable GrIntroduceConstantSettings getInitialSettingsForInplace(final @NotNull GrIntroduceContext context,
                                                                               final @NotNull OccurrencesChooser.ReplaceChoice choice,
                                                                               final String[] names) {
    return new GrIntroduceConstantSettings() {
      @Override
      public String getVisibilityModifier() {
        return PsiModifier.PUBLIC;
      }

      @Override
      public @Nullable PsiClass getTargetClass() {
        return (PsiClass)context.getScope();
      }

      @Override
      public @Nullable String getName() {
        return names[0];
      }

      @Override
      public boolean replaceAllOccurrences() {
        return isReplaceAllOccurrences();
      }

      @Override
      public @Nullable PsiType getSelectedType() {
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

      @Override
      public @Nullable String getName() {
        return getInputName();
      }

      @Override
      public boolean replaceAllOccurrences() {
        return isReplaceAllOccurrences();
      }

      @Override
      public @Nullable PsiType getSelectedType() {
        return GrInplaceConstantIntroducer.this.getSelectedType();
      }

      @Override
      public @Nullable PsiClass getTargetClass() {
        return (PsiClass)myContext.getScope();
      }
    };
  }

  @Override
  protected @Nullable PsiElement checkLocalScope() {
    return ((PsiField)getVariable()).getContainingClass();
  }


  @Override
  protected boolean performRefactoring() {
    if (myPanel.isMoveToAnotherClass()) {
      try {
        myEditor.putUserData(INTRODUCE_RESTART, true);
        myEditor.putUserData(ACTIVE_INTRODUCE, this);
        final GrIntroduceConstantHandler constantHandler = new GrIntroduceConstantHandler();
        final GrVariable localVariable = getLocalVariable();
        constantHandler.getContextAndInvoke(myProject, myEditor, ((GrExpression)myExpr), localVariable, null);
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

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
package org.jetbrains.plugins.groovy.refactoring.introduce.field;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.introduce.inplace.KeyboardComboSwitcher;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import com.intellij.refactoring.introduceField.IntroduceFieldHandler;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyNameSuggestionUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.*;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.EnumSet;

/**
 * @author Max Medvedev
 */
public class GrInplaceFieldIntroducer extends GrAbstractInplaceIntroducer<GrIntroduceFieldSettings> {
  private final EnumSet<GrIntroduceFieldSettings.Init> myApplicablePlaces;
  private GrInplaceIntroduceFieldPanel myPanel;
  private final GrFinalListener finalListener;
  private final String[] mySuggestedNames;
  private boolean myIsStatic;
  private final GrVariable myLocalVar;

  public GrInplaceFieldIntroducer(GrIntroduceContext context, OccurrencesChooser.ReplaceChoice choice) {
    super(IntroduceFieldHandler.REFACTORING_NAME, choice, context);

    finalListener = new GrFinalListener(myEditor);

    myLocalVar = GrIntroduceHandlerBase.resolveLocalVar(context);
    if (myLocalVar != null) {
      //myLocalVariable = myLocalVar;
      ArrayList<String> result = ContainerUtil.newArrayList(myLocalVar.getName());

      GrExpression initializer = myLocalVar.getInitializerGroovy();
      if (initializer != null) {
        ContainerUtil.addAll(result, GroovyNameSuggestionUtil.suggestVariableNames(initializer, new GroovyInplaceFieldValidator(getContext()), false));
      }
      mySuggestedNames = ArrayUtil.toStringArray(result);
    }
    else {
      mySuggestedNames = GroovyNameSuggestionUtil.suggestVariableNames(context.getExpression(), new GroovyInplaceFieldValidator(getContext()), false);
    }
    myApplicablePlaces = getApplicableInitPlaces();
  }

  @Nullable
  @Override
  protected PsiElement checkLocalScope() {
    final GrVariable variable = getVariable();
    if (variable instanceof PsiField) {
      return ((PsiField)getVariable()).getContainingClass();
    }
    else {
      final PsiFile file = variable.getContainingFile();
      if (file instanceof GroovyFile) {
        return ((GroovyFile)file).getScriptClass();
      }
      else {
        return null;
      }
    }
  }

  @Override
  protected GrVariable runRefactoring(GrIntroduceContext context, GrIntroduceFieldSettings settings, boolean processUsages) {
    return refactorInWriteAction(() -> {
      GrIntroduceFieldProcessor processor = new GrIntroduceFieldProcessor(context, settings);
      return processUsages ? processor.run()
                           : processor.insertField((PsiClass)context.getScope()).getVariables()[0];
    });
  }

  @Nullable
  @Override
  protected GrIntroduceFieldSettings getInitialSettingsForInplace(@NotNull final GrIntroduceContext context,
                                                                  @NotNull final OccurrencesChooser.ReplaceChoice choice,
                                                                  final String[] names) {
    return new GrIntroduceFieldSettings() {
      @Override
      public boolean declareFinal() {
        return false;
      }

      @Override
      public Init initializeIn() {
        return Init.FIELD_DECLARATION;
      }

      @Override
      public String getVisibilityModifier() {
        return PsiModifier.PRIVATE;
      }

      @Override
      public boolean isStatic() {
        boolean hasInstanceInScope = true;
        PsiClass clazz = (PsiClass)context.getScope();
        if (replaceAllOccurrences()) {
          for (PsiElement occurrence : context.getOccurrences()) {
            if (!PsiUtil.hasEnclosingInstanceInScope(clazz, occurrence, false)) {
              hasInstanceInScope = false;
              break;
            }
          }
        }
        else if (context.getExpression() != null) {
          hasInstanceInScope = PsiUtil.hasEnclosingInstanceInScope(clazz, context.getExpression(), false);
        }
        else if (context.getStringPart() != null) {
          hasInstanceInScope = PsiUtil.hasEnclosingInstanceInScope(clazz, context.getStringPart().getLiteral(), false);
        }

        return !hasInstanceInScope;
      }

      @Override
      public boolean removeLocalVar() {
        return myLocalVar != null;
      }

      @Nullable
      @Override
      public String getName() {
        return names[0];
      }

      @Override
      public boolean replaceAllOccurrences() {
        return context.getVar() != null || choice == OccurrencesChooser.ReplaceChoice.ALL;
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
  protected GrIntroduceFieldSettings getSettings() {
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
        return myIsStatic;
      }

      @Override
      public boolean removeLocalVar() {
        return myLocalVar != null;
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
        return GrInplaceFieldIntroducer.this.getSelectedType();
      }
    };
  }

  @Override
  protected String getActionName() {
    return IntroduceFieldHandler.REFACTORING_NAME;
  }

  @NotNull
  @Override
  protected String[] suggestNames(boolean replaceAll, @Nullable GrVariable variable) {
    return mySuggestedNames;
  }

  @Override
  protected void saveSettings(@NotNull GrVariable variable) {

  }

  @Override
  protected void restoreState(@NotNull GrVariable psiField) {
    myIsStatic = psiField.hasModifierProperty(PsiModifier.STATIC);

    super.restoreState(psiField);
  }

  @Nullable
  @Override
  protected JComponent getComponent() {
    myPanel = new GrInplaceIntroduceFieldPanel();
    return myPanel.getRootPane();
  }

  private EnumSet<GrIntroduceFieldSettings.Init> getApplicableInitPlaces() {
    return getApplicableInitPlaces(getContext(), isReplaceAllOccurrences());
  }

  public static EnumSet<GrIntroduceFieldSettings.Init> getApplicableInitPlaces(GrIntroduceContext context,
                                                                               boolean replaceAllOccurrences) {
    EnumSet<GrIntroduceFieldSettings.Init> result = EnumSet.noneOf(GrIntroduceFieldSettings.Init.class);

    if (!(context.getScope() instanceof GroovyScriptClass || context.getScope() instanceof GroovyFileBase)) {
      if (context.getExpression() != null ||
          context.getVar() != null && context.getVar().getInitializerGroovy() != null ||
          context.getStringPart() != null) {
        result.add(GrIntroduceFieldSettings.Init.FIELD_DECLARATION);
      }
      result.add(GrIntroduceFieldSettings.Init.CONSTRUCTOR);
    }

    PsiElement scope = context.getScope();
    if (scope instanceof GroovyScriptClass) scope = scope.getContainingFile();

    if (replaceAllOccurrences || context.getExpression() != null) {
      PsiElement[] occurrences = replaceAllOccurrences ? context.getOccurrences() : new PsiElement[]{context.getExpression()};
      PsiElement parent = PsiTreeUtil.findCommonParent(occurrences);
      PsiElement container = GrIntroduceHandlerBase.getEnclosingContainer(parent);
      if (container != null && PsiTreeUtil.isAncestor(scope, container, false)) {
        PsiElement anchor = GrIntroduceHandlerBase.findAnchor(occurrences, container);
        if (anchor != null) {
          result.add(GrIntroduceFieldSettings.Init.CUR_METHOD);
        }
      }
    }

    if (scope instanceof GrTypeDefinition && TestFrameworks.getInstance().isTestClass((PsiClass)scope)) {
      result.add(GrIntroduceFieldSettings.Init.SETUP_METHOD);
    }

    return result;
  }

  public class GrInplaceIntroduceFieldPanel {
    private JPanel myRootPane;
    private JComboBox myInitCB;
    private NonFocusableCheckBox myDeclareFinalCB;
    private JComponent myPreview;

    public GrInplaceIntroduceFieldPanel() {

      KeyboardComboSwitcher.setupActions(myInitCB, myProject);

      for (GrIntroduceFieldSettings.Init place : myApplicablePlaces) {
        myInitCB.addItem(place);
      }

      myDeclareFinalCB.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          WriteCommandAction.writeCommandAction(myProject).withName(getCommandName()).withGroupId(getCommandName()).run(() -> {
            PsiDocumentManager.getInstance(myProject).commitDocument(myEditor.getDocument());
            final GrVariable variable = getVariable();
            if (variable != null) {
              finalListener.perform(myDeclareFinalCB.isSelected(), variable);
            }
            ;
          });
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

    private void createUIComponents() {
      myPreview = getPreviewComponent();
    }
  }
}

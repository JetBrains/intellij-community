// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.introduce.variable;

import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsContexts.PopupAdvertisement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.ui.NonFocusableCheckBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SupertypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.refactoring.GroovyNameSuggestionUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrAbstractInplaceIntroducer;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrFinalListener;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;
import org.jetbrains.plugins.groovy.settings.GroovyApplicationSettings;
import org.jetbrains.plugins.groovy.template.expressions.ChooseTypeExpression;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

public abstract class GrInplaceVariableIntroducer extends GrAbstractInplaceIntroducer<GroovyIntroduceVariableSettings> {
  private JCheckBox myCanBeFinalCb;

  public GrInplaceVariableIntroducer(@NlsContexts.Command String title,
                                     OccurrencesChooser.ReplaceChoice replaceChoice,
                                     GrIntroduceContext context) {
    super(title, replaceChoice, context);
    setAdvertisementText(getAdvertisementText());
  }

  @Nullable
  private static @PopupAdvertisement String getAdvertisementText() {
    final Shortcut shortcut = KeymapUtil.getPrimaryShortcut("PreviousTemplateVariable");
    if  (shortcut != null) {
      return GroovyBundle.message("introduce.variable.change.type.advertisement", KeymapUtil.getShortcutText(shortcut));
    }
    return null;
  }

  @Override
  protected String getActionName() {
    return GrIntroduceVariableHandler.getRefactoringNameText();
  }

  @Override
  protected String @NotNull [] suggestNames(boolean replaceAll, @Nullable GrVariable variable) {
    return GroovyNameSuggestionUtil.suggestVariableNames(getContext().getExpression(), new GroovyVariableValidator(getContext()));
  }

  @Override
  protected JComponent getComponent() {
    myCanBeFinalCb = new NonFocusableCheckBox(GroovyRefactoringBundle.message("declare.final.checkbox"));
    myCanBeFinalCb.setSelected(false);
    myCanBeFinalCb.setMnemonic(KeyEvent.VK_F);
    final GrFinalListener finalListener = new GrFinalListener(myEditor);
    myCanBeFinalCb.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        WriteCommandAction.writeCommandAction(myProject).withName(getCommandName()).withGroupId(getCommandName()).run(() -> {
          PsiDocumentManager.getInstance(myProject).commitDocument(myEditor.getDocument());
          final GrVariable variable = getVariable();
          if (variable != null) {
            finalListener.perform(myCanBeFinalCb.isSelected(), variable);
          }
        });
      }
    });
    final JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(null);

    if (myCanBeFinalCb != null) {
      panel.add(myCanBeFinalCb, new GridBagConstraints(0, 1, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                                                       new Insets(5, 5, 5, 5), 0, 0));
    }

    panel.add(Box.createVerticalBox(),
              new GridBagConstraints(0, 2, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));

    return panel;
  }

  @Nullable
  @Override
  protected GroovyIntroduceVariableSettings getInitialSettingsForInplace(@NotNull final GrIntroduceContext context,
                                                                         @NotNull final OccurrencesChooser.ReplaceChoice choice,
                                                                         final String[] names) {
    return new GroovyIntroduceVariableSettings() {
      private final CanonicalTypes.Type myType;

      {
        GrExpression expression = context.getExpression();
        StringPartInfo stringPart = context.getStringPart();
        GrVariable var = context.getVar();
        PsiType type = expression != null ? expression.getType() :
                       var != null ? var.getType() :
                       stringPart != null ? stringPart.getLiteral().getType() :
                       null;
        myType = type != null && !PsiType.NULL.equals(type)? CanonicalTypes.createTypeWrapper(type) : null;
      }


      @Override
      public boolean isDeclareFinal() {
        return myCanBeFinalCb != null ? myCanBeFinalCb.isSelected() : false;
      }

      @Nullable
      @Override
      public String getName() {
        return names[0];
      }

      @Override
      public boolean replaceAllOccurrences() {
        return choice == OccurrencesChooser.ReplaceChoice.ALL;
      }

      @Nullable
      @Override
      public PsiType getSelectedType() {
        return myType != null ? myType.getType(context.getPlace()) : null;
      }
    };
  }

  @Override
  protected void addAdditionalVariables(TemplateBuilderImpl builder) {
    GrVariable variable = getVariable();
    assert variable != null && variable.getInitializerGroovy() != null;
    final PsiType initializerType = variable.getInitializerGroovy().getType();
    TypeConstraint[] constraints = initializerType != null && !initializerType.equals(PsiType.NULL) ? new SupertypeConstraint[]{SupertypeConstraint.create(initializerType)}
                                                                                                    : TypeConstraint.EMPTY_ARRAY;
    ChooseTypeExpression typeExpression = new ChooseTypeExpression(constraints, variable.getManager(), variable.getResolveScope(), true, GroovyApplicationSettings.getInstance().INTRODUCE_LOCAL_SELECT_DEF);
    PsiElement element = getTypeELementOrDef(variable);
    if (element == null) return;
    builder.replaceElement(element, "Variable_type", typeExpression, true, true);
  }

  @Nullable
  private static PsiElement getTypeELementOrDef(@NotNull GrVariable variable) {
    GrTypeElement typeElement = variable.getTypeElementGroovy();
    if (typeElement != null) return typeElement;

    GrModifierList modifierList = variable.getModifierList();
    if (modifierList != null) return modifierList.getModifier(GrModifier.DEF);
    return null;
  }

  @Override
  protected GroovyIntroduceVariableSettings getSettings() {
    return new GroovyIntroduceVariableSettings() {
      @Override
      public boolean isDeclareFinal() {
        return myCanBeFinalCb.isSelected();
      }

      @Nullable
      @Override
      public String getName() {
        return GrInplaceVariableIntroducer.this.getInputName();
      }

      @Override
      public boolean replaceAllOccurrences() {
        return isReplaceAllOccurrences();
      }

      @Nullable
      @Override
      public PsiType getSelectedType() {
        return GrInplaceVariableIntroducer.this.getSelectedType();
      }
    };
  }

  @Override
  protected void saveSettings(@NotNull GrVariable variable) {
    GroovyApplicationSettings.getInstance().INTRODUCE_LOCAL_SELECT_DEF = variable.getDeclaredType() == null;
  }

  @Override
  protected int getCaretOffset() {
    return getVariable().getNameIdentifierGroovy().getTextRange().getEndOffset();
  }
}
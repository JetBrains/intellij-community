// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.introduce.field;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.introduce.inplace.KeyboardComboSwitcher;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import com.intellij.refactoring.introduceField.IntroduceFieldHandler;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.SmartList;
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
import org.jetbrains.plugins.groovy.refactoring.introduce.GrAbstractInplaceIntroducer;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrFinalListener;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.List;
import java.util.ResourceBundle;

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
    super(IntroduceFieldHandler.getRefactoringNameText(), choice, context);

    finalListener = new GrFinalListener(myEditor);

    myLocalVar = GrIntroduceHandlerBase.resolveLocalVar(context);
    if (myLocalVar != null) {
      //myLocalVariable = myLocalVar;
      List<String> result = new SmartList<>(myLocalVar.getName());

      GrExpression initializer = myLocalVar.getInitializerGroovy();
      if (initializer != null) {
        ContainerUtil.addAll(result, GroovyNameSuggestionUtil.suggestVariableNames(initializer, new GroovyInplaceFieldValidator(getContext()), false));
      }
      mySuggestedNames = ArrayUtilRt.toStringArray(result);
    }
    else {
      mySuggestedNames = GroovyNameSuggestionUtil.suggestVariableNames(context.getExpression(), new GroovyInplaceFieldValidator(getContext()), false);
    }
    myApplicablePlaces = getApplicableInitPlaces();
  }

  @Override
  protected @Nullable PsiElement checkLocalScope() {
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

  @Override
  protected @Nullable GrIntroduceFieldSettings getInitialSettingsForInplace(final @NotNull GrIntroduceContext context,
                                                                            final @NotNull OccurrencesChooser.ReplaceChoice choice,
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

      @Override
      public @Nullable String getName() {
        return names[0];
      }

      @Override
      public boolean replaceAllOccurrences() {
        return context.getVar() != null || choice == OccurrencesChooser.ReplaceChoice.ALL;
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
        return GrInplaceFieldIntroducer.this.getSelectedType();
      }
    };
  }

  @Override
  protected String getActionName() {
    return IntroduceFieldHandler.getRefactoringNameText();
  }

  @Override
  protected String @NotNull [] suggestNames(boolean replaceAll, @Nullable GrVariable variable) {
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

  @Override
  protected @Nullable JComponent getComponent() {
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
    private final JPanel myRootPane;
    private final JComboBox myInitCB;
    private final NonFocusableCheckBox myDeclareFinalCB;
    private final JComponent myPreview;

    public GrInplaceIntroduceFieldPanel() {

      {
        myPreview = getPreviewComponent();
      }
      {
        // GUI initializer generated by IntelliJ IDEA GUI Designer
        // >>> IMPORTANT!! <<<
        // DO NOT EDIT OR ADD ANY CODE HERE!
        myRootPane = new JPanel();
        myRootPane.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        myRootPane.add(panel1, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                   null, 0, false));
        final JBLabel jBLabel1 = new JBLabel();
        this.$$$loadLabelText$$$(jBLabel1, this.$$$getMessageFromBundle$$$("messages/GroovyRefactoringBundle", "initialize.in.label"));
        panel1.add(jBLabel1,
                   new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                                       GridConstraints.SIZEPOLICY_FIXED,
                                       GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myInitCB = new JComboBox();
        panel1.add(myInitCB, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                 GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                 false));
        myDeclareFinalCB = new NonFocusableCheckBox();
        myDeclareFinalCB.setHorizontalAlignment(10);
        this.$$$loadButtonText$$$(myDeclareFinalCB,
                                  this.$$$getMessageFromBundle$$$("messages/GroovyRefactoringBundle", "declare.final.checkbox"));
        myRootPane.add(myDeclareFinalCB, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             null, null, null, 0, false));
        myRootPane.add(myPreview, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                      null,
                                                      null, 0, false));
        jBLabel1.setLabelFor(myInitCB);
      }
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
          });
        }
      });
    }

    private static Method $$$cachedGetBundleMethod$$$ = null;

    /** @noinspection ALL */
    private String $$$getMessageFromBundle$$$(String path, String key) {
      ResourceBundle bundle;
      try {
        Class<?> thisClass = this.getClass();
        if ($$$cachedGetBundleMethod$$$ == null) {
          Class<?> dynamicBundleClass = thisClass.getClassLoader().loadClass("com.intellij.DynamicBundle");
          $$$cachedGetBundleMethod$$$ = dynamicBundleClass.getMethod("getBundle", String.class, Class.class);
        }
        bundle = (ResourceBundle)$$$cachedGetBundleMethod$$$.invoke(null, path, thisClass);
      }
      catch (Exception e) {
        bundle = ResourceBundle.getBundle(path);
      }
      return bundle.getString(key);
    }

    /** @noinspection ALL */
    private void $$$loadLabelText$$$(JLabel component, String text) {
      StringBuffer result = new StringBuffer();
      boolean haveMnemonic = false;
      char mnemonic = '\0';
      int mnemonicIndex = -1;
      for (int i = 0; i < text.length(); i++) {
        if (text.charAt(i) == '&') {
          i++;
          if (i == text.length()) break;
          if (!haveMnemonic && text.charAt(i) != '&') {
            haveMnemonic = true;
            mnemonic = text.charAt(i);
            mnemonicIndex = result.length();
          }
        }
        result.append(text.charAt(i));
      }
      component.setText(result.toString());
      if (haveMnemonic) {
        component.setDisplayedMnemonic(mnemonic);
        component.setDisplayedMnemonicIndex(mnemonicIndex);
      }
    }

    /** @noinspection ALL */
    private void $$$loadButtonText$$$(AbstractButton component, String text) {
      StringBuffer result = new StringBuffer();
      boolean haveMnemonic = false;
      char mnemonic = '\0';
      int mnemonicIndex = -1;
      for (int i = 0; i < text.length(); i++) {
        if (text.charAt(i) == '&') {
          i++;
          if (i == text.length()) break;
          if (!haveMnemonic && text.charAt(i) != '&') {
            haveMnemonic = true;
            mnemonic = text.charAt(i);
            mnemonicIndex = result.length();
          }
        }
        result.append(text.charAt(i));
      }
      component.setText(result.toString());
      if (haveMnemonic) {
        component.setMnemonic(mnemonic);
        component.setDisplayedMnemonicIndex(mnemonicIndex);
      }
    }

    /** @noinspection ALL */
    public JComponent $$$getRootComponent$$$() { return myRootPane; }

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

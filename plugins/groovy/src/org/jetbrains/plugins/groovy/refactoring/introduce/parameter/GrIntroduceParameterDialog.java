/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.GridBag;
import gnu.trove.TIntArrayList;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParametersOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.refactoring.GrRefactoringError;
import org.jetbrains.plugins.groovy.refactoring.GroovyNameSuggestionUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.HelpID;
import org.jetbrains.plugins.groovy.refactoring.extract.closure.ExtractClosureFromMethodProcessor;
import org.jetbrains.plugins.groovy.refactoring.extract.closure.ExtractClosureHelperImpl;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContextImpl;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceDialog;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;
import org.jetbrains.plugins.groovy.refactoring.introduce.field.GroovyFieldValidator;
import org.jetbrains.plugins.groovy.refactoring.ui.GrTypeComboBox;

import javax.swing.*;
import java.awt.*;

import static com.intellij.refactoring.IntroduceParameterRefactoring.*;

public class GrIntroduceParameterDialog extends RefactoringDialog implements GrIntroduceDialog<GrIntroduceParameterSettings> {
  private JPanel myContentPane;
  private GrTypeComboBox myTypeComboBox;
  private NameSuggestionsField myNameSuggestionsField;
  private JCheckBox myDeclareFinalCheckBox;
  private JCheckBox myDelegateViaOverloadingMethodCheckBox;
  private JPanel myCheckBoxContainer;
  private JCheckBox myReplaceAllOccurrencesCheckBox;
  private JRadioButton myDoNotReplaceRadioButton;
  private JRadioButton myReplaceFieldsInaccessibleInRadioButton;
  private JRadioButton myReplaceAllFieldsRadioButton;
  private JCheckBox myRemoveLocalVariableCheckBox;
  private JPanel myGetterPanel;
  private JLabel myTypeLabel;
  private JLabel myNameLabel;
  private JCheckBox myChangeVarUsages;
  private IntroduceParameterInfo myInfo;
  TObjectIntHashMap<JCheckBox> toRemoveCBs;

  public GrIntroduceParameterDialog(IntroduceParameterInfo info) {
    super(info.getProject(), true);
    myInfo = info;

    TObjectIntHashMap<GrParameter> parametersToRemove = GroovyIntroduceParameterUtil.findParametersToRemove(info);
    toRemoveCBs = new TObjectIntHashMap<JCheckBox>(parametersToRemove.size());
    for (Object p : parametersToRemove.keys()) {
      JCheckBox cb = new JCheckBox(GroovyRefactoringBundle.message("remove.parameter.0.no.longer.used", ((GrParameter)p).getName()));
      toRemoveCBs.put(cb, parametersToRemove.get((GrParameter)p));
      cb.setSelected(true);
    }

    JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();

    if (myInfo.getStatements().length == 1 && GrIntroduceHandlerBase.findVariable(myInfo.getStatements()[0]) == null) {
      myRemoveLocalVariableCheckBox.setSelected(false);
      myRemoveLocalVariableCheckBox.setVisible(false);
    }
    else {
      myRemoveLocalVariableCheckBox.setSelected(settings.INTRODUCE_PARAMETER_DELETE_LOCAL_VARIABLE);
    }

    initReplaceFieldsWithGetters(settings);

    myDeclareFinalCheckBox.setSelected(hasFinalModifier());
    
    myChangeVarUsages.setVisible(info.getToReplaceIn() instanceof GrClosableBlock && info.getToSearchFor() instanceof GrVariable);
    myChangeVarUsages.setSelected(true);

    myDelegateViaOverloadingMethodCheckBox.setVisible(info.getToSearchFor() != null);

    setTitle(RefactoringBundle.message("introduce.parameter.title"));
    init();
  }

  private void initReplaceFieldsWithGetters(JavaRefactoringSettings settings) {

    final PsiField[] usedFields = GroovyIntroduceParameterUtil.findUsedFieldsWithGetters(myInfo.getStatements(), getContainingClass());
    myGetterPanel.setVisible(usedFields.length > 0);
    switch (settings.INTRODUCE_PARAMETER_REPLACE_FIELDS_WITH_GETTERS) {
      case REPLACE_FIELDS_WITH_GETTERS_ALL:
        myReplaceAllFieldsRadioButton.setSelected(true);
        break;
      case REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE:
        myReplaceFieldsInaccessibleInRadioButton.setSelected(true);
        break;
      case REPLACE_FIELDS_WITH_GETTERS_NONE:
        myDoNotReplaceRadioButton.setSelected(true);
        break;
    }
  }

  @Nullable
  private PsiClass getContainingClass() {
    final GrParametersOwner toReplaceIn = myInfo.getToReplaceIn();
    if (toReplaceIn instanceof GrMethod) {
      return ((GrMethod)toReplaceIn).getContainingClass();
    }
    else {
      return PsiTreeUtil.getContextOfType(toReplaceIn, PsiClass.class);
    }
  }

  private boolean hasFinalModifier() {
    final Boolean createFinals = JavaRefactoringSettings.getInstance().INTRODUCE_PARAMETER_CREATE_FINALS;
    return createFinals == null ? CodeStyleSettingsManager.getSettings(myProject).GENERATE_FINAL_PARAMETERS : createFinals.booleanValue();
  }

  @Override
  protected void doAction() {
    saveSettings();
    final GrParametersOwner toReplaceIn = myInfo.getToReplaceIn();
    final PsiType selectedType = myTypeComboBox.getSelectedType();

    final GrExpression expr = findExpr();
    final GrVariable var = findVar();

    if ((expr == null && var == null) || selectedType != null && selectedType.equalsToText(GroovyCommonClassNames.GROOVY_LANG_CLOSURE)) {
      GrIntroduceParameterSettings settings = new ExtractClosureHelperImpl(myInfo,
                                                                           myNameSuggestionsField.getEnteredName(),
                                                                           myDeclareFinalCheckBox.isSelected(),
                                                                           getParametersToRemove(),
                                                                           myDelegateViaOverloadingMethodCheckBox.isSelected(),
                                                                           getReplaceFieldsWithGetter());
      invokeRefactoring(new ExtractClosureFromMethodProcessor(settings));
    }
    else {

      GrIntroduceExpressionSettings settings = new GrIntroduceExpressionSettingsImpl(myInfo,
                                                                                     myNameSuggestionsField.getEnteredName(),
                                                                                     myDeclareFinalCheckBox.isSelected(),
                                                                                     getParametersToRemove(),
                                                                                     myDelegateViaOverloadingMethodCheckBox.isSelected(),
                                                                                     getReplaceFieldsWithGetter(),
                                                                                     expr,
                                                                                     var,
                                                                                     myTypeComboBox.getSelectedType());
      if (toReplaceIn instanceof GrMethod) {
        invokeRefactoring(new GrIntroduceParameterProcessor(settings));
      }
      else {
        invokeRefactoring(new GrIntroduceClosureParameterProcessor(settings));
      }
    }
  }

  private void saveSettings() {
    final JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();
    settings.INTRODUCE_PARAMETER_CREATE_FINALS = myDeclareFinalCheckBox.isSelected();
    if (myRemoveLocalVariableCheckBox.isVisible()) {
      settings.INTRODUCE_PARAMETER_DELETE_LOCAL_VARIABLE = myRemoveLocalVariableCheckBox.isSelected();
    }
    if (myGetterPanel.isVisible()) {
      settings.INTRODUCE_PARAMETER_REPLACE_FIELDS_WITH_GETTERS = getReplaceFieldsWithGetter();
    }
  }

  @Override
  protected JComponent createCenterPanel() {
    final GridBag c = new GridBag().setDefaultAnchor(GridBagConstraints.WEST).setLine(4);
    for (Object o : toRemoveCBs.keys()) {
      c.nextLine();
      myCheckBoxContainer.add(((JCheckBox)o), c);
    }

    myNameLabel.setLabelFor(myNameSuggestionsField);
    myTypeLabel.setLabelFor(myTypeComboBox);
    return myContentPane;
  }

  @Override
  public GrIntroduceParameterSettings getSettings() {
    return null;
  }

  private int getReplaceFieldsWithGetter() {
    if (myDoNotReplaceRadioButton.isSelected()) return REPLACE_FIELDS_WITH_GETTERS_NONE;
    if (myReplaceFieldsInaccessibleInRadioButton.isSelected()) return REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE;
    if (myReplaceAllFieldsRadioButton.isSelected()) return REPLACE_FIELDS_WITH_GETTERS_ALL;
    throw new GrRefactoringError("no check box selected");
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameSuggestionsField;
  }

  @Override
  protected String getHelpId() {
    return HelpID.GROOVY_INTRODUCE_PARAMETER;
  }

  private TIntArrayList getParametersToRemove() {
    TIntArrayList list = new TIntArrayList();
    for (Object o : toRemoveCBs.keys()) {
      if (((JCheckBox)o).isSelected()) {
        list.add(toRemoveCBs.get((JCheckBox)o));
      }
    }
    return list;
  }

  private void createUIComponents() {
    final GrVariable var = findVar();
    final GrExpression expr = findExpr();
    if (var != null) {
      myTypeComboBox = GrTypeComboBox.createTypeComboBoxWithDefType(var.getDeclaredType());
    }
    else if (expr != null) {
      myTypeComboBox = GrTypeComboBox.createTypeComboBoxFromExpression(expr);
    }
    else {
      myTypeComboBox = GrTypeComboBox.createEmptyTypeComboBox();
    }

    myTypeComboBox.addType(JavaPsiFacade.getElementFactory(myProject).createTypeFromText(GroovyCommonClassNames.GROOVY_LANG_CLOSURE, myInfo.getContext()));

    String[] possibleNames;
    final GrIntroduceContext introduceContext = new GrIntroduceContextImpl(myProject, null, expr, var, PsiElement.EMPTY_ARRAY, myInfo.getToReplaceIn());
    final GroovyFieldValidator validator = new GroovyFieldValidator(introduceContext);
    if (expr != null) {
      possibleNames = GroovyNameSuggestionUtil.suggestVariableNames(expr, validator, true);
    }
    else if (var != null) {
      possibleNames = GroovyNameSuggestionUtil.suggestVariableNameByType(var.getType(), validator);
    }
    else {
      possibleNames = ArrayUtil.EMPTY_STRING_ARRAY;
    }

    if (var != null) {
      String[] arr = new String[possibleNames.length + 1];
      arr[0] = var.getName();
      System.arraycopy(possibleNames, 0, arr, 1, possibleNames.length);
      possibleNames = arr;
    }
    myNameSuggestionsField = new NameSuggestionsField(possibleNames, myProject, GroovyFileType.GROOVY_FILE_TYPE);
  }

  @Nullable
  private GrVariable findVar() {
    final GrStatement[] statements = myInfo.getStatements();
    if (statements.length > 1) return null;
    return GrIntroduceHandlerBase.findVariable(statements[0]);
  }

  @Nullable
  private GrExpression findExpr() {
    final GrStatement[] statements = myInfo.getStatements();
    if (statements.length > 1) return null;
    return GrIntroduceHandlerBase.findExpression(statements[0]);
  }
}

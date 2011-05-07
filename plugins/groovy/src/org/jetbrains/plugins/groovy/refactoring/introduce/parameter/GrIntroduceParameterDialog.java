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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.util.ui.GridBag;
import gnu.trove.TIntArrayList;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.refactoring.GroovyNameSuggestionUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceDialog;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceRefactoringError;
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
  private GrIntroduceParameterContext myContext;
  TObjectIntHashMap<JCheckBox> toRemoveCBs;

  public GrIntroduceParameterDialog(GrIntroduceParameterContext context, TObjectIntHashMap<GrParameter> parametersToRemove) {
    super(context.project, true);
    myContext = context;
    toRemoveCBs = new TObjectIntHashMap<JCheckBox>(parametersToRemove.size());
    for (Object p : parametersToRemove.keys()) {
      JCheckBox cb = new JCheckBox(GroovyRefactoringBundle.message("remove.parameter.0.no.longer.used", ((GrParameter)p).getName()));
      toRemoveCBs.put(cb, parametersToRemove.get((GrParameter)p));
      cb.setSelected(true);
    }

    JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();

    final PsiElement[] occurrences = context.occurrences;
    if (occurrences.length < 2) {
      myReplaceAllOccurrencesCheckBox.setSelected(true);
      myReplaceAllOccurrencesCheckBox.setVisible(false);
    }
    if (myContext.var == null) {
      myRemoveLocalVariableCheckBox.setSelected(false);
      myRemoveLocalVariableCheckBox.setVisible(false);
    }
    else {
      myRemoveLocalVariableCheckBox.setSelected(settings.INTRODUCE_PARAMETER_DELETE_LOCAL_VARIABLE);
    }

    initReplaceFieldsWithGetters(settings);

    myDeclareFinalCheckBox.setSelected(hasFinalModifier());

    setTitle(RefactoringBundle.message("introduce.parameter.title"));
    init();
  }

  private void initReplaceFieldsWithGetters(JavaRefactoringSettings settings) {
    if (myContext.expression == null) {
      myGetterPanel.setVisible(false);
      return;
    }
    final PsiField[] usedFields =
      GroovyIntroduceParameterUtil.findUsedFieldsWithGetters(myContext.expression, myContext.methodToReplaceIn.getContainingClass());
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

  private boolean hasFinalModifier() {
    final Boolean createFinals = JavaRefactoringSettings.getInstance().INTRODUCE_PARAMETER_CREATE_FINALS;
    return createFinals == null ? CodeStyleSettingsManager.getSettings(myProject).GENERATE_FINAL_PARAMETERS : createFinals.booleanValue();
  }

  @Override
  protected void doAction() {
    saveSettings();
    GrIntroduceParameterSettings settings = new GrIntroduceParameterSettingsImpl(
      myNameSuggestionsField.getEnteredName(),
      myReplaceAllOccurrencesCheckBox.isSelected(),
      myTypeComboBox.getSelectedType(),
      myDeclareFinalCheckBox.isSelected(),
      myDelegateViaOverloadingMethodCheckBox.isSelected(),
      getParametersToRemove(),
      getReplaceFieldsWithGetter(),
      myRemoveLocalVariableCheckBox.isSelected());
    invokeRefactoring(new GrIntroduceParameterProcessor(settings, myContext));
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
    final GridBag c = new GridBag().setDefaultAnchor(GridBagConstraints.WEST).setLine(3);
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
    throw new GrIntroduceRefactoringError("no check box selected");
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameSuggestionsField;
  }

  @Override
  protected String getHelpId() {
    return HelpID.INTRODUCE_PARAMETER;
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
    myTypeComboBox = new GrTypeComboBox(myContext.var != null ? myContext.var.getDeclaredType() : myContext.expression.getType(), true);

    String[] possibleNames;
    final GroovyFieldValidator validator = new GroovyFieldValidator(myContext);
    if (myContext.expression != null) {
      possibleNames = GroovyNameSuggestionUtil.suggestVariableNames(myContext.expression, validator, true);
    }
    else {
      possibleNames = GroovyNameSuggestionUtil.suggestVariableNameByType(myContext.var.getType(), validator);
    }
    if (myContext.var != null) {
      String[] arr = new String[possibleNames.length + 1];
      arr[0] = myContext.var.getName();
      System.arraycopy(possibleNames, 0, arr, 1, possibleNames.length);
      possibleNames = arr;
    }
    myNameSuggestionsField = new NameSuggestionsField(possibleNames, myContext.project, GroovyFileType.GROOVY_FILE_TYPE);
  }

  static class GrIntroduceParameterSettingsImpl implements GrIntroduceParameterSettings {

    private String myName;
    private boolean myReplaceAllOccurrences;
    private PsiType mySelectedType;
    private boolean myDeclareFinal;
    private boolean myIsGenerateDelegate;
    private TIntArrayList myParameterToRemove;
    private int myReplaceFieldWithGetters;
    private boolean myRemoveLocalVariable;

    GrIntroduceParameterSettingsImpl(String name,
                                     boolean replaceAllOccurrences,
                                     PsiType selectedType,
                                     boolean declareFinal,
                                     boolean isGenerateDelegate,
                                     TIntArrayList parameterToRemove,
                                     int replaceFieldWithGetters,
                                     boolean removeLocalVariable) {
      myName = name;
      myReplaceAllOccurrences = replaceAllOccurrences;
      mySelectedType = selectedType;
      myDeclareFinal = declareFinal;
      myIsGenerateDelegate = isGenerateDelegate;
      myParameterToRemove = parameterToRemove;
      myReplaceFieldWithGetters = replaceFieldWithGetters;
      myRemoveLocalVariable = removeLocalVariable;
    }

    @Override
    public String getName() {
      return myName;
    }

    @Override
    public boolean replaceAllOccurrences() {
      return myReplaceAllOccurrences;
    }

    @Override
    public PsiType getSelectedType() {
      return mySelectedType;
    }

    @Override
    public boolean declareFinal() {
      return myDeclareFinal;
    }

    @Override
    public boolean removeLocalVariable() {
      return myRemoveLocalVariable;
    }

    @Override
    public boolean generateDelegate() {
      return myIsGenerateDelegate;
    }

    @NotNull
    @Override
    public TIntArrayList parametersToRemove() {
      return myParameterToRemove;
    }

    @Override
    public int replaceFieldsWithGetters() {
      return myReplaceFieldWithGetters;
    }
  }
}

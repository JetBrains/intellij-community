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

package org.jetbrains.plugins.groovy.refactoring.introduce.variable;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyNamesUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceDialog;
import org.jetbrains.plugins.groovy.refactoring.ui.GrTypeComboBox;
import org.jetbrains.plugins.groovy.settings.GroovyApplicationSettings;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashSet;

public class GroovyIntroduceVariableDialog extends DialogWrapper implements GrIntroduceDialog<GroovyIntroduceVariableSettings> {
  private static final String REFACTORING_NAME = GroovyRefactoringBundle.message("introduce.variable.title");

  private final Project myProject;
  private final GrExpression myExpression;
  private final int myOccurrencesCount;
  private final GrIntroduceVariableHandler.Validator myValidator;
  private final GrIntroduceContext myContext;

  private NameSuggestionsField myNameField;
  private JCheckBox myCbIsFinal;
  private JCheckBox myCbReplaceAllOccurrences;
  private GrTypeComboBox myTypeComboBox;

  public GroovyIntroduceVariableDialog(GrIntroduceContext context, GrIntroduceVariableHandler.Validator validator) {
    super(context.getProject(), true);
    myContext = context;
    myProject = context.getProject();
    myExpression = context.getStringPart() != null ? context.getStringPart().getLiteral() : context.getExpression();
    myOccurrencesCount = context.getOccurrences().length;
    myValidator = validator;
    init();
  }

  @Override
  protected void init() {
    super.init();

    setModal(true);
    setTitle(REFACTORING_NAME);

    myCbReplaceAllOccurrences.setFocusable(false);
    myCbIsFinal.setFocusable(false);

    myCbIsFinal.setSelected(GroovyApplicationSettings.getInstance().INTRODUCE_LOCAL_CREATE_FINALS);

    // Replace occurrences
    if (myOccurrencesCount > 1) {
      myCbReplaceAllOccurrences.setSelected(false);
      myCbReplaceAllOccurrences.setEnabled(true);
      myCbReplaceAllOccurrences.setText(myCbReplaceAllOccurrences.getText() + " (" + myOccurrencesCount + " occurrences)");
    }
    else {
      myCbReplaceAllOccurrences.setSelected(false);
      myCbReplaceAllOccurrences.setEnabled(false);
    }

    pack();
  }

  @Override
  @Nullable
  protected JComponent createCenterPanel() {
    JPanel contentPane = new JPanel(new BorderLayout());
    contentPane.add(createNamePanel(), BorderLayout.CENTER);
    contentPane.add(createCBPanel(), BorderLayout.SOUTH);
    return contentPane;
  }

  @Override
  protected ValidationInfo doValidate() {
    String text = getEnteredName();
    if (!GroovyNamesUtil.isIdentifier(text)) {
      return new ValidationInfo(GroovyRefactoringBundle.message("name.is.wrong", text), myNameField);
    }
    return null;
  }

  private JPanel createCBPanel() {
    final JPanel panel = new JPanel(new FlowLayout());
    myCbIsFinal = new JCheckBox(UIUtil.replaceMnemonicAmpersand("Declare &final"));
    panel.add(myCbIsFinal);
    myCbReplaceAllOccurrences = new JCheckBox(UIUtil.replaceMnemonicAmpersand("Replace &all occurrences"));
    panel.add(myCbReplaceAllOccurrences);
    return panel;
  }

  private JPanel createNamePanel() {
    final GridBag c = new GridBag().setDefaultAnchor(GridBagConstraints.WEST).setDefaultInsets(1, 1, 1, 1);
    final JPanel namePanel = new JPanel(new GridBagLayout());

    final JLabel typeLabel = new JLabel(UIUtil.replaceMnemonicAmpersand("&Type:"));
    c.nextLine().next().weightx(0).fillCellNone();
    namePanel.add(typeLabel, c);

    myTypeComboBox = GrTypeComboBox.createTypeComboBoxFromExpression(myExpression, GroovyApplicationSettings.getInstance().INTRODUCE_LOCAL_SELECT_DEF);
    c.next().weightx(1).fillCellHorizontally();
    namePanel.add(myTypeComboBox, c);
    typeLabel.setLabelFor(myTypeComboBox);

    final JLabel nameLabel = new JLabel(UIUtil.replaceMnemonicAmpersand("&Name:"));
    c.nextLine().next().weightx(0).fillCellNone();
    namePanel.add(nameLabel, c);

    myNameField = setUpNameComboBox();
    c.next().weightx(1).fillCellHorizontally();
    namePanel.add(myNameField, c);
    nameLabel.setLabelFor(myNameField);

    GrTypeComboBox.registerUpDownHint(myNameField, myTypeComboBox);
    return namePanel;
  }

  @Nullable
  protected String getEnteredName() {
    return myNameField.getEnteredName();
  }

  protected boolean isReplaceAllOccurrences() {
    return myCbReplaceAllOccurrences.isSelected();
  }

  private boolean isDeclareFinal() {
    return myCbIsFinal.isSelected();
  }

  @Nullable
  private PsiType getSelectedType() {
    return myTypeComboBox.getSelectedType();
  }

  private NameSuggestionsField setUpNameComboBox() {
    LinkedHashSet<String> names = suggestNames();
    return new NameSuggestionsField(ArrayUtil.toStringArray(names), myProject, GroovyFileType.GROOVY_FILE_TYPE);
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  @Override
  protected void doOKAction() {
    if (!myValidator.isOK(this)) {
      return;
    }
    if (myCbIsFinal.isEnabled()) {
      GroovyApplicationSettings.getInstance().INTRODUCE_LOCAL_CREATE_FINALS = myCbIsFinal.isSelected();
    }
    GroovyApplicationSettings.getInstance().INTRODUCE_LOCAL_SELECT_DEF = (myTypeComboBox.getSelectedType() == null);
    super.doOKAction();
  }


  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.INTRODUCE_VARIABLE);
  }

  private void createUIComponents() {

  }

  @Override
  public GroovyIntroduceVariableSettings getSettings() {
    return new MyGroovyIntroduceVariableSettings(this);
  }

  @NotNull
  @Override
  public LinkedHashSet<String> suggestNames() {
    return new GrVariableNameSuggester(myContext, myValidator).suggestNames();
  }

  private static class MyGroovyIntroduceVariableSettings implements GroovyIntroduceVariableSettings {
    String myEnteredName;
    boolean myIsReplaceAllOccurrences;
    boolean myIsDeclareFinal;
    PsiType mySelectedType;

    public MyGroovyIntroduceVariableSettings(GroovyIntroduceVariableDialog dialog) {
      myEnteredName = dialog.getEnteredName();
      myIsReplaceAllOccurrences = dialog.isReplaceAllOccurrences();
      myIsDeclareFinal = dialog.isDeclareFinal();
      mySelectedType = dialog.getSelectedType();
    }

    @Override
    public String getName() {
      return myEnteredName;
    }

    @Override
    public boolean replaceAllOccurrences() {
      return myIsReplaceAllOccurrences;
    }

    @Override
    public boolean isDeclareFinal() {
      return myIsDeclareFinal;
    }

    @Override
    public PsiType getSelectedType() {
      return mySelectedType;
    }
  }
}

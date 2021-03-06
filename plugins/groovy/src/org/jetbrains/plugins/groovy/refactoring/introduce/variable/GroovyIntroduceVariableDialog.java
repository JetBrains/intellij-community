// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.refactoring.introduce.variable;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
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
    setTitle(GroovyRefactoringBundle.message("introduce.variable.title"));

    myCbReplaceAllOccurrences.setFocusable(false);
    myCbIsFinal.setFocusable(false);

    myCbIsFinal.setSelected(GroovyApplicationSettings.getInstance().INTRODUCE_LOCAL_CREATE_FINALS);

    // Replace occurrences
    if (myOccurrencesCount > 1) {
      myCbReplaceAllOccurrences.setSelected(false);
      myCbReplaceAllOccurrences.setEnabled(true);
      myCbReplaceAllOccurrences.setText(UIUtil.replaceMnemonicAmpersand(
        GroovyBundle.message("introduce.variable.replace.all.0.occurrences", myOccurrencesCount)
      ));
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
    myCbIsFinal = new JCheckBox(UIUtil.replaceMnemonicAmpersand(
      GroovyBundle.message("introduce.variable.declare.final.label")
    ));
    panel.add(myCbIsFinal);
    myCbReplaceAllOccurrences = new JCheckBox(UIUtil.replaceMnemonicAmpersand(
      GroovyBundle.message("introduce.variable.replace.all.occurrences")
    ));
    panel.add(myCbReplaceAllOccurrences);
    return panel;
  }

  private JPanel createNamePanel() {
    final GridBag c = new GridBag().setDefaultAnchor(GridBagConstraints.WEST).setDefaultInsets(1, 1, 1, 1);
    final JPanel namePanel = new JPanel(new GridBagLayout());

    final JLabel typeLabel = new JLabel(UIUtil.replaceMnemonicAmpersand(GroovyBundle.message("introduce.variable.type.label")));
    c.nextLine().next().weightx(0).fillCellNone();
    namePanel.add(typeLabel, c);

    myTypeComboBox = GrTypeComboBox.createTypeComboBoxFromExpression(myExpression, GroovyApplicationSettings.getInstance().INTRODUCE_LOCAL_SELECT_DEF);
    c.next().weightx(1).fillCellHorizontally();
    namePanel.add(myTypeComboBox, c);
    typeLabel.setLabelFor(myTypeComboBox);

    final JLabel nameLabel = new JLabel(UIUtil.replaceMnemonicAmpersand(GroovyBundle.message("introduce.variable.name.label")));
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
    return new NameSuggestionsField(ArrayUtilRt.toStringArray(names), myProject, GroovyFileType.GROOVY_FILE_TYPE);
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
  protected String getHelpId() {
    return HelpID.INTRODUCE_VARIABLE;
  }

  private void createUIComponents() { }

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

    MyGroovyIntroduceVariableSettings(GroovyIntroduceVariableDialog dialog) {
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

package org.jetbrains.plugins.groovy.intentions.style.parameterToEntry;

import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.plugins.groovy.settings.GroovyApplicationSettings;

import javax.swing.*;

public class FirstParameterDialog extends DialogWrapper {
  private JPanel contentPane;
  private JRadioButton createNewButton;
  private JRadioButton useExistingButton;

  private boolean createNewFirst = true;

  public FirstParameterDialog() {
    super(true);
    setModal(true);
    final Boolean first = GroovyApplicationSettings.getInstance().CONVERT_PARAM_CREATE_NEW_FIRST;
    if (first == null) {
      useExistingButton.setSelected(true);
    }
    else {
      if (first) {
        createNewButton.setSelected(true);
      } else {
        useExistingButton.setSelected(true);
      }
    }
    GroovyApplicationSettings.getInstance().CONVERT_PARAM_CREATE_NEW_FIRST = true;
    init();
  }

  public boolean createNewFirst() {
    return createNewFirst;
  }

  @Override
  protected void doOKAction() {
    GroovyApplicationSettings.getInstance().CONVERT_PARAM_CREATE_NEW_FIRST = createNewButton.isSelected();
    createNewFirst = createNewButton.isSelected();
    super.doOKAction();
  }

  @Override
  public void doCancelAction() {
    GroovyApplicationSettings.getInstance().CONVERT_PARAM_CREATE_NEW_FIRST = createNewButton.isSelected();
    super.doCancelAction();
  }

  protected JComponent createCenterPanel() {
    return contentPane;
  }

  public JComponent getContentPane() {
    return contentPane;
  }

}

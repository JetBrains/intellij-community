package org.jetbrains.plugins.groovy.config.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;
import java.awt.event.KeyEvent;

public class CreateLibraryDialog extends DialogWrapper {
  private JPanel contentPane;
  private JRadioButton myInProject;
  private JRadioButton myGlobal;

  public CreateLibraryDialog(Project project, final String title, final String inProjectText, final String isGlobalText) {
    super(project, true);
    setModal(true);
    setTitle(title);
    myInProject.setSelected(true);
    myInProject.setMnemonic(KeyEvent.VK_P);
    myGlobal.setMnemonic(KeyEvent.VK_A);

    myInProject.setText(inProjectText);
    myGlobal.setText(isGlobalText);
    init();
  }

  protected JComponent createCenterPanel() {
    return contentPane;
  }

  public boolean isInProject() {
    return myInProject.isSelected();
  }

}

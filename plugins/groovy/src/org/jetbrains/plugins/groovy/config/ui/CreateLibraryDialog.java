package org.jetbrains.plugins.groovy.config.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.plugins.groovy.GroovyBundle;

import javax.swing.*;
import java.awt.event.KeyEvent;

public class CreateLibraryDialog extends DialogWrapper {
  private JPanel contentPane;
  private JRadioButton myInProject;
  private JRadioButton myGlobal;

  public CreateLibraryDialog(Project project, final String selectedName) {
    super(project, true);
    setModal(true);
    setTitle(GroovyBundle.message("facet.create.lib.title"));
    myInProject.setSelected(true);
    myInProject.setMnemonic(KeyEvent.VK_P);
    myGlobal.setMnemonic(KeyEvent.VK_A);

    myInProject.setText(GroovyBundle.message("facet.create.project.lib", selectedName));
    myGlobal.setText(GroovyBundle.message("facet.create.application.lib", selectedName));
    init();
  }

  protected JComponent createCenterPanel() {
    return contentPane;
  }

  public boolean isInProject() {
    return myInProject.isSelected();
  }

}

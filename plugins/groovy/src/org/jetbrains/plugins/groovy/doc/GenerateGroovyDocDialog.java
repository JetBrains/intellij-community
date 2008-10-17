package org.jetbrains.plugins.groovy.doc;

import com.intellij.CommonBundle;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.ui.IdeBorderFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

final class GenerateGroovyDocDialog extends DialogWrapper {

  private final GroovyDocConfigurable myConfigurable;
  private final Project myProject;

  GenerateGroovyDocDialog(Project project, GroovyDocConfiguration configuration, final DataContext dataContext) {
    super(project, true);
    myProject = project;

    myConfigurable = configuration.createConfigurable(dataContext);

    setOKButtonText(GroovyDocBundle.message("groovydoc.generate.start.button"));

    setTitle(GroovyDocBundle.message("groovydoc.generate.title"));
    init();

    myConfigurable.reset();
  }

  public GroovyDocConfigurable getConfigurable() {
    return myConfigurable;
  }

  //protected JComponent createNorthPanel() {
  //  JPanel panel = new JPanel(new GridBagLayout());
  //  panel.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 0));
  //  GridBagConstraints gbConstraints = new GridBagConstraints();
  //  gbConstraints.gridy = 0;
  //  gbConstraints.gridx = 0;
  //  gbConstraints.gridwidth = 3;
  //  gbConstraints.gridheight = 1;
  //  gbConstraints.weightx = 1;
  //
  //  gbConstraints.fill = GridBagConstraints.BOTH;
  //  gbConstraints.insets = new Insets(0, 0, 0, 0);
  //
  //  return panel;
  //}

  protected JComponent createCenterPanel() {
    JPanel pane = new JPanel(new BorderLayout());
    //pane.setBorder(IdeBorderFactory.createTitledBorder(GroovyDocBundle.message("groovydoc.generate.settings.group")));
    pane.add(myConfigurable.createComponent(), BorderLayout.CENTER);
    return pane;
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected void doOKAction() {
    if (checkOutputDirectory(myConfigurable.getOutputDir()) && checkOutputDirectory(myConfigurable.getInputDir())) {
      myConfigurable.apply();
      close(OK_EXIT_CODE);
    }
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("editing.groovydocGeneration");
  }

  private boolean checkOutputDirectory(String outputDirectory) {
    if (outputDirectory == null || outputDirectory.trim().length() == 0) {
      Messages.showMessageDialog(myProject, GroovyDocBundle.message("groovydoc.generate.output.directory.not.specified"),
                                 CommonBundle.getErrorTitle(), Messages.getErrorIcon());
      return false;
    }

    File outputDir = new File(outputDirectory);
    if (!outputDir.exists()) {
      int choice = Messages
        .showOkCancelDialog(myProject, GroovyDocBundle.message("groovydoc.generate.output.directory.not.exists", outputDirectory),
                            GroovyDocBundle.message("groovydoc.generate.message.title"), Messages.getWarningIcon());
      if (choice != 0) return false;
      if (!outputDir.mkdirs()) {
        Messages
          .showMessageDialog(myProject, GroovyDocBundle.message("groovydoc.generate.output.directory.creation.failed", outputDirectory),
                             CommonBundle.getErrorTitle(), Messages.getErrorIcon());
        return false;
      }
    }
    else if (!outputDir.isDirectory()) {
      Messages.showMessageDialog(myProject, GroovyDocBundle.message("groovydoc.generate.output.not.a.directory", outputDirectory),
                                 CommonBundle.getErrorTitle(), Messages.getErrorIcon());
      return false;
    }
    return true;
  }
}
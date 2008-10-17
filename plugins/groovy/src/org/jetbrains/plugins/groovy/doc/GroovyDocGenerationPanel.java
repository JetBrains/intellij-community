package org.jetbrains.plugins.groovy.doc;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.plugins.groovy.doc.actions.GroovyDocAddPackageAction;
import org.jetbrains.plugins.groovy.doc.actions.GroovyDocReducePackageAction;

import javax.swing.*;
import java.awt.*;
import java.util.Set;

final class GroovyDocGenerationPanel extends JPanel {
  JPanel myPanel;
  TextFieldWithBrowseButton myOutputDir;
  JCheckBox myIsUse;
  JCheckBox myIsPrivate;
  JCheckBox myOpenInBrowserCheckBox;
  TextFieldWithBrowseButton myInputDir;
  private JTextField myWindowTitle;
  JList myPackageNames;
  private JPanel myPackagesPanel;

  private DefaultActionGroup myActionGroup;
  private ActionToolbar myActionToolbar;

  private GroovyDocAddPackageAction myAddPackageAction;
  private GroovyDocReducePackageAction myReducePackageAction;

  GroovyDocGenerationPanel(DataContext dataContext) {
    myInputDir.addBrowseFolderListener(GroovyDocBundle.message("groovydoc.generate.input.directory.browse"), null, null,
                                       FileChooserDescriptorFactory.createSingleFolderDescriptor());

    myOutputDir.addBrowseFolderListener(GroovyDocBundle.message("groovydoc.generate.output.directory.browse"), null, null,
                                        FileChooserDescriptorFactory.createSingleFolderDescriptor());

    myPackageNames.setModel(new DefaultListModel());

    myPackagesPanel.setLayout(new BorderLayout());
    myActionToolbar = ActionManager.getInstance().createActionToolbar("GroovyDoc", getActionGroup(), true);
    myPackagesPanel.add(myActionToolbar.getComponent(), BorderLayout.WEST);

    myActionToolbar.updateActionsImmediately();
  }

  private ActionGroup getActionGroup() {
    if (myActionGroup == null) {
      initActions();
      myActionGroup = new DefaultActionGroup();
      myActionGroup.add(myAddPackageAction);
      myActionGroup.add(myReducePackageAction);
    }
    return myActionGroup;
  }

  private void initActions() {
    myAddPackageAction = new GroovyDocAddPackageAction(myPackageNames);
    myReducePackageAction = new GroovyDocReducePackageAction(myPackageNames);
  }

  public void setPackagesList(String[] packages){
    myPackageNames.removeAll();
    myPackageNames.setListData(packages);
  }
}
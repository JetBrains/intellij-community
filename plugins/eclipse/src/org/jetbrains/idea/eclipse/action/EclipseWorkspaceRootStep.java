package org.jetbrains.idea.eclipse.action;

import com.intellij.ide.util.projectWizard.NamePathComponent;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.projectImport.ProjectImportWizardStep;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

public class EclipseWorkspaceRootStep extends ProjectImportWizardStep{

  private JPanel myPanel;
  private NamePathComponent myWorkspacePathComponent;
  private ModuleDirComponent myModuleDirComponent;
  private WizardContext myWizardContext;
  private EclipseProjectWizardContext myContext;
  private EclipseImportWizard.Parameters myParameters;
  private JCheckBox myLinkCheckBox;

  public EclipseWorkspaceRootStep(final WizardContext context, EclipseProjectWizardContext eclipseContext, EclipseImportWizard.Parameters parameters) {
    super(parameters.updateCurrent);
    myWizardContext = context;
    myParameters = parameters;
    myContext = eclipseContext;
    myPanel = new JPanel(new GridBagLayout());
    myPanel.setBorder(BorderFactory.createEtchedBorder());

    myWorkspacePathComponent = new NamePathComponent("", EclipseBundle.message("eclipse.import.label.select.workspace"),
                                                     EclipseBundle.message("eclipse.import.title.select.workspace"), "", false);
    myWorkspacePathComponent.setNameComponentVisible(false);

    myPanel.add(myWorkspacePathComponent, new GridBagConstraints(0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST,
                                                                 GridBagConstraints.HORIZONTAL, new Insets(5, 6, 0, 6), 0, 0));

    myModuleDirComponent = new ModuleDirComponent();
    myPanel.add(myModuleDirComponent.createPanel(), new GridBagConstraints(0, 1, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST,
                                                                           GridBagConstraints.HORIZONTAL, new Insets(15, 6, 0, 6), 0, 0));

    myLinkCheckBox = new JCheckBox(EclipseBundle.message("eclipse.import.link"));
    myPanel.add(myLinkCheckBox, new GridBagConstraints(0, 2, 1, 1, 1.0, 1.0, GridBagConstraints.SOUTHWEST, GridBagConstraints.HORIZONTAL,
                                                       new Insets(0, 6, 0, 6), 0, 0));
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public void updateDataModel() {
    myContext.setRootDirectory(myWorkspacePathComponent.getPath());
    myParameters.converterOptions.commonModulesDirectory = myModuleDirComponent.getPath();
    myParameters.linkConverted = myLinkCheckBox.isSelected();
  }

  public void updateStep() {
    if (!myWorkspacePathComponent.isPathChangedByUser()) {
      String path = myContext.getRootDirectory();
      if (path == null) {
        path = myWizardContext.getProjectFileDirectory();
      }
      if(path!=null){
        myWorkspacePathComponent.setPath(path.replace('/', File.separatorChar));
        myWorkspacePathComponent.getPathComponent().selectAll();
      }
    }
    myLinkCheckBox.setSelected(myParameters.linkConverted);
  }

  public JComponent getPreferredFocusedComponent() {
    return myWorkspacePathComponent.getPathComponent();
  }

  public String getHelpId() {
    return null;
  }

  private class ModuleDirComponent {
    private TextFieldWithBrowseButton myDirComponent;

    private JPanel createPanel() {
      final JPanel panel = new JPanel(new GridBagLayout());
      panel.setBorder(BorderFactory.createTitledBorder(EclipseBundle.message("eclipse.import.modules.location")));

      final JRadioButton rbModulesColocated = new JRadioButton(EclipseBundle.message("eclipse.import.modules.colocated"));
      final JRadioButton rbModulesDedicated = new JRadioButton(EclipseBundle.message("eclipse.import.modules.dedicated"));
      ButtonGroup buttonGroup = new ButtonGroup();
      buttonGroup.add(rbModulesColocated);
      buttonGroup.add(rbModulesDedicated);

      myDirComponent = new TextFieldWithBrowseButton();
      myDirComponent.addBrowseFolderListener(EclipseBundle.message("eclipse.import.title.module.dir"), "", null,
                                             new FileChooserDescriptor(false, true, false, false, false, false));


      rbModulesColocated.setSelected(true);
      myDirComponent.setEnabled(false);

      ActionListener listener = new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          final boolean dedicated = rbModulesDedicated.isSelected();
          myDirComponent.setEnabled(dedicated);
          if (dedicated && myDirComponent.getText().length() == 0) {
            myDirComponent.setText(FileUtil.toSystemDependentName(myWorkspacePathComponent.getPath()));
          }
        }
      };

      rbModulesColocated.addActionListener(listener);
      rbModulesDedicated.addActionListener(listener);

      panel.add(rbModulesColocated, new GridBagConstraints(0, 0, 2, 1, 0.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                                           new Insets(5, 6, 5, 0), 0, 0));
      panel.add(rbModulesDedicated, new GridBagConstraints(0, 1, 1, 1, 0.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                                           new Insets(5, 6, 5, 0), 0, 0));
      panel.add(myDirComponent, new GridBagConstraints(1, 1, 1, 1, 2.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                                                       new Insets(5, 6, 5, 0), 0, 0));
      return panel;
    }

    @Nullable
    public String getPath() {
      return myDirComponent.isEnabled() ? myDirComponent.getText() : null;
    }
  }
}

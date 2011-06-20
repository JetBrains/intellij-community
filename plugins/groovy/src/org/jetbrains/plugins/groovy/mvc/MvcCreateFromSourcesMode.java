package org.jetbrains.plugins.groovy.mvc;

import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.ide.util.newProjectWizard.ProjectNameStep;
import com.intellij.ide.util.newProjectWizard.StepSequence;
import com.intellij.ide.util.newProjectWizard.modes.WizardMode;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.util.projectWizard.ProjectJdkStep;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author peter
 */
public abstract class MvcCreateFromSourcesMode extends WizardMode {
  private TextFieldWithBrowseButton myPathPanel;
  private final MvcFramework myFramework;
  private MvcModuleBuilder myProjectBuilder;
  private boolean myDisplayPath;

  protected MvcCreateFromSourcesMode(MvcFramework framework) {
    myFramework = framework;
  }

  @NotNull
  @Override
  public String getDisplayName(WizardContext context) {
    return "Import " + myFramework.getDisplayName() + " application from existing sources";
  }

  @NotNull
  @Override
  public String getDescription(WizardContext context) {
    if (myDisplayPath) {
      return "Select " + myFramework.getDisplayName() + " application root";
    }

    return "Create IDEA project over existing " + myFramework.getDisplayName() + " application";
  }

  protected abstract MvcModuleBuilder createModuleBuilder();

  @Nullable
  protected StepSequence createSteps(final WizardContext context, final ModulesProvider modulesProvider) {
    myProjectBuilder = createModuleBuilder();

    final StepSequence sequence = new StepSequence();
    final boolean isNewProject = context.getProject() == null;
    if (isNewProject) {
      sequence.addCommonStep(new ProjectNameStep(context, sequence, this));
    }
    for (ModuleWizardStep step : myProjectBuilder.createWizardSteps(context, modulesProvider)) {
      if (step instanceof ProjectJdkStep && !isNewProject) {
        step.disposeUIResources();
        continue; //otherwise it will be always visible
      }

      sequence.addCommonStep(step);
    }

    return sequence;
  }

  public boolean isAvailable(WizardContext context) {
    if (context.getProject() != null) {
      myDisplayPath = true;
    }
    return true;
  }

  public ProjectBuilder getModuleBuilder() {
    if (myDisplayPath) {
      final String contentRootPath = FileUtil.toSystemIndependentName(myPathPanel.getText().trim());
      final String moduleName = PathUtil.getFileName(contentRootPath);
      myProjectBuilder.setModuleFilePath(contentRootPath + "/" + moduleName + ".iml");
      myProjectBuilder.setContentEntryPath(contentRootPath);
      myProjectBuilder.setName(moduleName);
    }
    return myProjectBuilder;
  }

  public JComponent getAdditionalSettings() {
    if (!myDisplayPath) {
      return null;
    }

    JTextField tfModuleFilePath = new JTextField();
    final String title = "Select " + myFramework.getDisplayName() + " application root:";
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false) {
      @Override
      public boolean isFileSelectable(VirtualFile file) {
        return isMvcAppRoot(file);
      }
    };
    myPathPanel = new TextFieldWithBrowseButton(tfModuleFilePath, new BrowseFilesListener(tfModuleFilePath, title, null, descriptor));
    onChosen(false);
    return myPathPanel;
  }

  @Override
  public boolean validate() throws ConfigurationException {
    if (!myDisplayPath) {
      return true;
    }

    final String path = myPathPanel.getText().trim();
    final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemIndependentName(path));
    if (file == null) {
      throw new ConfigurationException("File \'" + path + "\' doesn't exist");
    }
    if (!isMvcAppRoot(file)) {
      throw new ConfigurationException("Invalid " + myFramework.getDisplayName() + " application: \'" + path + "\'");
    }
    return super.validate();
  }

  public void onChosen(final boolean enabled) {
    if (!myDisplayPath) {
      return;
    }

    UIUtil.setEnabled(myPathPanel, enabled, true);
    if (enabled) {
      myPathPanel.getTextField().requestFocusInWindow();
    }
  }

  public void dispose() {
    myDisplayPath = false;
    myProjectBuilder = null;
    myPathPanel = null;
    super.dispose();
  }

  private boolean isMvcAppRoot(VirtualFile file) {
    final VirtualFile appRoot = file.findChild(myFramework.getFrameworkName().toLowerCase() + "-app");
    return appRoot != null && appRoot.isDirectory();
  }


}

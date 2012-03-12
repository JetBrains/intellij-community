package org.jetbrains.android.actions;

import com.android.AndroidConstants;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.intellij.CommonBundle;
import com.intellij.ide.ui.ListCellRendererWrapper;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.resources.Resources;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.uipreview.DeviceConfiguratorPanel;
import org.jetbrains.android.uipreview.InvalidOptionValueException;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class CreateXmlResourceDialog extends DialogWrapper {
  private JPanel myPanel;
  private JPanel myDeviceConfigurationWrapper;
  private JTextField myNameField;
  private JComboBox myModuleCombo;
  private JBLabel myModuleLabel;
  private JTextField myDirectoryNameField;
  private JBLabel myErrorLabel;
  private JTextField myFileNameField;

  private final DeviceConfiguratorPanel myDeviceConfiguratorPanel;
  private final Module myModule;
  private final ResourceType myResourceType;

  public CreateXmlResourceDialog(@NotNull Module module, @NotNull ResourceType resourceType) {
    super(module.getProject());

    myResourceType = resourceType;

    final Set<Module> modulesSet = new HashSet<Module>();
    modulesSet.add(module);

    for (AndroidFacet depFacet : AndroidUtils.getAllAndroidDependencies(module, true)) {
      modulesSet.add(depFacet.getModule());
    }

    assert modulesSet.size() > 0;

    if (modulesSet.size() == 1) {
      myModule = module;
      myModuleLabel.setVisible(false);
      myModuleCombo.setVisible(false);
    }
    else {
      myModule = null;

      final Module[] modules = modulesSet.toArray(new Module[modulesSet.size()]);
      Arrays.sort(modules, new Comparator<Module>() {
        @Override
        public int compare(Module m1, Module m2) {
          return m1.getName().compareTo(m2.getName());
        }
      });

      myModuleCombo.setModel(new DefaultComboBoxModel(modules));
      myModuleCombo.setSelectedItem(module);

      myModuleCombo.setRenderer(new ListCellRendererWrapper<Module>(myModuleCombo.getRenderer()) {
        @Override
        public void customize(JList list, Module module, int index, boolean selected, boolean hasFocus) {
          setText(module.getName());
          setIcon(ModuleType.get(module).getNodeIcon(false));
        }
      });
    }

    myDeviceConfiguratorPanel = new DeviceConfiguratorPanel(null) {
      @Override
      public void applyEditors() {
        try {
          doApplyEditors();

          final FolderConfiguration config = myDeviceConfiguratorPanel.getConfiguration();
          myErrorLabel.setText("");
          myDirectoryNameField.setText(config.getFolderName(ResourceFolderType.VALUES));
        }
        catch (InvalidOptionValueException e) {
          myErrorLabel.setText("<html><body><font color=\"red\">" + e.getMessage() + "</font></body></html>");
          myDirectoryNameField.setText(AndroidConstants.FD_RES_VALUES);
        }
      }
    };
    myDeviceConfigurationWrapper.add(myDeviceConfiguratorPanel, BorderLayout.CENTER);

    final String defaultResFileName = AndroidResourceUtil.getDefaultResourceFileName(resourceType.getName());
    if (defaultResFileName != null) {
      myFileNameField.setText(defaultResFileName);
    }
    myDirectoryNameField.setText(AndroidConstants.FD_RES_VALUES);
    myDeviceConfiguratorPanel.updateAll();

    init();
  }

  @Override
  protected ValidationInfo doValidate() {
    final String resourceName = getResourceName();
    final Module selectedModule = getModule();
    final String directoryName = getDirectoryName();
    final String fileName = getFileName();

    if (resourceName.length() == 0) {
      return new ValidationInfo("specify resource name", myNameField);
    }
    else if (!AndroidResourceUtil.isCorrectAndroidResourceName(resourceName)) {
      return new ValidationInfo(resourceName + " is not correct resource name", myNameField);
    }
    else if (fileName.length() == 0) {
      return new ValidationInfo("specify file name", myFileNameField);
    }
    else if (selectedModule == null) {
      return new ValidationInfo("specify module", myModuleCombo);
    }
    else if (!ResourceFolderType.VALUES.getName().equals(
      AndroidCommonUtils.getResourceTypeByDirName(directoryName))) {
      return new ValidationInfo("directory name is not appropriate for value resources");
    }

    final ValidationInfo info = checkIfResourceAlreadyExists(selectedModule, resourceName, myResourceType, directoryName, fileName);
    if (info != null) {
      return info;
    }

    try {
      myDeviceConfiguratorPanel.doApplyEditors();
    }
    catch (InvalidOptionValueException e) {
      return new ValidationInfo("fix errors in configuration editor");
    }

    return null;
  }

  @Nullable
  private static ValidationInfo checkIfResourceAlreadyExists(@NotNull Module selectedModule,
                                                             @NotNull String resourceName,
                                                             @NotNull ResourceType resourceType,
                                                             @NotNull String directoryName,
                                                             @NotNull String fileName) {
    if (resourceName.length() == 0 ||
        directoryName.length() == 0 ||
        fileName.length() == 0) {
      return null;
    }

    final AndroidFacet facet = AndroidFacet.getInstance(selectedModule);
    final VirtualFile resourceDir = facet != null ? AndroidRootUtil.getResourceDir(facet) : null;
    if (resourceDir == null) {
      return null;
    }

    final VirtualFile resourceSubdir = resourceDir.findChild(directoryName);
    if (resourceSubdir == null) {
      return null;
    }

    final VirtualFile resFile = resourceSubdir.findChild(fileName);
    if (resFile == null) {
      return null;
    }

    if (resFile.getFileType() != StdFileTypes.XML) {
      return new ValidationInfo("File " + FileUtil.toSystemDependentName(resFile.getPath()) + " is not XML file");
    }

    final Resources resources = AndroidUtils.loadDomElement(selectedModule, resFile, Resources.class);
    if (resources == null) {
      return new ValidationInfo(AndroidBundle.message("not.resource.file.error", FileUtil.toSystemDependentName(resFile.getPath())));
    }

    for (ResourceElement element : AndroidResourceUtil.getValueResourcesFromElement(resourceType.getName(), resources)) {
      if (resourceName.equals(element.getName().getValue())) {
        return new ValidationInfo("resource '" + resourceName + "' already exists in " + FileUtil.toSystemDependentName(
          resFile.getPath()));
      }
    }
    return null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  @Override
  protected void doOKAction() {
    final String resourceName = getResourceName();
    final String fileName = getFileName();
    final String dirName = getDirectoryName();
    final Module module = getModule();

    if (resourceName.length() == 0) {
      Messages.showErrorDialog(myPanel, "Resource name is not specified", CommonBundle.getErrorTitle());
    }
    else if (!AndroidResourceUtil.isCorrectAndroidResourceName(resourceName)) {
      Messages.showErrorDialog(myPanel, resourceName + " is not correct resource name", CommonBundle.getErrorTitle());
    }
    else if (fileName.length() == 0) {
      Messages.showErrorDialog(myPanel, "File name is not specified", CommonBundle.getErrorTitle());
    }
    else if (dirName.length() == 0) {
      Messages.showErrorDialog(myPanel, "Directory name is not specified", CommonBundle.getErrorTitle());
    }
    else if (module == null) {
      Messages.showErrorDialog(myPanel, "Module is not specified", CommonBundle.getErrorTitle());
    }
    else {
      super.doOKAction();
    }
  }

  @NotNull
  public String getResourceName() {
    return myNameField.getText().trim();
  }

  @NotNull
  public String getDirectoryName() {
    return myDirectoryNameField.getText().trim();
  }

  @NotNull
  public String getFileName() {
    return myFileNameField.getText().trim();
  }

  @Nullable
  public Module getModule() {
    return myModule != null ? myModule : (Module)myModuleCombo.getSelectedItem();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }
}

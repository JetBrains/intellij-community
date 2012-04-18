package org.jetbrains.android.actions;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.intellij.CommonBundle;
import com.intellij.ide.ui.ListCellRendererWrapper;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileChooser.actions.VirtualFileDeleteProvider;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.resources.Resources;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class CreateXmlResourceDialog extends DialogWrapper {
  private JPanel myPanel;
  private JTextField myNameField;
  private JComboBox myModuleCombo;
  private JBLabel myModuleLabel;
  private JTextField myFileNameField;
  private JPanel myDirectoriesPanel;
  private JBLabel myDirectoriesLabel;

  private final Module myModule;
  private final ResourceType myResourceType;

  private Map<String, JCheckBox> myCheckBoxes = Collections.emptyMap();
  private String[] myDirNames = ArrayUtil.EMPTY_STRING_ARRAY;

  private final CheckBoxList myDirectoriesList;
  private VirtualFile myResourceDir;

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

    final String defaultResFileName = AndroidResourceUtil.getDefaultResourceFileName(resourceType.getName());
    if (defaultResFileName != null) {
      myFileNameField.setText(defaultResFileName);
    }


    myDirectoriesList = new CheckBoxList();
    myDirectoriesLabel.setLabelFor(myDirectoriesList);
    final ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myDirectoriesList);

    decorator.setEditAction(null);
    decorator.disableUpDownActions();

    decorator.setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        doAddNewDirectory();
      }
    });

    decorator.setRemoveAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        doDeleteDirectory();
      }
    });

    final AnActionButton selectAll = new AnActionButton("Select All", null, PlatformIcons.SELECT_ALL_ICON) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        doSelectAllDirs();
      }
    };
    decorator.addExtraAction(selectAll);

    final AnActionButton unselectAll = new AnActionButton("Unselect All", null, PlatformIcons.UNSELECT_ALL_ICON) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        doUnselectAllDirs();
      }
    };
    decorator.addExtraAction(unselectAll);

    myDirectoriesPanel.add(decorator.createPanel());

    updateDirectories();

    myModuleCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateDirectories();
      }
    });

    init();
  }

  private void doDeleteDirectory() {
    if (myResourceDir == null) {
      return;
    }

    final int selectedIndex = myDirectoriesList.getSelectedIndex();
    if (selectedIndex < 0) {
      return;
    }

    final String selectedDirName = myDirNames[selectedIndex];
    final VirtualFile selectedDir = myResourceDir.findChild(selectedDirName);
    if (selectedDir == null) {
      return;
    }

    final VirtualFileDeleteProvider provider = new VirtualFileDeleteProvider();
    provider.deleteElement(new DataContext() {
      @Override
      public Object getData(@NonNls String dataId) {
        if (PlatformDataKeys.VIRTUAL_FILE_ARRAY.getName().equals(dataId)) {
          return new VirtualFile[] {selectedDir};
        }
        else {
          return null;
        }
      }
    });
    updateDirectories();
  }

  private void doSelectAllDirs() {
    for (JCheckBox checkBox : myCheckBoxes.values()) {
      checkBox.setSelected(true);
    }
    myDirectoriesList.repaint();
  }

  private void doUnselectAllDirs() {
    for (JCheckBox checkBox : myCheckBoxes.values()) {
      checkBox.setSelected(false);
    }
    myDirectoriesList.repaint();
  }

  private void doAddNewDirectory() {
    if (myResourceDir == null) {
      return;
    }
    final Module module = getModule();
    if (module == null) {
      return;
    }
    final Project project = module.getProject();
    final PsiDirectory psiResDir = PsiManager.getInstance(project).findDirectory(myResourceDir);
    
    if (psiResDir != null) {
      final PsiElement[] createdElements = new CreateResourceDirectoryAction(ResourceFolderType.VALUES).invokeDialog(project, psiResDir);
      
      if (createdElements.length > 0) {
        updateDirectories();
      }
    }
  }

  private void updateDirectories() {
    final Module module = getModule();
    List<VirtualFile> valuesDirs = Collections.emptyList();

    if (module != null) {
      final AndroidFacet facet = AndroidFacet.getInstance(module);

      if (facet != null) {
        myResourceDir = AndroidRootUtil.getResourceDir(facet);

        if (myResourceDir != null) {
          valuesDirs = AndroidResourceUtil.getResourceSubdirs(ResourceFolderType.VALUES.getName(), new VirtualFile[]{myResourceDir});
        }
      }
    }

    Collections.sort(valuesDirs, new Comparator<VirtualFile>() {
      @Override
      public int compare(VirtualFile f1, VirtualFile f2) {
        return f1.getName().compareTo(f2.getName());
      }
    });

    final Map<String, JCheckBox> oldCheckBoxes = myCheckBoxes;
    final int selectedIndex = myDirectoriesList.getSelectedIndex();
    final String selectedDirName = selectedIndex >= 0 ? myDirNames[selectedIndex] : null;

    final List<JCheckBox> checkBoxList = new ArrayList<JCheckBox>();
    myCheckBoxes = new HashMap<String, JCheckBox>();
    myDirNames = new String[valuesDirs.size()];
    
    int newSelectedIndex = -1;
    
    int i = 0;
    
    for (VirtualFile dir : valuesDirs) {
      final String dirName = dir.getName();
      final JCheckBox oldCheckBox = oldCheckBoxes.get(dirName);
      final boolean selected = oldCheckBox != null && oldCheckBox.isSelected();
      final JCheckBox checkBox = new JCheckBox(dirName, selected);
      checkBoxList.add(checkBox);
      myCheckBoxes.put(dirName, checkBox);
      myDirNames[i] = dirName;
      
      if (dirName.equals(selectedDirName)) {
        newSelectedIndex = i; 
      }
      i++;
    }
    myDirectoriesList.setModel(new CollectionListModel<JCheckBox>(checkBoxList));
    
    if (newSelectedIndex >= 0) {
      myDirectoriesList.setSelectedIndex(newSelectedIndex);
    }

    if (checkBoxList.size() == 1) {
      checkBoxList.get(0).setSelected(true);
    }
  }

  @Override
  protected ValidationInfo doValidate() {
    final String resourceName = getResourceName();
    final Module selectedModule = getModule();
    final List<String> directoryNames = getDirNames();
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
    else if (directoryNames.size() == 0) {
      return new ValidationInfo("choose directories", myDirectoriesList);
    }

    final ValidationInfo info = checkIfResourceAlreadyExists(selectedModule, resourceName, myResourceType, directoryNames, fileName);
    if (info != null) {
      return info;
    }
    return null;
  }

  @Nullable
  private static ValidationInfo checkIfResourceAlreadyExists(@NotNull Module selectedModule,
                                                             @NotNull String resourceName,
                                                             @NotNull ResourceType resourceType,
                                                             @NotNull List<String> dirNames,
                                                             @NotNull String fileName) {
    if (resourceName.length() == 0 ||
        dirNames.size() == 0 ||
        fileName.length() == 0) {
      return null;
    }

    final AndroidFacet facet = AndroidFacet.getInstance(selectedModule);
    final VirtualFile resourceDir = facet != null ? AndroidRootUtil.getResourceDir(facet) : null;
    if (resourceDir == null) {
      return null;
    }

    for (String directoryName : dirNames) {
      final VirtualFile resourceSubdir = resourceDir.findChild(directoryName);
      if (resourceSubdir == null) {
        continue;
      }

      final VirtualFile resFile = resourceSubdir.findChild(fileName);
      if (resFile == null) {
        continue;
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
    final List<String> dirNames = getDirNames();
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
    else if (dirNames.size() == 0) {
      Messages.showErrorDialog(myPanel, "Directories are not selected", CommonBundle.getErrorTitle());
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
  public List<String> getDirNames() {
    final List<String> selectedDirs = new ArrayList<String>();

    for (Map.Entry<String, JCheckBox> entry : myCheckBoxes.entrySet()) {
      if (entry.getValue().isSelected()) {
        selectedDirs.add(entry.getKey());
      }
    }
    return selectedDirs;
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

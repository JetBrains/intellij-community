/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.android.actions;

import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.intellij.CommonBundle;
import com.intellij.ide.actions.TemplateKindCombo;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.DeviceConfiguratorPanel;
import org.jetbrains.android.uipreview.InvalidOptionValueException;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.android.util.ModuleListCellRendererWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 22, 2009
 * Time: 7:47:01 PM
 * To change this template use File | Settings | File Templates.
 */
public class CreateResourceFileDialog extends DialogWrapper {
  private JTextField myFileNameField;
  private TemplateKindCombo myResourceTypeCombo;
  private JPanel myPanel;
  private JLabel myUpDownHint;
  private JLabel myResTypeLabel;
  private JPanel myDeviceConfiguratorWrapper;
  private JBLabel myErrorLabel;
  private JTextField myDirectoryNameTextField;
  private JPanel myRootElementFieldWrapper;
  private JBLabel myRootElementLabel;
  private JLabel myFileNameLabel;
  private JComboBox myModuleCombo;
  private JBLabel myModuleLabel;
  private TextFieldWithAutoCompletion<String> myRootElementField;
  private InputValidator myValidator;

  private final Map<String, CreateTypedResourceFileAction> myResType2ActionMap = new HashMap<String, CreateTypedResourceFileAction>();
  private final DeviceConfiguratorPanel myDeviceConfiguratorPanel;
  private final AndroidFacet myFacet;
  private final ResourceType myPredefinedResourceType;

  public CreateResourceFileDialog(@NotNull AndroidFacet facet,
                                  Collection<CreateTypedResourceFileAction> actions,
                                  @Nullable ResourceType predefinedResourceType,
                                  @Nullable String predefinedFileName,
                                  @Nullable String predefinedRootElement,
                                  @Nullable FolderConfiguration predefinedConfig,
                                  boolean chooseFileName,
                                  @NotNull Module module,
                                  boolean chooseModule) {
    super(facet.getModule().getProject());
    myFacet = facet;
    myPredefinedResourceType = predefinedResourceType;

    myResTypeLabel.setLabelFor(myResourceTypeCombo);
    myResourceTypeCombo.registerUpDownHint(myFileNameField);
    myUpDownHint.setIcon(PlatformIcons.UP_DOWN_ARROWS);
    CreateTypedResourceFileAction[] actionArray = actions.toArray(new CreateTypedResourceFileAction[actions.size()]);

    Arrays.sort(actionArray, new Comparator<CreateTypedResourceFileAction>() {
      @Override
      public int compare(CreateTypedResourceFileAction a1, CreateTypedResourceFileAction a2) {
        return a1.toString().compareTo(a2.toString());
      }
    });
    String selectedTemplate = null;

    for (CreateTypedResourceFileAction action : actionArray) {
      String resType = action.getResourceType();
      assert !myResType2ActionMap.containsKey(resType);
      myResType2ActionMap.put(resType, action);
      myResourceTypeCombo.addItem(action.toString(), null, resType);

      if (predefinedResourceType != null && predefinedResourceType.getName().equals(resType)) {
        selectedTemplate = resType;
      }
    }

    myDeviceConfiguratorPanel = new DeviceConfiguratorPanel() {
      @Override
      public void applyEditors() {
        try {
          doApplyEditors();
          final FolderConfiguration config = myDeviceConfiguratorPanel.getConfiguration();
          final CreateTypedResourceFileAction selectedAction = getSelectedAction();
          myErrorLabel.setText("");
          myDirectoryNameTextField.setText("");
          if (selectedAction != null) {
            final String resTypeStr = selectedAction.getResourceType();
            if (resTypeStr != null) {
              final ResourceFolderType resFolderType = ResourceFolderType.getTypeByName(resTypeStr);
              if (resFolderType != null) {
                myDirectoryNameTextField.setText(config.getFolderName(resFolderType));
              }
            }
          }
        }
        catch (InvalidOptionValueException e) {
          myErrorLabel.setText("<html><body><font color=\"red\">" + e.getMessage() + "</font></body></html>");
          myDirectoryNameTextField.setText("");
        }
        setOKActionEnabled(myDirectoryNameTextField.getText().length() > 0);
      }
    };
    if (predefinedConfig != null) {
      myDeviceConfiguratorPanel.init(predefinedConfig);
    }

    myResourceTypeCombo.getComboBox().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myDeviceConfiguratorPanel.applyEditors();
        updateRootElementTextField();
      }
    });

    if (predefinedResourceType != null && selectedTemplate != null) {
      final boolean v = predefinedResourceType == ResourceType.LAYOUT;
      myRootElementLabel.setVisible(v);
      myRootElementFieldWrapper.setVisible(v);

      myResTypeLabel.setVisible(false);
      myResourceTypeCombo.setVisible(false);
      myUpDownHint.setVisible(false);
      myResourceTypeCombo.setSelectedName(selectedTemplate);
    }

    if (predefinedFileName != null) {
      if (!chooseFileName) {
        myFileNameField.setVisible(false);
        myFileNameLabel.setVisible(false);
      }
      myFileNameField.setText(predefinedFileName);
    }

    final Set<Module> modulesSet = new HashSet<Module>();
    modulesSet.add(module);
    for (AndroidFacet depFacet : AndroidUtils.getAllAndroidDependencies(module, true)) {
      modulesSet.add(depFacet.getModule());
    }

    final Module[] modules = modulesSet.toArray(new Module[modulesSet.size()]);
    Arrays.sort(modules, new Comparator<Module>() {
      @Override
      public int compare(Module m1, Module m2) {
        return m1.getName().compareTo(m2.getName());
      }
    });
    myModuleCombo.setModel(new DefaultComboBoxModel(modules));

    if (!chooseModule || modules.length == 1) {
      myModuleLabel.setVisible(false);
      myModuleCombo.setVisible(false);
    }
    myModuleCombo.setRenderer(new ModuleListCellRendererWrapper(myModuleCombo.getRenderer()));
    myModuleCombo.setSelectedItem(module);

    myDeviceConfiguratorPanel.updateAll();
    myDeviceConfiguratorWrapper.add(myDeviceConfiguratorPanel, BorderLayout.CENTER);
    setOKActionEnabled(myDirectoryNameTextField.getText().length() > 0);
    updateRootElementTextField();

    if (predefinedRootElement != null) {
      myRootElementLabel.setVisible(false);
      myRootElementFieldWrapper.setVisible(false);
      myRootElementField.setText(predefinedRootElement);
    }
    init();

    setTitle(AndroidBundle.message("new.resource.dialog.title"));
  }

  private void updateRootElementTextField() {
    final CreateTypedResourceFileAction action = getSelectedAction();

    if (action != null) {
      final List<String> allowedTagNames = action.getSortedAllowedTagNames(myFacet);
      myRootElementField = new TextFieldWithAutoCompletion<String>(
        myFacet.getModule().getProject(), new TextFieldWithAutoCompletion.StringsCompletionProvider(allowedTagNames, null), true, null);
      myRootElementField.setEnabled(allowedTagNames.size() > 1);
      myRootElementField.setText(!action.isChooseTagName() && myPredefinedResourceType != ResourceType.LAYOUT
                                 ? action.getDefaultRootTag()
                                 : "");
      myRootElementFieldWrapper.removeAll();
      myRootElementFieldWrapper.add(myRootElementField, BorderLayout.CENTER);
      myRootElementLabel.setLabelFor(myRootElementField);
    }
  }

  @NotNull
  public String getFileName() {
    return myFileNameField.getText().trim();
  }

  private static boolean containsElement(@NotNull ListModel model, @NotNull Object objectToFind) {
    for (int i = 0, n = model.getSize(); i < n; i++) {
      if (objectToFind.equals(model.getElementAt(i))) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  protected InputValidator createValidator(@NotNull String subdirName) {
    return null;
  }

  @Override
  protected void doOKAction() {
    String fileName = myFileNameField.getText().trim();
    final CreateTypedResourceFileAction action = getSelectedAction();
    assert action != null;

    if (fileName.length() == 0) {
      Messages.showErrorDialog(myPanel, AndroidBundle.message("file.name.not.specified.error"), CommonBundle.getErrorTitle());
      return;
    }

    if (!action.isChooseTagName() && getRootElement().length() == 0) {
      Messages.showErrorDialog(myPanel, AndroidBundle.message("root.element.not.specified.error"), CommonBundle.getErrorTitle());
      return;
    }

    final String subdirName = getSubdirName();
    if (subdirName.length() == 0) {
      Messages.showErrorDialog(myPanel, AndroidBundle.message("directory.not.specified.error"), CommonBundle.getErrorTitle());
      return;
    }

    final String errorMessage = AndroidResourceUtil.getInvalidResourceFileNameMessage(fileName);
    if (errorMessage != null) {
      Messages.showErrorDialog(myPanel, errorMessage, CommonBundle.getErrorTitle());
      return;
    }
    myValidator = createValidator(subdirName);
    if (myValidator == null || myValidator.checkInput(fileName) && myValidator.canClose(fileName)) {
      super.doOKAction();
    }
  }

  @Override
  protected String getDimensionServiceKey() {
    return "AndroidCreateResourceFileDialog";
  }

  @NotNull
  public Module getSelectedModule() {
    return (Module)myModuleCombo.getSelectedItem();
  }

  @NotNull
  public String getSubdirName() {
    return myDirectoryNameTextField.getText().trim();
  }

  @NotNull
  protected String getRootElement() {
    final String item = myRootElementField.getText().trim();
    return item != null ? item.trim() : "";
  }

  public InputValidator getValidator() {
    return myValidator;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    if (myFileNameField.getText().length() == 0) {
      return myFileNameField;
    }
    else if (myResourceTypeCombo.isVisible()) {
      return myResourceTypeCombo;
    }
    else if (myModuleCombo.isVisible()) {
      return myModuleCombo;
    }
    else if (myRootElementFieldWrapper.isVisible()) {
      return myRootElementField;
    }
    return myDirectoryNameTextField;
  }

  public CreateTypedResourceFileAction getSelectedAction() {
    return myResType2ActionMap.get(myResourceTypeCombo.getSelectedName());
  }
}

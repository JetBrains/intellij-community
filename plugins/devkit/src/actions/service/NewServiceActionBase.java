/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.actions.service;

import com.intellij.ide.IdeView;
import com.intellij.ide.actions.CreateInDirectoryActionBase;
import com.intellij.ide.actions.ElementCreator;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.RunResult;
import com.intellij.openapi.application.WriteActionAware;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import com.intellij.xml.util.IncludedXmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.actions.DevkitActionsUtil;
import org.jetbrains.idea.devkit.dom.Extensions;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.util.DescriptorUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * An base class for actions generating service classes (implementation and optionally interface) and registering new service in plugin.xml.
 */
abstract class NewServiceActionBase extends CreateInDirectoryActionBase implements WriteActionAware {
  NewServiceActionBase(String text, String description) {
    super(text, description, null);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public final void actionPerformed(AnActionEvent e) {
    IdeView view = e.getData(LangDataKeys.IDE_VIEW);
    if (view == null) {
      return;
    }

    Project project = e.getProject();

    PsiDirectory dir = view.getOrChooseDirectory();
    if (dir == null) return;

    ServiceCreator serviceCreator = new ServiceCreator(dir, getInterfaceTemplateName(), getImplementationTemplateName(),
                                                       getOnlyImplementationTemplateName(), getTagName());
    PsiClass[] createdClasses = invokeDialog(project, serviceCreator, dir);
    if (createdClasses == null) {
      return;
    }

    for (PsiClass createdClass : createdClasses) {
      view.selectElement(createdClass);
    }
  }

  @Nullable
  private PsiClass[] invokeDialog(Project project, ServiceCreator serviceCreator, PsiDirectory dir) {
    DialogWrapper dialog = new NewServiceDialog(project, serviceCreator, dir);
    dialog.show();
    return serviceCreator.getCreatedClasses();
  }

  protected abstract String getTagName();

  protected abstract String getOnlyImplementationTemplateName();
  protected abstract String getInterfaceTemplateName();
  protected abstract String getImplementationTemplateName();

  protected abstract String getDialogTitle();


  private class NewServiceDialog extends DialogWrapper {
    private final Project myProject;
    private final ServiceCreator myServiceCreator;
    private final PsiDirectory myDirectory;

    private JPanel myTopPanel;

    private JTextField myServiceNameTextField;
    private JCheckBox mySeparateServiceInterfaceCheckbox;
    private JTextField myServiceImplementationTextField;
    private JLabel myServiceNameLabel;

    private boolean myAdjusting = false;
    private boolean myNeedAdjust = true;

    NewServiceDialog(@Nullable Project project, ServiceCreator serviceCreator, PsiDirectory directory) {
      super(project);
      myProject = project;

      setTitle(getDialogTitle());

      myServiceCreator = serviceCreator;
      myDirectory = directory;

      mySeparateServiceInterfaceCheckbox.addActionListener(e -> {
        if (isSeparateMode()) {
          myServiceImplementationTextField.setEnabled(true);
          myServiceNameLabel.setText(DevKitBundle.message("new.service.dialog.interface"));
        } else {
          myServiceImplementationTextField.setEnabled(false);
          myServiceNameLabel.setText(DevKitBundle.message("new.service.dialog.class"));
        }
        adjustServiceImplementationTextField();
      });

      myServiceNameTextField.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(DocumentEvent e) {
          adjustServiceImplementationTextField();
        }
      });
      myServiceImplementationTextField.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(DocumentEvent e) {
          if (!myAdjusting) {
            myNeedAdjust = false;
          }
        }
      });

      init();
    }

    @NotNull
    @Override
    protected List<ValidationInfo> doValidateAll() {
      boolean nameValid = isServiceNameValid();
      if (!isSeparateMode()) {
        if (nameValid) {
          return Collections.emptyList();
        }
        return Collections.singletonList(
          new ValidationInfo(DevKitBundle.message("new.service.dialog.invalid.name"), myServiceNameTextField));
      }

      // separate mode
      boolean implementationValid = isServiceImplementationValid();
      if (nameValid && implementationValid) {
        return Collections.emptyList();
      }
      List<ValidationInfo> result = new ArrayList<>();
      if (!nameValid) {
        result.add(new ValidationInfo(
          DevKitBundle.message("new.service.dialog.invalid.interface"), myServiceNameTextField));
      }
      if (!implementationValid) {
        result.add(new ValidationInfo(
          DevKitBundle.message("new.service.dialog.invalid.implementation"), myServiceImplementationTextField));
      }
      return result;
    }

    private boolean isServiceImplementationValid() {
      return validateNameField(myServiceImplementationTextField);
    }

    private boolean isServiceNameValid() {
      return validateNameField(myServiceNameTextField);
    }

    private boolean validateNameField(JTextField field) {
      String text = field.getText();
      return text.length() > 0 && PsiNameHelper.getInstance(myProject).isQualifiedName(text);
    }

    private void adjustServiceImplementationTextField() {
      if (!isSeparateMode()) {
        myAdjusting = true;
        myServiceImplementationTextField.setText("");
        myAdjusting = false;
      } else if (myNeedAdjust) {
        myAdjusting = true;
        myServiceImplementationTextField.setText("impl." + myServiceNameTextField.getText() + "Impl");
        myAdjusting = false;
      }
    }

    private boolean isSeparateMode() {
      return mySeparateServiceInterfaceCheckbox.isSelected();
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return myServiceNameTextField;
    }

    @Override
    protected void doOKAction() {
      if (!getOKAction().isEnabled()) {
        return;
      }

      XmlFile pluginDescriptorToPatch = DevkitActionsUtil.choosePluginModuleDescriptor(myDirectory);
      if (pluginDescriptorToPatch == null) {
        return; // canceled
      }

      if (isSeparateMode()) {
        String serviceInterface = myServiceNameTextField.getText().trim();
        String serviceImplementation = myServiceImplementationTextField.getText().trim();
        if (myServiceCreator.createInterfaceAndImplementation(serviceInterface, serviceImplementation, pluginDescriptorToPatch)) {
          close(OK_EXIT_CODE);
        }
      }
      else {
        String serviceOnlyImplementation = myServiceNameTextField.getText().trim();
        if (myServiceCreator.createOnlyImplementation(serviceOnlyImplementation, pluginDescriptorToPatch)) {
          close(OK_EXIT_CODE);
        }
      }
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      return myTopPanel;
    }
  }


  static class ServiceCreator { // not private for testing purpose only
    private static final Logger LOG = Logger.getInstance(ServiceCreator.class);
    private static final String INTERFACE_NAME_PROPERTY = "INTERFACE_NAME";
    private static final String INTERFACE_PACKAGE_PROPERTY = "INTERFACE_PACKAGE_NAME";

    private final PsiDirectory myDirectory;
    private final String myServiceInterfaceTemplateName;
    private final String myServiceImplementationTemplateName;
    private final String myServiceOnlyImplementationTemplateName;
    private final String myTagName;

    private PsiClass[] createdClasses = null;

    ServiceCreator(PsiDirectory directory,
                   String serviceInterfaceTemplateName,
                   String serviceImplementationTemplateName,
                   String serviceOnlyImplementationTemplateName,
                   String tagName) {
      myDirectory = directory;
      myServiceInterfaceTemplateName = serviceInterfaceTemplateName;
      myServiceImplementationTemplateName = serviceImplementationTemplateName;
      myServiceOnlyImplementationTemplateName = serviceOnlyImplementationTemplateName;
      myTagName = tagName;
    }

    PsiClass[] getCreatedClasses() {
      return createdClasses;
    }

    /**
     * @return whether the service was created (which indicates whether the create service dialog can be closed).
     */
    boolean createInterfaceAndImplementation(String interfaceName, String implementationName, XmlFile pluginXml) {
      return doCreateService(() -> {
        PsiClass createdInterface = DevkitActionsUtil.createSingleClass(interfaceName, myServiceInterfaceTemplateName, myDirectory);

        String interfaceShortName = createdInterface.getName();
        String implementationDirRelativePackage = StringUtil.getPackageName(implementationName);
        String interfacePackage;
        if (implementationDirRelativePackage.isEmpty()) {
          interfacePackage = ""; // interface and implementation are placed in the same package; there shouldn't be an import statement
        } else {
          //noinspection ConstantConditions
          interfacePackage = StringUtil.getPackageName(createdInterface.getQualifiedName());
        }

        PsiClass createdImplementation = DevkitActionsUtil.createSingleClass(
          implementationName, myServiceImplementationTemplateName, myDirectory,
          ContainerUtil.stringMap(INTERFACE_NAME_PROPERTY, interfaceShortName, INTERFACE_PACKAGE_PROPERTY, interfacePackage));

        patchPluginXml(createdInterface, createdImplementation, pluginXml);

        createdClasses = new PsiClass[]{createdInterface, createdImplementation};
        return true;
      });
    }

    /**
     * @return whether the service was created (which indicates whether the create service dialog can be closed).
     */
    boolean createOnlyImplementation(String onlyImplementationName, XmlFile pluginXml) {
      return doCreateService(() -> {
        PsiClass createdOnlyImplementation = DevkitActionsUtil.createSingleClass(
          onlyImplementationName, myServiceOnlyImplementationTemplateName, myDirectory);

        patchPluginXml(null, createdOnlyImplementation, pluginXml);

        createdClasses = new PsiClass[]{createdOnlyImplementation};
        return true;
      });
    }

    private boolean doCreateService(Callable<Boolean> action) {
      RunResult<Boolean> result = new WriteCommandAction<Boolean>(getProject(), DevKitBundle.message("new.service.class.action.name")) {
        @Override
        protected void run(@NotNull Result<Boolean> result) throws Throwable {
          result.setResult(action.call());
        }

        @Override
        protected UndoConfirmationPolicy getUndoConfirmationPolicy() {
          return UndoConfirmationPolicy.REQUEST_CONFIRMATION;
        }
      }.execute();

      if (result.hasException()) {
        handleException(result.getThrowable());
        return false;
      }

      return result.getResultObject();
    }

    private void patchPluginXml(@Nullable PsiClass createdInterface, @NotNull PsiClass createdImplementation, XmlFile pluginXml) {
      DescriptorUtil.checkPluginXmlsWritable(getProject(), pluginXml);

      DomFileElement<IdeaPlugin> fileElement = DomManager.getDomManager(getProject()).getFileElement(pluginXml, IdeaPlugin.class);
      if (fileElement == null) {
        throw new IncorrectOperationException(DevKitBundle.message("error.cannot.process.plugin.xml", pluginXml));
      }

      IdeaPlugin ideaPlugin = fileElement.getRootElement();
      Extensions targetExtensions = ideaPlugin.getExtensions().stream()
        .filter(extensions -> !(extensions.getXmlTag() instanceof IncludedXmlTag))
        .filter(extensions -> Extensions.DEFAULT_PREFIX.equals(extensions.getDefaultExtensionNs().getStringValue()))
        .findAny()
        .orElseGet(() -> ideaPlugin.addExtensions());

      XmlTag serviceTag = targetExtensions.addExtension(Extensions.DEFAULT_PREFIX + "." + myTagName).getXmlTag();
      if (createdInterface != null) {
        serviceTag.setAttribute("serviceInterface", createdInterface.getQualifiedName());
      }
      serviceTag.setAttribute("serviceImplementation", createdImplementation.getQualifiedName());
    }

    private void handleException(Throwable t) {
      LOG.info(t);
      String errorMessage = ElementCreator.getErrorMessage(t);
      Messages.showMessageDialog(
        getProject(), errorMessage, DevKitBundle.message("error.cannot.create.service.class"), Messages.getErrorIcon());
    }

    private Project getProject() {
      return myDirectory.getProject();
    }
  }
}

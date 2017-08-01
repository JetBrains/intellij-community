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
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.WriteActionAware;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.IncorrectOperationException;
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
import java.util.HashSet;
import java.util.Set;

public abstract class GenerateServiceClassAndPatchPluginXmlActionBase extends CreateInDirectoryActionBase implements WriteActionAware {
  private final Set<XmlFile> myFilesToPatch = new HashSet<>();

  public GenerateServiceClassAndPatchPluginXmlActionBase(String text, String description) {
    super(text, description, null);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }


  private void patchPluginXmls(@NotNull XmlFile[] pluginXmls, @Nullable PsiClass serviceInterface,
                               @NotNull PsiClass serviceImplementation) {
    DescriptorUtil.checkPluginXmlsWritable(serviceImplementation.getProject(), pluginXmls);
    WriteCommandAction.runWriteCommandAction(serviceImplementation.getProject(),
                                             DevKitBundle.message("new.service.patch.plugin.xml.action.name"), null, () -> {
        for (XmlFile pluginXml : pluginXmls) {
          patchPluginXml(pluginXml, serviceInterface, serviceImplementation);
        }
      });
  }

  private void patchPluginXml(XmlFile pluginXml, @Nullable PsiClass serviceInterface, @NotNull PsiClass serviceImplementation) {
    DomFileElement<IdeaPlugin> fileElement = DomManager.getDomManager(pluginXml.getProject()).getFileElement(pluginXml, IdeaPlugin.class);
    if (fileElement == null) {
      throw new IncorrectOperationException(DevKitBundle.message("error.cannot.process.plugin.xml", pluginXml));
    }

    IdeaPlugin ideaPlugin = fileElement.getRootElement();
    Extensions targetExtensions = ideaPlugin.getExtensions().stream()
      .filter(extensions -> !(extensions instanceof IncludedXmlTag))
      .filter(extensions -> Extensions.DEFAULT_PREFIX.equals(extensions.getDefaultExtensionNs().getStringValue()))
      .findAny()
      .orElseGet(() -> ideaPlugin.addExtensions());

    XmlTag serviceTag = targetExtensions.addExtension(Extensions.DEFAULT_PREFIX + "." + getTagName()).getXmlTag();
    if (serviceInterface != null) {
      serviceTag.setAttribute("serviceInterface", serviceInterface.getQualifiedName());
    }
    serviceTag.setAttribute("serviceImplementation", serviceImplementation.getQualifiedName());
  }

  @Override
  public final void actionPerformed(final AnActionEvent e) {
    IdeView view = e.getData(LangDataKeys.IDE_VIEW);
    if (view == null) {
      return;
    }

    Project project = e.getProject();

    PsiDirectory dir = view.getOrChooseDirectory();
    if (dir == null) return;

    String errorTitle = DevKitBundle.message("error.cannot.create.service.class");
    MyServiceClassCreator onlyImplementationElementCreator = new MyServiceClassCreator(
      project, errorTitle, dir, getOnlyImplementationTemplateName());
    MyServiceClassCreator implementationElementCreator = new MyServiceClassCreator(
      project, errorTitle, dir, JavaTemplateUtil.INTERNAL_CLASS_TEMPLATE_NAME);
    MyServiceClassCreator interfaceElementCreator = new MyServiceClassCreator(project, errorTitle, dir, getInterfaceTemplateName());

    invokeDialog(project, onlyImplementationElementCreator, implementationElementCreator, interfaceElementCreator);
    PsiElement[] createdElements = handleCreatedElements(onlyImplementationElementCreator,
                                                         implementationElementCreator, interfaceElementCreator);
    for (PsiElement createdElement : createdElements) {
      view.selectElement(createdElement);
    }
  }

  private void invokeDialog(Project project, MyServiceClassCreator onlyImplementationElementCreator,
                            MyServiceClassCreator implementationElementCreator,
                            MyServiceClassCreator interfaceElementCreator) {
    DialogWrapper dialog = new NewServiceDialog(project, onlyImplementationElementCreator,
                                                interfaceElementCreator, implementationElementCreator);
    dialog.show();
  }

  @SuppressWarnings("ConstantConditions") //noinspection ConstantConditions - anonymous classes are not possible here, so no NPE
  @NotNull
  private PsiElement[] handleCreatedElements(MyServiceClassCreator onlyImplementationElementCreator,
                                             MyServiceClassCreator implementationElementCreator,
                                             MyServiceClassCreator interfaceElementCreator) {
    XmlFile[] pluginXmls = myFilesToPatch.toArray(new XmlFile[myFilesToPatch.size()]);
    PsiClass createdImplementation = implementationElementCreator.getCreatedClass();
    PsiClass createdInterface = interfaceElementCreator.getCreatedClass();
    boolean separatedInterface = createdImplementation != null && createdInterface != null;
    if (separatedInterface) {
      Project project = createdImplementation.getProject();
      WriteCommandAction.runWriteCommandAction(project, DevKitBundle.message("new.service.adding.interface.to.class"), null, () -> {
        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        PsiElementFactory factory = facade.getElementFactory();
        PsiJavaCodeReferenceElement interfaceReference =
          factory.createReferenceElementByFQClassName(createdInterface.getQualifiedName(), createdImplementation.getResolveScope());
        createdImplementation.getImplementsList().add(interfaceReference);
      });
      patchPluginXmls(pluginXmls, createdInterface, createdImplementation);
      return new PsiElement[]{createdInterface, createdImplementation};
    }
    else {
      PsiClass createdOnlyImplementation = onlyImplementationElementCreator.getCreatedClass();
      if (createdOnlyImplementation != null) {
        patchPluginXmls(pluginXmls, null, createdOnlyImplementation);
        return new PsiElement[]{createdOnlyImplementation};
      }
      return PsiElement.EMPTY_ARRAY;
    }
  }

  protected abstract String getTagName();

  protected abstract String getOnlyImplementationTemplateName();
  protected abstract String getInterfaceTemplateName();

  protected abstract String getDialogTitle();


  private class NewServiceDialog extends DialogWrapper {
    private final MyServiceClassCreator myOnlyImplementationElementCreator;
    private final MyServiceClassCreator myInterfaceElementCreator;
    private final MyServiceClassCreator myImplementationElementCreator;

    private JPanel myTopPanel;

    private JTextField myServiceNameTextField;
    private JCheckBox mySeparateServiceInterfaceCheckbox;
    private JTextField myServiceImplementationTextField;
    private JLabel myServiceNameLabel;

    private boolean myAdjusting = false;
    private boolean myNeedAdjust = true;

    NewServiceDialog(@Nullable Project project,
                               MyServiceClassCreator onlyImplementationElementCreator,
                               MyServiceClassCreator interfaceElementCreator,
                               MyServiceClassCreator implementationElementCreator) {
      super(project);

      setOKActionEnabled(false);
      setTitle(getDialogTitle());

      myOnlyImplementationElementCreator = onlyImplementationElementCreator;
      myInterfaceElementCreator = interfaceElementCreator;
      myImplementationElementCreator = implementationElementCreator;

      mySeparateServiceInterfaceCheckbox.addActionListener(e -> {
        if (mySeparateServiceInterfaceCheckbox.isSelected()) {
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
          setOKActionEnabled(myServiceNameTextField.getText().length() > 0);
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

    private void adjustServiceImplementationTextField() {
      if (!mySeparateServiceInterfaceCheckbox.isSelected()) {
        myAdjusting = true;
        myServiceImplementationTextField.setText("");
        myAdjusting = false;
      } else if (myNeedAdjust) {
        myAdjusting = true;
        myServiceImplementationTextField.setText("impl." + myServiceNameTextField.getText() + "Impl");
        myAdjusting = false;
      }
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return myServiceNameTextField;
    }

    @Override
    protected void doOKAction() {
      if (mySeparateServiceInterfaceCheckbox.isSelected()) {
        // separated interface and implementation
        String serviceInterface = myServiceNameTextField.getText().trim();
        String serviceImplementation = myServiceImplementationTextField.getText().trim();
        if (myImplementationElementCreator.canClose(serviceImplementation) && myInterfaceElementCreator.canClose(serviceInterface)) {
          close(OK_EXIT_CODE);
        }
      } else {
        // only implementation
        String serviceImplementation = myServiceNameTextField.getText().trim();
        if (myOnlyImplementationElementCreator.canClose(serviceImplementation)) {
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

  private class MyServiceClassCreator extends ElementCreator {
    private final PsiDirectory myDirectory;
    private final String myClassTemplateName;

    private PsiClass myCreatedClass = null;

    MyServiceClassCreator(Project project, String errorTitle, PsiDirectory directory, String classTemplateName) {
      super(project, errorTitle);
      myDirectory = directory;
      myClassTemplateName = classTemplateName;
    }

    @NotNull
    protected PsiElement[] create(String newName) throws Exception {
      return DevkitActionsUtil.createSinglePluginClass(
        newName, myClassTemplateName, myDirectory, myFilesToPatch, getTemplatePresentation());
    }

    @Override
    protected String getActionName(String newName) {
      return DevKitBundle.message("new.service.class.action.name", newName);
    }

    public boolean canClose(String inputString) {
      PsiElement[] createdElements = tryCreate(inputString);
      if (createdElements.length > 0) {
        myCreatedClass = (PsiClass)createdElements[0]; // cast is safe since create() returns array of single PsiClass
        return true;
      } else {
        return false;
      }
    }

    @Nullable
    public PsiClass getCreatedClass() {
      return myCreatedClass;
    }
  }
}

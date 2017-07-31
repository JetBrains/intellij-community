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
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.WriteActionAware;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.actions.DevkitActionsUtil;
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


  private void patchPluginXmls(@NotNull XmlFile[] pluginXmls, @Nullable PsiClass serviceInterface, @NotNull PsiClass serviceImplementation) {
    DescriptorUtil.checkPluginXmlsWritable(serviceImplementation.getProject(), pluginXmls);
    WriteAction.run(() -> CommandProcessor.getInstance().runUndoTransparentAction(() -> {
        for (XmlFile pluginXml : pluginXmls) {
          patchPluginXml(pluginXml, serviceInterface, serviceImplementation);
        }
    }));
  }

  private void patchPluginXml(XmlFile pluginXml, @Nullable PsiClass serviceInterface, @NotNull PsiClass serviceImplementation) {
    XmlDocument document = pluginXml.getDocument();
    if (document == null) {
      // shouldn't happen (actions won't be visible when there's no plugin.xml)
      return;
    }

    XmlTag rootTag = document.getRootTag();
    if (rootTag != null && "idea-plugin".equals(rootTag.getName())) {
      XmlTag extensions = rootTag.findFirstSubTag("extensions");
      if (extensions == null || !extensions.isPhysical()) {
        extensions = rootTag.addSubTag(rootTag.createChildTag("extensions", rootTag.getNamespace(), null, false), false);
        extensions.setAttribute("defaultExtensionNs", "com.intellij");
      }

      XmlTag serviceTag = extensions.createChildTag(getTagName(), rootTag.getNamespace(), null, false);
      if (serviceInterface != null) {
        serviceTag.setAttribute("serviceInterface", serviceInterface.getQualifiedName());
      }
      serviceTag.setAttribute("serviceImplementation", serviceImplementation.getQualifiedName());
      extensions.addSubTag(serviceTag, false);
    }
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
      WriteAction.run(() -> CommandProcessor.getInstance().runUndoTransparentAction(() -> {
        JavaPsiFacade facade = JavaPsiFacade.getInstance(createdImplementation.getProject());
        PsiElementFactory factory = facade.getElementFactory();
        PsiJavaCodeReferenceElement interfaceReference =
          factory.createReferenceElementByFQClassName(createdInterface.getQualifiedName(), createdImplementation.getResolveScope());
        createdImplementation.getImplementsList().add(interfaceReference);
      }));

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

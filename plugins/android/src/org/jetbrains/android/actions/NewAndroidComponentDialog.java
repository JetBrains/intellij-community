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

import com.intellij.CommonBundle;
import com.intellij.ide.actions.ElementCreator;
import com.intellij.ide.actions.TemplateKindCombo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.android.AndroidFileTemplateProvider;
import org.jetbrains.android.dom.manifest.Action;
import org.jetbrains.android.dom.manifest.*;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Eugene.Kudelevsky
 */
public class NewAndroidComponentDialog extends DialogWrapper {
  private JPanel myPanel;
  private JLabel myKindLabel;
  private JTextField myNameField;
  private JLabel myUpDownHint;
  private TemplateKindCombo myKindCombo;
  private JTextField myLabelField;
  private JCheckBox myMarkAsStartupActivityCheckBox;

  private ElementCreator myCreator;

  private PsiElement[] myCreatedElements;

  protected NewAndroidComponentDialog(@NotNull final Module module, final PsiDirectory directory) {
    super(module.getProject());
    myKindLabel.setLabelFor(myKindCombo);
    myKindCombo.registerUpDownHint(myNameField);
    myUpDownHint.setIcon(Icons.UP_DOWN_ARROWS);
    myKindCombo.addItem(AndroidBundle.message("android.new.component.dialog.activity.item"), null, AndroidFileTemplateProvider.ACTIVITY);

    if (!containsCustomApplicationClass(module)) {
      myKindCombo.addItem(AndroidBundle.message("android.new.component.dialog.application.item"), null,
                          AndroidFileTemplateProvider.APPLICATION);
    }

    myKindCombo.addItem(AndroidBundle.message("android.new.component.dialog.service.item"), null, AndroidFileTemplateProvider.SERVICE);
    myKindCombo.addItem(AndroidBundle.message("android.new.component.dialog.broadcast.receiver.item"), null,
                        AndroidFileTemplateProvider.BROADCAST_RECEIVER);
    myKindCombo.addItem(AndroidBundle.message("android.new.component.dialog.broadcast.remote.interface"), null,
                        AndroidFileTemplateProvider.REMOTE_INTERFACE_TEMPLATE);
    init();
    setTitle(AndroidBundle.message("android.new.component.action.command.name"));
    myCreator = new ElementCreator(module.getProject(), CommonBundle.getErrorTitle()) {
      @Override
      protected void checkBeforeCreate(String newName) throws IncorrectOperationException {
        JavaDirectoryService.getInstance().checkCreateClass(directory, newName);
      }

      @Override
      protected PsiElement[] create(String newName) throws Exception {
        final PsiElement element = NewAndroidComponentDialog.this.create(newName, directory, module.getProject());
        if (element != null) {
          return new PsiElement[]{element};
        }
        return PsiElement.EMPTY_ARRAY;
      }

      @Override
      protected String getActionName(String newName) {
        return AndroidBundle.message("android.new.component.action.command.name");
      }
    };
    myKindCombo.getComboBox().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        String selected = myKindCombo.getSelectedName();
        myMarkAsStartupActivityCheckBox.setEnabled(AndroidFileTemplateProvider.ACTIVITY.equals(selected));
        myLabelField.setEnabled(!AndroidFileTemplateProvider.REMOTE_INTERFACE_TEMPLATE.equals(selected) &&
                                !AndroidFileTemplateProvider.APPLICATION.equals(selected));
      }
    });
  }

  private static boolean containsCustomApplicationClass(@NotNull final Module module) {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(module.getProject());
    PsiClass applicationClass = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
      @Nullable
      public PsiClass compute() {
        return facade.findClass(AndroidUtils.APPLICATION_CLASS_NAME, module.getModuleWithDependenciesAndLibrariesScope(false));
      }
    });
    return applicationClass != null && ClassInheritorsSearch.search(applicationClass, module.getModuleScope(), true).findFirst() != null;
  }

  @Nullable
  private PsiElement create(String newName, PsiDirectory directory, Project project) throws Exception {
    return doCreate(myKindCombo.getSelectedName(), directory, project, newName, myLabelField.getText(),
                    myMarkAsStartupActivityCheckBox.isSelected());
  }

  @Nullable
  static PsiElement doCreate(String templateName,
                             PsiDirectory directory,
                             Project project,
                             String newName,
                             String label,
                             boolean startupActivity) throws Exception {
    PsiElement element = AndroidFileTemplateProvider.createFromTemplate(templateName, newName, directory);
    if (element == null) {
      return null;
    }
    Module module = ModuleUtil.findModuleForFile(directory.getVirtualFile(), project);
    if (module != null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      assert facet != null;
      if (element instanceof PsiClass) {
        registerComponent(templateName, (PsiClass)element, JavaDirectoryService.getInstance().getPackage(directory), facet,
                          label, startupActivity);
      }
    }
    return element;
  }

  protected static void registerComponent(String templateName,
                                          PsiClass aClass,
                                          PsiPackage aPackage,
                                          AndroidFacet facet,
                                          String label,
                                          boolean startupActivity) {
    Manifest manifest = facet.getManifest();
    if (manifest == null) return;
    String packageName = manifest.getPackage().getValue();
    if (packageName == null || packageName.length() == 0) {
      manifest.getPackage().setValue(aPackage.getQualifiedName());
    }
    Application application = manifest.getApplication();
    if (application == null) return;
    ApplicationComponent component = addToManifest(templateName, aClass, application, startupActivity);
    if (component != null && label.length() > 0) {
      component.getLabel().setValue(ResourceValue.literal(label));
    }
  }

  @Nullable
  protected static ApplicationComponent addToManifest(String templateName,
                                                      @NotNull PsiClass aClass,
                                                      @NotNull Application application,
                                                      boolean startupActivity) {
    if (AndroidFileTemplateProvider.ACTIVITY.equals(templateName)) {
      Activity activity = application.addActivity();
      activity.getActivityClass().setValue(aClass);

      if (startupActivity) {
        IntentFilter filter = activity.addIntentFilter();
        Action action = filter.addAction();
        action.getName().setValue(AndroidUtils.LAUNCH_ACTION_NAME);
        Category category = filter.addCategory();
        category.getName().setValue(AndroidUtils.LAUNCH_CATEGORY_NAME);
      }
      return activity;
    }
    else if (AndroidFileTemplateProvider.SERVICE.equals(templateName)) {
      Service service = application.addService();
      service.getServiceClass().setValue(aClass);
      return service;
    }
    else if (AndroidFileTemplateProvider.BROADCAST_RECEIVER.equals(templateName)) {
      Receiver receiver = application.addReceiver();
      receiver.getReceiverClass().setValue(aClass);
      return receiver;
    }
    else if (AndroidFileTemplateProvider.APPLICATION.equals(templateName)) {
      application.getName().setValue(aClass);
    }
    return null;
  }

  @Override
  protected void doOKAction() {
    myCreatedElements = myCreator.tryCreate(myNameField.getText());
    if (myCreatedElements.length == 0) {
      return;
    }
    super.doOKAction();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  @Override
  protected String getHelpId() {
    return "reference.new.android.component";
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @NotNull
  public PsiElement[] getCreatedElements() {
    return myCreatedElements;
  }
}

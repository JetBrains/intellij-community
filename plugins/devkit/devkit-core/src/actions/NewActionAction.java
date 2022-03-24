// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.actions;

import com.intellij.ide.actions.CreateElementActionBase;
import com.intellij.ide.actions.CreateTemplateInPackageAction;
import com.intellij.ide.actions.JavaCreateTemplateInPackageAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.UpdateInBackground;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.util.ActionType;
import org.jetbrains.idea.devkit.util.DescriptorUtil;
import org.jetbrains.idea.devkit.util.PsiUtil;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

public class NewActionAction extends CreateElementActionBase implements UpdateInBackground, DescriptorUtil.Patcher {
  private static class Holder {
    // length == 1 is important to make MyInputValidator close the dialog when
    // module selection is canceled. That's some weird interface actually...
    private static final PsiClass[] CANCELED = new PsiClass[1];
  }

  private NewActionDialog myDialog;
  private XmlFile pluginDescriptorToPatch;

  @Override
  protected final PsiElement @NotNull [] invokeDialog(@NotNull Project project, @NotNull PsiDirectory directory) {
    PsiElement[] psiElements = doInvokeDialog(project, directory);
    return psiElements == Holder.CANCELED ? PsiElement.EMPTY_ARRAY : psiElements;
  }

  private PsiElement[] doInvokeDialog(Project project, @NotNull PsiDirectory directory) {
    myDialog = new NewActionDialog(project, directory);
    try {
      myDialog.show();
      if (myDialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
        pluginDescriptorToPatch = DevkitActionsUtil.choosePluginModuleDescriptor(directory);
        if (pluginDescriptorToPatch != null) {
          MyInputValidator validator = new MyInputValidator(project, directory);
          // this actually runs the action to create the class from template
          validator.canClose(myDialog.getActionName());
          return validator.getCreatedElements();
        }
      }
      return PsiElement.EMPTY_ARRAY;
    }
    finally {
      myDialog = null;
      pluginDescriptorToPatch = null;
    }
  }

  @Override
  protected boolean isAvailable(DataContext dataContext) {
    if (!super.isAvailable(dataContext)) {
      return false;
    }

    Module module = dataContext.getData(PlatformCoreDataKeys.MODULE);
    if (module == null || !PsiUtil.isPluginModule(module)) {
      return false;
    }

    return CreateTemplateInPackageAction.isAvailable(dataContext, JavaModuleSourceRootTypes.SOURCES,
                                                     JavaCreateTemplateInPackageAction::doCheckPackageExists);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  protected PsiElement @NotNull [] create(@NotNull String newName, @NotNull PsiDirectory directory) throws Exception {
    PsiClass createdClass = DevkitActionsUtil.createSingleClass(newName, "Action.java", directory);
    DescriptorUtil.patchPluginXml(this, createdClass, pluginDescriptorToPatch);
    return new PsiElement[]{createdClass};
  }


  @Override
  public void patchPluginXml(XmlFile pluginXml, PsiClass klass) throws IncorrectOperationException {
    ActionType.ACTION.patchPluginXml(pluginXml, klass, myDialog);
  }

  @Override
  protected String getErrorTitle() {
    return DevKitBundle.message("new.action.error");
  }

  @Override
  protected @NotNull String getActionName(@NotNull PsiDirectory directory, @NotNull String newName) {
    return DevKitBundle.message("new.action.action.name", directory, newName);
  }
}
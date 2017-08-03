/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.actions;

import com.intellij.ide.IdeView;
import com.intellij.ide.actions.CreateElementActionBase;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.JavaDirectoryService;
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

public class NewActionAction extends CreateElementActionBase implements DescriptorUtil.Patcher {
  // length == 1 is important to make MyInputValidator close the dialog when
  // module selection is canceled. That's some weird interface actually...
  private static final PsiClass[] CANCELED = new PsiClass[1];

  private NewActionDialog myDialog;
  private XmlFile[] pluginDescriptorsToPatch;

  public NewActionAction() {
    super(DevKitBundle.message("new.menu.action.text"), DevKitBundle.message("new.menu.action.description"), null);
  }

  @NotNull
  @Override
  protected final PsiElement[] invokeDialog(Project project, PsiDirectory directory) {
    PsiElement[] psiElements = doInvokeDialog(project, directory);
    return psiElements == CANCELED ? PsiElement.EMPTY_ARRAY : psiElements;
  }

  private PsiElement[] doInvokeDialog(Project project, PsiDirectory directory) {
    myDialog = new NewActionDialog(project);
    try {
      myDialog.show();
      if (myDialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
        pluginDescriptorsToPatch = DevkitActionsUtil.showChooseModulesDialog(directory, getTemplatePresentation());
        if (pluginDescriptorsToPatch != null) {
          assert pluginDescriptorsToPatch.length > 0;
          MyInputValidator validator = new MyInputValidator(project, directory);
          // this actually runs the action to create the class from template
          validator.canClose(myDialog.getActionName());
          return validator.getCreatedElements();
        }
      }
      return PsiElement.EMPTY_ARRAY;
    } finally {
      myDialog = null;
      pluginDescriptorsToPatch = null;
    }
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);

    Presentation presentation = e.getPresentation();
    if (presentation.isEnabled()) {
      Project project = e.getProject();
      Module module = e.getData(LangDataKeys.MODULE);
      if (project != null && module != null &&
          PsiUtil.isPluginModule(module)) {
        IdeView view = e.getData(LangDataKeys.IDE_VIEW);
        if (view != null) {
          // from com.intellij.ide.actions.CreateClassAction.update()
          ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
          PsiDirectory[] dirs = view.getDirectories();
          for (PsiDirectory dir : dirs) {
            if (projectFileIndex.isUnderSourceRootOfType(dir.getVirtualFile(), JavaModuleSourceRootTypes.SOURCES) &&
                JavaDirectoryService.getInstance().getPackage(dir) != null) {
              return;
            }
          }
        }
      }

      presentation.setEnabledAndVisible(false);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @NotNull
  @Override
  protected PsiElement[] create(String newName, PsiDirectory directory) throws Exception {
    PsiClass createdClass = DevkitActionsUtil.createSingleClass(newName, "Action.java", directory);
    DescriptorUtil.patchPluginXml(this, createdClass, pluginDescriptorsToPatch);
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
  protected String getCommandName() {
    return DevKitBundle.message("new.action.command");
  }

  @Override
  protected String getActionName(PsiDirectory directory, String newName) {
    return DevKitBundle.message("new.action.action.name", directory, newName);
  }
}

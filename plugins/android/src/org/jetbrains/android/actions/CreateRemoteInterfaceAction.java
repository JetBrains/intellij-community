/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.android.actions;

import com.intellij.ide.actions.CreateElementActionBase;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.CommonBundle;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.fileTypes.AndroidIdlFileType;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.AndroidFileTemplateProvider;
import static org.jetbrains.android.AndroidFileTemplateProvider.REMOTE_INTERFACE_TEMPLATE;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 24, 2009
 * Time: 8:46:06 PM
 * To change this template use File | Settings | File Templates.
 */
public class CreateRemoteInterfaceAction extends CreateElementActionBase {
  public CreateRemoteInterfaceAction() {
    super(AndroidBundle.message("create.remote.interface.text"), AndroidBundle.message("create.remote.interface.description"),
          AndroidIdlFileType.ourFileType.getIcon());
  }

  @NotNull
  @Override
  protected PsiElement[] invokeDialog(Project project, PsiDirectory directory) {
    MyInputValidator validator = new MyInputValidator(project, directory);
    Messages.showInputDialog(project, AndroidBundle.message("new.file.dialog.text"), getCommandName(), Messages.getQuestionIcon(), "", validator);
    return validator.getCreatedElements();
  }

  @Override
  protected void checkBeforeCreate(String name, PsiDirectory directory) throws IncorrectOperationException {
    directory.checkCreateFile(AndroidFileTemplateProvider.getFileNameByNewElementName(name));
  }

  @NotNull
  @Override
  protected PsiElement[] create(String newName, PsiDirectory directory) throws Exception {
    PsiElement createdElement = AndroidFileTemplateProvider.createFromTemplate(REMOTE_INTERFACE_TEMPLATE, newName, directory);
    return new PsiElement[]{createdElement};
  }

  @Override
  protected String getErrorTitle() {
    return CommonBundle.getErrorTitle();
  }

  @Override
  protected String getCommandName() {
    return AndroidBundle.message("create.remove.interface.command");
  }

  @Nullable
  @Override
  protected String getActionName(PsiDirectory directory, String newName) {
    return null;
  }

  @Override
  protected boolean isAvailable(DataContext context) {
    Module module = DataKeys.MODULE.getData(context);
    PsiElement file = DataKeys.PSI_ELEMENT.getData(context);
    if (module != null && AndroidFacet.getInstance(module) != null) {
      if (file instanceof PsiDirectory) {
        JavaDirectoryService dirService = JavaDirectoryService.getInstance();
        return dirService.getPackage((PsiDirectory)file) != null;
      }
    }
    return false;
  }
}

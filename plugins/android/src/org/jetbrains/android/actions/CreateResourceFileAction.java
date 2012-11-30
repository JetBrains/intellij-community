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

import com.android.SdkConstants;
import com.intellij.CommonBundle;
import com.intellij.ide.actions.CreateElementActionBase;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class CreateResourceFileAction extends CreateElementActionBase {
  private final Map<String, CreateTypedResourceFileAction> mySubactions = new HashMap<String, CreateTypedResourceFileAction>();

  @NotNull
  public static CreateResourceFileAction getInstance() {
    AnAction action = ActionManager.getInstance().getAction("Android.CreateResourcesActionGroup");
    assert action instanceof CreateResourceFileActionGroup;
    return ((CreateResourceFileActionGroup)action).getCreateResourceFileAction();
  }

  public CreateResourceFileAction() {
    super(AndroidBundle.message("new.resource.action.title"), AndroidBundle.message("new.resource.action.description"),
          StdFileTypes.XML.getIcon());
  }

  public void add(CreateTypedResourceFileAction action) {
    mySubactions.put(action.getResourceType(), action);
  }

  public Collection<CreateTypedResourceFileAction> getSubactions() {
    return mySubactions.values();
  }

  @Override
  protected boolean isAvailable(DataContext context) {
    if (!super.isAvailable(context)) return false;
    final PsiElement element = (PsiElement)context.getData(DataKeys.PSI_ELEMENT.getName());
    if (!(element instanceof PsiDirectory)) {
      return false;
    }
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        return AndroidResourceUtil.isResourceDirectory((PsiDirectory)element);
      }
    });
  }

  // must be invoked in a write action
  public static PsiElement[] createResourceFile(Project project,
                                                @NotNull VirtualFile resDir,
                                                @NotNull String resType,
                                                @NotNull String resName) {
    PsiDirectory psiResDir = PsiManager.getInstance(project).findDirectory(resDir);
    if (psiResDir != null) {
      CreateElementActionBase.MyInputValidator validator = getInstance().createValidator(project, psiResDir, resType);
      if (validator.checkInput(resName) && validator.canClose(resName)) {
        return validator.getCreatedElements();
      }
    }
    return PsiElement.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  protected PsiElement[] invokeDialog(final Project project, final PsiDirectory directory) {
    CreateTypedResourceFileAction createLayoutAction = mySubactions.get("layout");
    CreateResourceDialog dialog = new CreateResourceDialog(project, mySubactions.values(), createLayoutAction) {
      @Override
      protected InputValidator createValidator(@NotNull String subdirName) {
        return CreateResourceFileAction.this.createValidator(project, directory, subdirName);
      }
    };
    dialog.setTitle(AndroidBundle.message("new.resource.dialog.title"));
    dialog.show();
    InputValidator validator = dialog.getValidator();
    if (validator == null) {
      return PsiElement.EMPTY_ARRAY;
    }
    return ((MyInputValidator)validator).getCreatedElements();
  }

  @NotNull
  private MyInputValidator createValidator(Project project, final PsiDirectory resDir, final String subdirName) {
    PsiDirectory resSubdir = resDir.findSubdirectory(subdirName);
    if (resSubdir == null) {
      resSubdir = ApplicationManager.getApplication().runWriteAction(new Computable<PsiDirectory>() {
        public PsiDirectory compute() {
          return resDir.createSubdirectory(subdirName);
        }
      });
    }
    return new MyInputValidator(project, resSubdir);
  }

  @NotNull
  @Override
  protected PsiElement[] create(String newName, PsiDirectory directory) throws Exception {
    CreateTypedResourceFileAction action = getActionByDir(directory);
    if (action == null) {
      throw new IllegalArgumentException("Incorrect directory");
    }
    return action.create(newName, directory);
  }

  private CreateTypedResourceFileAction getActionByDir(PsiDirectory directory) {
    String baseDirName = directory.getName();
    final int index = baseDirName.indexOf(SdkConstants.RES_QUALIFIER_SEP);
    if (index >= 0) {
      baseDirName = baseDirName.substring(0, index);
    }
    return mySubactions.get(baseDirName);
  }

  @Override
  protected String getErrorTitle() {
    return CommonBundle.getErrorTitle();
  }

  @Override
  protected String getCommandName() {
    return AndroidBundle.message("new.resource.command.name");
  }

  @Nullable
  @Override
  protected String getActionName(PsiDirectory directory, String newName) {
    return doGetActionName(directory, newName);
  }

  static String doGetActionName(PsiDirectory directory, String newName) {
    if (FileUtil.getExtension(newName).length() == 0) {
      newName += ".xml";
    }
    return AndroidBundle.message("new.resource.action.name", directory.getName() + File.separator + newName);
  }
}

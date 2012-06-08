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

import com.android.AndroidConstants;
import com.android.resources.ResourceType;
import com.intellij.CommonBundle;
import com.intellij.ide.actions.CreateElementActionBase;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import org.jetbrains.android.facet.AndroidFacet;
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
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.actions.CreateResourceFileAction");

  private final Map<String, CreateTypedResourceFileAction> mySubactions = new HashMap<String, CreateTypedResourceFileAction>();
  private String myRootElement;

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
  public static PsiElement[] createResourceFile(final Project project,
                                                @NotNull AndroidFacet facet,
                                                @NotNull final ResourceType resType,
                                                @NotNull String resName,
                                                boolean chooseDirectory) {
    final CreateResourceFileAction action = getInstance();
    String subdirName = resType.getName();
    VirtualFile resourceDir = facet.getLocalResourceManager().getResourceDir();

    if (chooseDirectory) {
      final MyDialog dialog = new MyDialog(facet, action.mySubactions.values(), resType, resName, action, facet.getModule(), true);
      dialog.setTitle(AndroidBundle.message("new.resource.dialog.title"));
      dialog.show();
      if (!dialog.isOK()) {
        return PsiElement.EMPTY_ARRAY;
      }
      subdirName = dialog.getSubdirName();
      final AndroidFacet selectedFacet = AndroidFacet.getInstance(dialog.getSelectedModule());
      LOG.assertTrue(selectedFacet != null);
      resourceDir = selectedFacet.getLocalResourceManager().getResourceDir();
    }

    if (resourceDir != null) {
      final PsiDirectory psiResDir = PsiManager.getInstance(project).findDirectory(resourceDir);
      if (psiResDir != null) {
        CreateElementActionBase.MyInputValidator validator = action.createValidator(project, psiResDir, subdirName);
        if (validator.checkInput(resName) && validator.canClose(resName)) {
          return validator.getCreatedElements();
        }
      }
    }
    return PsiElement.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  protected PsiElement[] invokeDialog(final Project project, final PsiDirectory directory) {
    final AndroidFacet facet = AndroidFacet.getInstance(directory);
    LOG.assertTrue(facet != null);

    MyDialog dialog = new MyDialog(facet, mySubactions.values(), null, null, CreateResourceFileAction.this, facet.getModule(), false) {
      @Override
      protected InputValidator createValidator(@NotNull String subdirName) {
        return CreateResourceFileAction.this.createValidator(project, directory, subdirName);
      }
    };
    dialog.setTitle(AndroidBundle.message("new.resource.dialog.title"));
    dialog.show();
    return PsiElement.EMPTY_ARRAY;
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
    if (myRootElement != null && myRootElement.length() > 0) {
      return action.doCreate(newName, directory, myRootElement, false);
    }
    return action.create(newName, directory);
  }

  private CreateTypedResourceFileAction getActionByDir(PsiDirectory directory) {
    String baseDirName = directory.getName();
    final int index = baseDirName.indexOf(AndroidConstants.RES_QUALIFIER_SEP);
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

  private static class MyDialog extends CreateResourceDialog {
    private final CreateResourceFileAction myAction;

    protected MyDialog(@NotNull AndroidFacet facet,
                       Collection<CreateTypedResourceFileAction> actions,
                       @Nullable ResourceType predefinedResourceType,
                       @Nullable String predefinedFileName,
                       @NotNull CreateResourceFileAction action,
                       @NotNull Module module,
                       boolean chooseModule) {
      super(facet, actions, predefinedResourceType, predefinedFileName, module, chooseModule);
      myAction = action;
    }

    @Override
    protected void doOKAction() {
      super.doOKAction();
      myAction.myRootElement = getRootElement();
    }
  }
}

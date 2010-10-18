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

import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.ide.actions.CreateElementActionBase;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.search.ProjectScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.android.dom.manifest.Application;
import org.jetbrains.android.dom.manifest.ApplicationComponent;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Mar 22, 2009
 * Time: 4:45:36 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class CreateComponentActionBase extends CreateElementActionBase {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.actions.CreateComponentActionBase");

  public CreateComponentActionBase(String text, String description) {
    super(text, description, AndroidUtils.ANDROID_ICON);
  }

  public void update(AnActionEvent e) {
    Module module = e.getData(DataKeys.MODULE);
    PsiElement file = e.getData(DataKeys.PSI_ELEMENT);
    boolean visible = false;
    if (module != null && AndroidFacet.getInstance(module) != null) {
      if (file instanceof PsiDirectory) {
        PsiDirectory dir = (PsiDirectory)file;
        JavaDirectoryService dirService = JavaDirectoryService.getInstance();
        PsiPackage aPackage = dirService.getPackage(dir);
        if (aPackage != null && AndroidUtils.contains2Ids(aPackage.getQualifiedName())) {
          visible = true;
        }
      }
    }
    e.getPresentation().setVisible(visible);
  }

  protected void checkBeforeCreate(String newName, PsiDirectory directory) throws IncorrectOperationException {
    JavaDirectoryService.getInstance().checkCreateClass(directory, newName);
  }

  @NotNull
  protected abstract String getClassName();

  @NotNull
  protected abstract String getLabel();

  @NotNull
  protected PsiElement[] create(String newName, PsiDirectory directory) throws Exception {
    return create(newName, directory, getLabel());
  }

  public PsiElement[] create(String newName, PsiDirectory directory, String label) {
    JavaDirectoryService dirService = JavaDirectoryService.getInstance();
    PsiClass aClass;
    String fileName = newName + '.' + StdFileTypes.JAVA.getDefaultExtension();
    VirtualFile existingActivityFile = directory.getVirtualFile().findChild(fileName);
    try {
      if (existingActivityFile != null) {
        existingActivityFile.delete(directory.getProject());
      }
      aClass = dirService.createClass(directory, newName);
    }
    catch (Exception e) {
      LOG.warn(e);
      return PsiElement.EMPTY_ARRAY;
    }
    Project project = directory.getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    PsiClass componentClass = facade.findClass(getClassName(), ProjectScope.getAllScope(project));
    if (componentClass != null) {
      PsiJavaCodeReferenceElement reference = facade.getElementFactory().createClassReferenceElement(componentClass);
      aClass.getExtendsList().add(reference);
      implementMethods(aClass);
    }
    tuneClass(aClass);
    Module module = ModuleUtil.findModuleForFile(directory.getVirtualFile(), project);
    if (module != null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      assert facet != null;
      registerComponent(aClass, dirService.getPackage(directory), facet, label);
    }
    return new PsiElement[]{aClass};
  }

  protected void tuneClass(@NotNull PsiClass c) {
  }

  private static void implementMethods(PsiClass aClass) {
    Collection<CandidateInfo> candidates = OverrideImplementUtil.getMethodsToOverrideImplement(aClass, true);
    List<PsiMethod> methods = OverrideImplementUtil.overrideOrImplementMethodCandidates(aClass, candidates, false);
    for (PsiMethod method : methods) {
      aClass.addBefore(method, null);
    }
  }

  protected void registerComponent(PsiClass aClass, PsiPackage aPackage, AndroidFacet facet, String label) {
    Manifest manifest = facet.getManifest();
    if (manifest == null) return;
    String packageName = manifest.getPackage().getValue();
    if (packageName == null || packageName.length() == 0) {
      manifest.getPackage().setValue(aPackage.getQualifiedName());
    }
    Application application = manifest.getApplication();
    if (application == null) return;
    ApplicationComponent component = addToManifest(aClass, application);
    if (label.length() > 0) {
      component.getLabel().setValue(ResourceValue.literal(label));
    }
  }

  @NotNull
  protected abstract DialogWrapper createDialog(Project project, InputValidator validator, PsiDirectory directory);

  @NotNull
  protected PsiElement[] invokeDialog(Project project, PsiDirectory directory) {
    final MyInputValidator validator = new MyInputValidator(project, directory);
    DialogWrapper dialog = createDialog(project, validator, directory);
    dialog.setTitle(getCommandName());
    dialog.show();
    return validator.getCreatedElements();
  }

  protected abstract ApplicationComponent addToManifest(@NotNull PsiClass aClass, @NotNull Application application);
}

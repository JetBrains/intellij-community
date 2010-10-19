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

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.psi.*;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.uiDesigner.core.GridConstraints;
import static com.intellij.uiDesigner.core.GridConstraints.*;
import org.jetbrains.android.dom.manifest.Action;
import org.jetbrains.android.dom.manifest.*;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author yole
 */
public class CreateActivityAction extends CreateComponentActionBase {
  private JCheckBox myAsStartupActivityCheckBox;
  private MyDialog myDialog;
  private final boolean myHelloAndroid;

  public CreateActivityAction(boolean helloAndroid) {
    super(AndroidBundle.message("create.activity.title"), AndroidBundle.message("create.activity.description"));
    myHelloAndroid = helloAndroid;
  }

  public CreateActivityAction() {
    this(false);
  }

  protected String getErrorTitle() {
    return "Cannot create activity";
  }

  protected String getActionName(PsiDirectory directory, String newName) {
    return "Creating activity " + newName;
  }

  private static boolean isFirstActivity(PsiDirectory directory) {
    Module module = ModuleUtil.findModuleForFile(directory.getVirtualFile(), directory.getProject());
    if (module == null) return false;
    AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null;
    Manifest manifest = facet.getManifest();
    if (manifest == null) return false;
    Application application = manifest.getApplication();
    return application != null && application.getActivities().isEmpty();
  }

  protected String getCommandName() {
    return "Create Activity";
  }

  @NotNull
  protected String getClassName() {
    return "android.app.Activity";
  }

  @NotNull
  protected String getLabel() {
    return myDialog.getLabel();
  }

  private class MyDialog extends CreateComponentDialog {
    public MyDialog(Project project, InputValidator validator, PsiDirectory directory) {
      super(project, validator);
      myAsStartupActivityCheckBox = new JCheckBox("Mark as startup activity");
      myPanel.add(myAsStartupActivityCheckBox,
                  new GridConstraints(2, 0, 1, 2, ANCHOR_NORTH, FILL_HORIZONTAL, SIZEPOLICY_CAN_GROW | SIZEPOLICY_CAN_SHRINK,
                                      SIZEPOLICY_FIXED, null, null, null));
      myAsStartupActivityCheckBox.setSelected(isFirstActivity(directory));
    }
  }

  @Override
  protected void tuneClass(@NotNull PsiClass c) {
    super.tuneClass(c);
    if (c.findMethodsByName("onCreate", true).length > 0) {
      Project project = c.getProject();
      JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      PsiElementFactory factory = facade.getElementFactory();
      PsiClass bundle = facade.findClass("android.os.Bundle", ProjectScope.getAllScope(project));
      assert bundle != null;
      PsiImportStatement importStatement = factory.createImportStatement(bundle);
      PsiJavaFile javaFile = (PsiJavaFile)c.getContainingFile();
      PsiImportList importList = javaFile.getImportList();
      assert importList != null;
      importList.add(importStatement);
      String methodText = myHelloAndroid ? "/**\n* Called when the activity is first created.\n*/\n" +
                                           "@Override\n" +
                                           "public void onCreate(Bundle savedInstanceState) {\n" +
                                           "   super.onCreate(savedInstanceState);\n" +
                                           "   setContentView(R.layout.main);\n" +
                                           "}"
                                         : "@Override\n" +
                                           "public void onCreate(Bundle savedInstanceState) {\n" +
                                           "   super.onCreate(savedInstanceState);\n" +
                                           "}";
      PsiMethod method = factory.createMethodFromText(methodText, c);
      c.addAfter(method, null);
    }
  }

  @NotNull
  @Override
  protected CreateComponentDialog createDialog(Project project, InputValidator validator, PsiDirectory directory) {
    myDialog = new MyDialog(project, validator, directory);
    return myDialog;
  }

  protected ApplicationComponent addToManifest(@NotNull PsiClass aClass, @NotNull Application application) {
    Activity activity = application.addActivity();
    activity.getActivityClass().setValue(aClass);

    if (asStartupActivity()) {
      IntentFilter filter = activity.addIntentFilter();
      Action action = filter.addAction();
      action.getName().setValue(AndroidUtils.LAUNCH_ACTION_NAME);
      Category category = filter.addCategory();
      category.getName().setValue(AndroidUtils.LAUNCH_CATEGORY_NAME);
    }
    return activity;
  }

  protected boolean asStartupActivity() {
    if (myAsStartupActivityCheckBox == null) return false;
    return myAsStartupActivityCheckBox.isSelected();
  }

  @Nullable
  public PsiClass createActivity(@NotNull String name, @NotNull String label, @NotNull PsiDirectory psiDir) {
    PsiElement[] createdElements = create(name, psiDir, label);
    if (createdElements.length == 0) return null;
    PsiClass c = (PsiClass)createdElements[0];
    psiDir.getProject().getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
    FileDocumentManager.getInstance().saveAllDocuments();
    return c;
  }
}

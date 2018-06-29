/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.actions.NewActionDialog;
import org.jetbrains.idea.devkit.util.ActionData;
import org.jetbrains.idea.devkit.util.ActionType;

public class RegisterActionFix extends AbstractRegisterFix {
  private NewActionDialog myDialog;

  public RegisterActionFix(@NotNull SmartPsiElementPointer<PsiClass> pointer) {
    super(pointer);
  }

  protected String getType() {
    return DevKitBundle.message("new.menu.action.text");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      super.applyFix(project, descriptor);
      return;
    }

    try {
      PsiClass element = myPointer.getElement();
      if (element == null) {
        LOG.info("Element is null for PsiPointer: " + myPointer);
        return;
      }
      myDialog = new NewActionDialog(element);
      if (myDialog.showAndGet()) {
        super.applyFix(project, descriptor);
      }
    }
    finally {
      myDialog = null;
    }
  }

  public void patchPluginXml(XmlFile pluginXml, PsiClass aClass) throws IncorrectOperationException {
    if (ActionType.GROUP.isOfType(aClass)) {
      ActionType.GROUP.patchPluginXml(pluginXml, aClass, getActionData());
    }
    else {
      ActionType.ACTION.patchPluginXml(pluginXml, aClass, getActionData());
    }
  }

  @TestOnly
  public static ActionData ourTestActionData = null;

  private ActionData getActionData() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return ourTestActionData;
    }
    return myDialog;
  }
}

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
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.actions.NewActionDialog;
import org.jetbrains.idea.devkit.util.ActionType;
import org.jetbrains.annotations.NotNull;

public class RegisterActionFix extends AbstractRegisterFix {
  private NewActionDialog myDialog;

  public RegisterActionFix(PsiClass klass) {
    super(klass);
  }

  protected String getType() {
    return DevKitBundle.message("new.menu.action.text");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    try {
      myDialog = new NewActionDialog(myClass);
      myDialog.show();

      if (myDialog.isOK()) {
        super.applyFix(project, descriptor);
      }
    }
    finally {
      myDialog = null;
    }
  }

  public void patchPluginXml(XmlFile pluginXml, PsiClass aClass) throws IncorrectOperationException {
    if (ActionType.GROUP.isOfType(aClass)) {
      ActionType.GROUP.patchPluginXml(pluginXml, aClass, myDialog);
    } else {
      ActionType.ACTION.patchPluginXml(pluginXml, aClass, myDialog);
    }
  }
}

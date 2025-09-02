// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.actions.obsolete.NewActionDialog;
import org.jetbrains.idea.devkit.util.ActionData;
import org.jetbrains.idea.devkit.util.ActionType;

public final class RegisterActionFix extends AbstractRegisterFix {
  private NewActionDialog myDialog;

  public RegisterActionFix(@NotNull PsiClass psiClass) {
    super(psiClass);
  }

  @Override
  protected String getType() {
    return DevKitBundle.message("new.menu.action.text");
  }

  @Override
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

  @Override
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

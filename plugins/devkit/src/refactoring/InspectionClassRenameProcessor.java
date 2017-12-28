/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.idea.devkit.refactoring;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.refactoring.RefactoringFactory;
import com.intellij.refactoring.RenameRefactoring;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.RenameJavaClassProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.DescriptionType;
import org.jetbrains.idea.devkit.inspections.InspectionDescriptionInfo;
import org.jetbrains.idea.devkit.util.PsiUtil;

//TODO support not only Java
public class InspectionClassRenameProcessor extends RenameJavaClassProcessor {
  private static final String RENAME_DESCRIPTION_AND_SHORT_NAME_DO_NOT_ASK_AGAIN_OPTION =
    "inspection.rename.description.and.short.name.do.not.ask.again";

  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    return isInspectionPsiClass(element);
  }

  @Override
  public void renameElement(PsiElement element, String newName, UsageInfo[] usages,
                            @Nullable RefactoringElementListener listener) throws IncorrectOperationException {
    super.renameElement(element, newName, usages, listener);

    PsiClass psiClass = (PsiClass)element;
    Module module = ModuleUtilCore.findModuleForPsiElement(psiClass);
    if (module == null) {
      return;
    }

    InspectionDescriptionInfo inspectionDescriptionInfo = InspectionDescriptionInfo.create(module, psiClass);
    PsiFile descriptionFile = inspectionDescriptionInfo.getDescriptionFile();
    if (descriptionFile == null) {
      return;
    }
    VirtualFile descriptionVirtualFile = descriptionFile.getVirtualFile();
    if (descriptionVirtualFile == null) {
      return;
    }

    String descriptionFileExtension = StringUtil.notNullize(descriptionVirtualFile.getExtension());
    String newDescriptionFileName = StringUtil.trimEnd(newName, "Inspection");
    if (newDescriptionFileName.equals(descriptionFile.getName())) {
      return;
    }
    String newDescriptionFileNameWithExtension = newDescriptionFileName  + "." + descriptionFileExtension;

    ApplicationManager.getApplication().invokeLater(() -> {
      if (!PropertiesComponent.getInstance().getBoolean(RENAME_DESCRIPTION_AND_SHORT_NAME_DO_NOT_ASK_AGAIN_OPTION)) {
        int dialogExitCode = Messages.showYesNoDialog(
          element.getProject(),
          DevKitBundle.message("inspections.rename.dialog.description"),
          DevKitBundle.message("inspections.rename.dialog.title"),
          Messages.getQuestionIcon(),
          new InspectionClassRenameDoNotAskOption());
        if (dialogExitCode != Messages.YES) {
          return;
        }
      }

      //TODO it'd be better to do both atomically (and support undo)
      updateShortNameMethod(inspectionDescriptionInfo.getShortNameMethod(), newDescriptionFileName);
      renameDescriptionFile(descriptionFile, newDescriptionFileNameWithExtension);
    });
  }


  private static boolean isInspectionPsiClass(@NotNull PsiElement element) {
    if (!(element instanceof PsiClass)) {
      return false;
    }
    if (!PsiUtil.isPluginProject(element.getProject())) {
      return false;
    }
    return InheritanceUtil.isInheritor((PsiClass)element, DescriptionType.INSPECTION.getClassName());
  }

  private static void updateShortNameMethod(PsiMethod shortNameMethod, String newName) {
    if (shortNameMethod == null) {
      // getShortName() is not overriden; probably it will be fine after changing the class name
      // (otherwise InspectionDescriptionNotFoundInspection will inform about not found description).
      return;
    }

    PsiCodeBlock shortNameMethodBody = shortNameMethod.getBody();
    if (shortNameMethodBody == null) {
      return;
    }

    PsiStatement[] statements = shortNameMethodBody.getStatements();
    if (statements.length != 1 || !(statements[0] instanceof PsiReturnStatement)) {
      return;
    }

    PsiStatement returnStatement = statements[0];
    Project project = shortNameMethod.getProject();
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    new WriteCommandAction<Void>(project,
                                 DevKitBundle.message("inspections.rename.updating.short.name"),
                                 shortNameMethod.getContainingFile()) {
      @Override
      protected void run(@NotNull Result<Void> result) {
        returnStatement.replace(elementFactory.createStatementFromText("return \"" + newName + "\";", shortNameMethod));
      }
    }.execute();
  }

  private static void renameDescriptionFile(PsiFile descriptionFile, String newName) {
    RenameRefactoring rename = RefactoringFactory.getInstance(descriptionFile.getProject()).createRename(descriptionFile, newName);
    rename.run();
  }


  private static class InspectionClassRenameDoNotAskOption extends DialogWrapper.DoNotAskOption.Adapter {
    @Override
    public void rememberChoice(boolean isSelected, int exitCode) {
      PropertiesComponent.getInstance().setValue(RENAME_DESCRIPTION_AND_SHORT_NAME_DO_NOT_ASK_AGAIN_OPTION, isSelected);
    }

    @Override
    public boolean shouldSaveOptionsOnCancel() {
      return true;
    }
  }
}

package org.jetbrains.android.actions;

import com.intellij.CommonBundle;
import com.intellij.ide.actions.CreateElementActionBase;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.PlatformIcons;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author Eugene.Kudelevsky
 */
public class CreateResourceDirectoryAction extends CreateElementActionBase {
  public CreateResourceDirectoryAction() {
    super(AndroidBundle.message("new.resource.dir.action.title"), AndroidBundle.message("new.resource.action.description"),
          PlatformIcons.DIRECTORY_CLOSED_ICON);
  }

  @NotNull
  @Override
  protected PsiElement[] invokeDialog(final Project project, final PsiDirectory directory) {
    final CreateResourceDirectoryDialog dialog = new CreateResourceDirectoryDialog(project) {
      @Override
      protected InputValidator createValidator() {
        return CreateResourceDirectoryAction.this.createValidator(project, directory);
      }
    };
    dialog.setTitle(AndroidBundle.message("new.resource.dir.dialog.title"));
    dialog.show();
    final InputValidator validator = dialog.getValidator();
    if (validator == null) {
      return PsiElement.EMPTY_ARRAY;
    }
    return ((MyInputValidator)validator).getCreatedElements();
  }

  @NotNull
  private MyInputValidator createValidator(Project project, final PsiDirectory resDir) {
    return new MyInputValidator(project, resDir);
  }

  @NotNull
  @Override
  protected PsiElement[] create(String newName, PsiDirectory directory) throws Exception {
    return new PsiElement[]{directory.createSubdirectory(newName)};
  }

  @Override
  protected String getErrorTitle() {
    return CommonBundle.getErrorTitle();
  }

  @Override
  protected String getCommandName() {
    return AndroidBundle.message("new.resource.dir.command.name");
  }

  @Nullable
  @Override
  protected String getActionName(PsiDirectory directory, String newName) {
    return AndroidBundle.message("new.resource.dir.action.name", directory.getName() + File.separator + newName);
  }

  @Override
  protected boolean isAvailable(DataContext context) {
    if (!super.isAvailable(context)) return false;
    final PsiElement element = (PsiElement)context.getData(LangDataKeys.PSI_ELEMENT.getName());
    if (!(element instanceof PsiDirectory)) {
      return false;
    }
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        return ResourceManager.isResourceDirectory((PsiDirectory)element);
      }
    });
  }
}

package org.jetbrains.android.actions;

import com.intellij.ide.actions.CreateElementActionBase;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class CreateResourceDirectoryAction extends CreateElementActionBase {
  public CreateResourceDirectoryAction() {
    super(AndroidBundle.message("new.resource.dir.action.title"), AndroidBundle.message("new.resource.action.description"), PlatformIcons.DIRECTORY_CLOSED_ICON);
  }

  @NotNull
  @Override
  protected PsiElement[] invokeDialog(Project project, PsiDirectory directory) {
    return new PsiElement[0];  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  protected void checkBeforeCreate(String newName, PsiDirectory directory) throws IncorrectOperationException {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  @NotNull
  @Override
  protected PsiElement[] create(String newName, PsiDirectory directory) throws Exception {
    return new PsiElement[0];  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  protected String getErrorTitle() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  protected String getCommandName() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  protected String getActionName(PsiDirectory directory, String newName) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }
}

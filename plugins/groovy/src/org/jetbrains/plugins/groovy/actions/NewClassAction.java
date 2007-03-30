package org.jetbrains.plugins.groovy.actions;

import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.Icons;

public class NewClassAction extends NewActionBase
{
  public NewClassAction()
  {
    super(GroovyBundle.message("newclass.menu.action.text"),
            GroovyBundle.message("newclass.menu.action.description"),
            Icons.FILE_TYPE);
  }

  protected String getActionName(PsiDirectory directory, String newName)
  {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  protected String getDialogPrompt()
  {
    return GroovyBundle.message("newclass.dlg.prompt");
  }

  protected String getDialogTitle()
  {
    return GroovyBundle.message("newclass.dlg.title");
  }

  protected String getCommandName()
  {
    return GroovyBundle.message("newclass.command.name");
  }

  @NotNull
  protected PsiElement[] doCreate(String newName, PsiDirectory directory) throws Exception
  {
    return new PsiElement[]{createClassFromTemplate(directory, newName, "GroovyClass.groovy")};
  }
}

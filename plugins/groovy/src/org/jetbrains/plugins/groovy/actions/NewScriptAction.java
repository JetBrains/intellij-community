package org.jetbrains.plugins.groovy.actions;

import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.Icons;

public class NewScriptAction extends NewActionBase
{
  public NewScriptAction()
  {
    super(GroovyBundle.message("newscript.menu.action.text"),
            GroovyBundle.message("newscript.menu.action.description"),
            Icons.FILE_TYPE);
  }

  protected String getActionName(PsiDirectory directory, String newName)
  {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  protected String getDialogPrompt()
  {
    return GroovyBundle.message("newscript.dlg.prompt");
  }

  protected String getDialogTitle()
  {
    return GroovyBundle.message("newscript.dlg.title");
  }

  protected String getCommandName()
  {
    return GroovyBundle.message("newscript.command.name");
  }

  @NotNull
  protected PsiElement[] doCreate(String newName, PsiDirectory directory) throws Exception
  {
    return new PsiElement[]{createClassFromTemplate(directory, newName, "GroovyScript.groovy")};
  }
}
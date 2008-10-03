package org.jetbrains.plugins.gant.actions;

import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gant.GantBundle;
import org.jetbrains.plugins.gant.GantFileType;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.actions.GroovyTemplatesFactory;
import org.jetbrains.plugins.groovy.actions.NewGroovyActionBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

/**
 * @author ilyas
 */
public class NewGantScriptAction extends NewGroovyActionBase {

  public NewGantScriptAction() {
    super(GantBundle.message("newscript.menu.action.text"), GantBundle.message("newscript.menu.action.description"),
          GroovyIcons.GANT_FILE_ICON);
  }

  protected String getActionName(PsiDirectory directory, String newName) {
    return null;
  }

  protected String getDialogPrompt() {
    return GantBundle.message("newscript.dlg.prompt");
  }

  protected String getDialogTitle() {
    return GantBundle.message("newscript.dlg.title");
  }

  protected String getCommandName() {
    return GantBundle.message("newscript.command.name");
  }

  @NotNull
  protected PsiElement[] doCreate(String newName, PsiDirectory directory) throws Exception {
    PsiFile file = createGantScriptFromTemplate(directory, newName, "GantScript.gant");
    PsiElement lastChild = file.getLastChild();
    PsiElement child = null;
    if (lastChild instanceof GrMethodCallExpression) {
      child = lastChild;
    }
    if (child == null && file.getChildren().length > 0) {
      child = file.getLastChild();
    }
    return child != null ? new PsiElement[]{file, child} : new PsiElement[]{file};
  }

  private static PsiFile createGantScriptFromTemplate(final PsiDirectory directory,
                                                      String className,
                                                      String templateName,
                                                      @NonNls String... parameters) throws IncorrectOperationException {
    return GroovyTemplatesFactory
      .createFromTemplate(directory, className, className + "." + GantFileType.DEFAULT_EXTENSION, templateName, parameters);
  }

}
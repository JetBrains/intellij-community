package org.jetbrains.plugins.groovy.gant;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.actions.GroovyTemplatesFactory;
import org.jetbrains.plugins.groovy.actions.NewGroovyActionBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

/**
 * @author ilyas
 */
public class NewGantScriptAction extends NewGroovyActionBase {

  public NewGantScriptAction() {
    super("Gant Script", "Create new Gant Script", GantIcons.GANT_ICON_16x16);
  }

  protected String getActionName(PsiDirectory directory, String newName) {
    return null;
  }

  protected String getDialogPrompt() {
    return "Enter name for new Gant Script";
  }

  protected String getDialogTitle() {
    return "New Gant Script";
  }

  protected String getCommandName() {
    return "Create Gant Script";
  }

  @Override
  protected boolean isAvailable(DataContext dataContext) {
    return super.isAvailable(dataContext) &&
           GantUtils.isSDKConfiguredToRun(ObjectUtils.assertNotNull(DataKeys.MODULE.getData(dataContext)));
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
      .createFromTemplate(directory, className, className + "." + GantScriptType.DEFAULT_EXTENSION, templateName, parameters);
  }

}
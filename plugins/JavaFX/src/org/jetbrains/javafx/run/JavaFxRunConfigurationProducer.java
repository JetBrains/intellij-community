package org.jetbrains.javafx.run;

import com.intellij.execution.Location;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.javafx.JavaFxFileType;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxRunConfigurationProducer extends RuntimeConfigurationProducer {
  private PsiFile mySourceFile = null;

  public JavaFxRunConfigurationProducer() {
    super(new JavaFxRunConfigurationType());
  }

  public PsiElement getSourceElement() {
    return mySourceFile;
  }

  @Override
  protected RunnerAndConfigurationSettings createConfigurationByElement(Location location, ConfigurationContext context) {
    final PsiFile script = location.getPsiElement().getContainingFile();
    if (script == null || script.getFileType() != JavaFxFileType.INSTANCE) {
      return null;
    }
    if (!RunnableScriptUtil.isRunnable(script)) {
      return null;
    }
    final Module module = ModuleUtil.findModuleForPsiElement(script);
    if (module == null) {
      return null;
    }
    mySourceFile = script;

    final Project project = mySourceFile.getProject();
    final RunnerAndConfigurationSettings settings = cloneTemplateConfiguration(project, context);
    final JavaFxRunConfiguration configuration = (JavaFxRunConfiguration) settings.getConfiguration();
    final VirtualFile vFile = mySourceFile.getVirtualFile();
    if (vFile == null) {
      return null;
    }
    configuration.setMainScript(vFile.getPath());
    configuration.setName(configuration.suggestedName());
    configuration.setModule(module);
    return settings;
  }

  public int compareTo(Object o) {
    return PREFERED;
  }
}

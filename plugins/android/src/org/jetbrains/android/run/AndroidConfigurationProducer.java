package org.jetbrains.android.run;

import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.junit.JavaRuntimeConfigurationProducerBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.module.Module;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class AndroidConfigurationProducer extends JavaRuntimeConfigurationProducerBase implements Cloneable {
  private PsiElement mySourceElement;

  public AndroidConfigurationProducer() {
    super(AndroidRunConfigurationType.getInstance());
  }

  public PsiElement getSourceElement() {
    return mySourceElement;
  }

  @Nullable
  protected RunnerAndConfigurationSettings createConfigurationByElement(Location location, ConfigurationContext context) {
    final Module module = context.getModule();
    if (module == null) return null;
    location = JavaExecutionUtil.stepIntoSingleClass(location);
    PsiElement element = location.getPsiElement();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(element.getProject());
    GlobalSearchScope scope = module.getModuleWithDependenciesAndLibrariesScope(true);
    PsiClass activityClass = facade.findClass(AndroidUtils.ACTIVITY_BASE_CLASS_NAME, scope);
    if (activityClass == null) return null;

    PsiClass elementClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    while (elementClass != null) {
      if (elementClass.isInheritor(activityClass, true)) {
        mySourceElement = elementClass;
        return createRunActivityConfiguration((PsiClass)mySourceElement, context);
      }
      elementClass = PsiTreeUtil.getParentOfType(elementClass, PsiClass.class);
    }
    return null;
  }

  private RunnerAndConfigurationSettings createRunActivityConfiguration(PsiClass psiClass, ConfigurationContext context) {
    Project project = psiClass.getProject();
    RunnerAndConfigurationSettings settings = cloneTemplateConfiguration(project, context);
    final AndroidRunConfiguration configuration = (AndroidRunConfiguration)settings.getConfiguration();
    configuration.ACTIVITY_CLASS = psiClass.getQualifiedName();
    configuration.MODE = AndroidRunConfiguration.LAUNCH_SPECIFIC_ACTIVITY;
    configuration.setName(JavaExecutionUtil.getPresentableClassName(configuration.ACTIVITY_CLASS, configuration.getConfigurationModule()));
    setupConfigurationModule(context, configuration);
    return settings;
  }

  public int compareTo(Object o) {
    return PREFERED;
  }
}

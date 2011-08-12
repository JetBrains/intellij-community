package org.jetbrains.plugins.gradle.importing.wizard;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import org.jetbrains.annotations.NotNull;

/**
 * Just a holder for the common useful functionality.
 * 
 * @author Denis Zhdanov
 * @since 8/2/11 3:22 PM
 */
public abstract class AbstractImportFromGradleWizardStep extends ModuleWizardStep {

  private final WizardContext myContext;
  
  protected AbstractImportFromGradleWizardStep(@NotNull WizardContext context) {
    myContext = context;
  }

  @NotNull
  public WizardContext getContext() {
    return myContext;
  }

  @NotNull
  protected GradleProjectImportBuilder getBuilder() {
    return (GradleProjectImportBuilder)myContext.getProjectBuilder();
  }
}

package org.jetbrains.plugins.groovy.mvc;

import com.intellij.ide.util.projectWizard.*;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import org.jetbrains.plugins.groovy.config.GroovyAwareModuleBuilder;

import javax.swing.*;

/**
 * @author peter
 */
public class MvcModuleBuilder extends GroovyAwareModuleBuilder {
  private final MvcFramework myFramework;

  protected MvcModuleBuilder(MvcFramework framework, Icon bigIcon) {
    super(framework.getFrameworkName(), framework.getDisplayName() + " Application", "Create a new " + framework.getDisplayName() + " application", bigIcon);
    myFramework = framework;
  }

  @Override
  public ModuleWizardStep[] createWizardSteps(WizardContext wizardContext, ModulesProvider modulesProvider) {
    final ModuleWizardStep sdkStep = new GroovySdkForNewModuleWizardStep(this, wizardContext, myFramework);
    return new ModuleWizardStep[]{ProjectWizardStepFactory.getInstance().createProjectJdkStep(wizardContext), sdkStep};
  }
}

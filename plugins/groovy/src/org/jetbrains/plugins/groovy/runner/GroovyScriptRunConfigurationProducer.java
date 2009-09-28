package org.jetbrains.plugins.groovy.runner;

import com.intellij.execution.Location;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * @author ilyas
 */
public class GroovyScriptRunConfigurationProducer extends RuntimeConfigurationProducer implements Cloneable {
  private PsiElement mySourceElement;

  public GroovyScriptRunConfigurationProducer() {
    super(GroovyScriptRunConfigurationType.getInstance());
  }

  public PsiElement getSourceElement() {
    return mySourceElement;
  }

  protected RunnerAndConfigurationSettingsImpl createConfigurationByElement(final Location location, final ConfigurationContext context) {
    final PsiElement element = location.getPsiElement();
    final PsiFile file = element.getContainingFile();
    if (!(file instanceof GroovyFile)) {
      return null;
    }

    GroovyFile groovyFile = (GroovyFile)file;
    if (groovyFile.isScript()) {
      mySourceElement = element;
      final RunnerAndConfigurationSettings settings = GroovyScriptRunConfigurationType.getInstance().createConfigurationByLocation(location);
      if (settings != null) {
        final GroovyScriptRunConfiguration configuration = (GroovyScriptRunConfiguration)settings.getConfiguration();
        GroovyScriptType.getScriptType(groovyFile).tuneConfiguration(groovyFile, configuration, location);
        return (RunnerAndConfigurationSettingsImpl)settings;
      }
    }

    return null;
  }

  public int compareTo(final Object o) {
    return PREFERED;
  }
}

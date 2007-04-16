package org.jetbrains.plugins.groovy.runner;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.Icons;

import javax.swing.*;

public class GroovyScriptRunConfigurationType implements ConfigurationType
{
  private GroovyScriptConfigurationFactory myConfigurationFactory;

  public GroovyScriptRunConfigurationType()
  {
    myConfigurationFactory = new GroovyScriptConfigurationFactory(this);
  }

  public void initComponent()
  {
  }

  public void disposeComponent()
  {
  }

  @NotNull
  public String getComponentName()
  {
    return "GroovyScriptRunConfigurationType";
  }

  public String getDisplayName()
  {
    return "Groovy Script";
  }

  public String getConfigurationTypeDescription()
  {
    return "Groovy Script";
  }

  public Icon getIcon()
  {
    return Icons.FILE_TYPE;
  }

  public ConfigurationFactory[] getConfigurationFactories()
  {
    myConfigurationFactory = new GroovyScriptConfigurationFactory(this);
    return new ConfigurationFactory[]
            {
                    myConfigurationFactory
            };
  }

}

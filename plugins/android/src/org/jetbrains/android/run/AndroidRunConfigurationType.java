package org.jetbrains.android.run;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author yole
 */
public class AndroidRunConfigurationType implements ConfigurationType {
  private final ConfigurationFactory myFactory = new ConfigurationFactory(this) {
    public RunConfiguration createTemplateConfiguration(Project project) {
      return new AndroidRunConfiguration("", project, this);
    }
  };

  public static AndroidRunConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(AndroidRunConfigurationType.class);
  }

  public String getDisplayName() {
    return AndroidBundle.message("android.run.configuration.type.name");
  }

  public String getConfigurationTypeDescription() {
    return AndroidBundle.message("android.run.configuration.type.description");
  }

  public Icon getIcon() {
    return AndroidUtils.ANDROID_ICON;
  }

  @NotNull
  public String getId() {
    return "AndroidRunConfigurationType";
  }

  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{myFactory};
  }

  public ConfigurationFactory getFactory() {
    return myFactory;
  }
}

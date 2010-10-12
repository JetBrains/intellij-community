package org.jetbrains.javafx.run;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.JavaFxBundle;
import org.jetbrains.javafx.JavaFxFileType;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxRunConfigurationType implements ConfigurationType {
  private final ConfigurationFactory myFactory = new ConfigurationFactory(this) {
    public RunConfiguration createTemplateConfiguration(Project project) {
      return new JavaFxRunConfiguration(project, this, "");
    }
  };

  public String getDisplayName() {
    return JavaFxBundle.message("javafx.application");
  }

  public String getConfigurationTypeDescription() {
    return JavaFxBundle.message("javafx.application");
  }

  public Icon getIcon() {
    return JavaFxFileType.INSTANCE.getIcon();
  }

  @NotNull
  public String getId() {
    return "JavaFXRunConfigurationType";
  }

  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{myFactory};
  }
}

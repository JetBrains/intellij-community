package org.jetbrains.plugins.groovy.config;

import com.intellij.ide.util.projectWizard.JavaModuleBuilder;

import javax.swing.*;

/**
 * @author peter
 */
public abstract class GroovyAwareModuleBuilder extends JavaModuleBuilder {
  private final String myBuilderId;
  private final String myPresentableName;
  private final String myDescription;
  private final Icon myBigIcon;

  protected GroovyAwareModuleBuilder(String builderId, String presentableName, String description, Icon bigIcon) {
    myBuilderId = builderId;
    myPresentableName = presentableName;
    myDescription = description;
    myBigIcon = bigIcon;
  }

  @Override
  public String getBuilderId() {
    return myBuilderId;
  }

  @Override
  public Icon getBigIcon() {
    return myBigIcon;
  }

  @Override
  public String getDescription() {
    return myDescription;
  }

  @Override
  public String getPresentableName() {
    return myPresentableName;
  }
}

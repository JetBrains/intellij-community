package org.jetbrains.plugins.javaFX;

import com.intellij.ide.projectWizard.TemplateBasedProjectType;

/**
 * @author Dmitry Avdeev
 *         Date: 20.09.13
 */
public class JavaFxProjectType extends TemplateBasedProjectType {

  public JavaFxProjectType() {
    super("/resources/projectTemplates/Java/JavaFX Application.zip");
  }

  @Override
  public String getId() {
    return "JavaFX";
  }
}

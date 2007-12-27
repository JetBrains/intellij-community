package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.DynamicProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

/**
 * User: Dmitry.Krasilschikov
 * Date: 23.11.2007
 */
public abstract class DynamicPropertiesManager implements ProjectComponent {
  public static final String DYNAMIC_PROPERTIES_DIR = "dynamicProperties";
  public static final String DYNAMIC_PROPERTIES_MODULE = "module";
  public static final String DYNAMIC_PROPERTIES_PROJECT = "project";

  @NotNull
  public static DynamicPropertiesManager getInstance(@NotNull Project project) {
    return project.getComponent(DynamicPropertiesManager.class);
  }

  /*
  * Return dynamic property with be added or null if not
  */

  @Nullable
  public abstract DynamicProperty addDynamicProperty(DynamicProperty dynamicProperty);

  /*
  * Return dynamic property with be removed or null if not
  */

  @Nullable
  public abstract DynamicProperty removeDynamicProperty(DynamicProperty dynamicProperty);

  /*
  * Find dynamic property in class with name
  */

  @Nullable
  public abstract Element findConcreateDynamicProperty(GrReferenceExpression referenceExpression, String moduleName, final String conatainingClassName, final String propertyName);

  /*
   * Gets type of property
   */
  @Nullable
  protected abstract String getTypeOfDynamicProperty(GrReferenceExpression referenceExpression, String moduleName, final String conatainingClassName, final String propertyName);

  /*
  * Finds dynamic property
  */

  @Nullable
  public abstract String[] findDynamicPropertiesOfClass(String moduleName, final String conatainingClassName);
}
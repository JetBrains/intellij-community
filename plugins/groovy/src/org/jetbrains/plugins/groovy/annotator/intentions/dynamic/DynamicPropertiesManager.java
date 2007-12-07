package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.DynamicProperty;

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
  public abstract Element findDynamicProperty(String moduleName, final String typeQualifiedName, final String propertyName);

  /*
  * Find dynamic property
  */

  @Nullable
  public abstract Element findDynamicProperty(DynamicProperty dynamicProperty);

  @Nullable
  public abstract Element findDynamicPropertyType(Element rootElement, final String typeQualifiedName);

}
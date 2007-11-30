package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
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

}
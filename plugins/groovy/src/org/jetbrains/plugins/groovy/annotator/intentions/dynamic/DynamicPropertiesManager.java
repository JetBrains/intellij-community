package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.virtual.DynamicPropertyVirtual;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import java.util.Set;

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
  public abstract DynamicPropertyVirtual addDynamicProperty(DynamicPropertyVirtual dynamicPropertyReal);

  /*
  * Return dynamic property with be removed or null if not
  */

  @Nullable
  public abstract DynamicPropertyVirtual removeDynamicProperty(DynamicPropertyVirtual dynamicProperty);

  /*
  * Return dynamic property with be removed or null if not
  */

  @Nullable
  public abstract void removeDynamicPropertiesOfClass(String moduleName, String className);

  /*
  * Find dynamic property in class with name
  */

  @Nullable
  public abstract Element findConcreteDynamicProperty(GrReferenceExpression referenceExpression, String moduleName, final String conatainingClassName, final String propertyName);

  /*
   * Gets type of property
   */
  @Nullable
  protected abstract String getTypeOfDynamicProperty(GrReferenceExpression referenceExpression, String moduleName, final String conatainingClassName, final String propertyName);

  /*
  * Finds dynamic properties of class
  */

  @NotNull
  public abstract String[] findDynamicPropertiesOfClass(String moduleName, final String conatainingClassName);

  /*
  * Finds dynamic property type
  */

  @NotNull
  public abstract String findDynamicPropertyType(String moduleName, String className, String propertyName);

  /*
  * Returns all dynamic properties
  */

  @NotNull
  public abstract DynamicPropertyVirtual[] getAllDynamicProperties(String moduleName);

  /*
  * Returns all containing classes
  */

  @NotNull
  public abstract Set<String> getAllContainingClasses(String moduleName);

  /*
   * Returns root element
   */

  public abstract Element getRootElement(String moduleName);

  /*
   * Adds dynamicPropertyChange listener
   */
  public abstract void addDynamicChangeListener(DynamicPropertyChangeListener listener);

  /*
   * Removes dynamicPropertyChange listener
   */
  public abstract void removeDynamicChangeListener(DynamicPropertyChangeListener listener);

  /*
  * Changes dynamic property type
  */
  public abstract DynamicPropertyVirtual replaceDynamicProperty(DynamicPropertyVirtual oldProperty, DynamicPropertyVirtual newProperty);

  /*
  * Changes dynamic property type
  */
  public abstract String replaceClassName(String moduleName, String oldClassName, String newClassName);

  /*
   * Fire changes
   */

  public abstract void fireChange();
}
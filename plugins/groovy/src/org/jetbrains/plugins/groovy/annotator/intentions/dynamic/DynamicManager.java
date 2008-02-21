package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.MethodSignature;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.virtual.DynamicVirtualProperty;

import java.util.Set;

/**
 * User: Dmitry.Krasilschikov
 * Date: 23.11.2007
 */
public abstract class DynamicManager implements ProjectComponent {
  public static final String DYNAMIC_PROPERTIES_DIR = "dynamicProperties";
  public static final String DYNAMIC_PROPERTIES_MODULE = "module";
  public static final String DYNAMIC_PROPERTIES_PROJECT = "project";

  @NotNull
  public static DynamicManager getInstance(@NotNull Project project) {
    return project.getComponent(DynamicManager.class);
  }

  /**
   * ************* Properties and methods *************
   */

  /*
  * Fire changes
  */
  public abstract void fireChange();

  /*
  * Returns root element
  */

  public abstract Element getRootElement(String moduleName);

  /*
  * Returns all containing classes
  */

  @NotNull
  public abstract Set<String> getAllContainingClasses(String moduleName);


  /*
  * Changes dynamic property type
  */
  public abstract String replaceClassName(String moduleName, String oldClassName, String newClassName);

  /*
  * Adds dynamicPropertyChange listener
  */
  public abstract void addDynamicChangeListener(DynamicChangeListener listener);

  /*
   * Removes dynamicPropertyChange listener
   */
  public abstract void removeDynamicChangeListener(DynamicChangeListener listener);

  /*
  * Changes dynamic property
  */

  /**
   * ********** Dymanic properties **************
   *
   * @param virtualElement
   */

  /*
  * Return dynamic property with be added or null if not
  */
  @Nullable
  public abstract DynamicVirtualElement addDynamicElement(DynamicVirtualElement virtualElement);

  /*
  * Return dynamic property with be removed or null if not
  */

  @Nullable
  public abstract DynamicVirtualElement removeDynamicElement(DynamicVirtualElement virtualElement);

  /*
  * Return dynamic property with be removed or null if not
  */

  public abstract void removeDynamicPropertiesOfClass(String moduleName, String className);

  /*
  * Find dynamic property in class with name
  */

//  @Nullable
//  public abstract Element findConcreteDynamicProperty(GrReferenceExpression referenceExpression, String moduleName, final String conatainingClassName, final String propertyName);

  @Nullable
  public abstract Element findConcreteDynamicProperty(String moduleName, final String conatainingClassName, final String propertyName);

  /*
   * Gets type of property
   */
//  @Nullable
//  protected abstract String getTypeOfDynamicProperty(String moduleName, final String conatainingClassName, final String propertyName);

  /*
  * Finds dynamic properties of class
  */

  @NotNull
  public abstract String[] findDynamicPropertiesOfClass(String moduleName, final String conatainingClassName);

  /*
  * Finds dynamic property type
  */

//  @NotNull
  public abstract String getPropertyType(String moduleName, String className, String propertyName);

  /*
  * Returns all dynamic properties
  */

  @NotNull
  public abstract DynamicVirtualProperty[] getAllDynamicProperties(String moduleName);

  /*
   * Replaces dymanic property by another dynamic property
   */

  public abstract DynamicVirtualProperty replaceDynamicProperty(DynamicVirtualProperty oldProperty, DynamicVirtualProperty newProperty);

  /*
  * Changes dynamic property
  */
  public abstract String replaceDynamicProperty(String moduleName, String className, String oldPropertyName, String newPropertyName);

  /**
   * *********** Dynamic methods *********
   */

  /*
  * Find dynamic property in class with name
  */
//  @Nullable
//  public abstract Element[] findConcreteDynamicMethodsWithName(String moduleName, final String conatainingClassName, final String name);

/*
  * Find dynamic property in class with name
  */

    @Nullable
  public abstract Element findConcreteDynamicMethod(String moduleName, final String conatainingClassName, final String name, final PsiType[] types);

  @NotNull
  public abstract Set<MethodSignature> findMethodsSignaturesOfClass(final String moduleName, final String conatainingClassName);

  @Nullable
  public abstract String getMethodReturnType(String moduleName, String className, String methodName, PsiType[] paramTypes);

//  @NotNull
//  public abstract Pair<String, String>[] getMethodsAttributes(final String moduleName, final String conatainingClassName, String methodName);
}
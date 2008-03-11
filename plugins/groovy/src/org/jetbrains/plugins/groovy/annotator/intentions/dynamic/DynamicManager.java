package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DItemElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DMethodElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DPropertyElement;

import java.util.Collection;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 23.11.2007
 */
public abstract class DynamicManager implements ProjectComponent, PersistentStateComponent<Element> {
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

  public abstract DRootElement getRootElement();

  /*
  * Returns all containing classes
  */

  @NotNull
  public abstract Collection<DClassElement> getAllContainingClasses();

  public abstract String replaceClassName(String oldClassName, String newClassName);

 /*
  * Adds dynamicPropertyChange listener
  */
  public abstract void addDynamicChangeListener(DynamicChangeListener listener);

  /*
   * Removes dynamicPropertyChange listener
   */
  public abstract void removeDynamicChangeListener(DynamicChangeListener listener);

  public abstract void addProperty(DClassElement classElement, DPropertyElement propertyElement);

  public abstract void addMethod(DClassElement classElement, DMethodElement methodElement);

  public abstract void removeClassElement(String className);

  public abstract void removePropertyElement(DPropertyElement propertyElement);

  @Nullable
  public abstract DPropertyElement findConcreteDynamicProperty(final String conatainingClassName, final String propertyName);

  @NotNull
  public abstract Collection<DPropertyElement> findDynamicPropertiesOfClass(final String conatainingClassName);

  @NotNull
  public abstract String[] getPropertiesNamesOfClass(final String conatainingClassName);

  @Nullable
  public abstract String getPropertyType(String className, String propertyName);

  public abstract void replaceDynamicMethod(DMethodElement oldElement, DMethodElement newElement);

  public abstract void replaceDynamicProperty(DPropertyElement oldElement, DPropertyElement newElement);

  public abstract String replaceDynamicPropertyName(String className, String oldPropertyName, String newPropertyName);

  public abstract String replaceDynamicPropertyType(String className, String propertyName, String oldPropertyType, String newPropertyType);

  @Nullable
  public abstract DMethodElement findConcreteDynamicMethod(final String conatainingClassName, final String name, final String[] types);

  @Nullable
  public abstract String getMethodReturnType(String className, String methodName, String[] paramTypes);

  public abstract void removeMethodElement(DMethodElement element);

  public abstract void removeItemElement(DItemElement element);

  public abstract void replaceDynamicMethodType(String className, String name, List<MyPair> myPairList, String oldType, String newType);

  @NotNull
  public abstract DClassElement getOrCreateClassElement(Module module, String className, boolean binded);

  public abstract DClassElement getClassElementByItem(DItemElement itemElement);

  public abstract void replaceDynamicMethodName(String className, String oldName, String newName, String[] types);
}
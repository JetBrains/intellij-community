// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.*;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicElementSettings;

import java.util.Collection;
import java.util.List;

public abstract class DynamicManager implements PersistentStateComponent<DRootElement> {

  public static @NotNull DynamicManager getInstance(@NotNull Project project) {
    return project.getService(DynamicManager.class);
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

  public abstract @NotNull Collection<DClassElement> getAllContainingClasses();

  public abstract void replaceClassName(final @Nullable DClassElement oldClassElement, String newClassName);

  public abstract void addProperty(DynamicElementSettings settings);

  public abstract void addMethod(DynamicElementSettings settings);

  public abstract void removeClassElement(DClassElement classElement);

  public abstract @Nullable DPropertyElement findConcreteDynamicProperty(final String containingClassName, final String propertyName);

  public abstract @NotNull Collection<DPropertyElement> findDynamicPropertiesOfClass(final String containingClassName);

  public abstract @Nullable String getPropertyType(String className, String propertyName);

  public abstract @Nullable String replaceDynamicPropertyName(String className, String oldPropertyName, String newPropertyName);

  public abstract @Nullable String replaceDynamicPropertyType(String className, String propertyName, String oldPropertyType, String newPropertyType);

  public abstract @Nullable DMethodElement findConcreteDynamicMethod(final String containingClassName, final String name, final String[] types);

  public abstract void removeItemElement(DItemElement element);

  public abstract void replaceDynamicMethodType(String className, String name, List<ParamInfo> myPairList, String oldType, String newType);

  public abstract @NotNull DClassElement getOrCreateClassElement(Project project, String className);

  public abstract DClassElement getClassElementByItem(DItemElement itemElement);

  public abstract void replaceDynamicMethodName(String className, String oldName, String newName, String[] types);

  public abstract Iterable<PsiMethod> getMethods(String classQname);

  public abstract Iterable<PsiVariable> getProperties(String classQname);

  public abstract DItemElement createDynamicElement(DynamicElementSettings settings);
}
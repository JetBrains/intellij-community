// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicManager;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ParamInfo;

import java.util.*;

public class DClassElement implements DNamedElement {
  public String myName;
  public Set<DPropertyElement> myProperties = new HashSet<>();
  public Set<DMethodElement> myMethods = new HashSet<>();

  @SuppressWarnings("UnusedDeclaration") //used for serialization
  public DClassElement() {
  }

  public DClassElement(Project project, String name) {
    myName = name;
    DynamicManager.getInstance(project).getRootElement().mergeAddClass(this);
  }

  public void addMethod(DMethodElement methodElement) {
    myMethods.add(methodElement);
  }

   void addMethods(Collection<DMethodElement> methods) {
    myMethods.addAll(methods);
  }

  public void addProperty(DPropertyElement propertyElement) {
    myProperties.add(propertyElement);
  }

  protected void addProperties(Collection<DPropertyElement> properties) {
    for (DPropertyElement property : properties) {
      addProperty(property);
    }
  }

  @Nullable
  public DPropertyElement getPropertyByName(String propertyName) {
    for (final DPropertyElement property : myProperties) {
      if (propertyName.equals(property.getName())) {
        return property;
      }
    }
    return null;
  }

  public Collection<DPropertyElement> getProperties() {
    return myProperties;
  }

  public Set<DMethodElement> getMethods() {
    return myMethods;
  }

  @Override
  public @NlsSafe String getName() {
    return myName;
  }

  @Override
  public void setName(@NlsSafe String name) {
    myName = name;
  }

  public void removeProperty(DPropertyElement name) {
    myProperties.remove(name);
  }

  public boolean removeMethod(DMethodElement methodElement) {
    return myMethods.remove(methodElement);
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DClassElement that = (DClassElement) o;

    if (myName != null ? !myName.equals(that.myName) : that.myName != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (myName != null ? myName.hashCode() : 0);
    result = 31 * result + (myProperties != null ? myProperties.hashCode() : 0);
    result = 31 * result + (myMethods != null ? myMethods.hashCode() : 0);
    return result;
  }

  @Nullable
  public DMethodElement getMethod(String methodName, String[] parametersTypes) {
    for (DMethodElement method : myMethods) {
      final List<ParamInfo> myPairList = method.getPairs();
      if (method.getName().equals(methodName)
          && Arrays.equals(QuickfixUtil.getArgumentsTypes(myPairList), parametersTypes)) return method;
    }
    return null;
  }

  public boolean containsElement(DItemElement itemElement){
    //noinspection SuspiciousMethodCalls
    return myProperties.contains(itemElement) ||
           (itemElement instanceof DMethodElement && myMethods.contains(itemElement));
  }
}

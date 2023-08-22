// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ParamInfo;

import java.util.List;

public class DynamicElementSettings {
  private String myName;
  private String myContainingClassName;
  private String myType;
  private boolean isMethod;
  private List<ParamInfo> myParams;
  private boolean isStatic;

  public void setContainingClassName(String newName) {
    myContainingClassName = newName;
  }

  public @NlsSafe String getContainingClassName() {
    return myContainingClassName;
  }

  public String getType() {
    return myType;
  }

  public void setType(String type) {
    this.myType = type;
  }

  public boolean isMethod() {
    return isMethod;
  }

  public void setMethod(boolean method) {
    isMethod = method;
  }

  public List<ParamInfo> getParams() {
    return myParams;
  }

  public void setParams(List<ParamInfo> pairs) {
    myParams = pairs;
  }

  public String getName() {
    return myName;
  }

  public void setName(String name) {
    myName = name;
  }

  public boolean isStatic() {
      return isStatic;
  }

  public void setStatic(boolean aStatic) {
    isStatic = aStatic;
  }
}
